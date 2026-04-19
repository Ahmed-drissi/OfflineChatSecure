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
import android.os.ParcelUuid;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.adapters.DeviceListAdapter;
import com.example.offlinechatsecure.database.DBHelper;
import com.example.offlinechatsecure.models.BluetoothDeviceItem;
import com.example.offlinechatsecure.network.BluetoothConnectionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeviceListActivity extends AuthenticatedActivity {

    public static final String EXTRA_SELECTED_DEVICE_NAME = "extra_selected_device_name";
    public static final String EXTRA_SELECTED_DEVICE_ADDRESS = "extra_selected_device_address";

    private static final int DISCOVERABLE_DURATION_SECONDS = 120;

    private final Map<String, BluetoothDeviceItem> discoveredMap = new LinkedHashMap<>();

    private BluetoothAdapter bluetoothAdapter;
    private DeviceListAdapter adapter;
    private View scanningChip;
    private TextView tvEmptyState;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etFilter;
    private boolean receiverRegistered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pendingStartAfterSettings;
    private DBHelper dbHelper;
    private final Set<String> chattedPeerAddresses = new LinkedHashSet<>();
    private final Set<String> pendingUuidChecks = new LinkedHashSet<>();

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

                int rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI,
                        (short) BluetoothDeviceItem.RSSI_UNKNOWN
                );
                String deviceAddress = device.getAddress();
                String deviceName = getSafeDeviceName(device);
                boolean paired = isBonded(device);
                BluetoothDeviceItem existing = discoveredMap.get(deviceAddress);
                if (existing != null) {
                    existing.setRssi(rssi);
                } else {
                    discoveredMap.put(
                            deviceAddress,
                            new BluetoothDeviceItem(deviceName, deviceAddress, paired, rssi)
                    );
                }
                publishDevices();
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                showScanning(false);
                updateEmptyState();
            }

            if (BluetoothDevice.ACTION_UUID.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || device.getAddress() == null) {
                    return;
                }

                String address = device.getAddress();
                pendingUuidChecks.remove(address);

                BluetoothDeviceItem existing = discoveredMap.get(address);
                if (existing == null) {
                    return;
                }

                android.os.Parcelable[] raw = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                ParcelUuid[] uuids = toParcelUuidArray(raw);
                boolean reachable = containsChatServiceUuid(uuids);
                existing.setAppReachable(reachable);
                publishDevices();
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

    private final ActivityResultLauncher<Intent> discoverableLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() > 0) {
                    Toast.makeText(
                            this,
                            getString(R.string.discoverable_active, result.getResultCode()),
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = manager != null ? manager.getAdapter() : null;
        dbHelper = new DBHelper(this);
        refreshChattedPeerAddresses();

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
        if (dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
        }
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
        scanningChip = findViewById(R.id.scanningChip);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        swipeRefresh = findViewById(R.id.swipeRefreshDevices);
        etFilter = findViewById(R.id.etDeviceFilter);

        adapter = new DeviceListAdapter(item -> {
            Intent data = new Intent();
            data.putExtra(EXTRA_SELECTED_DEVICE_NAME, item.getName());
            data.putExtra(EXTRA_SELECTED_DEVICE_ADDRESS, item.getAddress());
            setResult(RESULT_OK, data);
            finish();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.app_primary);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.card_background);
        swipeRefresh.setOnRefreshListener(this::refreshScan);

        findViewById(R.id.btnScanAgain).setOnClickListener(v -> refreshScan());
        findViewById(R.id.btnDiscoverable).setOnClickListener(v -> requestDiscoverable());

        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setFilter(s == null ? "" : s.toString());
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        updateEmptyState();
    }

    private void refreshScan() {
        if (!hasScanPermissions()) {
            requestScanPermissions();
            swipeRefresh.setRefreshing(false);
            return;
        }
        discoveredMap.clear();
        pendingUuidChecks.clear();
        refreshChattedPeerAddresses();
        publishDevices();
        loadBondedDevices();
        startDiscovery();
        swipeRefresh.setRefreshing(false);
    }

    private void requestDiscoverable() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(
                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                DISCOVERABLE_DURATION_SECONDS
        );
        try {
            discoverableLauncher.launch(intent);
        } catch (SecurityException securityException) {
            Toast.makeText(this, R.string.bluetooth_permission_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void publishDevices() {
        List<BluetoothDeviceItem> snapshot = new ArrayList<>();
        for (BluetoothDeviceItem item : discoveredMap.values()) {
            if (!item.isPaired() || chattedPeerAddresses.contains(normalizeAddress(item.getAddress()))) {
                snapshot.add(item);
            }
        }
        adapter.setDevices(snapshot);
        updateEmptyState();
    }

    private void startDiscovery() {
        if (bluetoothAdapter == null) {
            return;
        }

        refreshChattedPeerAddresses();
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
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {
                // best effort
            }
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
        filter.addAction(BluetoothDevice.ACTION_UUID);
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

    private boolean isBonded(@NonNull BluetoothDevice device) {
        try {
            return device.getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (SecurityException e) {
            return false;
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

        if (isBonded(device)) {
            return name + " " + getString(R.string.paired_device_suffix);
        }
        return name;
    }

    private void showScanning(boolean scanning) {
        scanningChip.setVisibility(scanning ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState() {
        boolean scanning = scanningChip.getVisibility() == View.VISIBLE;
        boolean empty = adapter.getItemCount() == 0;
        if (empty && !scanning) {
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
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

                String address = bonded.getAddress();
                if (!discoveredMap.containsKey(address)) {
                    String bondedName = getSafeDeviceName(bonded);
                    BluetoothDeviceItem item = new BluetoothDeviceItem(
                            bondedName,
                            address,
                            true,
                            BluetoothDeviceItem.RSSI_UNKNOWN
                    );
                    item.setAppReachable(false);
                    discoveredMap.put(
                            address,
                            item
                    );
                }

                if (chattedPeerAddresses.contains(normalizeAddress(address))) {
                    requestServiceAvailability(bonded);
                }
            }
        } catch (SecurityException ignored) {
            // Permissions are checked before call; keep list stable if OEM still throws.
        }

        publishDevices();
    }

    private void refreshChattedPeerAddresses() {
        chattedPeerAddresses.clear();
        if (dbHelper == null) {
            return;
        }
        chattedPeerAddresses.addAll(dbHelper.getPeerAddressesWithHistory());
    }

    @NonNull
    private String normalizeAddress(@NonNull String address) {
        return address.trim().toUpperCase(Locale.US);
    }

    private void requestServiceAvailability(@NonNull BluetoothDevice device) {
        String address = device.getAddress();
        if (address == null || pendingUuidChecks.contains(address)) {
            return;
        }

        pendingUuidChecks.add(address);
        try {
            boolean started = device.fetchUuidsWithSdp();
            if (!started) {
                pendingUuidChecks.remove(address);
            }
        } catch (SecurityException ignored) {
            pendingUuidChecks.remove(address);
        }
    }

    private boolean containsChatServiceUuid(ParcelUuid[] uuids) {
        if (uuids == null || uuids.length == 0) {
            return false;
        }

        UUID expected = BluetoothConnectionManager.CHAT_SERVICE_UUID;
        for (ParcelUuid uuid : uuids) {
            if (uuid != null && expected.equals(uuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private ParcelUuid[] toParcelUuidArray(android.os.Parcelable[] raw) {
        if (raw == null || raw.length == 0) {
            return new ParcelUuid[0];
        }

        List<ParcelUuid> uuids = new ArrayList<>();
        for (android.os.Parcelable parcelable : raw) {
            if (parcelable instanceof ParcelUuid) {
                uuids.add((ParcelUuid) parcelable);
            }
        }
        return uuids.toArray(new ParcelUuid[0]);
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
