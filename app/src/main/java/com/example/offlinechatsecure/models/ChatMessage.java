package com.example.offlinechatsecure.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ChatMessage {

    private final String text;
    private final long timestamp;
    private final boolean sentByMe;
    @Nullable
    private final String attachmentUri;

    public ChatMessage(@NonNull String text, long timestamp, boolean sentByMe) {
        this(text, timestamp, sentByMe, null);
    }

    public ChatMessage(
            @NonNull String text,
            long timestamp,
            boolean sentByMe,
            @Nullable String attachmentUri
    ) {
        this.text = text;
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
        this.attachmentUri = attachmentUri;
    }

    @NonNull
    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSentByMe() {
        return sentByMe;
    }

    public boolean hasAttachment() {
        return attachmentUri != null && !attachmentUri.trim().isEmpty();
    }

    @Nullable
    public String getAttachmentUri() {
        return attachmentUri;
    }
}

