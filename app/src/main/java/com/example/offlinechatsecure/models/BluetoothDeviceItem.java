package com.example.offlinechatsecure.models;

import androidx.annotation.NonNull;

import java.util.Objects;

public class BluetoothDeviceItem {

    private final String name;
    private final String address;

    public BluetoothDeviceItem(@NonNull String name, @NonNull String address) {
        this.name = name;
        this.address = address;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getAddress() {
        return address;
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

