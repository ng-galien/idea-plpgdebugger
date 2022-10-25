/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.google.common.hash.Hashing
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

const val PG_PORT = 5432

data class FunctionDef(val schema: String, val routine: String, val args: Map<String,  String>)

fun String.md5(): String = Hashing.md5().hashString(this, Charsets.UTF_8).toString()

fun loadProcedure(obj: Any, handle: Handle, functionName: String) {
    val fileContent = obj.javaClass.getResourceAsStream("/${functionName}.sql")?.bufferedReader().use { it?.readText() }
    handle.execute(fileContent)
}

fun getProcedureDefinition(handle: Handle, procedureName: String): String? {
    return handle.createQuery("SELECT pg_get_functiondef(oid) FROM pg_catalog.pg_proc WHERE proname = :proname")
        .bind("proname", procedureName)
        .mapTo(String::class.java)
        .firstOrNull()
}

fun getPGContainer(pgVersion: String): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse("galien0xffffff/postgres-debugger:$pgVersion"))
        .withExposedPorts(PG_PORT)
        .withEnv("POSTGRES_PASSWORD", "postgres")
        .withEnv("POSTGRES_USER", "postgres")
        .withEnv("POSTGRES_DB", "postgres")
}

fun getHandle(container: GenericContainer<*>): Handle {
    val host = container.host
    val port = container.getMappedPort(PG_PORT)
    val jdbi = Jdbi.create("jdbc:postgresql://${host}:${port}/postgres", "postgres", "postgres")
        .installPlugin(PostgresPlugin()).installPlugin(KotlinPlugin())
    return jdbi.open()
}

fun getFunctionSource(obj: Any, container: GenericContainer<*>, functionName: String): String {
    return getHandle(container).use {
        loadProcedure(obj, it, functionName)
        getProcedureDefinition(it, functionName)?: throw Exception("Function $functionName not found")
    }
}