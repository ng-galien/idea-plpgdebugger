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

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.command.PlActivity
import net.plpgsql.ideadebugger.ui.ConnectionDiagnosticDialog

/**
 * Represents a diagnostic for the connection, including information about various components of the connection.
 *
 * @property customCommandMessage The message related to the custom command executed on the connection.
 * @property customCommandOk Indicates whether the custom command executed successfully.
 * @property sharedLibraries The shared libraries used by the connection.
 * @property sharedLibraryOk Indicates whether the shared libraries are available.
 * @property extensions The extensions installed on the connection.
 * @property extensionOk Indicates whether the extensions are enabled.
 * @property activities The list of PL/pgSQL activities running on the connection.
 * @property activityOk Indicates whether the activities are active.
 */
data class ConnectionDiagnostic (
    var customCommandMessage: String,
    var customCommandOk: Boolean,
    var sharedLibraries: String,
    var sharedLibraryOk: Boolean,
    var extensions: String,
    var extensionOk: Boolean,
    var activities: List<PlActivity>,
    var activityOk: Boolean
)

/**
 * Checks if the given ConnectionDiagnostic object indicates that the extension is OK.
 *
 * @param diag The ConnectionDiagnostic object to check
 * @return true if the extension is OK, false otherwise
 */
fun extensionOk(diag: ConnectionDiagnostic): Boolean =
    diag.customCommandOk && diag.extensionOk && diag.sharedLibraryOk && diag.activityOk

/**
 * Displays a diagnostic dialog for a connection diagnostic.
 *
 * @param project The current project.
 * @param diag The connection diagnostic object containing the diagnostic information.
 */
fun showExtensionDiagnostic(project: Project, diag: ConnectionDiagnostic) {
    val dialog = ConnectionDiagnosticDialog(project, diag)
    runInEdt {
        dialog.show()
    }
}
