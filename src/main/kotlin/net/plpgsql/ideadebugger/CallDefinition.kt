/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.database.dialects.postgres.model.PgRoutine
import com.intellij.database.psi.DbRoutine
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.psi.PgParameterDefinitionImpl
import com.intellij.sql.psi.SqlExpressionList
import com.intellij.sql.psi.SqlIdentifier
import com.intellij.sql.psi.SqlReferenceExpression
import com.jetbrains.rd.util.first
import net.plpgsql.ideadebugger.command.PlExecutor

/**
 * Encapsulate routine call information
 */
class CallDefinition(
    var debugMode: DebugMode,
    private var psi: PsiElement?,
    var query: String
) {

    var schema: String = DEFAULT_SCHEMA
    var routine: String? = null
    var oid: Long = 0L
    val args = mutableMapOf<String, String?>()
    var selectionOk: Boolean = false

    constructor(routine: PgRoutine) : this(DebugMode.INDIRECT, null, "") {
        this.oid = routine.objectId
        this.routine = routine.name
        this.schema = routine.schemaName?: "public"
        this.selectionOk = true
    }

    /**
     * Get routine information from Database Tool internal API
     */
    fun identify() {
        psi?.let {
            runReadAction {
                PsiTreeUtil.collectElements(psi) { it.reference != null }.firstOrNull()?.references?.forEach { ref ->
                    ref.resolve()?.let { it ->
                        if (it is DbRoutine) {
                            val delegate = it.delegate
                            if (delegate is PgRoutine) {
                                oid = delegate.objectId
                                schema = delegate.schemaName ?: DEFAULT_SCHEMA
                                routine = delegate.name
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Get routine information from SQL queries
     */
    fun identify(executor: PlExecutor) {
        parseFunctionCall()
        searchCallee(executor)
    }

    /**
     * If the statement is a candidate for selection
     */
    fun canSelect(): Boolean =debugMode != DebugMode.NONE && psi != null

    /**
     * If the selected statement can be debugged
     */
    fun canDebug(): Boolean = canSelect() && selectionOk

    /**
     * If the debugger can be started
     */
    fun canStartDebug(): Boolean = selectionOk && oid != 0L

    /**
     * Extract information from the original statement
     */
    fun parseFunctionCall() {
        runReadAction {
            val funcEl = PsiTreeUtil.findChildOfType(psi, SqlReferenceExpression::class.java)
            val func = funcEl?.let {
                PsiTreeUtil.findChildrenOfType(funcEl, SqlIdentifier::class.java).map {
                    it.text.trim().removeSurrounding("\"")
                }
            } ?: listOf()

            if (func.isNotEmpty()) {
                routine = func.last()
            }
            if (func.size > 1) {
                schema = func.first()
            }

            if (routine == null) {
                return@runReadAction
            }

            when (debugMode) {
                DebugMode.DIRECT -> {
                    PsiTreeUtil.findChildOfType(
                        psi,
                        SqlExpressionList::class.java
                    )?.children?.map {
                        it.text.trim()
                    }?.filter {
                        it != "" && it != "," && !it.startsWith("--")
                    }?.forEachIndexed { idx, it ->
                        args["arg_$idx"] = it
                    }
                }
                DebugMode.INDIRECT -> {
                    PsiTreeUtil.findChildrenOfType(psi, PgParameterDefinitionImpl::class.java).mapNotNull { p ->
                        PsiTreeUtil.findChildOfType(p, SqlIdentifier::class.java)?.text
                    }.forEach {
                        args[it] = null
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Execute SQL to get information from pg_catalog
     */
    private fun searchCallee(executor: PlExecutor) {
        val rt = routine?: ""
        val functions = executor.getCallArgs(schema, rt).filter {
            if (args.isEmpty()) {
                it.nb == 0 || it.default
            } else {
                if (debugMode == DebugMode.DIRECT) it.nb >= args.size else it.nb == args.size
            }
        }

        val plArgs = functions.groupBy {
            it.oid
        }

        if (plArgs.size == 1) {
            oid = plArgs.first().key
            return
        }

        when (debugMode) {
            DebugMode.DIRECT -> {
                oid = plArgs.filter {
                    //Check args length
                    val minimalCount = it.value.count { arg -> !arg.default }
                    args.size >= minimalCount && args.size <= it.value.size
                }.filterValues { arg ->
                    //Map call values
                    val namedValues = args.values.mapIndexedNotNull { index, s ->
                        s?.let {
                            if (s.contains(":=")) s.split(":=")[1].trim() to s.split(":=")[2].trim()
                            else arg[index].name to s.trim()
                        }
                    }
                    //Build the test query like SELECT [(cast([value] AS [type]) = [value])] [ AND ...]
                    val query = namedValues.joinToString(prefix = "(SELECT (", separator = " AND ", postfix = ")) t") {
                        val type = arg.find { plFunctionArg -> plFunctionArg.name == it.first }?.type
                        "(cast(${it.second} as ${type}) = ${it.second})"
                    }
                    executor.testBooleanStatement(query)
                }.map { it.key }.firstOrNull() ?: oid
            }
            DebugMode.INDIRECT -> {
                oid = plArgs.filter { f ->
                    val names = f.value.map { a ->
                        a.name
                    }
                    if (names.size != args.size) {
                        false
                    } else {
                        val pairList = names.zip(args.keys)
                        pairList.all { (elt1, elt2) ->
                            elt1 == elt2
                        }
                    }
                }.map { it.key }.firstOrNull() ?: oid
            }
            else -> {}
        }

    }
}