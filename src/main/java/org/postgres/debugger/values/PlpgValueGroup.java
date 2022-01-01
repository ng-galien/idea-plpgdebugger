package org.postgres.debugger.values;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.postgres.debugger.PlpgStackInfo;

import java.util.Collection;

public class PlpgValueGroup extends XValueGroup {

    private final Collection<PlpgStackInfo.StackValue> myValues;
    public PlpgValueGroup(@NotNull String name, Collection<PlpgStackInfo.StackValue> values) {
        super(name);
        this.myValues = values;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList list = new XValueChildrenList();
        myValues.forEach(
                stackValue -> list.add(stackValue.getQualifier(), new PlpgNamedValue(stackValue))
        );
        node.addChildren(list, false);
    }

    @Override
    public boolean isAutoExpand() {
        return true;
    }
}
