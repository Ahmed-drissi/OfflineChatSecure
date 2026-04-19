package com.example.offlinechatsecure.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.example.offlinechatsecure.models.ChatMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "offline_chat_secure.db";
    private static final int DATABASE_VERSION = 3;

    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_KNOWN_PEERS = "known_peers";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PEER_ADDRESS = "peer_address";
    private static final String COLUMN_MESSAGE_TEXT = "message_text";
    private static final String COLUMN_TIMESTAMP = "timestamp_ms";
    private static final String COLUMN_SENT_BY_ME = "sent_by_me";
    private static final String COLUMN_ATTACHMENT_URI = "attachment_uri";

    public DBHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_MESSAGES + " ("
                        + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COLUMN_PEER_ADDRESS + " TEXT NOT NULL, "
                        + COLUMN_MESSAGE_TEXT + " TEXT NOT NULL, "
                        + COLUMN_TIMESTAMP + " INTEGER NOT NULL, "
                        + COLUMN_SENT_BY_ME + " INTEGER NOT NULL, "
                        + COLUMN_ATTACHMENT_URI + " TEXT"
                        + ")"
        );

        db.execSQL(
                "CREATE INDEX idx_messages_peer_timestamp ON "
                        + TABLE_MESSAGES + "(" + COLUMN_PEER_ADDRESS + ", " + COLUMN_TIMESTAMP + ")"
        );

        db.execSQL(
                "CREATE TABLE " + TABLE_KNOWN_PEERS + " ("
                        + COLUMN_PEER_ADDRESS + " TEXT PRIMARY KEY"
                        + ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_MESSAGES
                    + " ADD COLUMN " + COLUMN_ATTACHMENT_URI + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_KNOWN_PEERS + " ("
                            + COLUMN_PEER_ADDRESS + " TEXT PRIMARY KEY"
                            + ")"
            );
            db.execSQL(
                    "INSERT OR IGNORE INTO " + TABLE_KNOWN_PEERS + "(" + COLUMN_PEER_ADDRESS + ") "
                            + "SELECT DISTINCT " + COLUMN_PEER_ADDRESS + " FROM " + TABLE_MESSAGES
            );
        }
    }

    public long insertMessage(@NonNull String peerAddress, @NonNull ChatMessage message) {
        SQLiteDatabase db = getWritableDatabase();
        String normalizedAddress = normalizePeerAddress(peerAddress);
        ensureKnownPeer(db, normalizedAddress);

        ContentValues values = new ContentValues();
        values.put(COLUMN_PEER_ADDRESS, normalizedAddress);
        values.put(COLUMN_MESSAGE_TEXT, message.getText());
        values.put(COLUMN_TIMESTAMP, message.getTimestamp());
        values.put(COLUMN_SENT_BY_ME, message.isSentByMe() ? 1 : 0);
        values.put(COLUMN_ATTACHMENT_URI, message.getAttachmentUri());

        return db.insert(TABLE_MESSAGES, null, values);
    }

    @NonNull
    public List<ChatMessage> getMessagesForPeer(@NonNull String peerAddress) {
        String normalized = normalizePeerAddress(peerAddress);
        return queryMessagesForKeys(new String[]{normalized});
    }

    @NonNull
    public List<ChatMessage> getMessagesForPeerWithLegacyKeys(
            @NonNull String peerAddress,
            @NonNull List<String> legacyKeys
    ) {
        Set<String> keys = buildNormalizedPeerKeySet(peerAddress, legacyKeys);
        return queryMessagesForKeys(keys.toArray(new String[0]));
    }

    public int deleteMessagesForPeer(@NonNull String peerAddress) {
        return deleteMessagesForKeys(new String[]{normalizePeerAddress(peerAddress)});
    }

    public int deleteMessagesForPeerWithLegacyKeys(
            @NonNull String peerAddress,
            @NonNull List<String> legacyKeys
    ) {
        Set<String> keys = buildNormalizedPeerKeySet(peerAddress, legacyKeys);
        return deleteMessagesForKeys(keys.toArray(new String[0]));
    }

    @NonNull
    public List<com.example.offlinechatsecure.models.ConversationSummary> getConversations() {
        List<com.example.offlinechatsecure.models.ConversationSummary> result = new ArrayList<>();
        Set<String> knownPeers = getPeerAddressesWithHistory();
        Set<String> withMessages = new LinkedHashSet<>();
        SQLiteDatabase db = getReadableDatabase();

        String sql = "SELECT m." + COLUMN_PEER_ADDRESS
                + ", m." + COLUMN_MESSAGE_TEXT
                + ", m." + COLUMN_TIMESTAMP
                + ", g.cnt"
                + " FROM " + TABLE_MESSAGES + " m"
                + " JOIN ("
                + "   SELECT " + COLUMN_PEER_ADDRESS
                + ", MAX(" + COLUMN_TIMESTAMP + ") AS max_ts"
                + ", COUNT(*) AS cnt"
                + "   FROM " + TABLE_MESSAGES
                + "   GROUP BY " + COLUMN_PEER_ADDRESS
                + " ) g ON m." + COLUMN_PEER_ADDRESS + " = g." + COLUMN_PEER_ADDRESS
                + " AND m." + COLUMN_TIMESTAMP + " = g.max_ts"
                + " GROUP BY m." + COLUMN_PEER_ADDRESS
                + " ORDER BY m." + COLUMN_TIMESTAMP + " DESC";

        try (Cursor cursor = db.rawQuery(sql, null)) {
            int peerIdx = cursor.getColumnIndexOrThrow(COLUMN_PEER_ADDRESS);
            int textIdx = cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_TEXT);
            int tsIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);
            int cntIdx = cursor.getColumnIndexOrThrow("cnt");

            while (cursor.moveToNext()) {
                String peer = cursor.getString(peerIdx);
                withMessages.add(peer);
                result.add(new com.example.offlinechatsecure.models.ConversationSummary(
                        peer,
                        cursor.getString(textIdx),
                        cursor.getLong(tsIdx),
                        cursor.getInt(cntIdx)
                ));
            }
        }

        for (String peer : knownPeers) {
            if (!withMessages.contains(peer)) {
                result.add(new com.example.offlinechatsecure.models.ConversationSummary(
                        peer,
                        "",
                        0L,
                        0
                ));
            }
        }

        return result;
    }

    @NonNull
    public Set<String> getPeerAddressesWithHistory() {
        Set<String> addresses = new LinkedHashSet<>();
        SQLiteDatabase db = getReadableDatabase();

        String[] columns = {COLUMN_PEER_ADDRESS};
        try (Cursor cursor = db.query(
                true,
                TABLE_KNOWN_PEERS,
                columns,
                null,
                null,
                null,
                null,
                COLUMN_PEER_ADDRESS + " ASC",
                null
        )) {
            int addressIndex = cursor.getColumnIndexOrThrow(COLUMN_PEER_ADDRESS);
            while (cursor.moveToNext()) {
                String address = cursor.getString(addressIndex);
                if (address != null && !address.trim().isEmpty()) {
                    addresses.add(normalizePeerAddress(address));
                }
            }
        }

        try (Cursor cursor = db.query(
                true,
                TABLE_MESSAGES,
                columns,
                null,
                null,
                null,
                null,
                COLUMN_PEER_ADDRESS + " ASC",
                null
        )) {
            int addressIndex = cursor.getColumnIndexOrThrow(COLUMN_PEER_ADDRESS);
            while (cursor.moveToNext()) {
                String address = cursor.getString(addressIndex);
                if (address != null && !address.trim().isEmpty()) {
                    addresses.add(normalizePeerAddress(address));
                }
            }
        }

        return addresses;
    }

    private void ensureKnownPeer(@NonNull SQLiteDatabase db, @NonNull String normalizedAddress) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PEER_ADDRESS, normalizedAddress);
        db.insertWithOnConflict(
                TABLE_KNOWN_PEERS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    @NonNull
    private List<ChatMessage> queryMessagesForKeys(@NonNull String[] peerKeys) {
        List<ChatMessage> messages = new ArrayList<>();
        if (peerKeys.length == 0) {
            return messages;
        }

        SQLiteDatabase db = getReadableDatabase();

        String[] columns = {
                COLUMN_MESSAGE_TEXT,
                COLUMN_TIMESTAMP,
                COLUMN_SENT_BY_ME,
                COLUMN_ATTACHMENT_URI
        };

        StringBuilder selectionBuilder = new StringBuilder();
        for (int i = 0; i < peerKeys.length; i++) {
            if (i > 0) {
                selectionBuilder.append(" OR ");
            }
            selectionBuilder.append(COLUMN_PEER_ADDRESS).append(" = ?");
        }

        try (Cursor cursor = db.query(
                TABLE_MESSAGES,
                columns,
                selectionBuilder.toString(),
                peerKeys,
                null,
                null,
                COLUMN_TIMESTAMP + " ASC, " + COLUMN_ID + " ASC"
        )) {
            int textIndex = cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_TEXT);
            int timestampIndex = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);
            int sentByMeIndex = cursor.getColumnIndexOrThrow(COLUMN_SENT_BY_ME);
            int attachmentIndex = cursor.getColumnIndexOrThrow(COLUMN_ATTACHMENT_URI);

            while (cursor.moveToNext()) {
                String text = cursor.getString(textIndex);
                long timestamp = cursor.getLong(timestampIndex);
                boolean sentByMe = cursor.getInt(sentByMeIndex) == 1;
                String attachmentUri = cursor.isNull(attachmentIndex)
                        ? null
                        : cursor.getString(attachmentIndex);
                messages.add(new ChatMessage(text, timestamp, sentByMe, attachmentUri));
            }
        }

        return messages;
    }

    private int deleteMessagesForKeys(@NonNull String[] peerKeys) {
        if (peerKeys.length == 0) {
            return 0;
        }

        SQLiteDatabase db = getWritableDatabase();
        StringBuilder selectionBuilder = new StringBuilder();
        for (int i = 0; i < peerKeys.length; i++) {
            if (i > 0) {
                selectionBuilder.append(" OR ");
            }
            selectionBuilder.append(COLUMN_PEER_ADDRESS).append(" = ?");
        }

        return db.delete(TABLE_MESSAGES, selectionBuilder.toString(), peerKeys);
    }

    @NonNull
    private Set<String> buildNormalizedPeerKeySet(
            @NonNull String peerAddress,
            @NonNull List<String> legacyKeys
    ) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(normalizePeerAddress(peerAddress));
        for (String key : legacyKeys) {
            if (key != null && !key.trim().isEmpty()) {
                keys.add(normalizePeerAddress(key));
            }
        }
        return keys;
    }

    @NonNull
    private String normalizePeerAddress(@NonNull String peerAddress) {
        return peerAddress.trim().toUpperCase(Locale.US);
    }
}

