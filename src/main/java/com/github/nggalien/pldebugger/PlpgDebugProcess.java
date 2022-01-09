package com.github.nggalien.pldebugger;

import com.intellij.database.dataSource.DatabaseConnectionPoint;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.sql.dialects.postgres.PgDialect;
import com.github.nggalien.pldebugger.breakpoint.PlpgLineBreakPointProperties;
import com.github.nggalien.pldebugger.breakpoint.PlpgLineBreakPointType;
import com.intellij.database.debugger.SqlDebugProcess;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.nggalien.pldebugger.command.PlpgException;
import com.github.nggalien.pldebugger.command.PlpgTask;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlpgDebugProcess extends SqlDebugProcess {

    private static final Logger LOGGER = Logger.getInstance(PlpgDebugProcess.class);

    private final PlpgDebugThread myDebugThread;
    private final Map<Long, PsiFile> psiRegistry = new ConcurrentHashMap<>();
    private ExecutionConsole myConsole;


    protected PlpgDebugProcess(@NotNull XDebugSession session, @NotNull DatabaseConnectionPoint connectionPoint) throws ExecutionException {
        super(session);
        try {
            this.myDebugThread = new PlpgDebugThread(this, connectionPoint);
        } catch (PlpgException e) {
            throw new ExecutionException(e);
        }
    }


    @Override
    public void sessionInitialized() {
        super.sessionInitialized();
    }

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
        return PlpgDebugEditorProvider.INSTANCE;
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        //myDebugThread.queueTask(PlpgTask.STEP_OVER);
    }


    @Override
    public boolean checkCanInitBreakpoints() {
        return true;
    }

    @Override
    public boolean checkCanPerformCommands() {
        return true;
    }

    @Override
    public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
        return new XBreakpointHandler[]{new XBreakpointHandler<>(PlpgLineBreakPointType.class) {

            @Override
            public void registerBreakpoint(@NotNull XLineBreakpoint<PlpgLineBreakPointProperties> breakpoint) {
            }

            @Override
            public void unregisterBreakpoint(@NotNull XLineBreakpoint<PlpgLineBreakPointProperties> breakpoint, boolean temporary) {

            }
        }};
    }

    public synchronized void stackReady(@NotNull PlpgTask task,
                             @NotNull PlpgStackInfo info)  {
        if (!psiRegistry.containsKey(info.getOid())) {
            final Project project = this.getSession().getProject();
            final String src = info.getFunctionSource();
            final Language language = PgDialect.INSTANCE;
            final String psiName =  info.getFunctionName()+"@"+info.getOid();
            final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(
                    (Computable<PsiFile>) () -> PsiFileFactory.getInstance(project).createFileFromText(psiName, language, src)
            );
            psiRegistry.put(info.getOid(), psiFile);
        }
        final PlpgSuspendContext context = new PlpgSuspendContext(this, info, psiRegistry.get(info.getOid()));
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(() -> getSession().positionReached(context));
        } else {
            getSession().positionReached(context);
        }
    }

    public PlpgDebugThread getDebugThread() {
        return myDebugThread;
    }

    @Override
    public void stop() {
        try {
            myDebugThread.finish();
        } catch (PlpgException e) {
            LOGGER.error(e);
        }
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {

        //myDebugThread.queueTask(PlpgTask.RESUME);
    }

    @Override
    public @NotNull ExecutionConsole createConsole() {
        myConsole = super.createConsole();
        return myConsole;
    }
}
