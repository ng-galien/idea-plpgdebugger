/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.sql.SqlFileType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

class PlEditorProvider : XDebuggerEditorsProvider() {

    companion object {
        val INSTANCE = PlEditorProvider()
    }

    override fun getFileType(): FileType {
        return SqlFileType.INSTANCE
    }

    override fun createDocument(
        project: Project,
        expression: XExpression,
        sourcePosition: XSourcePosition?,
        mode: EvaluationMode
    ): Document {
        return EditorFactory.getInstance().createDocument("")
    }


}