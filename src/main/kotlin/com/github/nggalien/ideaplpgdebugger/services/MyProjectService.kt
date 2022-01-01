package com.github.nggalien.ideaplpgdebugger.services

import com.intellij.openapi.project.Project
import com.github.nggalien.ideaplpgdebugger.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
