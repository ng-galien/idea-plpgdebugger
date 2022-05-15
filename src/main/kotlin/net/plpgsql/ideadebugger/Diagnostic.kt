/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.command.PlActivity
import net.plpgsql.ideadebugger.ui.ConnectionDiagnosticDialog

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

fun extensionOk(diag: ConnectionDiagnostic): Boolean =
    diag.customCommandOk && diag.extensionOk && diag.sharedLibraryOk && diag.activityOk

fun showExtensionDiagnostic(project: Project, diag: ConnectionDiagnostic) {
    val dialog = ConnectionDiagnosticDialog(project, diag)
    runInEdt {
        dialog.show()
    }
}
