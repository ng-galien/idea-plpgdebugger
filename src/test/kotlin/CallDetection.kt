import com.intellij.lang.Language
import com.intellij.patterns.PlatformPatterns.virtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.SqlFileType
import com.intellij.sql.child
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.psi.SqlFile
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import junit.framework.TestCase
import net.plpgsql.ideadebugger.DebugMode
import net.plpgsql.ideadebugger.getCallStatement
import net.plpgsql.ideadebugger.settings.PlPluginSettings
import org.junit.jupiter.api.Test


/*
 * Copyright (c) 2022. Alexandre Boyer
 */

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class CallDetection : BasePlatformTestCase() {

    @Test
    fun testDetection() {
        TestCase.assertNotNull(myFixture)
        val psiFile = myFixture.configureByText(SqlFileType.INSTANCE, "SELECT test_debug('TEST', 1224312);")
        val sqlFile = assertInstanceOf(psiFile, SqlFile::class.java)
        assertFalse(PsiErrorElementUtil.hasErrors(project, sqlFile.virtualFile))
        val statement = PsiTreeUtil.findChildOfType(psiFile, SqlStatement::class.java)
        TestCase.assertNotNull(statement)
        val setting = PlPluginSettings()
        setting.enableIndirect = false
        val test = statement?.let { getCallStatement(it, setting) }
        TestCase.assertNotNull(test)
        if (test != null) {
            TestCase.assertSame(DebugMode.DIRECT, test.first)
        }
    }
}