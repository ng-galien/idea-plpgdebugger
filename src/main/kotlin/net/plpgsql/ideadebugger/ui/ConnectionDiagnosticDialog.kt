/*
 * MIT License
 *
 * IntelliJ PL/pg SQL Debugger
 *
 * Copyright (c) 2022-2024. Alexandre Boyer.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.plpgsql.ideadebugger.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import net.plpgsql.ideadebugger.ConnectionDiagnostic
import net.plpgsql.ideadebugger.DEBUGGER_EXTENSION
import net.plpgsql.ideadebugger.DEBUGGER_SHARED_LIBRARY
import javax.swing.JComponent

/**
 * Represents a dialog for displaying connection diagnostics.
 *
 * @param project The current project.
 * @param diagnostic The connection diagnostic object containing the diagnostic information.
 */
class ConnectionDiagnosticDialog(project: Project, private var diagnostic: ConnectionDiagnostic) :
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
                row("Custom command") {
                    label(diagnostic.customCommandMessage)
                    icon(if (diagnostic.customCommandOk) AllIcons.General.InspectionsOK else AllIcons.General.Error)
                }
                row {
                }.comment(
                    """
                    The custom command from plugin configuration
                    """.trimIndent()
                )
                row("Shared libraries") {
                    label(diagnostic.sharedLibraries)
                    icon(if (diagnostic.sharedLibraryOk) AllIcons.General.InspectionsOK else AllIcons.General.Error)
                }
                row {
                }.comment(
                    """
                    The library '$DEBUGGER_SHARED_LIBRARY' detected in the database server 
                    Shared libraries are set in postgresql.conf
                    """.trimIndent()
                )
                row("Database extensions") {
                    label(diagnostic.extensions)
                    icon(if (diagnostic.extensionOk) AllIcons.General.InspectionsOK else AllIcons.General.Error)
                }
                row {
                }.comment(
                    """
                    Extension '$DEBUGGER_EXTENSION' must be created in the current database 
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
                    row {
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
