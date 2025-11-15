package com.hf.easydelivery.component;

public interface BatchSubmitCallback {
    /**
     * @param done    已处理（成功+失败）条数
     * @param total   总条数
     * @param success 已成功条数
     * @param fail    已失败条数
     */
    void onProgress(int done, int total, int success, int fail);

    void onSingleComplete(String trackingNo);

    /** 全部完成时调用 */
    void onComplete(int successCount, int failCount);

    void onFail(Exception e);
}
