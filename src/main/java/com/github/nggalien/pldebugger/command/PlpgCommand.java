package com.github.nggalien.pldebugger.command;

import com.intellij.database.dataSource.DatabaseConnection;
import com.intellij.database.remote.jdbc.RemoteResultSet;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class PlpgCommand<T> {

    final String myCommand;
    final DatabaseConnection myConnection;

    public PlpgCommand(String myCommand, DatabaseConnection myConnection) {
        this.myCommand = myCommand;
        this.myConnection = myConnection;

    }

    public abstract T consume(RemoteResultSet remoteResultSet) throws RemoteException, SQLException;

    private String formatCommand(String ...args) {
        return String.format("SELECT * FROM %s;", String.format(myCommand, (Object[]) args));
    }

    public List<T> call(String ...args) throws PlpgCommandException {
        List<T> result = new ArrayList<>();
        try {

            String sqlStatement = formatCommand(args);
            RemoteResultSet resultSet = myConnection
                    .getRemoteConnection()
                    .createStatement()
                    .executeQuery(sqlStatement);
            while (resultSet.next()) {
                result.add(consume(resultSet));
            }
            resultSet.close();
        } catch (RemoteException | SQLException e) {
            throw new PlpgCommandException("Call failed", e);
        }
        return result;
    }

}

