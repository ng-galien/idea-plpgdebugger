package org.postgres.debugger;

import com.intellij.database.console.session.DatabaseSessionManager;
import com.intellij.database.dataSource.DatabaseConnection;
import com.intellij.database.dataSource.DatabaseConnectionPoint;
import com.intellij.database.dataSource.connection.DGDepartment;
import com.intellij.openapi.application.ApplicationManager;
import org.hamcrest.core.IsInstanceOf;
import org.jetbrains.annotations.NotNull;
import org.postgres.debugger.command.PlpgCommandException;
import org.postgres.debugger.command.PlpgCommandHelper;
import org.postgres.debugger.command.PlpgException;
import org.postgres.debugger.command.PlpgTask;
import org.postgres.debugger.command.result.PlpgFunctionSource;
import org.postgres.debugger.command.result.PlpgStackResult;
import org.postgres.debugger.command.result.PlpgVariableResult;

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

    private final BlockingQueue<PlpgTask> tasks;
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

    public void queueTask(PlpgTask task) {
        tasks.add(task);
    }

    @Override
    public void run() {
        running = true;
        do {
            try {
                PlpgTask task = tasks.take();
                switch (task) {
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
                myDebugProcess.stackReady(task, plpgStackInfo);

            } catch (InterruptedException| PlpgCommandException e) {
                e.printStackTrace();
            }
        }
        while (running);
    }

    public void directDebugging(Long port) throws PlpgCommandException {
        this.sessionId = myCommandHelper.attachToPort(port).getValue();
        tasks.add(PlpgTask.DUMMY);
        ApplicationManager.getApplication().executeOnPooledThread(PlpgDebugThread.this);
    }

    public void finish() throws PlpgException {
        running = false;
        queueTask(PlpgTask.DUMMY);
        try {
            myConnection.getRemoteConnection().cancelAll();
            myConnection.getRemoteConnection().close();
        } catch (SQLException | RemoteException e) {
            throw new PlpgException("Failed to close debug connection", e);
        }
    }
}
