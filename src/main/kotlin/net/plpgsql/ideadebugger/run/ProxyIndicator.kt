package net.plpgsql.ideadebugger.run

import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import net.plpgsql.ideadebugger.command.ApiQuery
import net.plpgsql.ideadebugger.command.PlApiStep

class ProxyIndicator(task: PlProcess.ProxyTask): BackgroundableProcessIndicator(task) {
    fun displayInfo(query: ApiQuery, step: PlApiStep, info: PlProcess.StepInfo) {
        fraction = info.ratio
        if (step.line < 0) {
            this.text2 = "Waiting for step [${info.pos} / ${info.total}]"
        } else {
            this.text2 = "Last step ${query.name} [${info.pos} / ${info.total}]"
        }
    }
}