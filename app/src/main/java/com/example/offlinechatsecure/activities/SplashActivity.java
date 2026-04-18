package com.example.offlinechatsecure.activities;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.utils.BiometricHelper;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 600L;

    private BiometricHelper biometricHelper;
    private ActivityResultLauncher<Intent> deviceCredentialLauncher;
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private boolean authenticationStarted;
    private final Runnable showBiometricRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed() && !authenticationStarted) {
                authenticationStarted = true;
                showBiometricPrompt();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        biometricHelper = new BiometricHelper(this);
        deviceCredentialLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        openMainScreen();
                    } else {
                        showRetryOrExitDialog();
                    }
                }
        );

        if (!biometricHelper.canAuthenticate()) {
            showUnsupportedDialog();
            return;
        }

        scheduleBiometricPrompt();
    }

    private void scheduleBiometricPrompt() {
        splashHandler.postDelayed(showBiometricRunnable, SPLASH_DELAY_MS);
    }

    private void showBiometricPrompt() {
        biometricHelper.authenticate(
                R.string.biometric_title,
                R.string.biometric_subtitle,
                R.string.biometric_negative_button,
                new BiometricHelper.AuthenticationListener() {
                    @Override
                    public void onSuccess() {
                        openMainScreen();
                    }

                    @Override
                    public void onFailed() {
                        Toast.makeText(
                                SplashActivity.this,
                                R.string.biometric_failed,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(int errorCode, @NonNull CharSequence errorMessage) {
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
                            Toast.makeText(SplashActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
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
                deviceCredentialLauncher.launch(intent);
                return;
            }
        }

        showRetryOrExitDialog();
    }

    private void showUnsupportedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.biometric_unavailable_title)
                .setMessage(R.string.biometric_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit_label, (dialog, which) -> finishAffinity())
                .show();
    }

    private void showRetryOrExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.biometric_retry_title)
                .setMessage(R.string.biometric_retry_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        authenticationStarted = false;
                        showBiometricPrompt();
                    }
                })
                .setNegativeButton(R.string.exit_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishAffinity();
                    }
                })
                .show();
    }

    private void openMainScreen() {
        splashHandler.removeCallbacks(showBiometricRunnable);
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_SKIP_REAUTH_ON_LAUNCH, true);
        startActivity(mainIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        splashHandler.removeCallbacks(showBiometricRunnable);
        super.onDestroy();
    }
}
