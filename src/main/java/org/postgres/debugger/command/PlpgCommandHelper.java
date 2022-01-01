package org.postgres.debugger.command;


import com.intellij.database.dataSource.DatabaseConnection;
import com.intellij.database.remote.jdbc.RemoteResultSet;
import com.intellij.database.remote.jdbc.RemoteResultSetMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.*;
import org.jetbrains.annotations.NotNull;
import org.postgres.debugger.command.result.*;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PlpgCommandHelper {

    private final DatabaseConnection myConnection;
    private static final String CREATE_LISTENER = "pldbg_create_listener()";
    private static final String OID_DEBUG = "plpgsql_oid_debug(%s)";
    private static final String FUNCTION_DEF = "pg_get_functiondef(%s)";
    private static final String STEP_OVER = "pldbg_step_over(%s)";
    private static final String STEP_INTO = "pldbg_step_into(%s)";
    private static final String CONTINUE = "pldbg_continue(%s)";
    private static final String GET_STACK = "pldbg_get_stack(%s)";
    private static final String GET_VARIABLES = "(SELECT\n" +
            "name,\n" +
            "varclass,\n" +
            "linenumber,\n" +
            "isunique,\n" +
            "isconst,\n" +
            "isnotnull,\n" +
            "dtype,\n" +
            "value,\n" +
            "(SELECT t.typname FROM pg_type t WHERE t.oid = dtype LIMIT 1) as typname\n" +
            "FROM pldbg_get_variables(%s)) variable";
    private static final String ATTACH_TO_PORT = "pldbg_attach_to_port(%s)";
    private static final String SEARCH_FUNCTION_INFO = "(SELECT\n" +
            "t_proc.oid,\n" +
            "t_namespace.nspname,\n" +
            "t_proc.proname,\n" +
            "pg_catalog.pg_get_function_arguments(t_proc.oid),\n" +
            "pg_catalog.pg_get_functiondef(t_proc.oid),\n" +
            "t_proc.prosrc\n" +
            "FROM pg_proc t_proc\n" +
            "JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid\n" +
            "WHERE t_namespace.nspname = '%s'\n" +
            "AND t_proc.proname = '%s') info";

    private static final String GET_FUNCTION_INFO = "(SELECT\n" +
            "t_proc.oid,\n" +
            "t_namespace.nspname,\n" +
            "t_proc.proname,\n" +
            "pg_catalog.pg_get_function_arguments(t_proc.oid),\n" +
            "pg_catalog.pg_get_functiondef(t_proc.oid),\n" +
            "t_proc.prosrc\n" +
            "FROM pg_proc t_proc\n" +
            "JOIN pg_namespace t_namespace on t_proc.pronamespace = t_namespace.oid\n" +
            "WHERE t_proc.oid = '%s') info";

    public PlpgCommandHelper(DatabaseConnection myConnection) {
        this.myConnection = myConnection;
    }

    public final PlpgLongResult attachToPort(@NotNull Long port) throws PlpgCommandException {
        List<Long> res = new PlpgCommand<Long>(ATTACH_TO_PORT, PlpgCommandHelper.this.myConnection) {
            @Override
            public Long consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return remoteResultSet.getLong(1);
            }
        }.call(port.toString());
        if (res.isEmpty()) {
            throw new PlpgCommandException("Listener creation failed");
        }
        return new PlpgLongResult(res.get(0));
    }

    public final PlpgLongResult createListener() throws PlpgCommandException {
        List<Long> res = new PlpgCommand<Long>(CREATE_LISTENER, PlpgCommandHelper.this.myConnection) {
            @Override
            public Long consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return remoteResultSet.getLong(1);
            }
        }.call();
        if (res.isEmpty()) {
            throw new PlpgCommandException("Listener creation failed");
        }
        return new PlpgLongResult(res.get(0));
    }

    public final PlpgLongResult oidDebug(@NotNull Long oid) throws PlpgCommandException {
        List<Long> res = new PlpgCommand<Long>(OID_DEBUG, PlpgCommandHelper.this.myConnection) {
            @Override
            public Long consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return remoteResultSet.getLong(1);
            }
        }.call(oid.toString());
        if (res.isEmpty()) {
            throw new PlpgCommandException("Debug oid failed");
        }
        return new PlpgLongResult(res.get(0));
    }

    public final PlpgStringResult getFunctionDef(@NotNull Long oid) throws PlpgCommandException {
        List<String> res = new PlpgCommand<String>(FUNCTION_DEF, PlpgCommandHelper.this.myConnection) {
            @Override
            public String consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return remoteResultSet.getString(1);
            }
        }.call(oid.toString());
        if (res.isEmpty()) {
            throw new PlpgCommandException("Unable to get function code");
        }
        return new PlpgStringResult(res.get(0));
    }

    public final PlpgStepResult stepOver(@NotNull Long session) throws PlpgCommandException {
        return getPlpgStepResult(session, STEP_OVER);
    }

    public final PlpgStepResult stepInto(@NotNull Long session) throws PlpgCommandException {
        return getPlpgStepResult(session, STEP_INTO);
    }
    public final PlpgStepResult resume(@NotNull Long session) throws PlpgCommandException {
        return getPlpgStepResult(session, CONTINUE);
    }

    private PlpgStepResult getPlpgStepResult(@NotNull Long session, String step) throws PlpgCommandException {
        List<PlpgStepResult> res = new PlpgCommand<PlpgStepResult>(step, PlpgCommandHelper.this.myConnection) {
            @Override
            public PlpgStepResult consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return new PlpgStepResult(
                        remoteResultSet.getLong(1),
                        remoteResultSet.getInt(2),
                        remoteResultSet.getString(3)
                );
            }
        }.call(session.toString());
        if (res.isEmpty()) {
            throw new PlpgCommandException("Unable to get function code");
        }
        return res.get(0);
    }

    public final PlpgStackResult getStack(@NotNull Long oid) throws PlpgCommandException {
        List<PlpgStackResult> res = new PlpgCommand<PlpgStackResult>(GET_STACK, PlpgCommandHelper.this.myConnection) {
            @Override
            public PlpgStackResult consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                RemoteResultSetMetaData meta = remoteResultSet.getMetaData();
                int count = meta.getColumnCount();
                for (int i = 1; i <= count; i++) {
                    System.out.println(meta.getColumnName(i) + ": " + remoteResultSet.getString(i));
                }
                return new PlpgStackResult(
                        remoteResultSet.getInt(1),
                        remoteResultSet.getString(5),
                        new PlpgStepResult(
                                remoteResultSet.getLong(3),
                                remoteResultSet.getInt(4),
                                remoteResultSet.getString(2)
                        )
                );
            }
        }.call(oid.toString());
        if (res.isEmpty()) {
            throw new PlpgCommandException("Unable to get function code");
        }
        return res.get(0);
    }

    public final PlpgSetResult getVariables(@NotNull Long oid) throws PlpgCommandException {
        List<PlpgVariableResult> res = new PlpgCommand<PlpgVariableResult>(GET_VARIABLES, PlpgCommandHelper.this.myConnection) {
            @Override
            public PlpgVariableResult consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return new PlpgVariableResult(
                        remoteResultSet.getString(1),
                        remoteResultSet.getString(2),
                        remoteResultSet.getInt(3),
                        remoteResultSet.getBoolean(4),
                        remoteResultSet.getBoolean(5),
                        remoteResultSet.getBoolean(6),
                        remoteResultSet.getInt(7),
                        remoteResultSet.getString(8),
                        remoteResultSet.getString(9)
                );
            }
        }.call(oid.toString());
        return new PlpgSetResult(res);
    }

    public final PlpgFunctionSource searchFunctionInfo(@NotNull SqlFunctionCallExpression callExpression) throws PlpgCommandException {

        SqlIdentifier identifier = PsiTreeUtil.findChildOfType(callExpression, SqlIdentifier.class);
        assert identifier != null;
        String[] functionIds = identifier.getText().split("\\.");

        if(functionIds.length != 2) {
            functionIds = new String[]{"public", identifier.getText()};
        }
        final String functionIdentifier = Arrays.stream(functionIds).sequential().collect(Collectors.joining("."));

        List<PlpgFunctionSource> res = new PlpgCommand<PlpgFunctionSource>(SEARCH_FUNCTION_INFO, PlpgCommandHelper.this.myConnection) {
            @Override
            public PlpgFunctionSource consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return new PlpgFunctionSource(
                        remoteResultSet.getLong(1),
                        remoteResultSet.getString(2),
                        remoteResultSet.getString(3),
                        remoteResultSet.getString(4),
                        remoteResultSet.getString(5),
                        remoteResultSet.getString(6));
            }
        }.call(functionIds);

        SqlExpressionList sqlExpressionList = PsiTreeUtil.findChildOfType(callExpression, SqlExpressionList.class);
        Collection<String> expressionArgs = null;
        if (sqlExpressionList != null) {
            expressionArgs = PsiTreeUtil.findChildrenOfAnyType(sqlExpressionList, SqlLiteralExpression.class)
                    .stream().map(sqlLiteralExpression -> sqlLiteralExpression.getNode().getElementType().getDebugName()).collect(Collectors.toList());
        }
        Collection<String> finalExpressionArgs = expressionArgs;
        return res.stream().filter(plpgFunctionInfo -> {
                    return (finalExpressionArgs == null) || finalExpressionArgs.size() == plpgFunctionInfo.getArgs().size();
        }).findFirst()
                .orElseThrow(() -> new PlpgCommandException(
                        String.format("Procedure not found %s", functionIdentifier)));
    }

    public final PlpgFunctionSource getFunctionInfo(@NotNull Long oid) throws PlpgCommandException {


        return new PlpgCommand<PlpgFunctionSource>(GET_FUNCTION_INFO, PlpgCommandHelper.this.myConnection) {
            @Override
            public PlpgFunctionSource consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException {
                return new PlpgFunctionSource(
                        remoteResultSet.getLong(1),
                        remoteResultSet.getString(2),
                        remoteResultSet.getString(3),
                        remoteResultSet.getString(4),
                        remoteResultSet.getString(5),
                        remoteResultSet.getString(6));
            }
        }.call(oid.toString())
                .stream()
                .findFirst()
                .orElseThrow(() -> new PlpgCommandException(
                String.format("Procedure not found %s", oid)));

    }


}
