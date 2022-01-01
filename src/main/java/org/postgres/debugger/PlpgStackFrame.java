package org.postgres.debugger;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.sql.dialects.postgres.PgDialect;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.postgres.debugger.values.PlpgValueGroup;

import java.io.File;

public class PlpgStackFrame extends XStackFrame {

    private final PlpgExecutionStack myExecutionStack;
    //private final AtomicNullableLazyValue<VirtualFile> myVirtualFile;

    public PlpgStackFrame(PlpgExecutionStack executionStack) {
        this.myExecutionStack = executionStack;
        /*
        myVirtualFile = AtomicNullableLazyValue.createValue(() -> {
            String remoteFilePath = PlpgStackFrame.this.myExecutionStack.getStackInfo().getFunctionName() + ".debug";
            VirtualFile result = VfsUtil.findFileByIoFile(new File(localFilePath), false);

            if (result == null) {
                String remoteFileUrl = PerlRemoteFileSystem.PROTOCOL_PREFIX + remoteFilePath;
                result = VirtualFileManager.getInstance().findFileByUrl(remoteFileUrl);

                if (result == null) {    // suppose that we need to fetch file
                    result = myDebugThread.loadRemoteSource(remoteFilePath);
                }
            }

            return result;
        });
        VirtualFile virtualFile = VfsUtil.findFile(new File(vfsFileName).toPath(), false);

        if (virtualFile == null) {
            Project project = myExecutionStack.getSuspendContext().getDebugProcess().getProject();
            String src = myExecutionStack.getStackInfo().getFunctionSource();
            Language language = PgDialect.INSTANCE;
            PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(vfsFileName, language, src);
            virtualFile = file.getVirtualFile();
        }
        myVirtualFile = virtualFile;

         */
    }




    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return XSourcePositionImpl.create(myExecutionStack.PsiFile().getVirtualFile(), getSourceOffset());
    }

    public int getSourceOffset() {
        return this.myExecutionStack.getStackInfo().getSourcePosition();
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        return new XDebuggerEvaluator() {
            @Override
            public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {

            }
        };
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList list = new XValueChildrenList();
        list.addTopGroup(new PlpgValueGroup("Arguments", myExecutionStack.getStackInfo().getArguments()));
        list.addTopGroup(new PlpgValueGroup("Variables", myExecutionStack.getStackInfo().getVariables()));
        node.addChildren(list, true);
    }
}
