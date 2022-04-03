/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import net.plpgsql.ideadebugger.ExtensionDiagnostic
import javax.swing.JComponent

@Suppress("UnstableApiUsage")
class ConnectionDiagnosticDialog(project: Project, private var diagnostic: ExtensionDiagnostic) :
    DialogWrapper(project, true) {

    private var panel: DialogPanel? = null

    init {
        title = "Diagnostic Error"
        init()
        panel?.apply()
    }

    override fun createCenterPanel(): JComponent? {
        panel = panel {
            group(title = "Plugin Diagnostic") {
                row("Shared libraries") {
                    label(diagnostic.sharedLibraries)
                    icon(if (diagnostic.sharedLibraryOk) AllIcons.General.InspectionsOK else AllIcons.General.Error)
                }
                row() {
                }.comment(
                    """
                    The library plugin_debugger activated in the database server 
                    Shared libraries are set in postgresql.conf
                    """.trimIndent()
                )
                row("Database extensions") {
                    label(diagnostic.extensions)
                    icon(if (diagnostic.extensionOk) AllIcons.General.InspectionsOK else AllIcons.General.Error)
                }
                row() {
                }.comment(
                    """
                    Extension must be created in the current database
                    """.trimIndent()
                )
                if (!diagnostic.activityOk) {
                    val pids = diagnostic.activities.joinToString(
                        prefix = "[",
                        postfix = "]",
                        separator = ", ") {
                            act -> "${act.pid}"
                    }
                    row("Remaining debugger session") {
                        label("Remaining session $pids")
                        icon(AllIcons.General.Error)
                    }
                    row() {
                    }.comment(
                        """
                        A died debugger session can't be stopped, server you be restarted or pid killed manually
                        """.trimIndent()
                    )
                }

            }
        }
        return panel
    }

    override fun isModal(): Boolean = true
}