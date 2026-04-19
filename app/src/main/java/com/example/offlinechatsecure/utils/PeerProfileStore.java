package com.example.offlinechatsecure.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Persists user-defined display name/photo per chat peer on this device.
 */
public final class PeerProfileStore {

    private static final String PREFS_NAME = "peer_profiles";
    private static final String KEY_NAME_PREFIX = "name_";
    private static final String KEY_PHOTO_PREFIX = "photo_";

    private PeerProfileStore() {
    }

    @NonNull
    public static String getDisplayName(
            @NonNull Context context,
            @NonNull String peerKey,
            @NonNull String fallbackName
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_NAME_PREFIX + normalize(peerKey), null);
        if (value == null || value.trim().isEmpty()) {
            return fallbackName;
        }
        return value;
    }

    @Nullable
    public static String getPhotoUri(@NonNull Context context, @NonNull String peerKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_PHOTO_PREFIX + normalize(peerKey), null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    public static void saveProfile(
            @NonNull Context context,
            @NonNull String peerKey,
            @NonNull String displayName,
            @Nullable String photoUri
    ) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String normalized = normalize(peerKey);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_NAME_PREFIX + normalized, displayName.trim());

        if (photoUri == null || photoUri.trim().isEmpty()) {
            editor.remove(KEY_PHOTO_PREFIX + normalized);
        } else {
            editor.putString(KEY_PHOTO_PREFIX + normalized, photoUri);
        }
        editor.apply();
    }

    @NonNull
    private static String normalize(@NonNull String peerKey) {
        return peerKey.trim().toUpperCase(Locale.US);
    }
}

