/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.run

import com.intellij.database.actions.PerformActionBase
import com.intellij.database.dialects.postgres.model.PgRoutine
import com.intellij.database.view.DatabaseView
import com.intellij.openapi.actionSystem.AnActionEvent
import net.plpgsql.ideadebugger.console

class PlIndirectDebugAction : PerformActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        console("PlIndirectDebugAction")
        getSelectedNodes(e)
    }
    /*
    override fun update(e: AnActionEvent) {
        val p = e.presentation
        if (!e.place.startsWith("Editor")) {
            p.isEnabledAndVisible = false
        } else {
            val project = e.getData(CommonDataKeys.PROJECT)
            project?.let {
                val registry = it.getService(DbModelRegistry::class.java)

            }

            val enable = true
            p.isVisible = enable
            p.isEnabled = enable

        }
    }*/

    fun getSelectedNodes(e: AnActionEvent) {
        val nodes = e.getData(DatabaseView.DATABASE_NODES)
        nodes?.forEach {
            if (it is PgRoutine) {
                console("${it.objectId}")
            }
        }
    }

}

