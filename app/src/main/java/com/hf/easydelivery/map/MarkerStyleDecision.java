package com.hf.easydelivery.map;

final class MarkerStyleDecision {
    final MarkerColorTone colorTone;
    final boolean compact;
    final boolean highlighted;
    final boolean largeParcel;

    MarkerStyleDecision(MarkerColorTone colorTone,
            boolean compact,
            boolean highlighted,
            boolean largeParcel) {
        this.colorTone = colorTone;
        this.compact = compact;
        this.highlighted = highlighted;
        this.largeParcel = largeParcel;
    }
}
