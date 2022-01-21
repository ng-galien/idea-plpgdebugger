/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlLineBreakpointProperties
import com.intellij.database.debugger.SqlLineBreakpointType
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.psi.PgCreateFunctionStatementImpl
import com.intellij.sql.psi.impl.SqlTokenElement
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import javax.swing.Icon


class PlLineBreakpointProperties(val file: VirtualFile, val line: Int) : SqlLineBreakpointProperties() {}

/*
TODO All this code must be merge with code analysis of PlFile
 */
class PlLineBreakpointType : SqlLineBreakpointType<PlLineBreakpointProperties>("plpg_line_breakpoint", "PL/pg SQL") {

    companion object {
        val INSTANCE = PlLineBreakpointType()
    }

    private val fileRegex = Regex("^.+\\..+\\[\\d+]\\.sql\$")
    //private val fileRegexTest = Regex("^.+\\.sql\$")

    override fun createBreakpointProperties(file: VirtualFile, line: Int): PlLineBreakpointProperties {
        return PlLineBreakpointProperties(file, line)
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        if (!fileRegex.matches(file.name)) {
            return false
        }

        var goodCandidate = false
        val psi: PsiFile? = PsiManager.getInstance(project).findFile(file)
        val funcEl = psi?.firstChild
        if (funcEl is PgCreateFunctionStatementImpl) {
            val begin = PsiTreeUtil.collectElements(funcEl, PsiElementFilter {
                it is SqlTokenElement && it.text.uppercase() == "BEGIN"
            }).firstOrNull()
            if (begin != null) {
                val doc = FileDocumentManager.getInstance().getDocument(file)
                val beginPos = doc?.getLineNumber(begin.textOffset)
                if (beginPos != null) {
                    goodCandidate = beginPos < line
                }
            }
        }
        return goodCandidate
    }

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<PlLineBreakpointProperties>,
        project: Project
    ): XDebuggerEditorsProvider? {
        return null
    }

    override fun getEnabledIcon(): Icon {
        return AllIcons.Providers.Postgresql

    }

}



