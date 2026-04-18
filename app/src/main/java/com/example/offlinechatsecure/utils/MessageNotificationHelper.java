package com.example.offlinechatsecure.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.activities.ChatActivity;

import java.util.concurrent.atomic.AtomicInteger;

public final class MessageNotificationHelper {

    public static final String CHANNEL_ID_MESSAGES = "offline_chat_messages";
    private static final AtomicInteger NOTIFICATION_ID = new AtomicInteger(1000);

    private MessageNotificationHelper() {
    }

    public static void createMessageChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_MESSAGES,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.notification_channel_description));

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static void showIncomingMessageNotification(
            @NonNull Context context,
            @NonNull String senderName,
            @NonNull String senderAddress,
            @NonNull String message
    ) {
        if (!hasNotificationPermission(context)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent openIntent = new Intent(context, ChatActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra(ChatActivity.EXTRA_REMOTE_NAME, senderName);
        openIntent.putExtra(ChatActivity.EXTRA_REMOTE_ADDRESS, senderAddress);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_new_message_title, senderName))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID.incrementAndGet(), builder.build());
        } catch (SecurityException ignored) {
            // Permission may be revoked while posting; fail gracefully.
        }
    }

    public static void showIncomingConnectionNotification(
            @NonNull Context context,
            @NonNull String senderName,
            @NonNull String senderAddress
    ) {
        if (!hasNotificationPermission(context)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent openIntent = new Intent(context, ChatActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra(ChatActivity.EXTRA_REMOTE_NAME, senderName);
        openIntent.putExtra(ChatActivity.EXTRA_REMOTE_ADDRESS, senderAddress);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_connection_title, senderName))
                .setContentText(context.getString(R.string.notification_connection_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID.incrementAndGet(), builder.build());
        } catch (SecurityException ignored) {
            // Permission may be revoked while posting; fail gracefully.
        }
    }

    private static boolean hasNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}

