/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.console.session.DatabaseSessionManager.getFacade
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.database.debugger.SqlDebuggerEditorsProvider
import com.intellij.lang.Language
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlIdentifier
import com.intellij.sql.psi.SqlPsiFacade
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.InlineDebuggerHelper
import com.jetbrains.rd.util.first




fun getPlLanguage(): Language = PgDialect.INSTANCE

fun plNull(value: String) = (value.uppercase() == "NULL")

fun parseFunctionCall(callExpr: SqlFunctionCallExpression): Pair<List<String>, List<String>> {
    val (func, args) = runReadAction {
        val func = PsiTreeUtil.findChildrenOfAnyType(callExpr, SqlIdentifier::class.java).map {
            it.text.trim()
        }
        val values = PsiTreeUtil.findChildOfType(
            callExpr,
            SqlExpressionList::class.java
        )?.children?.map { it.text.trim() }?.filter { it != "" && it != "," && !it.startsWith("--") }
        Pair(first = func, second = values ?: listOf())
    }
    return Pair(func, args)
}

fun getDefaultSchema(): String = "public"


object PlEditorProvider : SqlDebuggerEditorsProvider() {
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

