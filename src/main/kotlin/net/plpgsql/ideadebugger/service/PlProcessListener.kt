/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.service

import com.intellij.util.messages.Topic
import net.plpgsql.ideadebugger.PlProcess

interface PlProcessListener {
    companion object {
        val PROCESS_TOPIC: Topic<PlProcessListener>
            get() = Topic.create("plpgdebugger", PlProcessListener::class.java)
    }

    fun started(process: PlProcess)
    fun finished(process: PlProcess)
}