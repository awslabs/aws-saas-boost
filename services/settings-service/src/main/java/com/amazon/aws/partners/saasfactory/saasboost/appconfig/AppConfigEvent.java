package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import java.util.Map;

public enum AppConfigEvent {
    APP_CONFIG_CHANGED("Application Configuration Changed"),
    APP_CONFIG_RESOURCE_CHANGED("Application Configuration Resource Changed")
    ;

    private final String detailType;

    AppConfigEvent(String detailType) {
        this.detailType = detailType;
    }

    public String detailType() {
        return detailType;
    }

    public static AppConfigEvent fromDetailType(String detailType) {
        AppConfigEvent event = null;
        for (AppConfigEvent tenantEvent : AppConfigEvent.values()) {
            if (tenantEvent.detailType().equals(detailType)) {
                event = tenantEvent;
                break;
            }
        }
        return event;
    }

    public static boolean validate(Map<String, Object> event) {
        return validate(event, null);
    }

    public static boolean validate(Map<String, Object> event, String... requiredKeys) {
        if (event == null || !event.containsKey("detail")) {
            return false;
        }
        try {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            if (detail == null) {
                return false;
            }
            if (requiredKeys != null) {
                for (String requiredKey : requiredKeys) {
                    if (!detail.containsKey(requiredKey)) {
                        return false;
                    }
                }
            }
        } catch (ClassCastException cce) {
            return false;
        }
        return true;
    }
}
