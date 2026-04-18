package com.example.offlinechatsecure.models;

import androidx.annotation.NonNull;

import java.util.Objects;

public class BluetoothDeviceItem {

    public static final int RSSI_UNKNOWN = Integer.MIN_VALUE;

    private final String name;
    private final String address;
    private final boolean paired;
    private int rssi;

    public BluetoothDeviceItem(@NonNull String name, @NonNull String address) {
        this(name, address, false, RSSI_UNKNOWN);
    }

    public BluetoothDeviceItem(@NonNull String name, @NonNull String address, boolean paired, int rssi) {
        this.name = name;
        this.address = address;
        this.paired = paired;
        this.rssi = rssi;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    public boolean isPaired() {
        return paired;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    /** 0..4 bars based on RSSI in dBm. Returns 0 if unknown. */
    public int getSignalLevel() {
        if (rssi == RSSI_UNKNOWN) {
            return 0;
        }
        if (rssi >= -55) return 4;
        if (rssi >= -67) return 3;
        if (rssi >= -80) return 2;
        if (rssi >= -90) return 1;
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BluetoothDeviceItem)) {
            return false;
        }
        BluetoothDeviceItem that = (BluetoothDeviceItem) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}

