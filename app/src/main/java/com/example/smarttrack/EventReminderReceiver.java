package com.example.smarttrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;

public class EventReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String eventTitle = intent.getStringExtra("eventTitle");
        String eventMessage = intent.getStringExtra("eventMessage");
        int notificationId = intent.getIntExtra("notificationId", 0); // Unique ID for students/teachers

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "event_reminder")
                .setSmallIcon(R.drawable.notification_icon) // Ensure this icon exists
                .setContentTitle(eventTitle)
                .setContentText(eventMessage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, builder.build()); // Use unique ID for different users
    }
}
