package com.weiqiang.skyai.advisor;

import com.weiqiang.skyai.intent_recognition.model.IntentType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Metrics for tracking user profile injection in the advisor.
 * Records counts and character lengths of injected profiles during context building and intent recognition.
 */
@Component
@RequiredArgsConstructor
public class UserProfileInjectionMetrics {

    private final MeterRegistry meterRegistry;

    public void recordContext(IntentType intentType, ProfileInjectionLevel level, boolean profilePresent, int charsInjected) {
        record("context", intentType, level, profilePresent, charsInjected);
    }

    public void recordIntentRecognition(IntentType intentType, ProfileInjectionLevel level, boolean profilePresent, int charsInjected) {
        record("intent_recognition", intentType, level, profilePresent, charsInjected);
    }

    // Helper method to record metrics with consistent tagging
    private void record(String stage, IntentType intentType, ProfileInjectionLevel level, boolean profilePresent, int charsInjected) {
        String intentTag = intentType == null ? "unknown" : intentType.value();
        String levelTag = level == null ? ProfileInjectionLevel.NONE.name() : level.name();
        String presentTag = Boolean.toString(profilePresent);
        Counter.builder("skyai.user_profile_notes.injection.count")
                .tag("stage", stage)
                .tag("intent_type", intentTag)
                .tag("profile_injection_level", levelTag)
                .tag("profile_present", presentTag)
                .register(meterRegistry)
                .increment();
        DistributionSummary.builder("skyai.user_profile_notes.injection.chars")
                .baseUnit("chars")
                .tag("stage", stage)
                .tag("intent_type", intentTag)
                .tag("profile_injection_level", levelTag)
                .tag("profile_present", presentTag)
                .register(meterRegistry)
                .record(Math.max(0, charsInjected));
    }
}
