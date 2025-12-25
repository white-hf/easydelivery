package com.hf.easydelivery.core.policy;

public interface LocationPolicy {
    LocationRequestParams getRequestParams(LocationPolicyContext context);
}
