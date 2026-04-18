package com.example.offlinechatsecure.activities;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.adapters.MessageAdapter;
import com.example.offlinechatsecure.database.DBHelper;
import com.example.offlinechatsecure.models.ChatMessage;
import com.example.offlinechatsecure.network.BluetoothConnectionManager;
import com.example.offlinechatsecure.network.BluetoothSession;
import com.example.offlinechatsecure.utils.AppAuthState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_REMOTE_NAME = "extra_remote_name";
    public static final String EXTRA_REMOTE_ADDRESS = "extra_remote_address";
    public static final String EXTRA_READ_ONLY = "extra_read_only";

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private BluetoothConnectionManager connectionManager;
    private RecyclerView rvMessages;
    private EditText etInput;
    private ImageButton btnSend;
    private DBHelper dbHelper;
    private String peerAddress;
    private boolean disconnectAlertShown;
    private boolean readOnlyMode;
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private static final String TAG = "ChatActivity";

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    handlePickedFile(uri);
                }
            }
    );

    private final BluetoothConnectionManager.MessageListener incomingMessageListener =
            new BluetoothConnectionManager.MessageListener() {
                @Override
                public void onMessageReceived(@NonNull String message) {
                    runOnUiThread(() -> {
                        ChatMessage incoming = new ChatMessage(message, System.currentTimeMillis(), false);
                        chatMessages.add(incoming);
                        messageAdapter.addMessage(incoming);
                        dbHelper.insertMessage(peerAddress, incoming);
                        rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
                    });
                }

                @Override
                public void onFileReceived(@NonNull String fileName, @NonNull byte[] data) {
                    runOnUiThread(() -> handleIncomingFile(fileName, data));
                }
            };
    private final BluetoothConnectionManager.ConnectionListener connectionStateListener =
            (state, detail) -> runOnUiThread(() -> {
                boolean connected = state == BluetoothConnectionManager.ConnectionState.CONNECTED;
                setSendEnabled(connected);

                if ((state == BluetoothConnectionManager.ConnectionState.DISCONNECTED
                        || state == BluetoothConnectionManager.ConnectionState.FAILED)
                        && !disconnectAlertShown
                        && !isFinishing()) {
                    disconnectAlertShown = true;
                    showDisconnectedAlert(detail);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        TextView tvChatTitle = findViewById(R.id.tvChatTitle);
        TextView tvChatStatus = findViewById(R.id.tvChatStatus);
        TextView tvChatSubStatus = findViewById(R.id.tvChatSubStatus);
        TextView tvDaySeparator = findViewById(R.id.tvDaySeparator);
        View statusDot = findViewById(R.id.chatStatusDot);
        ImageButton btnBack = findViewById(R.id.btnChatBack);
        rvMessages = findViewById(R.id.rvMessages);
        etInput = findViewById(R.id.etInputMessage);
        btnSend = findViewById(R.id.btnSend);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        readOnlyMode = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);

        connectionManager = BluetoothSession.getConnectionManager();
        if (!readOnlyMode && (connectionManager == null || !connectionManager.isConnected())) {
            Toast.makeText(this, R.string.chat_connection_unavailable, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String remoteName = getIntent().getStringExtra(EXTRA_REMOTE_NAME);
        String remoteAddress = getIntent().getStringExtra(EXTRA_REMOTE_ADDRESS);
        if (remoteName == null || remoteName.trim().isEmpty()) {
            remoteName = getString(R.string.chat_unknown_peer);
        }
        if (remoteAddress == null) {
            remoteAddress = "";
        }
        String legacyPeerKey = "unknown:" + remoteName;
        peerAddress = remoteAddress.trim().isEmpty() ? legacyPeerKey : remoteAddress;

        tvChatTitle.setText(getString(R.string.chat_title_with_device, remoteName, remoteAddress));
        if (tvChatStatus != null) {
            tvChatStatus.setText(readOnlyMode
                    ? R.string.chat_status_archived
                    : R.string.connection_state_connected);
        }
        if (tvChatSubStatus != null) {
            tvChatSubStatus.setText(readOnlyMode ? R.string.chat_status_history : R.string.chat_status_bt);
        }
        if (tvDaySeparator != null) {
            tvDaySeparator.setText(R.string.chat_day_today);
        }

        messageAdapter = new MessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messageAdapter);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(220L);
        rvMessages.setItemAnimator(itemAnimator);
        dbHelper = new DBHelper(this);

        chatMessages.clear();
        if (remoteAddress.trim().isEmpty()) {
            chatMessages.addAll(dbHelper.getMessagesForPeer(peerAddress));
        } else {
            chatMessages.addAll(
                    dbHelper.getMessagesForPeerWithLegacyKeys(
                            peerAddress,
                            Collections.singletonList(legacyPeerKey)
                    )
            );
        }
        messageAdapter.submitMessages(chatMessages);
        if (!chatMessages.isEmpty()) {
            rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
        }

        btnSend.setOnClickListener(v -> {
            sendCurrentInputMessage();
        });
        ImageButton btnAttach = findViewById(R.id.btnAttach);
        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> {
                if (readOnlyMode) {
                    return;
                }
                try {
                    filePickerLauncher.launch("*/*");
                } catch (Exception ignored) {
                    Toast.makeText(this, R.string.chat_file_send_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (readOnlyMode) {
            setSendEnabled(false);
            View inputContainer = findViewById(R.id.inputContainer);
            if (inputContainer != null) {
                inputContainer.setVisibility(View.GONE);
            }
        } else {
            setSendEnabled(connectionManager.isConnected());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppAuthState.setChatScreenActive(true);
        if (!readOnlyMode && connectionManager != null) {
            connectionManager.setMessageListener(incomingMessageListener);
            connectionManager.setExternalConnectionListener(connectionStateListener);
            setSendEnabled(connectionManager.isConnected());
        }
    }

    @Override
    protected void onStop() {
        AppAuthState.setChatScreenActive(false);
        if (!readOnlyMode && connectionManager != null) {
            connectionManager.setMessageListener(null);
            connectionManager.setExternalConnectionListener(null);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }

    private void sendCurrentInputMessage() {
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }

        if (connectionManager == null || !connectionManager.isConnected()) {
            Toast.makeText(this, R.string.chat_connection_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sent = connectionManager.sendMessage(text);
        if (!sent) {
            Toast.makeText(this, R.string.chat_send_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage(text, System.currentTimeMillis(), true);
        chatMessages.add(message);
        messageAdapter.addMessage(message);
        dbHelper.insertMessage(peerAddress, message);
        etInput.setText("");
        rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
    }

    private void setSendEnabled(boolean enabled) {
        if (btnSend == null || etInput == null) {
            return;
        }

        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1.0f : 0.5f);
        etInput.setEnabled(enabled);
    }

    private void showDisconnectedAlert(String detail) {
        String message = (detail == null || detail.trim().isEmpty())
                ? getString(R.string.chat_disconnected_message)
                : getString(R.string.chat_disconnected_message_with_detail, detail);

        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_disconnected_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit_label, (dialog, which) -> finish())
                .show();
    }

    private void handlePickedFile(@NonNull Uri uri) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            Toast.makeText(this, R.string.chat_connection_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = queryDisplayName(uri);
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                Toast.makeText(this, R.string.chat_file_send_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(tmp)) > 0) {
                total += n;
                if (total > MAX_FILE_BYTES) {
                    Toast.makeText(this, R.string.chat_file_too_large, Toast.LENGTH_SHORT).show();
                    return;
                }
                buf.write(tmp, 0, n);
            }
            bytes = buf.toByteArray();
        } catch (IOException ioException) {
            Log.w(TAG, "Failed to read picked file", ioException);
            Toast.makeText(this, R.string.chat_file_send_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sent = connectionManager.sendFile(fileName, bytes);
        if (!sent) {
            Toast.makeText(this, R.string.chat_file_send_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String label = getString(R.string.chat_file_sent, fileName);
        ChatMessage msg = new ChatMessage(label, System.currentTimeMillis(), true);
        chatMessages.add(msg);
        messageAdapter.addMessage(msg);
        dbHelper.insertMessage(peerAddress, msg);
        rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
    }

    private void handleIncomingFile(@NonNull String fileName, @NonNull byte[] data) {
        String savedPath = saveIncomingFile(fileName, data);
        String label = getString(R.string.chat_file_received, fileName);
        if (savedPath != null) {
            label = label + "\n" + getString(R.string.chat_file_saved_to, savedPath);
        }
        ChatMessage msg = new ChatMessage(label, System.currentTimeMillis(), false);
        chatMessages.add(msg);
        messageAdapter.addMessage(msg);
        dbHelper.insertMessage(peerAddress, msg);
        rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
    }

    private String queryDisplayName(@NonNull Uri uri) {
        String fallback = "file_" + System.currentTimeMillis();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        return fallback;
    }

    private String saveIncomingFile(@NonNull String fileName, @NonNull byte[] data) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/BlueLink");
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        out.write(data);
                    }
                }
                return "Downloads/BlueLink/" + fileName;
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "BlueLink"
                );
                if (!dir.exists() && !dir.mkdirs()) {
                    dir = getFilesDir();
                }
                File out = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(data);
                }
                return out.getAbsolutePath();
            }
        } catch (IOException ioException) {
            Log.w(TAG, "Failed to save incoming file", ioException);
            return null;
        }
    }

}

