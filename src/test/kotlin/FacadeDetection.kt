import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.SqlFileType
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlFile
import com.intellij.sql.psi.SqlSelectStatement
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.getCallStatement
import net.plpgsql.ideadebugger.parseFunctionCall
import net.plpgsql.ideadebugger.settings.PlPluginSettings
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


/*
 * Copyright (c) 2022. Alexandre Boyer
 */

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class FacadeDetection : BasePlatformTestCase() {
    
    @Test
    fun testSelectDetection() {
        val testSuite = mapOf(
            "SELECT\n * \nFROM \nfunc();" to Pair("func()",  Pair(listOf("func"), listOf<String>())),
            "SELECT * FROM func();" to Pair("func()",  Pair(listOf("func"), listOf<String>())),
            "SELECT func();" to Pair("func()",  Pair(listOf("func"), listOf<String>())),
            "SELECT sch.func();" to Pair("sch.func()",  Pair(listOf("sch", "func"), listOf<String>())),
            "SELECT sch.func('arg');" to Pair("sch.func('arg')",  Pair(listOf("sch", "func"), listOf<String>("'arg'"))),
            "SELECT sch.func('arg', 123);" to Pair("sch.func('arg', 123)",  Pair(listOf("sch", "func"), listOf<String>("'arg'", "123"))),
            "SELECT sch.func(\n'arg', \n123);" to Pair("sch.func(\n'arg', \n123)",  Pair(listOf("sch", "func"), listOf<String>("'arg'", "123"))),
        )
        Assertions.assertNotNull(myFixture)

        testSuite.forEach {
            val psiFile = createLightFile("dummy.sql", PgDialect.INSTANCE, it.key)
            val stmt = psiFile.children.first() as SqlStatement
            val call = getCallStatement(stmt, PlPluginSettings())
            Assertions.assertNotNull(call)
            Assertions.assertEquals(DebugMode.DIRECT, call.first)
            Assertions.assertEquals(it.value.first, call.second?.text)

            val parsed = call.second?.let { parseFunctionCall(call.second!!, call.first) }
            Assertions.assertNotNull(parsed)
            if (parsed != null) {
                Assertions.assertEquals(it.value.second.first, parsed.first)
                Assertions.assertEquals(it.value.second.second, parsed.second)
            }
        }
    }

    @Test
    fun testCallDetection() {
        val testSuite = mapOf(
            "CALL\nfunc();" to Pair("func()",  Pair(listOf("func"), listOf<String>())),
            "CALL func();" to Pair("func()",  Pair(listOf("func"), listOf<String>())),
            "CALL sch.func();" to Pair("sch.func()",  Pair(listOf("sch", "func"), listOf<String>())),
            "CALL sch.func('arg');" to Pair("sch.func('arg')",  Pair(listOf("sch", "func"), listOf<String>("'arg'"))),
            "CALL sch.func('arg', 123);" to Pair("sch.func('arg', 123)",  Pair(listOf("sch", "func"), listOf<String>("'arg'", "123"))),
            "CALL sch.func(\n'arg', \n123);" to Pair("sch.func(\n'arg', \n123)",  Pair(listOf("sch", "func"), listOf<String>("'arg'", "123"))),
        )
        Assertions.assertNotNull(myFixture)

        testSuite.forEach {
            val psiFile = createLightFile("dummy.sql", PgDialect.INSTANCE, it.key)
            val stmt = psiFile.children.first() as SqlStatement
            val call = getCallStatement(stmt, PlPluginSettings())
            Assertions.assertNotNull(call)
            Assertions.assertEquals(DebugMode.DIRECT, call.first)
            Assertions.assertEquals(it.value.first, call.second?.text)

            val parsed = call.second?.let { parseFunctionCall(call.second!!, call.first) }
            Assertions.assertNotNull(parsed)
            if (parsed != null) {
                Assertions.assertEquals(it.value.second.first, parsed.first)
                Assertions.assertEquals(it.value.second.second, parsed.second)
            }
        }
    }

}