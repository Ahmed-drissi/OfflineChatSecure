package com.example.offlinechatsecure.activities;

import android.content.ContentValues;
import android.content.ActivityNotFoundException;
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
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
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
import com.example.offlinechatsecure.utils.PeerProfileStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AuthenticatedActivity {

    public static final String EXTRA_REMOTE_NAME = "extra_remote_name";
    public static final String EXTRA_REMOTE_ADDRESS = "extra_remote_address";
    public static final String EXTRA_READ_ONLY = "extra_read_only";

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private BluetoothConnectionManager connectionManager;
    private RecyclerView rvMessages;
    private EditText etInput;
    private TextView tvChatTitle;
    private ImageView ivChatPeerPhoto;
    private ImageButton btnSend;
    private ImageButton btnAttach;
    private TextView tvChatSubStatus;
    private View fileTransferContainer;
    private ProgressBar pbFileTransfer;
    private TextView tvFileTransferProgress;
    private DBHelper dbHelper;
    private String peerAddress;
    private String legacyPeerKey;
    private String fallbackRemoteName;
    private String resolvedRemoteAddress;
    private String currentPeerDisplayName;
    private String currentPeerPhotoUri;
    private String pendingProfilePhotoUri;
    private ImageView profilePreviewImage;
    private EditText profileNameInput;
    private boolean disconnectAlertShown;
    private boolean readOnlyMode;
    private boolean isFileSending;
    private boolean isConnectionActive;
    private CharSequence defaultSubStatusText;
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private static final String TAG = "ChatActivity";
    private final ExecutorService fileSendExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService textSendExecutor = Executors.newSingleThreadExecutor();
    private AttachKind pendingAttachKind = AttachKind.FILE;

    private enum AttachKind {
        FILE,
        IMAGE
    }

    private static final class SavedFileResult {
        private final Uri contentUri;
        private final String displayPath;

        SavedFileResult(@NonNull Uri contentUri, @NonNull String displayPath) {
            this.contentUri = contentUri;
            this.displayPath = displayPath;
        }
    }

    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    final int readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, readPermission);
                    } catch (SecurityException ignored) {
                        // Some providers do not grant persistable permission; opening still works for session URI.
                    }
                    handlePickedFile(uri, pendingAttachKind);
                }
            }
    );

    private final ActivityResultLauncher<PickVisualMediaRequest> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        pendingAttachKind = AttachKind.IMAGE;
                        // Visual media picker usually provides temporary grant; persist best-effort.
                        final int readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        try {
                            getContentResolver().takePersistableUriPermission(uri, readPermission);
                        } catch (SecurityException ignored) {
                            // Not all providers grant persistable permissions.
                        }

                        handlePickedFile(uri, AttachKind.IMAGE);
                    }
            );

    private final ActivityResultLauncher<String[]> profilePhotoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) {
                    return;
                }

                final int readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                try {
                    getContentResolver().takePersistableUriPermission(uri, readPermission);
                } catch (SecurityException ignored) {
                    // Some providers only grant temporary read access.
                }

                pendingProfilePhotoUri = uri.toString();
                if (!applyPhotoToView(profilePreviewImage, pendingProfilePhotoUri)) {
                    Toast.makeText(this, R.string.chat_profile_photo_failed, Toast.LENGTH_SHORT).show();
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
                isConnectionActive = connected;
                updateComposerState();

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

        tvChatTitle = findViewById(R.id.tvChatTitle);
        ivChatPeerPhoto = findViewById(R.id.ivChatPeerPhoto);
        TextView tvChatStatus = findViewById(R.id.tvChatStatus);
        tvChatSubStatus = findViewById(R.id.tvChatSubStatus);
        TextView tvDaySeparator = findViewById(R.id.tvDaySeparator);
        View statusDot = findViewById(R.id.chatStatusDot);
        ImageButton btnBack = findViewById(R.id.btnChatBack);
        rvMessages = findViewById(R.id.rvMessages);
        etInput = findViewById(R.id.etInputMessage);
        btnSend = findViewById(R.id.btnSend);
        fileTransferContainer = findViewById(R.id.fileTransferContainer);
        pbFileTransfer = findViewById(R.id.pbFileTransfer);
        tvFileTransferProgress = findViewById(R.id.tvFileTransferProgress);
        resetFileTransferUi();

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        ImageButton btnMore = findViewById(R.id.btnChatMore);
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> showMoreActionsDialog());
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
        fallbackRemoteName = remoteName;
        resolvedRemoteAddress = remoteAddress;
        legacyPeerKey = "unknown:" + remoteName;
        peerAddress = remoteAddress.trim().isEmpty() ? legacyPeerKey : remoteAddress;
        currentPeerDisplayName = PeerProfileStore.getDisplayName(this, peerAddress, fallbackRemoteName);
        currentPeerPhotoUri = PeerProfileStore.getPhotoUri(this, peerAddress);
        renderPeerHeader();
        if (tvChatStatus != null) {
            tvChatStatus.setText(readOnlyMode
                    ? R.string.chat_status_archived
                    : R.string.connection_state_connected);
        }
        if (tvChatSubStatus != null) {
            tvChatSubStatus.setText(readOnlyMode ? R.string.chat_status_history : R.string.chat_status_bt);
            defaultSubStatusText = tvChatSubStatus.getText();
        }
        if (tvDaySeparator != null) {
            tvDaySeparator.setText(R.string.chat_day_today);
        }

        messageAdapter = new MessageAdapter();
        messageAdapter.setOnMessageClickListener(this::onMessageClicked);
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
        btnAttach = findViewById(R.id.btnAttach);
        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> {
                if (readOnlyMode) {
                    return;
                }
                showAttachOptionsDialog();
            });
        }
        if (readOnlyMode) {
            updateComposerState();
            View inputContainer = findViewById(R.id.inputContainer);
            if (inputContainer != null) {
                inputContainer.setVisibility(View.GONE);
            }
        } else {
            isConnectionActive = connectionManager.isConnected();
            updateComposerState();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppAuthState.setChatScreenActive(true);
        if (!readOnlyMode && connectionManager != null) {
            connectionManager.setMessageListener(incomingMessageListener);
            connectionManager.setExternalConnectionListener(connectionStateListener);
            isConnectionActive = connectionManager.isConnected();
            updateComposerState();
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
        fileSendExecutor.shutdownNow();
        textSendExecutor.shutdownNow();
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }

    private void sendCurrentInputMessage() {
        final String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }

        if (connectionManager == null || !connectionManager.isConnected()) {
            Toast.makeText(this, R.string.chat_connection_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        etInput.setText("");
        textSendExecutor.execute(() -> {
            boolean sent = connectionManager != null && connectionManager.sendMessage(text);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!sent) {
                    Toast.makeText(this, R.string.chat_send_failed, Toast.LENGTH_SHORT).show();
                    if (TextUtils.isEmpty(etInput.getText())) {
                        etInput.setText(text);
                        etInput.setSelection(etInput.length());
                    }
                    return;
                }

                ChatMessage message = new ChatMessage(text, System.currentTimeMillis(), true);
                chatMessages.add(message);
                messageAdapter.addMessage(message);
                dbHelper.insertMessage(peerAddress, message);
                rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
            });
        });
    }

    private void setTextMessagingEnabled(boolean enabled) {
        if (btnSend == null || etInput == null) {
            return;
        }

        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1.0f : 0.5f);
        etInput.setEnabled(enabled);
    }

    private void setAttachEnabled(boolean enabled) {
        if (btnAttach != null) {
            btnAttach.setEnabled(enabled);
            btnAttach.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    private void updateComposerState() {
        boolean textEnabled = !readOnlyMode && isConnectionActive;
        boolean attachEnabled = textEnabled && !isFileSending;
        setTextMessagingEnabled(textEnabled);
        setAttachEnabled(attachEnabled);
    }

    private void showDisconnectedAlert(String detail) {
        String message = (detail == null || detail.trim().isEmpty())
                ? getString(R.string.chat_disconnected_message)
                : getString(R.string.chat_disconnected_message_with_detail, detail);

        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_disconnected_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit_label, (dialog, which) -> {
                    Intent intent = new Intent(ChatActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void showEditPeerProfileDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_peer_profile, null);
        profilePreviewImage = dialogView.findViewById(R.id.ivProfilePhotoPreview);
        profileNameInput = dialogView.findViewById(R.id.etProfileName);
        TextView choosePhoto = dialogView.findViewById(R.id.btnChooseProfilePhoto);
        TextView removePhoto = dialogView.findViewById(R.id.btnRemoveProfilePhoto);

        pendingProfilePhotoUri = currentPeerPhotoUri;
        profileNameInput.setText(currentPeerDisplayName);
        applyPhotoToView(profilePreviewImage, pendingProfilePhotoUri);

        choosePhoto.setOnClickListener(v -> profilePhotoPickerLauncher.launch(new String[]{"image/*"}));
        removePhoto.setOnClickListener(v -> {
            pendingProfilePhotoUri = null;
            applyPhotoToView(profilePreviewImage, null);
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_edit_profile_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_save_profile, (dialog, which) -> savePeerProfile())
                .show();
    }

    private void showMoreActionsDialog() {
        String[] actions = new String[]{
                getString(R.string.chat_more_edit_contact),
                getString(R.string.chat_more_delete_conversation)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_more)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showEditPeerProfileDialog();
                    } else if (which == 1) {
                        showDeleteConversationConfirmDialog();
                    }
                })
                .show();
    }

    private void showDeleteConversationConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_delete_conversation_title)
                .setMessage(R.string.chat_delete_conversation_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_delete_label, (dialog, which) -> deleteConversationMessages())
                .show();
    }

    private void deleteConversationMessages() {
        int deletedCount = dbHelper.deleteMessagesForPeerWithLegacyKeys(
                peerAddress,
                Collections.singletonList(legacyPeerKey)
        );

        chatMessages.clear();
        messageAdapter.submitMessages(chatMessages);
        Toast.makeText(
                this,
                getString(R.string.chat_delete_conversation_success, deletedCount),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void savePeerProfile() {
        String enteredName = profileNameInput != null ? profileNameInput.getText().toString().trim() : "";
        if (enteredName.isEmpty()) {
            enteredName = fallbackRemoteName;
        }

        currentPeerDisplayName = enteredName;
        currentPeerPhotoUri = pendingProfilePhotoUri;
        PeerProfileStore.saveProfile(this, peerAddress, currentPeerDisplayName, currentPeerPhotoUri);
        renderPeerHeader();
    }

    private void renderPeerHeader() {
        if (tvChatTitle != null) {
            tvChatTitle.setText(getString(
                    R.string.chat_title_with_device,
                    currentPeerDisplayName,
                    resolvedRemoteAddress
            ));
        }
        applyPhotoToView(ivChatPeerPhoto, currentPeerPhotoUri);
    }

    private boolean applyPhotoToView(ImageView imageView, String photoUri) {
        if (imageView == null) {
            return false;
        }

        if (photoUri == null || photoUri.trim().isEmpty()) {
            int iconPadding = dpToPx(12);
            imageView.setImageResource(R.drawable.ic_phone_line);
            imageView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            ImageViewCompat.setImageTintList(
                    imageView,
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.app_primary)
                    )
            );
            return true;
        }

        try {
            ImageViewCompat.setImageTintList(imageView, null);
            imageView.setPadding(0, 0, 0, 0);
            imageView.setImageURI(Uri.parse(photoUri));
            return true;
        } catch (Exception exception) {
            int iconPadding = dpToPx(12);
            imageView.setImageResource(R.drawable.ic_phone_line);
            imageView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            ImageViewCompat.setImageTintList(
                    imageView,
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.app_primary)
                    )
            );
            return false;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showAttachOptionsDialog() {
        String[] options = new String[]{
                getString(R.string.chat_attach_image),
                getString(R.string.chat_attach_file)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_attach)
                .setItems(options, (dialog, which) -> {
                    try {
                        if (which == 0) {
                            imagePickerLauncher.launch(
                                    new PickVisualMediaRequest.Builder()
                                            .setMediaType(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE
                                            )
                                            .build()
                            );
                        } else {
                            pendingAttachKind = AttachKind.FILE;
                            filePickerLauncher.launch(new String[]{"*/*"});
                        }
                    } catch (Exception ignored) {
                        Toast.makeText(this, R.string.chat_file_send_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void handlePickedFile(@NonNull Uri uri, @NonNull AttachKind attachKind) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            Toast.makeText(this, R.string.chat_connection_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isFileSending) {
            Toast.makeText(this, R.string.chat_file_send_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        setFileSendingState(true);
        fileSendExecutor.execute(() -> sendFileInBackground(uri, attachKind));
    }

    private void sendFileInBackground(@NonNull Uri uri, @NonNull AttachKind attachKind) {
        String fileName = queryDisplayName(uri);
        long fileSize = queryFileSize(uri);
        byte[] bytes;

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                postFileSendFailure(R.string.chat_file_send_failed, null);
                return;
            }
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            long total = 0;
            int lastProgress = -1;
            while ((n = in.read(tmp)) > 0) {
                total += n;
                if (total > MAX_FILE_BYTES) {
                    postFileSendFailure(R.string.chat_file_too_large, null);
                    return;
                }
                if (fileSize > 0) {
                    int progress = (int) Math.min(100, (total * 100L) / fileSize);
                    if (progress != lastProgress && (progress == 100 || progress - lastProgress >= 5)) {
                        lastProgress = progress;
                        int finalProgress = progress;
                        runOnUiThread(() -> showPreparingFileProgress(finalProgress));
                    }
                }
                buf.write(tmp, 0, n);
            }
            bytes = buf.toByteArray();
        } catch (IOException ioException) {
            Log.w(TAG, "Failed to read picked file", ioException);
            postFileSendFailure(R.string.chat_file_send_failed, ioException);
            return;
        }

        runOnUiThread(() -> showSendingStatus(0));
        boolean sent = connectionManager != null && connectionManager.isConnected()
                && connectionManager.sendFile(fileName, bytes, progress ->
                runOnUiThread(() -> showSendingStatus(progress)));
        if (!sent) {
            postFileSendFailure(R.string.chat_file_send_failed, null);
            return;
        }

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            String label = attachKind == AttachKind.IMAGE
                    ? getString(R.string.chat_image_sent)
                    : getString(R.string.chat_file_sent, fileName);
            ChatMessage msg = new ChatMessage(label, System.currentTimeMillis(), true, uri.toString());
            chatMessages.add(msg);
            messageAdapter.addMessage(msg);
            dbHelper.insertMessage(peerAddress, msg);
            rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
            setFileSendingState(false);
        });
    }

    private void postFileSendFailure(int messageResId, Exception exception) {
        runOnUiThread(() -> {
            if (exception != null) {
                Log.w(TAG, "File send failed", exception);
            }
            Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
            setFileSendingState(false);
        });
    }

    private void setFileSendingState(boolean sending) {
        isFileSending = sending;
        if (tvChatSubStatus != null) {
            if (sending) {
                tvChatSubStatus.setText(R.string.chat_status_preparing_file);
            } else if (defaultSubStatusText != null) {
                tvChatSubStatus.setText(defaultSubStatusText);
            }
        }
        if (!sending) {
            resetFileTransferUi();
        } else {
            showFileTransferUi();
        }
        updateComposerState();
    }

    private void showPreparingFileProgress(int progress) {
        if (!isFileSending || tvChatSubStatus == null) {
            return;
        }
        tvChatSubStatus.setText(getString(R.string.chat_status_preparing_file_progress, progress));
        showFileTransferProgress(progress);
    }

    private void showSendingStatus(int progress) {
        if (!isFileSending || tvChatSubStatus == null) {
            return;
        }
        tvChatSubStatus.setText(getString(R.string.chat_status_sending_file_progress, progress));
        showFileTransferProgress(progress);
    }

    private void showFileTransferUi() {
        if (fileTransferContainer != null) {
            fileTransferContainer.setVisibility(View.VISIBLE);
        }
        if (tvFileTransferProgress != null) {
            tvFileTransferProgress.setVisibility(View.VISIBLE);
            tvFileTransferProgress.setText(getString(R.string.chat_file_progress_percent, 0));
        }
        if (pbFileTransfer != null) {
            pbFileTransfer.setIndeterminate(true);
            pbFileTransfer.setProgress(0);
        }
    }

    private void showFileTransferProgress(int progress) {
        if (fileTransferContainer != null) {
            fileTransferContainer.setVisibility(View.VISIBLE);
        }
        if (pbFileTransfer != null) {
            pbFileTransfer.setIndeterminate(false);
            pbFileTransfer.setMax(100);
            pbFileTransfer.setProgress(Math.max(0, Math.min(100, progress)));
        }
        if (tvFileTransferProgress != null) {
            tvFileTransferProgress.setVisibility(View.VISIBLE);
            tvFileTransferProgress.setText(getString(R.string.chat_file_progress_percent, progress));
        }
    }


    private void resetFileTransferUi() {
        if (fileTransferContainer != null) {
            fileTransferContainer.setVisibility(View.GONE);
        }
        if (pbFileTransfer != null) {
            pbFileTransfer.setIndeterminate(true);
            pbFileTransfer.setProgress(0);
        }
        if (tvFileTransferProgress != null) {
            tvFileTransferProgress.setVisibility(View.VISIBLE);
            tvFileTransferProgress.setText(getString(R.string.chat_file_progress_percent, 0));
        }
    }

    private long queryFileSize(@NonNull Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx);
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
        return -1L;
    }

    private void onMessageClicked(@NonNull ChatMessage message) {
        if (!message.hasAttachment()) {
            return;
        }

        Uri uri;
        try {
            uri = Uri.parse(message.getAttachmentUri());
        } catch (Exception ignored) {
            Toast.makeText(this, R.string.chat_open_file_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String type = getContentResolver().getType(uri);
        if (type == null || type.trim().isEmpty()) {
            type = "*/*";
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(openIntent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.chat_open_file_no_app, Toast.LENGTH_SHORT).show();
        } catch (SecurityException exception) {
            Toast.makeText(this, R.string.chat_open_file_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIncomingFile(@NonNull String fileName, @NonNull byte[] data) {
        SavedFileResult savedFile = saveIncomingFile(fileName, data);
        boolean isImage = isImageFileName(fileName);
        String label = isImage
                ? getString(R.string.chat_image_received)
                : getString(R.string.chat_file_received, fileName);
        String attachmentUri = null;
        if (savedFile != null) {
            if (!isImage) {
                label = label + "\n" + getString(R.string.chat_file_saved_to, savedFile.displayPath);
            }
            attachmentUri = savedFile.contentUri.toString();
        }
        ChatMessage msg = new ChatMessage(label, System.currentTimeMillis(), false, attachmentUri);
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

    private SavedFileResult saveIncomingFile(@NonNull String fileName, @NonNull byte[] data) {
        String safeFileName = sanitizeFileName(fileName);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeFileName);
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
                return new SavedFileResult(uri, "Downloads/BlueLink/" + safeFileName);
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "BlueLink"
                );
                if (!dir.exists() && !dir.mkdirs()) {
                    dir = getFilesDir();
                }
                File out = new File(dir, safeFileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(data);
                }
                Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        out
                );
                return new SavedFileResult(contentUri, out.getAbsolutePath());
            }
        } catch (IOException ioException) {
            Log.w(TAG, "Failed to save incoming file", ioException);
            return null;
        } catch (IllegalArgumentException illegalArgumentException) {
            Log.w(TAG, "Failed to create file URI for incoming file", illegalArgumentException);
            return null;
        }
    }

    @NonNull
    private String sanitizeFileName(@NonNull String fileName) {
        String safeName = fileName.replace('/', '_').replace('\\', '_').trim();
        if (safeName.isEmpty()) {
            safeName = "file_" + System.currentTimeMillis();
        }
        return safeName;
    }

    private boolean isImageFileName(@NonNull String fileName) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                extension.toLowerCase()
        );
        return mimeType != null && mimeType.startsWith("image/");
    }

}

