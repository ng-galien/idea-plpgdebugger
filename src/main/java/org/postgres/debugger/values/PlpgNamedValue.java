package org.postgres.debugger.values;

import com.intellij.util.Icons;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.postgres.debugger.PlpgStackInfo;
import org.postgres.debugger.command.result.PlpgVariableResult;

public class PlpgNamedValue extends XValue {
    private final PlpgStackInfo.StackValue myStackValue;


    public PlpgNamedValue(PlpgStackInfo.StackValue myStackValue) {
        this.myStackValue = myStackValue;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        node.setPresentation(
                PlatformIcons.FIELD_ICON,
                myStackValue.getType(),
                myStackValue.getValue(), false);
    }
}
