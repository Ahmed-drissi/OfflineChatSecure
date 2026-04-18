package com.example.offlinechatsecure.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.adapters.MessageAdapter;
import com.example.offlinechatsecure.models.ChatMessage;
import com.example.offlinechatsecure.network.BluetoothConnectionManager;
import com.example.offlinechatsecure.network.BluetoothSession;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_REMOTE_NAME = "extra_remote_name";
    public static final String EXTRA_REMOTE_ADDRESS = "extra_remote_address";

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private BluetoothConnectionManager connectionManager;
    private RecyclerView rvMessages;
    private EditText etInput;
    private final BluetoothConnectionManager.MessageListener incomingMessageListener =
            message -> runOnUiThread(() -> {
                ChatMessage incoming = new ChatMessage(message, System.currentTimeMillis(), false);
                chatMessages.add(incoming);
                messageAdapter.addMessage(incoming);
                rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        TextView tvChatTitle = findViewById(R.id.tvChatTitle);
        rvMessages = findViewById(R.id.rvMessages);
        etInput = findViewById(R.id.etInputMessage);
        ImageButton btnSend = findViewById(R.id.btnSend);

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

        tvChatTitle.setText(getString(R.string.chat_title_with_device, remoteName, remoteAddress));

        messageAdapter = new MessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messageAdapter);
        messageAdapter.submitMessages(chatMessages);

        btnSend.setOnClickListener(v -> {
            sendCurrentInputMessage();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (connectionManager != null) {
            connectionManager.setMessageListener(incomingMessageListener);
        }
    }

    @Override
    protected void onStop() {
        if (connectionManager != null) {
            connectionManager.setMessageListener(null);
        }
        super.onStop();
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
        etInput.setText("");
        rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
    }
}

