package com.github.nggalien.pldebugger;

import com.intellij.database.console.session.DatabaseSessionManager;
import com.intellij.database.dataSource.DatabaseConnection;
import com.intellij.database.dataSource.DatabaseConnectionPoint;
import com.intellij.database.dataSource.connection.DGDepartment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import com.github.nggalien.pldebugger.command.PlpgCommandException;
import com.github.nggalien.pldebugger.command.PlpgCommandHelper;
import com.github.nggalien.pldebugger.command.PlpgException;
import com.github.nggalien.pldebugger.command.PlpgTask;
import com.github.nggalien.pldebugger.command.result.PlpgFunctionSource;
import com.github.nggalien.pldebugger.command.result.PlpgStackResult;
import com.github.nggalien.pldebugger.command.result.PlpgVariableResult;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

public class PlpgDebugThread extends Thread {

    private final PlpgCommandHelper myCommandHelper;
    private final PlpgDebugProcess myDebugProcess;
    private final DatabaseConnection myConnection;

    private final BlockingQueue<Pair<PlpgTask, CallBack>> tasks;
    private boolean running;
    private long sessionId;

    public PlpgDebugThread(@NotNull PlpgDebugProcess debugProcess, @NotNull DatabaseConnectionPoint connectionPoint) throws PlpgException {
        try {
            myConnection = DatabaseSessionManager.getFacade(
                    debugProcess.getSession().getProject(),
                    connectionPoint,
                    null,
                    null,
                    true,
                    null,
                    DGDepartment.DEBUGGER).connect().get();
            this.myCommandHelper = new PlpgCommandHelper(myConnection);
            this.myDebugProcess = debugProcess;
            this.tasks = new LinkedBlockingDeque<>();
        } catch (Exception e) {
            throw new PlpgException("Thread creation failed", e);
        }

    }

    public void queueTask(@NotNull PlpgTask task, CallBack callBack) {
        tasks.add(new Pair<>(task, callBack));
    }

    @Override
    public void run() {
        running = true;
        do {
            try {
                final Pair<PlpgTask, CallBack> next = tasks.take();
                switch (next.first) {
                    case DUMMY:
                        if (!running) {
                            return;
                        }
                        break;
                    case STEP_OVER:
                        myCommandHelper.stepOver(PlpgDebugThread.this.sessionId);
                        break;
                    case STEP_INTO:
                        myCommandHelper.stepInto(PlpgDebugThread.this.sessionId);
                        break;
                    case RESUME:
                        myCommandHelper.resume(PlpgDebugThread.this.sessionId);
                        break;
                }
                final PlpgStackResult stackResult = myCommandHelper.getStack(PlpgDebugThread.this.sessionId);
                final PlpgFunctionSource functionInfo  =  myCommandHelper.getFunctionInfo(stackResult.getStep().getOid());
                final List<PlpgVariableResult> list = myCommandHelper.getVariables(PlpgDebugThread.this.sessionId).getList().stream().map(
                        plpgDebuggerResult -> plpgDebuggerResult instanceof PlpgVariableResult? (PlpgVariableResult)plpgDebuggerResult: null
                ).filter(Objects::nonNull).collect(Collectors.toList());

                final PlpgStackInfo plpgStackInfo = new PlpgStackInfo(
                        stackResult,
                        functionInfo,
                        list
                );
                if (next.second != null) {
                    next.second.stackReached(next.first, plpgStackInfo);
                }

            } catch (InterruptedException| PlpgCommandException e) {
                e.printStackTrace();
            }
        }
        while (running);
    }

    public void directDebugging(@NotNull Long port, @NotNull CallBack callBack ) throws PlpgCommandException {
        this.sessionId = myCommandHelper.attachToPort(port).getValue();
        ApplicationManager.getApplication().executeOnPooledThread(PlpgDebugThread.this);
        queueTask(PlpgTask.DUMMY, callBack);
    }

    public void finish() throws PlpgException {
        running = false;
        queueTask(PlpgTask.DUMMY, null);
        try {
            myConnection.getRemoteConnection().cancelAll();
            myConnection.getRemoteConnection().close();
        } catch (SQLException | RemoteException e) {

        }
    }

    public interface CallBack {
        void stackReached(PlpgTask task, PlpgStackInfo info);
        void compositeInspected(int position, String name, List<PlpgVariableResult> results);
    }

    public static class CallBackDefault implements CallBack {
        @Override
        public void stackReached(PlpgTask task, PlpgStackInfo info) {

        }

        @Override
        public void compositeInspected(int position, String name, List<PlpgVariableResult> results) {

        }
    }

}
