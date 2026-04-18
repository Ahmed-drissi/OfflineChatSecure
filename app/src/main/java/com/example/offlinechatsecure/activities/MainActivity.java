package com.example.offlinechatsecure.activities;

import android.app.Activity;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.network.BluetoothConnectionManager;
import com.example.offlinechatsecure.network.BluetoothSession;
import com.example.offlinechatsecure.utils.BiometricHelper;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_SKIP_REAUTH_ON_LAUNCH = "extra_skip_reauth_on_launch";

    private BiometricHelper biometricHelper;
    private BluetoothAdapter bluetoothAdapter;
    private ActivityResultLauncher<Intent> deviceCredentialLauncher;
    private ActivityResultLauncher<String[]> bluetoothPermissionLauncher;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;
    private ActivityResultLauncher<Intent> deviceListLauncher;
    private boolean skipReAuthOnFirstResume;
    private boolean shouldRequireReAuth;
    private boolean isReAuthInProgress;
    private boolean isBluetoothPromptInProgress;
    private boolean isBluetoothReady;
    private TextView tvSelectedDevice;
    private TextView tvConnectionState;
    private Button btnConnectSelected;
    private Button btnOpenChat;
    private BluetoothConnectionManager connectionManager;
    private String selectedDeviceAddress;
    private String selectedDeviceName;
    private boolean isConnectionEstablished;
    private boolean isConnectionInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSelectedDevice = findViewById(R.id.tvSelectedDevice);
        tvConnectionState = findViewById(R.id.tvConnectionState);
        btnConnectSelected = findViewById(R.id.btnConnectSelected);
        btnOpenChat = findViewById(R.id.btnOpenChat);
        Button btnDiscoverDevices = findViewById(R.id.btnDiscoverDevices);
        btnDiscoverDevices.setOnClickListener(v -> openDeviceList());
        btnConnectSelected.setOnClickListener(v -> connectToSelectedDevice());
        btnOpenChat.setOnClickListener(v -> openChatScreen());
        updateConnectionStateUi(BluetoothConnectionManager.ConnectionState.IDLE, getString(R.string.connection_state_idle));
        updateConnectButtonState();
        updateOpenChatButtonState();

        biometricHelper = new BiometricHelper(this);
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        skipReAuthOnFirstResume = getIntent().getBooleanExtra(EXTRA_SKIP_REAUTH_ON_LAUNCH, false);

        if (bluetoothAdapter != null) {
            connectionManager = new BluetoothConnectionManager(
                    bluetoothAdapter,
                    (state, detail) -> runOnUiThread(() -> {
                        isConnectionEstablished = state == BluetoothConnectionManager.ConnectionState.CONNECTED;
                        isConnectionInProgress = state == BluetoothConnectionManager.ConnectionState.CONNECTING;
                        updateConnectionStateUi(state, detail);
                        updateConnectButtonState();
                        updateOpenChatButtonState();
                    })
            );
            BluetoothSession.setConnectionManager(connectionManager);
        }

        deviceCredentialLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    isReAuthInProgress = false;
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        shouldRequireReAuth = false;
                        ensureBluetoothReady();
                        updateConnectButtonState();
                    } else {
                        showRetryOrExitDialog();
                    }
                }
        );

        bluetoothPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (areAllBluetoothPermissionsGranted()) {
                        requestEnableBluetoothIfNeeded();
                    } else {
                        showBluetoothPermissionRequiredDialog();
                    }
                }
        );

        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    isBluetoothPromptInProgress = false;
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        isBluetoothReady = true;
                        startAcceptingConnectionsIfReady();
                        updateConnectButtonState();
                    } else {
                        showEnableBluetoothRequiredDialog();
                    }
                }
        );

        deviceListLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        return;
                    }

                    Intent data = result.getData();
                    String selectedName = data.getStringExtra(DeviceListActivity.EXTRA_SELECTED_DEVICE_NAME);
                    String selectedAddress = data.getStringExtra(DeviceListActivity.EXTRA_SELECTED_DEVICE_ADDRESS);

                    if (selectedName == null || selectedAddress == null) {
                        return;
                    }

                    selectedDeviceName = selectedName;
                    selectedDeviceAddress = selectedAddress;

                    tvSelectedDevice.setText(
                            getString(R.string.selected_device_label, selectedName, selectedAddress)
                    );
                    updateConnectButtonState();
                }
        );

        ensureBluetoothReady();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (skipReAuthOnFirstResume) {
            skipReAuthOnFirstResume = false;
            ensureBluetoothReady();
            return;
        }

        if (shouldRequireReAuth && !isReAuthInProgress) {
            requestReAuthentication();
            return;
        }

        ensureBluetoothReady();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            shouldRequireReAuth = true;
            updateConnectButtonState();
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && connectionManager != null) {
            connectionManager.release();
            BluetoothSession.clear();
        }
        super.onDestroy();
    }

    private void requestReAuthentication() {
        if (!biometricHelper.canAuthenticate()) {
            showUnavailableAndExitDialog();
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
                        shouldRequireReAuth = false;
                        ensureBluetoothReady();
                        updateConnectButtonState();
                    }

                    @Override
                    public void onFailed() {
                        Toast.makeText(
                                MainActivity.this,
                                R.string.biometric_failed,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(int errorCode, @NonNull CharSequence errorMessage) {
                        isReAuthInProgress = false;
                        updateConnectButtonState();

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
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
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

    private void ensureBluetoothReady() {
        if (isReAuthInProgress || shouldRequireReAuth || isBluetoothReady) {
            if (isBluetoothReady) {
                startAcceptingConnectionsIfReady();
            }
            updateConnectButtonState();
            updateOpenChatButtonState();
            return;
        }

        if (bluetoothAdapter == null) {
            showBluetoothUnavailableAndExitDialog();
            updateConnectButtonState();
            updateOpenChatButtonState();
            return;
        }

        if (!areAllBluetoothPermissionsGranted()) {
            requestBluetoothPermissions();
            updateConnectButtonState();
            updateOpenChatButtonState();
            return;
        }

        requestEnableBluetoothIfNeeded();
        updateConnectButtonState();
        updateOpenChatButtonState();
    }

    private boolean areAllBluetoothPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else {
            bluetoothPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    private void requestEnableBluetoothIfNeeded() {
        if (bluetoothAdapter == null) {
            showBluetoothUnavailableAndExitDialog();
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            isBluetoothReady = true;
            startAcceptingConnectionsIfReady();
            updateConnectButtonState();
            updateOpenChatButtonState();
            return;
        }

        if (isBluetoothPromptInProgress) {
            return;
        }

        showEnableBluetoothRequiredDialog();
    }

    private void openDeviceList() {
        ensureBluetoothReady();
        if (!isBluetoothReady || shouldRequireReAuth || isReAuthInProgress) {
            return;
        }

        Intent intent = new Intent(this, DeviceListActivity.class);
        deviceListLauncher.launch(intent);
    }

    private void connectToSelectedDevice() {
        if (selectedDeviceAddress == null || selectedDeviceAddress.trim().isEmpty()) {
            Toast.makeText(this, R.string.select_device_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isConnectionInProgress) {
            Toast.makeText(this, R.string.connection_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isBluetoothReady || shouldRequireReAuth || isReAuthInProgress) {
            ensureBluetoothReady();
            return;
        }

        if (connectionManager == null || bluetoothAdapter == null) {
            Toast.makeText(this, R.string.connection_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            BluetoothDevice remote = bluetoothAdapter.getRemoteDevice(selectedDeviceAddress);
            connectionManager.connectToDevice(remote);
        } catch (IllegalArgumentException illegalAddressException) {
            Toast.makeText(this, R.string.invalid_device_address, Toast.LENGTH_SHORT).show();
        }
    }

    private void startAcceptingConnectionsIfReady() {
        if (connectionManager == null || !isBluetoothReady || shouldRequireReAuth || isReAuthInProgress) {
            return;
        }

        connectionManager.startAccepting();
    }

    private void updateConnectionStateUi(
            @NonNull BluetoothConnectionManager.ConnectionState state,
            String detail
    ) {
        int stateTextRes;
        switch (state) {
            case LISTENING:
                stateTextRes = R.string.connection_state_listening;
                break;
            case CONNECTING:
                stateTextRes = R.string.connection_state_connecting;
                break;
            case CONNECTED:
                stateTextRes = R.string.connection_state_connected;
                break;
            case FAILED:
                stateTextRes = R.string.connection_state_failed;
                break;
            case DISCONNECTED:
                stateTextRes = R.string.connection_state_disconnected;
                break;
            case IDLE:
            default:
                stateTextRes = R.string.connection_state_idle;
                break;
        }

        if (detail == null || detail.trim().isEmpty()) {
            tvConnectionState.setText(getString(R.string.connection_state_label, getString(stateTextRes)));
        } else {
            tvConnectionState.setText(
                    getString(
                            R.string.connection_state_with_detail,
                            getString(stateTextRes),
                            detail
                    )
            );
        }
    }

    private void updateConnectButtonState() {
        if (btnConnectSelected == null) {
            return;
        }

        boolean hasSelectedDevice = selectedDeviceAddress != null && !selectedDeviceAddress.trim().isEmpty();
        boolean canConnect = isBluetoothReady
                && !shouldRequireReAuth
                && !isReAuthInProgress
                && !isConnectionInProgress
                && hasSelectedDevice;
        btnConnectSelected.setEnabled(canConnect);
        btnConnectSelected.setAlpha(canConnect ? 1.0f : 0.6f);
    }

    private void updateOpenChatButtonState() {
        if (btnOpenChat == null) {
            return;
        }

        boolean canOpenChat = isConnectionEstablished && !shouldRequireReAuth && !isReAuthInProgress;
        btnOpenChat.setEnabled(canOpenChat);
        btnOpenChat.setAlpha(canOpenChat ? 1.0f : 0.6f);
    }

    private void openChatScreen() {
        if (!isConnectionEstablished) {
            Toast.makeText(this, R.string.connect_before_chat, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_REMOTE_NAME, selectedDeviceName);
        intent.putExtra(ChatActivity.EXTRA_REMOTE_ADDRESS, selectedDeviceAddress);
        startActivity(intent);
    }

    private void showBluetoothPermissionRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_permission_title)
                .setMessage(R.string.bluetooth_permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestBluetoothPermissions();
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

    private void showEnableBluetoothRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_enable_title)
                .setMessage(R.string.bluetooth_enable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        isBluetoothPromptInProgress = true;
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        enableBluetoothLauncher.launch(enableIntent);
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

    private void showBluetoothUnavailableAndExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_unavailable_title)
                .setMessage(R.string.bluetooth_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit_label, (dialog, which) -> finishAffinity())
                .show();
    }

    private void showRetryOrExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.biometric_retry_title)
                .setMessage(R.string.reauth_retry_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry_label, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestReAuthentication();
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

    private void showUnavailableAndExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.biometric_unavailable_title)
                .setMessage(R.string.biometric_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit_label, (dialog, which) -> finishAffinity())
                .show();
    }
}
