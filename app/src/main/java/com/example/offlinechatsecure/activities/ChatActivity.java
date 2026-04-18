package com.example.offlinechatsecure.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_REMOTE_NAME = "extra_remote_name";
    public static final String EXTRA_REMOTE_ADDRESS = "extra_remote_address";

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private BluetoothConnectionManager connectionManager;
    private RecyclerView rvMessages;
    private EditText etInput;
    private ImageButton btnSend;
    private DBHelper dbHelper;
    private String peerAddress;
    private boolean disconnectAlertShown;
    private final BluetoothConnectionManager.MessageListener incomingMessageListener =
            message -> runOnUiThread(() -> {
                ChatMessage incoming = new ChatMessage(message, System.currentTimeMillis(), false);
                chatMessages.add(incoming);
                messageAdapter.addMessage(incoming);
                dbHelper.insertMessage(peerAddress, incoming);
                rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
            });
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
        rvMessages = findViewById(R.id.rvMessages);
        etInput = findViewById(R.id.etInputMessage);
        btnSend = findViewById(R.id.btnSend);

        connectionManager = BluetoothSession.getConnectionManager();
        if (connectionManager == null || !connectionManager.isConnected()) {
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
        setSendEnabled(connectionManager.isConnected());
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppAuthState.setChatScreenActive(true);
        if (connectionManager != null) {
            connectionManager.setMessageListener(incomingMessageListener);
            connectionManager.setExternalConnectionListener(connectionStateListener);
            setSendEnabled(connectionManager.isConnected());
        }
    }

    @Override
    protected void onStop() {
        AppAuthState.setChatScreenActive(false);
        if (connectionManager != null) {
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

}

