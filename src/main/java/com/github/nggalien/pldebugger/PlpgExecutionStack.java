package com.github.nggalien.pldebugger;

import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;

public class PlpgExecutionStack extends XExecutionStack {

    private final PlpgSuspendContext mySuspendContext;
    private final PlpgStackInfo myStackInfo;
    private final PsiFile myPsiFile;
    private final ArrayList<PlpgStackFrame> myPlpgStackFrames = new ArrayList<>();

    public PlpgExecutionStack(@NotNull PlpgSuspendContext context, @NotNull PlpgStackInfo info, @NotNull PsiFile psiFile) {
        super("");
        this.mySuspendContext = context;
        this.myStackInfo = info;
        this.myPsiFile = psiFile;
        PlpgStackFrame frame = new PlpgStackFrame(this);
        myPlpgStackFrames.add(frame);

    }

    @Override
    public @Nullable XStackFrame getTopFrame() {
        return ContainerUtil.getFirstItem(myPlpgStackFrames);
    }

    @Override
    public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
        container.addStackFrames(myPlpgStackFrames, true);
    }

    public PlpgStackInfo getStackInfo() {
        return myStackInfo;
    }

    public PsiFile PsiFile() {
        return myPsiFile;
    }
}
