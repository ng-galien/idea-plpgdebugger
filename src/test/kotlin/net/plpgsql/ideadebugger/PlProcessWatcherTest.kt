/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 */

package net.plpgsql.ideadebugger

import junit.framework.TestCase
import net.plpgsql.ideadebugger.service.PlProcessWatcherImpl

class PlProcessWatcherTest : TestCase() {

    fun testInitializationCanOnlyBeReservedOnce() {
        val watcher = PlProcessWatcherImpl()

        assertTrue(watcher.tryReserveInitialization())
        assertTrue(watcher.isInitializing())
        assertFalse(watcher.tryReserveInitialization())

        watcher.releaseInitialization()

        assertFalse(watcher.isInitializing())
        assertTrue(watcher.tryReserveInitialization())
    }
}
