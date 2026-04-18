package com.example.offlinechatsecure.network;

import androidx.annotation.Nullable;

/**
 * Holds the active Bluetooth connection manager for cross-screen access.
 */
public final class BluetoothSession {

    private static BluetoothConnectionManager connectionManager;

    private BluetoothSession() {
    }

    public static synchronized void setConnectionManager(@Nullable BluetoothConnectionManager manager) {
        connectionManager = manager;
    }

    @Nullable
    public static synchronized BluetoothConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public static synchronized void clear() {
        connectionManager = null;
    }
}

