package com.hf.easydelivery.core.strategy;

public enum BoostReason {
    UNKNOWN("unknown"),
    FORCE("force"),
    EDGE_RISK("edge_risk"),
    EDGE_FALLBACK("edge_fallback"),
    PROXIMITY("proximity"),
    DISPLACEMENT("displacement_wake"),
    JUMP("jump_risk"),
    POOR_FIX("poor_fix"),
    EMERGENCY("emergency"),
    MOTION("motion_wake"),
    INSIDE("inside_zone"),
    MANUAL_CENTER("manual_center"),
    RESUME_FOLLOW("resume_follow");

    private final String key;

    BoostReason(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static BoostReason fromKey(String key) {
        if (key == null) {
            return UNKNOWN;
        }
        switch (key) {
            case "force":
                return FORCE;
            case "edge_risk":
                return EDGE_RISK;
            case "edge_fallback":
                return EDGE_FALLBACK;
            case "proximity":
                return PROXIMITY;
            case "displacement_wake":
                return DISPLACEMENT;
            case "jump_risk":
                return JUMP;
            case "poor_fix":
                return POOR_FIX;
            case "emergency":
                return EMERGENCY;
            case "motion_wake":
                return MOTION;
            case "inside_zone":
                return INSIDE;
            case "manual_center":
                return MANUAL_CENTER;
            case "resume_follow":
                return RESUME_FOLLOW;
            default:
                return UNKNOWN;
        }
    }
}
