package com.example.offlinechatsecure;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.offlinechatsecure.utils.AppAuthState;

/**
 * Marks the app as locked whenever the process goes to background.
 */
public class OfflineChatSecureApp extends Application implements DefaultLifecycleObserver {

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        AppAuthState.setReauthRequired(true);
    }
}

