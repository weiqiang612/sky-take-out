package com.weiqiang.skyai.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skyai.memory.user-profile")
public class UserProfileMemoryProperties {

    /**
     * Master switch for user profile note injection.
     */
    private boolean enabled = true;

    /**
     * Controls whether a summary is prepended to intent recognition input.
     */
    private boolean intentRecognitionSummaryEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIntentRecognitionSummaryEnabled() {
        return intentRecognitionSummaryEnabled;
    }

    public void setIntentRecognitionSummaryEnabled(boolean intentRecognitionSummaryEnabled) {
        this.intentRecognitionSummaryEnabled = intentRecognitionSummaryEnabled;
    }
}
