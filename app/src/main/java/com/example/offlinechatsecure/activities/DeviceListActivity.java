package com.example.offlinechatsecure.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.adapters.DeviceListAdapter;
import com.example.offlinechatsecure.models.BluetoothDeviceItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_DEVICE_NAME = "extra_selected_device_name";
    public static final String EXTRA_SELECTED_DEVICE_ADDRESS = "extra_selected_device_address";

    private final Set<BluetoothDeviceItem> discoveredSet = new LinkedHashSet<>();
    private final List<BluetoothDeviceItem> discoveredList = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private DeviceListAdapter adapter;
    private ProgressBar progressScanning;
    private TextView tvEmptyState;
    private boolean receiverRegistered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pendingStartAfterSettings;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || device.getAddress() == null) {
                    return;
                }

                String deviceName = getSafeDeviceName(device);
                String deviceAddress = device.getAddress();
                BluetoothDeviceItem item = new BluetoothDeviceItem(deviceName, deviceAddress);
                if (discoveredSet.add(item)) {
                    discoveredList.clear();
                    discoveredList.addAll(discoveredSet);
                    adapter.setDevices(discoveredList);
                    updateEmptyState();
                }
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                showScanning(false);
                updateEmptyState();
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (hasScanPermissions()) {
                    startDiscovery();
                } else {
                    Toast.makeText(
                            this,
                            R.string.bluetooth_permission_message,
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = manager != null ? manager.getAdapter() : null;

        setupUi();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.bluetooth_enable_message, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!hasScanPermissions()) {
            requestScanPermissions();
            return;
        }

        loadBondedDevices();
        startDiscovery();
    }

    @Override
    protected void onDestroy() {
        stopDiscovery();
        unregisterScanReceiverSafely();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingStartAfterSettings && hasScanPermissions()) {
            pendingStartAfterSettings = false;
            startDiscovery();
        }
    }

    private void setupUi() {
        RecyclerView recyclerView = findViewById(R.id.rvDevices);
        progressScanning = findViewById(R.id.progressScanning);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        adapter = new DeviceListAdapter(item -> {
            Intent data = new Intent();
            data.putExtra(EXTRA_SELECTED_DEVICE_NAME, item.getName());
            data.putExtra(EXTRA_SELECTED_DEVICE_ADDRESS, item.getAddress());
            setResult(RESULT_OK, data);
            finish();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnScanAgain).setOnClickListener(v -> {
            if (!hasScanPermissions()) {
                requestScanPermissions();
                return;
            }

            discoveredSet.clear();
            discoveredList.clear();
            adapter.setDevices(discoveredList);
            loadBondedDevices();
            startDiscovery();
        });

        updateEmptyState();
    }

    private void startDiscovery() {
        if (bluetoothAdapter == null) {
            return;
        }

        loadBondedDevices();

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationEnabled()) {
            showLocationRequiredDialog();
            return;
        }

        registerScanReceiverIfNeeded();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
            handler.postDelayed(this::startDiscoveryInternal, 450L);
            return;
        }

        startDiscoveryInternal();
    }

    private void startDiscoveryInternal() {
        if (bluetoothAdapter == null) {
            return;
        }

        showScanning(true);
        boolean started;
        try {
            started = bluetoothAdapter.startDiscovery();
        } catch (SecurityException securityException) {
            started = false;
        }

        if (!started) {
            showScanning(false);
            Toast.makeText(this, R.string.device_scan_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        showScanning(false);
    }

    private void registerScanReceiverIfNeeded() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(scanReceiver, filter);
        receiverRegistered = true;
    }

    private void unregisterScanReceiverSafely() {
        if (!receiverRegistered) {
            return;
        }

        receiverRegistered = false;
        unregisterReceiver(scanReceiver);
    }

    private boolean hasScanPermissions() {
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

    private void requestScanPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
        }
    }

    @NonNull
    private String getSafeDeviceName(@NonNull BluetoothDevice device) {
        String name;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            name = getString(R.string.unknown_device_name);
        } else {
            name = device.getName();
        }

        if (name == null || name.trim().isEmpty()) {
            return getString(R.string.unknown_device_name);
        }

        return name;
    }

    private void showScanning(boolean scanning) {
        progressScanning.setVisibility(scanning ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState() {
        if (progressScanning.getVisibility() == View.VISIBLE) {
            tvEmptyState.setText(R.string.scanning_devices);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        if (discoveredList.isEmpty()) {
            tvEmptyState.setText(R.string.no_devices_found);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        }

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void loadBondedDevices() {
        if (bluetoothAdapter == null || !hasScanPermissions()) {
            return;
        }

        try {
            for (BluetoothDevice bonded : bluetoothAdapter.getBondedDevices()) {
                if (bonded == null || bonded.getAddress() == null) {
                    continue;
                }

                String bondedName = getSafeDeviceName(bonded) + " " + getString(R.string.paired_device_suffix);
                BluetoothDeviceItem item = new BluetoothDeviceItem(bondedName, bonded.getAddress());
                discoveredSet.add(item);
            }
        } catch (SecurityException ignored) {
            // Permissions are checked before call; keep list stable if OEM still throws.
        }

        discoveredList.clear();
        discoveredList.addAll(discoveredSet);
        adapter.setDevices(discoveredList);
        updateEmptyState();
    }

    private void showLocationRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_required_title)
                .setMessage(R.string.location_required_message)
                .setCancelable(false)
                .setPositiveButton(R.string.retry_label, (dialog, which) -> {
                    pendingStartAfterSettings = true;
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                })
                .setNegativeButton(R.string.exit_label, (dialog, which) -> finish())
                .show();
    }
}

