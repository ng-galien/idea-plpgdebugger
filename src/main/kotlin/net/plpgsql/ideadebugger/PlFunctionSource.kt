/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlBlockStatement
import com.intellij.testFramework.LightVirtualFile
import java.nio.charset.Charset
import kotlin.properties.Delegates

class PlFunctionSource(project: Project, def: PlApiFunctionDef) : LightVirtualFile(
    "${def.schema}.${def.name}",
    getPlLanguage(),
    def.source
) {

    val oid: Long = def.oid
    val md5 = def.md5
    var start: Int = 0
    var end: Int = 0

    init {
        runReadAction {
            PsiManager.getInstance(project).findFile(this)?.let { psi ->
                PsiTreeUtil.findChildOfType(psi, SqlBlockStatement::class.java)?.let { block ->
                    PsiDocumentManager.getInstance(psi.project).getDocument(psi)?.let { doc ->
                        start = doc.getLineNumber(block.textOffset) - 2
                        end = doc.getLineNumber(block.textOffset + block.textLength) - 2
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