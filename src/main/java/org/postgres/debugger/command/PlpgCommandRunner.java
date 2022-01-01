package org.postgres.debugger.command;

import org.postgres.debugger.command.result.PlpgDebuggerResult;
import com.intellij.openapi.application.ApplicationManager;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public abstract class PlpgCommandRunner<T> implements Callable<T> {

    private final PlpgCommandHelper helper;
    private Object commandResult;

    public PlpgCommandRunner(PlpgCommandHelper helper) {
        this.helper = helper;
    }

    public abstract T performCommand(PlpgCommandHelper commandHelper) throws PlpgCommandException;

    @Override
    public T call() throws Exception {
        return performCommand(helper);
    }

    public static <E extends PlpgDebuggerResult> E runCommand(PlpgCommandRunner<E> commandRunner) throws PlpgCommandException {
        try {
            return ApplicationManager.getApplication().executeOnPooledThread(commandRunner).get();
        } catch (InterruptedException|ExecutionException e) {
            throw new PlpgCommandException("", e);
        }
    }

}
