package net.plpgsql.ideadebugger

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PostgresContainerTest {

    @Container
    var postgres = getPGContainer("14")

    @BeforeEach
    fun setup() {
        postgres.start()
        getHandle(postgres).use {
            it.execute("CREATE EXTENSION if not exists pldbgapi;")
        }
    }

    @TestFactory
    fun `test retrieve function source`() = listOf(
        "function_with_declare",
        "function_with_declare_with_comments",
        "function_without_declare",
        "function_without_declare_with_comments",
    ).map { name ->
        DynamicTest.dynamicTest("the function $name should be loaded and retrieved" ) {
            val sourceCode = getFunctionSource(this, postgres, name)
            assertNotNull(sourceCode)
        }
    }
}