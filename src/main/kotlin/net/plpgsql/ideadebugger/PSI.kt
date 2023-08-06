package net.plpgsql.ideadebugger

import com.intellij.database.dialects.postgres.model.PgRoutine
import com.intellij.database.psi.DbRoutine
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlCreateTriggerStatement
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlIdentifier
import com.intellij.sql.psi.SqlParameterList
import com.intellij.sql.psi.SqlStatement
import com.intellij.sql.psi.SqlVariableDefinition
import com.intellij.sql.psi.impl.SqlTokenElement
import com.intellij.usageView.UsageInfo


fun statementOk(statement: SqlStatement): PsiElement? {
    return PsiTreeUtil.findChildrenOfType(statement, SqlFunctionCallExpression::class.java).firstOrNull()
}

fun findProcedure(project: Project, file: VirtualFile?, range: RangeMarker?): PostgresLib.Oid? {
    if (file == null || range == null) {
        return null
    }
    return runReadAction {
        psiElement(project, file, range)?.let {
            unWrapPgRoutine(it)?.let { routine ->
                PostgresLib.Oid(routine.objectId)
            }
        }
    }
}

fun findProcedure(psiElement: PsiElement): PostgresLib.Oid? {
    return runReadAction {
        unWrapPgRoutine(psiElement)?.let { routine ->
            PostgresLib.Oid(routine.objectId)
        }
    }
}

fun psiElement(project: Project, file: VirtualFile?, range: RangeMarker?): PsiElement? {
    if (file == null || range == null) {
        return null
    }
    return FileDocumentManager.getInstance().getDocument(file)?.let {
        PsiDocumentManager.getInstance(project).getPsiFile(it)?.findElementAt(range.startOffset)
    }
}

fun unWrapPgRoutine(psiElement: PsiElement): PgRoutine? {
    return PsiTreeUtil.collectElements(psiElement) { it.reference != null }
        .firstOrNull()?.references
        ?.map { it.resolve() }
        ?.filterIsInstance<DbRoutine>()
        ?.map { it.delegate }
        ?.filterIsInstance<PgRoutine>()
        ?.firstOrNull()
}

fun isTriggerFunction(psiElement: PsiElement): Boolean =
    PsiTreeUtil.findChildOfType(psiElement, SqlCreateTriggerStatement::class.java) != null

fun codeRange(psiElement: PsiElement): Pair<Int, Int> {
    PsiTreeUtil.collectElements(psiElement){
        it is SqlTokenElement && it.text.lowercase() == "\$function\$"
    }.map {
        it.textOffset
    }.toList().let { offsets ->
        if (offsets.size == 2) {
            return Pair(offsets[0]-1, offsets[1])
        } else {
            return Pair(0, 0)
        }
    }
}

fun computeFileVariables(psiElement: PsiElement): List<FileVariable> {
    return allDeclaredVariables(psiElement)
        .flatMap { variable ->
            identifiers(variable).map {
                FileVariable(unquote(it.text), isParameter(variable), it)
        }
    }.toList()
}

fun usagesOfVariable(psiElement: PsiElement): List<UsageInfo> =
    ReferencesSearch.search(psiElement).map { UsageInfo(it) }.toList()

private fun allDeclaredVariables(psiElement: PsiElement) =
    PsiTreeUtil.findChildrenOfAnyType(psiElement, SqlParameterList::class.java, SqlVariableDefinition::class.java)

private fun identifiers(psiElement: PsiElement) =
    PsiTreeUtil.findChildrenOfAnyType(psiElement, SqlIdentifier::class.java)

private fun isParameter(psiElement: PsiElement) = psiElement is SqlVariableDefinition