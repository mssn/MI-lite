package edu.purdue.cs.mssn.mi_lite;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class GlobalStore extends Application {

    private Activity mainActivity;
    private NotificationManager manager;
    private TextView myTextView;

    void Init(Activity activity) {
        this.mainActivity = activity;
        myTextView = activity.findViewById(R.id.myTextView);

        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel("milite_channel", "MI-lite Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            // Configure the notification channel.
            notificationChannel.setDescription("MI-lite Notification");
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    void showMessage(String message) {
        myTextView.append(message + "\n");
    }


    public static String executeCommand(String[] command) {
        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                p.waitFor(5, TimeUnit.SECONDS);
            } else {
                p.waitFor();
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));

            int read;
            char[] buffer = new char[4096];
            StringBuilder output = new StringBuilder();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();
            return output.toString();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }
}
