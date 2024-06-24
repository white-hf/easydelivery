package com.uniuni.SysMgrTool.common;

public interface ResponseCallBack<T> {
        void onComplete(Result<T> result);
        void onFail(Result<T> result);
}
