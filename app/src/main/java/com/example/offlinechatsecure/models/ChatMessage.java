package com.example.offlinechatsecure.models;

import androidx.annotation.NonNull;

public class ChatMessage {

    private final String text;
    private final long timestamp;
    private final boolean sentByMe;

    public ChatMessage(@NonNull String text, long timestamp, boolean sentByMe) {
        this.text = text;
        this.timestamp = timestamp;
        this.sentByMe = sentByMe;
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
}

