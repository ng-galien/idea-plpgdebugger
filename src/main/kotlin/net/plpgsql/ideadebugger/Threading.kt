/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction

/**
 * Runs PSI/model reads without blocking a dispatcher thread while IntelliJ is
 * waiting for a pending write action.
 */
internal inline fun <T> withReadAction(crossinline action: () -> T): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
        return action()
    }
    return ReadAction.nonBlocking<T> { action() }.executeSynchronously()
}
