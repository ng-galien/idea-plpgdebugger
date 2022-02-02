import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.SqlFileType
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
        Assertions.assertNotNull(myFixture)
        val psiFile = myFixture.configureByText(SqlFileType.INSTANCE,
            """
                
            SELECT test_debug();
                
            SELECT test_debug('TEST', 123);
                
            SELECT public.test_debug('TEST', 123);
               
            """.trimIndent()

        )
        val sqlFile = assertInstanceOf(psiFile, SqlFile::class.java)
        Assertions.assertFalse(PsiErrorElementUtil.hasErrors(project, sqlFile.virtualFile))

        val elements = PsiTreeUtil.findChildrenOfType(psiFile, SqlSelectStatement::class.java).toList()
        Assertions.assertEquals(3, elements.size)

        // SELECT test_debug();
        var stmt = elements[0] as SqlStatement
        var call = getCallStatement(stmt, PlPluginSettings())
        Assertions.assertNotNull(call)
        Assertions.assertEquals(DebugMode.DIRECT, call.first)
        Assertions.assertEquals("test_debug()", call.second?.text)
        var parsed = call.second?.let { parseFunctionCall(it, call.first) }
        Assertions.assertNotNull(parsed)
        if (parsed != null) {
            Assertions.assertEquals(listOf("test_debug"), parsed.first)
            Assertions.assertEquals(listOf<String>(), parsed.second)
        }

        // SELECT test_debug('TEST', 123);;
        stmt = elements[1] as SqlStatement
        call = getCallStatement(stmt, PlPluginSettings())
        Assertions.assertNotNull(call)
        Assertions.assertEquals(DebugMode.DIRECT, call.first)
        Assertions.assertEquals("test_debug('TEST', 123)", call.second?.text)
        parsed = call.second?.let { parseFunctionCall(it, call.first) }
        Assertions.assertNotNull(parsed)
        if (parsed != null) {
            Assertions.assertEquals(listOf("test_debug"), parsed.first)
            Assertions.assertEquals(listOf("'TEST'", "123"), parsed.second)
        }

        // SELECT public.test_debug('TEST', 123);
        stmt = elements[2] as SqlStatement
        call = getCallStatement(stmt, PlPluginSettings())
        Assertions.assertNotNull(call)
        Assertions.assertEquals(DebugMode.DIRECT, call.first)
        Assertions.assertEquals("public.test_debug('TEST', 123)", call.second?.text)
        parsed = call.second?.let { parseFunctionCall(it, call.first) }
        Assertions.assertNotNull(parsed)
        if (parsed != null) {
            Assertions.assertEquals(listOf("public", "test_debug"), parsed.first)
            Assertions.assertEquals(listOf("'TEST'", "123"), parsed.second)
        }

    }

    @Test
    fun testCallDetection() {
        Assertions.assertNotNull(myFixture)
        val psiFile = myFixture.configureByText(SqlFileType.INSTANCE, "CALL test_function();")
        val sqlFile = assertInstanceOf(psiFile, SqlFile::class.java)
        Assertions.assertFalse(PsiErrorElementUtil.hasErrors(project, sqlFile.virtualFile))
        val statement = psiFile.children.first() as SqlStatement
        val test = getCallStatement(statement, PlPluginSettings())
        Assertions.assertNotNull(test)
        Assertions.assertEquals(DebugMode.DIRECT, test.first)
        Assertions.assertEquals("test_function", test.second?.text)
    }

}