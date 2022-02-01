/*
 * Copyright (c) 2022. Alexandre Boyer
 */

package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import net.plpgsql.ideadebugger.ui.ConnectionDiagnosticDialog

data class ExtensionDiagnostic (
    var sharedLibraries: String,
    var sharedLibraryOk: Boolean,
    var extensions: String,
    var extensionOk: Boolean,
)

fun extensionOk(diag: ExtensionDiagnostic): Boolean = diag.extensionOk && diag.sharedLibraryOk

fun showExtensionDiagnostic(project: Project, diag: ExtensionDiagnostic) {
    runInEdt {
        val dialog = ConnectionDiagnosticDialog(project, diag)
        dialog.show()
    }
}
