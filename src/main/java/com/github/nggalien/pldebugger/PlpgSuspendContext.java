package com.github.nggalien.pldebugger;

import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class PlpgSuspendContext extends XSuspendContext {

    private final PlpgExecutionStack myXExecutionStack;
    private final PlpgDebugProcess myDebugProcess;


    public PlpgSuspendContext(@NotNull PlpgDebugProcess debugProcess,
                              @NotNull PlpgStackInfo info,
                              @NotNull PsiFile psiFile) {
        this.myDebugProcess = debugProcess;
        this.myXExecutionStack = new PlpgExecutionStack(this, info, psiFile);
    }

    @Override
    public @Nullable XExecutionStack getActiveExecutionStack() {
        return myXExecutionStack;
    }

    @Override
    public void computeExecutionStacks(XExecutionStackContainer container) {
        container.addExecutionStack(Collections.singletonList(myXExecutionStack), true);
    }

    public PlpgDebugProcess getDebugProcess() {
        return myDebugProcess;
    }
}
