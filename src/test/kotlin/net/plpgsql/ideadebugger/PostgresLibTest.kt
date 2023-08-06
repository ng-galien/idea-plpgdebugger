package net.plpgsql.ideadebugger

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresLibTest: AbstractDebuggerTest() {
    @Test
    fun `shared debugger library is found`() {
        
        assertFalse("Shared library list is not empty") {
            debugger().getSharedPreloadLibraries().isEmpty()
        }
        assertTrue("Debugger extension is installed") {
            debugger().hasDebuggerSharedLibrary()
        }
    }

    @Test
    fun `extension is installed`() {
        assertFalse("Extension list is not empty") {
            debugger().getExtensions().isEmpty()
        }
        assertTrue("Debugger extension is installed") {
            debugger().hasExtensionInstalled()
        }
    }


    private fun debugger(): PostgresLib = db().attach(PostgresLib::class.java)

}