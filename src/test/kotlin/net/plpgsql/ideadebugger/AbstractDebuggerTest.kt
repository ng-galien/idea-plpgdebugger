package net.plpgsql.ideadebugger

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertTrue

const val PG_PORT = 5432
const val pgVersion = "14"
@Testcontainers
open class AbstractDebuggerTest {

    companion object {
        @JvmStatic
        private val postgres = GenericContainer(DockerImageName.parse("galien0xffffff/postgres-debugger:$pgVersion"))
            .withExposedPorts(PG_PORT)
            .withEnv("POSTGRES_PASSWORD", "postgres")
            .withEnv("POSTGRES_USER", "postgres")
            .withEnv("POSTGRES_DB", "postgres")

        private lateinit var jdbi: Jdbi

        @JvmStatic
        @BeforeAll
        fun setup() {
            postgres.start()
            val port = postgres.getMappedPort(PG_PORT)
            jdbi = Jdbi.create("jdbc:postgresql://${postgres.host}:${port}/postgres",
            "postgres", "postgres")
                .installPlugin(PostgresPlugin())
                .installPlugin(KotlinPlugin())
                .installPlugin(KotlinSqlObjectPlugin())
            jdbi.open().use {
                it.execute("CREATE EXTENSION IF NOT EXISTS pldbgapi;")
                listOf(
                    "function_with_declare",
                    "function_with_declare_with_comments",
                    "function_without_declare",
                    "function_without_declare_with_comments",
                ).forEach { name ->
                    assertTrue("Function $name is created") {
                        loadSqlInFile(it, name)
                    }
                }
            }
        }
    }

    protected fun db(): Handle = jdbi.open()
}

fun loadSqlInFile(handle: Handle, fileName: String): Boolean {
    //Get the file in the resources folder
    val fileContent = AbstractDebuggerTest::class.java.getResourceAsStream("/$fileName.sql")
        ?.bufferedReader()?.use { it.readText() }
    return fileContent?.let {
            handle.execute(it)
            true
        }?: false

}
