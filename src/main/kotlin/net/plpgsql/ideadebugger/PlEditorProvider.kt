/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlDebuggerEditorsProvider
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.sql.psi.SqlPsiFacade
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.InlineDebuggerHelper

class PlEditorProvider : SqlDebuggerEditorsProvider() {

    companion object {
        val INSTANCE = PlEditorProvider()
    }

    override fun createExpressionCodeFragment(
        project: Project,
        text: String,
        context: PsiElement?,
        isPhysical: Boolean
    ): PsiFile {
        return SqlPsiFacade.getInstance(project)
            .createExpressionFragment(getPlLanguage(), null, null, text)
    }

    override fun getFileType(): FileType {
        return super.getFileType()
    }

    override fun createDocument(
        project: Project,
        expression: XExpression,
        sourcePosition: XSourcePosition?,
        mode: EvaluationMode
    ): Document {
        return super.createDocument(project, expression, sourcePosition, mode)
    }



    override fun createDocument(
        project: Project,
        expression: XExpression,
        context: PsiElement?,
        mode: EvaluationMode
    ): Document {
        return super.createDocument(project, expression, context, mode)
    }

    override fun getSupportedLanguages(context: PsiElement?): MutableCollection<Language> {
        return super.getSupportedLanguages(context)
    }

    override fun getSupportedLanguages(
        project: Project,
        sourcePosition: XSourcePosition?
    ): MutableCollection<Language> {
        return super.getSupportedLanguages(project, sourcePosition)
    }

    override fun createExpression(
        project: Project,
        document: Document,
        language: Language?,
        mode: EvaluationMode
    ): XExpression {
        return super.createExpression(project, document, language, mode)
    }

    override fun getInlineDebuggerHelper(): InlineDebuggerHelper {
        return super.getInlineDebuggerHelper()
    }
}