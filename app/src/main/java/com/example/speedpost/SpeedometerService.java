package com.example.speedpost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

public class SpeedometerService extends Service {

    static final String START_SPEEDOMETER_SERVICE = "com.example.speedometer.startservice";
    static final String STOP_SPEEDOMETER_SERVICE = "com.example.speedometer.stopservice";

    // Start and stop options for the speedometer service.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction() != null) {
            switch (intent.getAction()) {
                case START_SPEEDOMETER_SERVICE:
                    startCustomForeground();
                    break;

                case STOP_SPEEDOMETER_SERVICE:
                    stopForeground(true);
                    stopSelf();
                    break;

                default:
                    break;

            }

            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    // Build the notification to let the user know that the speedometer is running in the background.
    private void startCustomForeground() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "com.example.speedometer";
            String channelName = "Speedometer service";
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if(notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
                builder.setSmallIcon(R.mipmap.ic_launcher);
                builder.setColor(Color.argb(127, 0, 100, 210));
                builder.setContentTitle("Speedometer Service running in background.");
                //builder.setContentText("ContentText");

                Notification notification = builder.build();
                startForeground(1, notification);
            }
        }

        // Required option for older Android versions.
        else {
            startForeground(1, new Notification());
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
