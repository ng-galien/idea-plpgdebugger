/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2026. Alexandre Boyer.
 */

package net.plpgsql.ideadebugger

import com.intellij.database.debugger.SqlLineBreakpointIdentity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import net.plpgsql.ideadebugger.breakpoint.PlLineBreakpointProperties
import net.plpgsql.ideadebugger.breakpoint.PlLineBreakpointType
import net.plpgsql.ideadebugger.command.PlApiFunctionDef
import net.plpgsql.ideadebugger.run.breakpointPath
import net.plpgsql.ideadebugger.run.serverBreakpointLine
import net.plpgsql.ideadebugger.vfs.PlFunctionSource
import net.plpgsql.ideadebugger.vfs.PlVirtualFileSystem
import java.security.MessageDigest

class PlLineBreakpointTypeTest : BasePlatformTestCase() {

    fun testPlPgBreakpointWinsOverGenericSqlBreakpointTypes() {
        val sourceCode = requireNotNull(javaClass.getResource("/function_without_declare.sql"))
            .readText()
            .replace("$$", "\$function\$")
        val definition = PlApiFunctionDef(
            4242,
            "public",
            "breakpoint_priority",
            sourceCode,
            sourceCode.md5()
        )
        val source = PlFunctionSource(project, definition, definition.md5)
        val line = source.codeRange.first + 1

        val eligibleTypes = XDebuggerUtil.getInstance().lineBreakpointTypes
            .filter { it.canPutAt(source, line, project) }
        val selectedType = eligibleTypes.maxByOrNull { it.priority }

        assertTrue(
            "The regression requires DatabaseTools' Oracle breakpoint type to compete for this line",
            eligibleTypes.any { it.id == "OracleBreakpointType" }
        )
        assertNotNull("At least one breakpoint type must accept a PL/pgSQL source line", selectedType)
        assertEquals(PlLineBreakpointType::class.java, selectedType!!::class.java)
        assertTrue(selectedType.priority > 0)
    }

    fun testWrappedPlPgSourceUsesRegisteredSourceCodeRange() {
        val source = functionSource(4343)
        PlVirtualFileSystem.Util.getInstance().registerNewDefinition(source)
        val wrapper = WrappedPlVirtualFile(source)
        val type = PlLineBreakpointType()

        assertTrue(type.canPutAt(wrapper, source.codeRange.first + 1, project))
        assertFalse(type.canPutAt(wrapper, source.codeRange.first, project))
        assertFalse(type.canPutAt(wrapper, source.codeRange.second, project))
    }

    fun testBreakpointTargetAndServerLineMappings() {
        assertEquals("16424", breakpointPath("plpgsql://16424"))
        assertEquals("16424", breakpointPath("plpgsql:///16424"))
        assertEquals(6, serverBreakpointLine(editorLine = 9, sourceStart = 3))
    }

    fun testPlPgBreakpointCanBeAddedAndRemovedFromDebuggerManager() {
        val source = functionSource(4545)
        val line = source.codeRange.first + 1
        val type = XDebuggerUtil.getInstance().lineBreakpointTypes
            .filterIsInstance<PlLineBreakpointType>()
            .single()
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        val breakpoint = manager.addLineBreakpoint(
            type,
            source.url,
            line,
            type.createBreakpointProperties(source, line)
        )

        assertContainsElements(manager.allBreakpoints.asList(), breakpoint)
        assertEquals("plpg_line_breakpoint", breakpoint.type.id)
        assertEquals(source.url, breakpoint.fileUrl)
        assertEquals(line, breakpoint.line)
        val serializedState = XmlSerializer.serialize(requireNotNull(breakpoint.properties?.state))
        val restoredState = XmlSerializer.deserialize(
            serializedState,
            SqlLineBreakpointIdentity::class.java
        )
        val restoredProperties = PlLineBreakpointProperties()
        restoredProperties.loadState(restoredState)
        assertNotNull(restoredProperties.state)

        manager.removeBreakpoint(breakpoint)
        assertFalse(manager.allBreakpoints.contains(breakpoint))
    }

    private fun functionSource(oid: Long): PlFunctionSource {
        val sourceCode = requireNotNull(javaClass.getResource("/function_without_declare.sql"))
            .readText()
            .replace("$$", "\$function\$")
        val definition = PlApiFunctionDef(
            oid,
            "public",
            "breakpoint_$oid",
            sourceCode,
            sourceCode.md5()
        )
        return PlFunctionSource(project, definition, definition.md5)
    }
}

private class WrappedPlVirtualFile(source: PlFunctionSource) : LightVirtualFile(
    source.name,
    source.fileType,
    source.content
) {
    private val sourcePath = source.path

    override fun getFileSystem() = PlVirtualFileSystem.Util.getInstance()

    override fun getPath(): String = "/$sourcePath"
}

private fun String.md5(): String =
    MessageDigest.getInstance("MD5")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
