package com.example.offlinechatsecure.utils;

/**
 * Stores process-wide auth requirements between activities.
 */
public final class AppAuthState {

    private static volatile boolean reauthRequired;

    private AppAuthState() {
    }

    public static boolean isReauthRequired() {
        return reauthRequired;
    }

    public static void setReauthRequired(boolean required) {
        reauthRequired = required;
    }
}

