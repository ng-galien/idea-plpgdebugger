/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlBlockStatement
import com.intellij.sql.psi.impl.SqlTokenElement
import com.intellij.testFramework.LightVirtualFile
import java.nio.charset.Charset

class PlFunctionSource(project: Project, def: PlApiFunctionDef) : LightVirtualFile(
    "${def.schema}.${def.name}",
    getPlLanguage(),
    def.source
) {

    val oid: Long = def.oid
    val md5 = def.md5
    var start: Int = 0
    var end: Int = 0
    var codeRange = Pair(0, 0)
    val lineRangeCount: Int by lazy {
        codeRange.second - codeRange.first
    }

    init {
        runReadAction {
            PsiManager.getInstance(project).findFile(this)?.let { psi ->
                PsiTreeUtil.findChildOfType(psi, SqlBlockStatement::class.java)?.let { block ->
                    PsiDocumentManager.getInstance(psi.project).getDocument(psi)?.let { doc ->
                        start = doc.getLineNumber(block.textOffset) - 2
                        end = doc.getLineNumber(block.textOffset + block.textLength) - 2

                        val beginEl = PsiTreeUtil.collectElements(block, PsiElementFilter {
                            it is SqlTokenElement && it.text.uppercase() == "BEGIN"
                        }).firstOrNull()

                        val endEl = PsiTreeUtil.collectElements(block, PsiElementFilter {
                            it is SqlTokenElement && it.text.uppercase() == "END"
                        }).lastOrNull()

                        if (beginEl != null && endEl != null) {
                            codeRange = Pair(doc.getLineNumber(beginEl.textOffset), doc.getLineNumber(endEl.textOffset))
                        }
                    }
                }
            }
        }
    }

    override fun getCharset(): Charset = Charsets.UTF_8

    override fun getFileSystem(): VirtualFileSystem = PlVirtualFileSystem.getInstance()

    override fun getPath(): String = "$oid"

    override fun isWritable(): Boolean = true
}