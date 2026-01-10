package com.hf.easydelivery.core.pipeline;

import java.util.ArrayList;
import java.util.List;

public final class LocationPipeline {
    private final List<LocationProcessor> processors = new ArrayList<>();

    public LocationPipeline addProcessor(LocationProcessor processor) {
        if (processor != null) {
            processors.add(processor);
        }
        return this;
    }

    public void process(ProcessingContext context) {
        for (LocationProcessor processor : processors) {
            processor.process(context);
        }
    }
}
