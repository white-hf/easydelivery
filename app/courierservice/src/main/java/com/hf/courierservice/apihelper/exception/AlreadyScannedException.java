package com.hf.courierservice.apihelper.exception;

public class AlreadyScannedException extends Exception {
    private final String requestUrl;
    private final String bizCode;

    public AlreadyScannedException(String requestUrl, String bizCode, String bizMessage) {
        super(buildMessage(requestUrl, bizCode, bizMessage));
        this.requestUrl = requestUrl;
        this.bizCode = bizCode;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getBizCode() {
        return bizCode;
    }

    private static String buildMessage(String requestUrl, String bizCode, String bizMessage) {
        StringBuilder builder = new StringBuilder("Already scanned");
        if (bizCode != null && !bizCode.isEmpty()) {
            builder.append(" [biz=").append(bizCode).append(']');
        }
        if (bizMessage != null && !bizMessage.isEmpty()) {
            builder.append(" [message=").append(bizMessage).append(']');
        }
        if (requestUrl != null && !requestUrl.isEmpty()) {
            builder.append(" [url=").append(requestUrl).append(']');
        }
        return builder.toString();
    }
}
