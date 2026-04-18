package com.example.offlinechatsecure.utils;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Centralizes biometric capability checks and prompt display.
 */
public class BiometricHelper {

    private final FragmentActivity activity;
    private final BiometricManager biometricManager;
    private final KeyguardManager keyguardManager;

    public BiometricHelper(@NonNull FragmentActivity activity) {
        this.activity = activity;
        this.biometricManager = BiometricManager.from(activity);
        this.keyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
    }

    public boolean canAuthenticate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int result = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL
            );
            return result == BiometricManager.BIOMETRIC_SUCCESS;
        }

        int biometricOnly = biometricManager.canAuthenticate();
        return biometricOnly == BiometricManager.BIOMETRIC_SUCCESS || isDeviceCredentialSet();
    }

    public void authenticate(
            @StringRes int titleRes,
            @StringRes int subtitleRes,
            @StringRes int negativeButtonRes,
            @NonNull final AuthenticationListener listener
    ) {
        BiometricPrompt biometricPrompt = new BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result
                    ) {
                        super.onAuthenticationSucceeded(result);
                        listener.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        listener.onFailed();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        listener.onError(errorCode, errString);
                    }
                }
        );

        BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(titleRes))
                .setSubtitle(activity.getString(subtitleRes));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL
            );
        } else {
            promptBuilder.setNegativeButtonText(activity.getString(negativeButtonRes));
        }

        BiometricPrompt.PromptInfo promptInfo = promptBuilder.build();

        biometricPrompt.authenticate(promptInfo);
    }

    public boolean isDeviceCredentialSet() {
        return keyguardManager != null && keyguardManager.isDeviceSecure();
    }

    public interface AuthenticationListener {
        void onSuccess();

        void onFailed();

        void onError(int errorCode, @NonNull CharSequence errorMessage);
    }
}

