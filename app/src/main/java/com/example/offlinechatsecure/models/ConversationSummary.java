package com.example.offlinechatsecure.models;

import androidx.annotation.NonNull;

/**
 * Aggregated info for a peer's stored conversation.
 */
public class ConversationSummary {

    private final String peerAddress;
    private final String lastMessage;
    private final long lastTimestamp;
    private final int messageCount;

    public ConversationSummary(
            @NonNull String peerAddress,
            @NonNull String lastMessage,
            long lastTimestamp,
            int messageCount
    ) {
        this.peerAddress = peerAddress;
        this.lastMessage = lastMessage;
        this.lastTimestamp = lastTimestamp;
        this.messageCount = messageCount;
    }

    @NonNull
    public String getPeerAddress() {
        return peerAddress;
    }

    @NonNull
    public String getLastMessage() {
        return lastMessage;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public int getMessageCount() {
        return messageCount;
    }
}
