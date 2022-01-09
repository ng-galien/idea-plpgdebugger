package com.github.nggalien.pldebugger;

import com.intellij.database.dataSource.DatabaseConnectionPoint;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.datagrid.DataRequest;
import com.intellij.database.debugger.SqlDebugController;
import com.intellij.database.debugger.SqlDebuggerFacade;
import com.intellij.database.model.basic.BasicSourceAware;
import com.intellij.database.util.SearchPath;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlFunctionCallExpression;
import com.intellij.sql.psi.SqlStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PlpgDebugFacade implements SqlDebuggerFacade {

    private SqlFunctionCallExpression currentFunction;

    @Override
    public boolean isApplicableToDebugStatement(@NotNull SqlStatement sqlStatement) {
        //System.out.println("isApplicableToDebugStatement "+sqlStatement);
        Collection<SqlFunctionCallExpression> functionCallExpressions
                = PsiTreeUtil.findChildrenOfAnyType(sqlStatement, SqlFunctionCallExpression.class);
        if (functionCallExpressions.isEmpty()) {
            currentFunction = null;
            return false;
        }
        currentFunction = functionCallExpressions.stream().findFirst().get();
        return true;
    }

    @Override
    public boolean isApplicableToDebugRoutine(@NotNull BasicSourceAware basicSourceAware) {
        System.out.println("isApplicableToDebugRoutine " + basicSourceAware);
        return true;
    }

    @Override
    public boolean canDebug(@NotNull LocalDataSource localDataSource) {
        //System.out.println("canDebug");
        return localDataSource.getDbms().isPostgres();
    }

    @Override
    public @NotNull SqlDebugController createController(@NotNull Project project,
                                                        @NotNull DatabaseConnectionPoint databaseConnectionPoint,
                                                        DataRequest.@NotNull OwnerEx ownerEx,
                                                        boolean b,
                                                        @Nullable VirtualFile virtualFile,
                                                        @Nullable RangeMarker rangeMarker,
                                                        @Nullable SearchPath searchPath) {
        return new PlpgDebugController(project, databaseConnectionPoint, ownerEx, virtualFile, rangeMarker, searchPath, currentFunction);    }


}
