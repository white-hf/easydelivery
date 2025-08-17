package com.hf.easydelivery.view.model;

import androidx.lifecycle.ViewModel;

/**
 * 管理 Scan 页的业务状态
 */
public class ScanViewModel extends ViewModel {

    private boolean firstShown = true;

    /**
     * 是否首次进入 Scan 页
     */
    public boolean shouldDoFirstEnter() {
        if (firstShown) {
            firstShown = false;
            return true;
        }
        return false;
    }
}