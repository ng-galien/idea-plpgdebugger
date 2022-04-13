/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger
import com.intellij.openapi.diagnostic.Logger
import kotlin.reflect.KClass

fun getLogger(clazz: KClass<*>): Logger = Logger.getInstance(clazz.java)