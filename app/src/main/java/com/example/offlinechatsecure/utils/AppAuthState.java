package com.example.offlinechatsecure.utils;

/**
 * Stores process-wide auth requirements between activities.
 */
public final class AppAuthState {

    private static volatile boolean reauthRequired;
    private static volatile boolean appInForeground;
    private static volatile boolean chatScreenActive;

    private AppAuthState() {
    }

    public static boolean isReauthRequired() {
        return reauthRequired;
    }

    public static void setReauthRequired(boolean required) {
        reauthRequired = required;
    }

    public static boolean isAppInForeground() {
        return appInForeground;
    }

    public static void setAppInForeground(boolean inForeground) {
        appInForeground = inForeground;
    }

    public static boolean isChatScreenActive() {
        return chatScreenActive;
    }

    public static void setChatScreenActive(boolean active) {
        chatScreenActive = active;
    }
}

