package com.github.nggalien.pldebugger;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.connection.throwable.info.WarningInfo;
import com.intellij.database.dataSource.DatabaseConnectionPoint;
import com.github.nggalien.pldebugger.breakpoint.PlpgLineBreakPointType;
import com.github.nggalien.pldebugger.command.PlpgException;
import com.github.nggalien.pldebugger.command.result.PlpgFunctionSource;
import com.intellij.database.dataSource.DatabaseConnection;
import com.intellij.database.datagrid.*;
import com.intellij.database.debugger.SqlDebugController;
import com.intellij.database.util.SearchPath;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sql.psi.SqlFunctionCallExpression;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.nggalien.pldebugger.command.PlpgCommandException;
import com.github.nggalien.pldebugger.command.PlpgCommandHelper;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlpgDebugController extends SqlDebugController {

    private static final Logger LOGGER = Logger.getInstance(PlpgDebugController.class);

    private final static PlpgLineBreakPointType BREAK_POINT_TYPE = new PlpgLineBreakPointType();

    private final Project myProject;
    private final DatabaseConnectionPoint myDatabaseConnectionPoint;
    private final RangeMarker myRangeMarker;
    private final VirtualFile myVirtualFile;
    private DataRequest.OwnerEx ownerEx;
    private PlpgDebugProcess myDebugProcess;
    private long listenerId;

    private final SqlFunctionCallExpression functionCallExpression;


    public PlpgDebugController(Project project,
                               DatabaseConnectionPoint databaseConnectionPoint,
                               DataRequest.OwnerEx ownerEx,
                               VirtualFile virtualFile,
                               RangeMarker rangeMarker,
                               SearchPath searchPath,
                               SqlFunctionCallExpression functionCallExpression) {
        this.myProject = project;
        this.myDatabaseConnectionPoint = databaseConnectionPoint;
        this.ownerEx = ownerEx;
        this.myVirtualFile = virtualFile;
        this.myRangeMarker = rangeMarker;

        this.ownerEx.getMessageBus().addAuditor(new DataAuditors.Adapter() {

            private final Pattern debugPortPattern = Pattern.compile(".*PLDBGBREAK:([0-9]+).*");

            @Override
            public void print(DataRequest.@NotNull Context context, @Nullable String message) {
                LOGGER.debug("print", message);
            }

            @Override
            public void warn(DataRequest.@NotNull Context context, @NotNull WarningInfo warningInfo) {

                String message = warningInfo.getMessage();
                LOGGER.debug("warn", message);
                Matcher matcher = debugPortPattern.matcher(message);
                if (matcher.matches()) {
                    long debuggerPort = Long.parseLong(matcher.group(1));
                    //myDebugProcess.getDebugThread().directDebugging(debuggerPort);
                }
            }


            @Override
            public void error(DataRequest.@NotNull Context context, @NotNull ErrorInfo errorInfo) {
                super.error(context, errorInfo);
            }
        });
        this.functionCallExpression = functionCallExpression;
    }
    @Override
    public void getReady() {
        System.out.println("getReady");
    }

    @Override
    public @NotNull XDebugProcess initLocal(@NotNull XDebugSession xDebugSession) throws ExecutionException {
        LOGGER.debug("initLocal");
        XDebuggerManager debuggerManager = XDebuggerManager.getInstance(myProject);
        //debuggerManager.getBreakpointManager().addBreakpointListener(BREAK_POINT_TYPE, this.breakPointHandler);
        Arrays.stream(debuggerManager.getBreakpointManager().getAllBreakpoints()).forEach(
                xBreakpoint -> {
                    if (xBreakpoint instanceof XLineBreakpoint) {
                        XLineBreakpoint lineBreakpoint = (XLineBreakpoint) xBreakpoint;
                        System.out.println(lineBreakpoint);
                    }
                }
        );
        myDebugProcess = new PlpgDebugProcess(xDebugSession, myDatabaseConnectionPoint);
        return myDebugProcess;
    }

    @Override
    public void initRemote(@NotNull DatabaseConnection databaseConnection) {
        LOGGER.debug("initRemote");
        try {

            final PlpgCommandHelper commandHelper = new PlpgCommandHelper(databaseConnection);
            PlpgFunctionSource functionInfo = ApplicationManager.getApplication().runReadAction((Computable<PlpgFunctionSource>) () -> {
                PlpgFunctionSource functionInfo1 = null;
                try {
                    functionInfo1 = commandHelper.searchFunctionInfo(PlpgDebugController.this.functionCallExpression);
                } catch (PlpgCommandException e) {
                    e.printStackTrace();
                }
                return functionInfo1;
            });

            if (functionInfo == null) {
                throw new PlpgCommandException("Start debugging failed");
            }
            long res = commandHelper.oidDebug(functionInfo.getOid()).getValue();
            if (res != 0) {
                throw new PlpgCommandException("Start debugging failed");
            }

        } catch (PlpgCommandException e) {
            LOGGER.error(e);
        }
    }

    @Override
    public void debugBegin() {
        LOGGER.debug("debugBegin");
    }

    @Override
    public void debugEnd() {
        LOGGER.debug("debugEnd");
        try {
            LOGGER.debug("afterLastRowAdded");
            myDebugProcess.getDebugThread().finish();
        } catch (PlpgException e) {
            LOGGER.error(e);
        }
    }

    @Override
    public void close() {
        LOGGER.debug("close");
    }

}
