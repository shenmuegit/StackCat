package com.stackcat.agent;

import java.lang.instrument.Instrumentation;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zhenzijun
 */
@Slf4j
public class MethodTrackingAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            log.info("Starting method tracking agent...");
            log.info("Can retransform classes: {}", inst.isRetransformClassesSupported());
            log.info("Can redefine classes: {}", inst.isRedefineClassesSupported());
            inst.addTransformer(new MethodTrackingTransformer(), true);
            log.info("Transformer registered successfully (with retransform support)");
        } catch (Exception e) {
            log.error("Error in premain: {}: {}", e.getClass().getName(), e.getMessage(), e);
            // Don't fail the application startup
        }
    }
}

