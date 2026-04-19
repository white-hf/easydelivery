package com.hf.courierservice.apihelper.exception;

public class UnAuthorizedException extends Exception {
    private final int httpStatusCode;
    private final String requestUrl;

    public UnAuthorizedException() {
        this(-1, null);
    }

    public UnAuthorizedException(int httpStatusCode, String requestUrl) {
        super(buildMessage(httpStatusCode, requestUrl));
        this.httpStatusCode = httpStatusCode;
        this.requestUrl = requestUrl;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    private static String buildMessage(int httpStatusCode, String requestUrl) {
        StringBuilder builder = new StringBuilder("Unauthorized Exception");
        if (httpStatusCode > 0) {
            builder.append(" [http=").append(httpStatusCode).append(']');
        }
        if (requestUrl != null && !requestUrl.isEmpty()) {
            builder.append(" [url=").append(requestUrl).append(']');
        }
        return builder.toString();
    }
}
