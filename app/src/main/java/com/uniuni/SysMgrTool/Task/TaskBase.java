package com.uniuni.SysMgrTool.Task;

import android.os.Message;

/**
 * This is a interface for tasks handled asynchronously in the handler
 */
public interface TaskBase {
    void doIt(Message msg);
}
