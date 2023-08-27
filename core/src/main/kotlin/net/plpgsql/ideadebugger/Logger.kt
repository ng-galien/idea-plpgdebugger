/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun getLogger(clazz: KClass<*>): Logger = LoggerFactory.getLogger(clazz.java)