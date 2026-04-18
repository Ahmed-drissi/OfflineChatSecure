package com.example.offlinechatsecure.network;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles RFCOMM server/client connection setup and state propagation.
 */
public class BluetoothConnectionManager {

    public enum ConnectionState {
        IDLE,
        LISTENING,
        CONNECTING,
        CONNECTED,
        FAILED,
        DISCONNECTED
    }

    public interface ConnectionListener {
        void onStateChanged(@NonNull ConnectionState state, @Nullable String detail);
    }

    public interface MessageListener {
        void onMessageReceived(@NonNull String message);
    }

    public interface ExternalMessageListener {
        void onExternalMessageReceived(@NonNull String message);
    }

    private static final String SERVICE_NAME = "OfflineChatSecureRfcomm";
    public static final UUID CHAT_SERVICE_UUID = UUID.fromString("7d90d8df-aa4d-4f8a-8a1f-e7fb72b1e868");

    private final BluetoothAdapter bluetoothAdapter;
    private final ConnectionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AcceptThread secureAcceptThread;
    private AcceptThread insecureAcceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private BluetoothSocket connectedSocket;
    private ConnectionState currentState = ConnectionState.IDLE;
    private MessageListener messageListener;
    private ConnectionListener externalConnectionListener;
    private ExternalMessageListener externalMessageListener;
    private final AtomicLong connectAttemptCounter = new AtomicLong(0L);
    private long activeConnectAttemptId = 0L;
    private String connectedDeviceAddress;
    private String connectedDeviceName;

