package com.example.offlinechatsecure;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.offlinechatsecure.utils.AppAuthState;

/**
 * Marks the app as locked whenever the process goes to background.
 * Also installs a global window-inset listener so every activity's content
 * root reserves space for the status / navigation bars (avoids the system
 * top bar cutting off our title text on edge-to-edge devices).
 */
public class OfflineChatSecureApp extends Application implements DefaultLifecycleObserver {

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        registerActivityLifecycleCallbacks(new InsetsLifecycleCallbacks());
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        AppAuthState.setAppInForeground(true);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        AppAuthState.setAppInForeground(false);
        AppAuthState.setReauthRequired(true);
    }

    private static final class InsetsLifecycleCallbacks
            implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) {
                return;
            }
            // Capture original padding so we add the inset on top of it
            // instead of stomping the layout's own padding values.
            final int basePaddingLeft = root.getPaddingLeft();
            final int basePaddingTop = root.getPaddingTop();
            final int basePaddingRight = root.getPaddingRight();
            final int basePaddingBottom = root.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(
                        basePaddingLeft + bars.left,
                        basePaddingTop + bars.top,
                        basePaddingRight + bars.right,
                        basePaddingBottom + bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(root);
        }

        @Override public void onActivityStarted(@NonNull Activity activity) { }
        @Override public void onActivityResumed(@NonNull Activity activity) { }
        @Override public void onActivityPaused(@NonNull Activity activity) { }
        @Override public void onActivityStopped(@NonNull Activity activity) { }
        @Override public void onActivitySaveInstanceState(
                @NonNull Activity activity, @NonNull Bundle bundle) { }
        @Override public void onActivityDestroyed(@NonNull Activity activity) { }
    }
}

