package com.example.offlinechatsecure.activities;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.utils.AppAuthState;
import com.example.offlinechatsecure.utils.BiometricHelper;

/**
 * Shared auth gate for all non-splash screens.
 */
public abstract class AuthenticatedActivity extends AppCompatActivity {

    private BiometricHelper biometricHelper;
    private ActivityResultLauncher<Intent> deviceCredentialLauncher;
    private boolean isReAuthInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        biometricHelper = new BiometricHelper(this);
        deviceCredentialLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    isReAuthInProgress = false;
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        onAuthenticationSucceeded();
                    } else {
                        showRetryOrExitDialog();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppAuthState.isReauthRequired() && !isReAuthInProgress) {
            requestReAuthentication();
        }
    }

    private void requestReAuthentication() {
        if (!biometricHelper.canAuthenticate()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.biometric_unavailable_title)
                    .setMessage(R.string.biometric_unavailable_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.exit_label, (dialog, which) -> finishAffinity())
                    .show();
            return;
        }

        isReAuthInProgress = true;
        biometricHelper.authenticate(
                R.string.reauth_title,
                R.string.reauth_subtitle,
                R.string.biometric_negative_button,
                new BiometricHelper.AuthenticationListener() {
                    @Override
                    public void onSuccess() {
                        isReAuthInProgress = false;
                        onAuthenticationSucceeded();
                    }

                    @Override
                    public void onFailed() {
                        Toast.makeText(
                                AuthenticatedActivity.this,
                                R.string.biometric_failed,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(int errorCode, @NonNull CharSequence errorMessage) {
                        isReAuthInProgress = false;

                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            showDeviceCredentialPrompt();
                            return;
                        }

                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_CANCELED
                                || errorCode == BiometricPrompt.ERROR_TIMEOUT
                                || errorCode == BiometricPrompt.ERROR_LOCKOUT
                                || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                            showRetryOrExitDialog();
                        } else {
                            Toast.makeText(
                                    AuthenticatedActivity.this,
                                    errorMessage,
                                    Toast.LENGTH_SHORT
                            ).show();
                            showRetryOrExitDialog();
                        }
                    }
                }
        );
    }

    private void showDeviceCredentialPrompt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && biometricHelper.isDeviceCredentialSet()) {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (keyguardManager == null) {
                showRetryOrExitDialog();
                return;
            }

            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                    getString(R.string.device_credential_title),
                    getString(R.string.device_credential_description)
            );

            if (intent != null) {
                isReAuthInProgress = true;
                deviceCredentialLauncher.launch(intent);
                return;
            }
        }

        showRetryOrExitDialog();
    }

    private void onAuthenticationSucceeded() {
        AppAuthState.setReauthRequired(false);
        onUnlocked();
    }

    protected void onUnlocked() {
        // Optional hook for child activities.
    }

    private void showRetryOrExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.biometric_retry_title)
                .setMessage(R.string.reauth_retry_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry_label, (dialog, which) -> requestReAuthentication())
                .setNegativeButton(R.string.exit_label, (dialog, which) -> finishAffinity())
                .show();
    }
}

