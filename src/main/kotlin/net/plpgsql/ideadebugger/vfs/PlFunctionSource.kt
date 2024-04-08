/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.plpgsql.ideadebugger.vfs

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.*
import com.intellij.sql.psi.impl.SqlTokenElement
import com.intellij.testFramework.LightVirtualFile
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.getPlLanguage
import net.plpgsql.ideadebugger.unquote
import java.nio.charset.Charset

class PlFunctionSource(project: Project, def: PlApiFunctionDef, val md5: String) : LightVirtualFile(
    "${def.schema}.${def.name}[${def.oid}]",
    getPlLanguage(),
    def.source
) {

    companion object {
        val triggerArgs = listOf("NEW", "OLD", "TG_NAME", "TG_WHEN", "TG_LEVEL", "TG_OP", "TG_RELID", "TG_RELNAME",
        "TG_TABLE_NAME", "TG_TABLE_SCHEMA", "TG_NARGS", "TG_ARGV")
    }

    val oid: Long = def.oid
    private var isTrigger = false
    var start: Int = 0
    var codeRange = Pair(0, 0)
    val lineRangeCount: Int by lazy {
        codeRange.second - codeRange.first
    }
    val psiArgs = mutableMapOf<String, PsiElement>()
    val psiVariables = mutableMapOf<String, PsiElement>()
    val psiUse = mutableMapOf<String, MutableList<Pair<Int, PsiElement>>>()

    init {
        runReadAction {
            PsiManager.getInstance(project).findFile(this)?.let { psi ->

                PsiTreeUtil.findChildOfType(psi, SqlCreateTriggerStatement::class.java).let { isTrigger = true }

                PsiTreeUtil.findChildOfType(psi, SqlParameterList::class.java).let { params ->
                    PsiTreeUtil.findChildrenOfType(params, SqlIdentifier::class.java).toList().forEach { arg ->
                        psiArgs[unquote(arg.text)] = arg
                    }
                }
                PsiTreeUtil.findChildrenOfType(psi, SqlVariableDefinition::class.java).forEach{ params ->
                    PsiTreeUtil.findChildrenOfType(params, SqlIdentifier::class.java).toList().forEach { v ->
                        psiVariables[unquote(v.text)] = v
                    }
                }
                PsiTreeUtil.collectElements(psi){
                    it is SqlTokenElement && it.text.lowercase() == "\$function\$"
                }.firstOrNull()?.let { el ->
                    PsiDocumentManager.getInstance(psi.project).getDocument(psi)?.let { doc ->
                        start = doc.getLineNumber(el.textOffset) - 1
                    }
                }
                PsiTreeUtil.findChildOfType(psi, SqlBlockStatement::class.java)?.let { block ->
                    PsiDocumentManager.getInstance(psi.project).getDocument(psi)?.let { doc ->
                        val beginEl = PsiTreeUtil.collectElements(block){
                            it is SqlTokenElement && it.text.uppercase() == "BEGIN"
                        }.firstOrNull()

                        val endEl = PsiTreeUtil.collectElements(block) {
                            it is SqlTokenElement && it.text.uppercase() == "END"
                        }.lastOrNull()

                        if (beginEl != null && endEl != null) {
                            codeRange = Pair(doc.getLineNumber(beginEl.textOffset), doc.getLineNumber(endEl.textOffset))
                        }

                        //SQL_SET_ASSIGNMENT -> SQL_COLUMN_REFERENCE -> SQL_IDENTIFIER
                        //SQL_SELECT_STATEMENT -> SQL_SELECT_INTO_CLAUSE -> SQL_VARIABLE_REFERENCE -> SQL_IDENTIFIER
                        PsiTreeUtil.findChildrenOfAnyType(
                            block,
                            SqlSetAssignment::class.java,
                            SqlSelectIntoClause::class.java).filter {
                            doc.getLineNumber(it.textOffset) >= codeRange.first
                        }.forEach { set ->
                            PsiTreeUtil.findChildrenOfType(set, SqlIdentifier::class.java).filter{ id ->
                                psiArgs.containsKey(unquote(id.text))
                                        || psiVariables.containsKey(unquote(id.text))
                                        || (isTrigger && triggerArgs.contains(id.text.uppercase()))
                            }.forEach { el ->
                                val toAdd = Pair(doc.getLineNumber(el.textOffset), el)
                                psiUse[unquote(el.text)]?.let {
                                    it.find { test ->
                                        test.first == toAdd.first
                                                && unquote(test.second.text) == unquote(toAdd.second.text)
                                    } ?: it.add(toAdd)
                                } ?: kotlin.run {
                                    psiUse[unquote(el.text)] = mutableListOf(toAdd)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun positionToLine(position: Int): Int {
        return position + start
    }

    override fun getCharset(): Charset = Charsets.UTF_8

    override fun getFileSystem(): VirtualFileSystem = PlVirtualFileSystem.Util.getInstance()

    override fun getPath(): String = "$oid"

    override fun isWritable(): Boolean = true
}
