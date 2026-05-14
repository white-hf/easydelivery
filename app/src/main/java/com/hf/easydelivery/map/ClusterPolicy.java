package com.hf.easydelivery.map;

import androidx.annotation.NonNull;

interface ClusterPolicy {
    @NonNull
    ClusterPolicyDecision evaluate();
}
