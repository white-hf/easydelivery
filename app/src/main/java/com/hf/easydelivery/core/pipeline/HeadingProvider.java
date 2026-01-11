package com.hf.easydelivery.core.pipeline;

public interface HeadingProvider {
    boolean hasReliableHeading();

    float getHeadingDegrees();
}