    public BluetoothConnectionManager(
            @NonNull BluetoothAdapter bluetoothAdapter,
            @NonNull ConnectionListener listener
    ) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.listener = listener;
    }

    public synchronized void startAccepting() {
        if (connectedSocket != null || isAccepting() || currentState == ConnectionState.CONNECTING) {
            return;
        }

        secureAcceptThread = new AcceptThread(false);
        insecureAcceptThread = new AcceptThread(true);
        secureAcceptThread.start();
        insecureAcceptThread.start();
        notifyState(ConnectionState.LISTENING, "Waiting for incoming connection");
    }

    public synchronized void connectToDevice(@NonNull BluetoothDevice device) {
        closeConnectThread();
        closeConnectedThread();
        closeConnectedSocket();

        long attemptId = connectAttemptCounter.incrementAndGet();
        activeConnectAttemptId = attemptId;

        ConnectThread thread = new ConnectThread(device, attemptId);
        connectThread = thread;
        thread.start();
        notifyState(ConnectionState.CONNECTING, "Connecting to " + safeName(device));
    }

    public synchronized void disconnect() {
        closeConnectedThread();
        closeConnectedSocket();
        connectedDeviceAddress = null;
        connectedDeviceName = null;
        notifyState(ConnectionState.DISCONNECTED, "Connection closed");
        if (!isAccepting()) {
            startAccepting();
        }
    }

    public synchronized void release() {
        activeConnectAttemptId = 0L;
        closeConnectThread();
        closeAcceptThread();
        closeConnectedThread();
        closeConnectedSocket();
        connectedDeviceAddress = null;
        connectedDeviceName = null;
        notifyState(ConnectionState.IDLE, "Connection manager released");
    }

    public synchronized void setMessageListener(@Nullable MessageListener listener) {
        this.messageListener = listener;
    }

    public synchronized void setExternalMessageListener(@Nullable ExternalMessageListener listener) {
        this.externalMessageListener = listener;
    }

    public synchronized void setExternalConnectionListener(@Nullable ConnectionListener listener) {
        this.externalConnectionListener = listener;
    }

    public synchronized boolean sendMessage(@NonNull String message) {
        if (message.trim().isEmpty()) {
            return false;
        }

        if (connectedThread == null) {
            return false;
        }

        return connectedThread.writeMessage(message);
    }

    public synchronized boolean isConnected() {
        return connectedSocket != null && connectedSocket.isConnected();
    }

    @Nullable
    public synchronized String getConnectedDeviceAddress() {
        return connectedDeviceAddress;
    }

    @Nullable
    public synchronized String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    private synchronized void onConnected(@NonNull BluetoothSocket socket) {
        closeConnectThread();
        closeAcceptThread();

        if (connectedSocket != null && connectedSocket.isConnected()) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Duplicate accepted/connected socket can be ignored and closed.
            }
            return;
        }

        closeConnectedThread();
        closeConnectedSocket();
        connectedSocket = socket;

        ConnectedThread thread;
        try {
            thread = new ConnectedThread(socket);
        } catch (IllegalStateException illegalStateException) {
            onConnectionFailed("Cannot open data streams");
            return;
        }
        connectedThread = thread;
        thread.start();

        BluetoothDevice remote = socket.getRemoteDevice();
        connectedDeviceAddress = remote.getAddress();
        connectedDeviceName = safeName(remote);
        notifyState(ConnectionState.CONNECTED, "Connected: " + safeName(remote));
    }

    private synchronized void onConnectedFromAttempt(@NonNull BluetoothSocket socket, long attemptId) {
        if (attemptId != activeConnectAttemptId || currentState != ConnectionState.CONNECTING) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Stale connection attempt socket can be safely dropped.
            }
            return;
        }

        onConnected(socket);
    }

    private synchronized void onConnectionFailed(@Nullable String detail) {
        if (currentState == ConnectionState.CONNECTED) {
            return;
        }
        closeConnectThread();
        closeConnectedThread();
        closeConnectedSocket();
        connectedDeviceAddress = null;
        connectedDeviceName = null;
        notifyState(ConnectionState.FAILED, detail == null ? "Connection failed" : detail);
        if (!isAccepting()) {
            startAccepting();
        }
    }

    private synchronized void onConnectionFailedFromAttempt(@Nullable String detail, long attemptId) {
        if (attemptId != activeConnectAttemptId || currentState != ConnectionState.CONNECTING) {
            return;
        }

        onConnectionFailed(detail);
    }

    private synchronized void onConnectionLost(@Nullable String detail) {
        closeConnectThread();
        closeConnectedThread();
        closeConnectedSocket();
        connectedDeviceAddress = null;
        connectedDeviceName = null;
        notifyState(ConnectionState.DISCONNECTED, detail == null ? "Connection lost" : detail);
        if (!isAccepting()) {
            startAccepting();
        }
    }

    private void onIncomingMessage(@NonNull String message) {
        MessageListener listenerSnapshot;
        ExternalMessageListener externalListenerSnapshot;
        synchronized (this) {
            listenerSnapshot = messageListener;
            externalListenerSnapshot = externalMessageListener;
        }

        mainHandler.post(() -> {
            if (listenerSnapshot != null) {
                listenerSnapshot.onMessageReceived(message);
            }
            if (externalListenerSnapshot != null) {
                externalListenerSnapshot.onExternalMessageReceived(message);
            }
        });
    }

    private synchronized void closeAcceptThread() {
        if (secureAcceptThread != null) {
            secureAcceptThread.cancel();
            secureAcceptThread = null;
        }

        if (insecureAcceptThread != null) {
            insecureAcceptThread.cancel();
            insecureAcceptThread = null;
        }
    }

    private synchronized boolean isAccepting() {
        return secureAcceptThread != null || insecureAcceptThread != null;
    }

    private synchronized void closeConnectThread() {
        if (connectThread != null) {
            // If connection is being finalized from the same thread, do not cancel/close its socket.
            if (connectThread != Thread.currentThread()) {
                connectThread.cancel();
            }
            connectThread = null;
        }
    }

    private void drainDiscovery() {
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            for (int i = 0; i < 8 && bluetoothAdapter.isDiscovering(); i++) {
                Thread.sleep(120L);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (SecurityException ignored) {
            // Caller already performs permission checks; keep best-effort behavior.
        }
    }

    private synchronized void closeConnectedThread() {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    private synchronized void closeConnectedSocket() {
        if (connectedSocket != null) {
            try {
                connectedSocket.close();
            } catch (IOException ignored) {
                // Closing best-effort to avoid leaking socket resources.
            }
            connectedSocket = null;
        }
    }

    private void notifyState(@NonNull ConnectionState state, @Nullable String detail) {
        currentState = state;
        ConnectionListener externalListenerSnapshot;
        synchronized (this) {
            externalListenerSnapshot = externalConnectionListener;
        }

        mainHandler.post(() -> {
            listener.onStateChanged(state, detail);
            if (externalListenerSnapshot != null) {
                externalListenerSnapshot.onStateChanged(state, detail);
            }
        });
    }

    @NonNull
    private String safeName(@NonNull BluetoothDevice device) {
        String name = device.getName();
        return name == null || name.trim().isEmpty() ? device.getAddress() : name;
    }

    private final class AcceptThread extends Thread {

        private final boolean insecure;

        private BluetoothServerSocket serverSocket;

        AcceptThread(boolean insecure) {
            this.insecure = insecure;
        }

        @Override
        public void run() {
            try {
                if (insecure) {
                    serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            SERVICE_NAME,
                            CHAT_SERVICE_UUID
                    );
                } else {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                            SERVICE_NAME,
                            CHAT_SERVICE_UUID
                    );
                }
            } catch (IOException e) {
                // Some devices may not support one listening mode; keep the other mode active.
                return;
            }

            while (!isInterrupted()) {
                BluetoothSocket socket;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                if (socket != null) {
                    onConnected(socket);
                    break;
                }
            }
        }

        void cancel() {
            interrupt();
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                    // Socket close exception can be ignored on shutdown.
                }
                serverSocket = null;
            }
        }
    }

    private final class ConnectThread extends Thread {

        private final BluetoothDevice device;
        private final long attemptId;
        private BluetoothSocket socket;

        ConnectThread(@NonNull BluetoothDevice device, long attemptId) {
            this.device = device;
            this.attemptId = attemptId;
        }

        @Override
        public void run() {
            IOException lastException = null;

            try {
                socket = device.createRfcommSocketToServiceRecord(CHAT_SERVICE_UUID);
                drainDiscovery();
                socket.connect();
            } catch (IOException secureException) {
                lastException = secureException;
                closeSocketQuietly();
            }

            if (socket == null) {
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(CHAT_SERVICE_UUID);
                    drainDiscovery();
                    socket.connect();
                } catch (IOException insecureException) {
                    lastException = insecureException;
                    closeSocketQuietly();
                }
            }

            if (socket == null) {
                String detail = "Failed to connect to selected device";
                if (lastException != null && lastException.getMessage() != null) {
                    detail = detail + ": " + lastException.getMessage();
                }
                onConnectionFailedFromAttempt(detail, attemptId);
                return;
            }

            if (socket != null) {
                onConnectedFromAttempt(socket, attemptId);
            }
        }

        void cancel() {
            interrupt();
            closeSocketQuietly();
        }

        private void closeSocketQuietly() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Socket close exception can be ignored on shutdown.
                }
                socket = null;
            }
        }
    }

    private final class ConnectedThread extends Thread {

        private final BluetoothSocket socket;
        private final BufferedInputStream inputStream;
        private final BufferedOutputStream outputStream;

        ConnectedThread(@NonNull BluetoothSocket socket) {
            this.socket = socket;
            try {
                this.inputStream = new BufferedInputStream(socket.getInputStream());
                this.outputStream = new BufferedOutputStream(socket.getOutputStream());
            } catch (IOException ioException) {
                throw new IllegalStateException("Unable to access socket streams", ioException);
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            StringBuilder pending = new StringBuilder();

            while (!isInterrupted()) {
                int bytesRead;
                try {
                    bytesRead = inputStream.read(buffer);
                } catch (IOException readException) {
                    onConnectionLost("Connection lost while receiving messages");
                    break;
                }

                if (bytesRead == -1) {
                    onConnectionLost("Remote device disconnected");
                    break;
                }

                String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                pending.append(chunk);

                int newlineIndex;
                while ((newlineIndex = pending.indexOf("\n")) >= 0) {
                    String message = pending.substring(0, newlineIndex).trim();
                    pending.delete(0, newlineIndex + 1);
                    if (!message.isEmpty()) {
                        onIncomingMessage(message);
                    }
                }
            }
        }

        boolean writeMessage(@NonNull String message) {
            try {
                byte[] payload = (message + "\n").getBytes(StandardCharsets.UTF_8);
                outputStream.write(payload);
                outputStream.flush();
                return true;
            } catch (IOException writeException) {
                onConnectionLost("Failed to send message");
                return false;
            }
        }

        void cancel() {
            interrupt();
            try {
                socket.close();
            } catch (IOException ignored) {
                // Socket close exception can be ignored on shutdown.
            }
        }
    }
}

