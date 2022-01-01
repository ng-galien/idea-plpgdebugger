package org.postgres.debugger;

import com.intellij.database.debugger.SqlDebuggerEditorsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlpgDebugEditorProvider extends SqlDebuggerEditorsProvider {

    public static final PlpgDebugEditorProvider INSTANCE = new PlpgDebugEditorProvider();

    @Override
    protected PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
        return PsiFileFactory.getInstance(project).createFileFromText("file.dummy", getFileType(), text, 0, isPhysical);
    }

}
