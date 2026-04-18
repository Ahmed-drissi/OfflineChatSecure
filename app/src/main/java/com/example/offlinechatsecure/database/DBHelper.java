package com.example.offlinechatsecure.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.example.offlinechatsecure.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "offline_chat_secure.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PEER_ADDRESS = "peer_address";
    private static final String COLUMN_MESSAGE_TEXT = "message_text";
    private static final String COLUMN_TIMESTAMP = "timestamp_ms";
    private static final String COLUMN_SENT_BY_ME = "sent_by_me";

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
                        + COLUMN_SENT_BY_ME + " INTEGER NOT NULL"
                        + ")"
        );

        db.execSQL(
                "CREATE INDEX idx_messages_peer_timestamp ON "
                        + TABLE_MESSAGES + "(" + COLUMN_PEER_ADDRESS + ", " + COLUMN_TIMESTAMP + ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }

    public long insertMessage(@NonNull String peerAddress, @NonNull ChatMessage message) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_PEER_ADDRESS, peerAddress);
        values.put(COLUMN_MESSAGE_TEXT, message.getText());
        values.put(COLUMN_TIMESTAMP, message.getTimestamp());
        values.put(COLUMN_SENT_BY_ME, message.isSentByMe() ? 1 : 0);

        return db.insert(TABLE_MESSAGES, null, values);
    }

    @NonNull
    public List<ChatMessage> getMessagesForPeer(@NonNull String peerAddress) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String[] columns = {
                COLUMN_MESSAGE_TEXT,
                COLUMN_TIMESTAMP,
                COLUMN_SENT_BY_ME
        };

        try (Cursor cursor = db.query(
                TABLE_MESSAGES,
                columns,
                COLUMN_PEER_ADDRESS + " = ?",
                new String[]{peerAddress},
                null,
                null,
                COLUMN_TIMESTAMP + " ASC, " + COLUMN_ID + " ASC"
        )) {
            int textIndex = cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_TEXT);
            int timestampIndex = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);
            int sentByMeIndex = cursor.getColumnIndexOrThrow(COLUMN_SENT_BY_ME);

            while (cursor.moveToNext()) {
                String text = cursor.getString(textIndex);
                long timestamp = cursor.getLong(timestampIndex);
                boolean sentByMe = cursor.getInt(sentByMeIndex) == 1;
                messages.add(new ChatMessage(text, timestamp, sentByMe));
            }
        }

        return messages;
    }
}

