package com.hex.heapradio.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hex.heapradio.BuildConfig;
import com.hex.heapradio.MainActivity;
import com.hex.heapradio.R;

public class MediaNotificationManager {

    public static final int NOTIFICATION_ID = 555;
    private final String PRIMARY_CHANNEL = "PRIMARY_CHANNEL_ID";
    private final String PRIMARY_CHANNEL_NAME = "PRIMARY";

    private RadioService service;

    private String strAppName, strLiveBroadcast;

    private Resources resources;

    private NotificationManagerCompat notificationManager;

    public MediaNotificationManager(RadioService service) {

        this.service = service;
        this.resources = service.getResources();

        strAppName = resources.getString(R.string.app_name);
        strLiveBroadcast = resources.getString(R.string.playingNow);

        notificationManager = NotificationManagerCompat.from(service);
    }

    public void startNotify(String playbackStatus) {


        RemoteViews simpleContentView = new RemoteViews(BuildConfig.APPLICATION_ID ,R.layout.music_notification);

        if(playbackStatus.equals(PlaybackStatus.PAUSED)){
            simpleContentView.setViewVisibility(R.id.btnStop, View.GONE);
            simpleContentView.setViewVisibility(R.id.btnPlay, View.VISIBLE);

        }else{
            simpleContentView.setViewVisibility(R.id.btnStop, View.VISIBLE);
            simpleContentView.setViewVisibility(R.id.btnPlay, View.GONE);
        }

        simpleContentView.setTextViewText(R.id.textSongName, strAppName);
        simpleContentView.setTextViewText(R.id.textAlbumName, strLiveBroadcast);
        Bitmap largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher);

        PendingIntent pi;
        Intent mIntent = new Intent(service, MainActivity.class);
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mIntent.setAction(Intent.ACTION_MAIN);
        mIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        pi = PendingIntent.getActivity(service, NOTIFICATION_ID, mIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        notificationManager.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(PRIMARY_CHANNEL, PRIMARY_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            assert manager != null;
            manager.createNotificationChannel(channel);
        }

        Notification builder = new NotificationCompat.Builder(service, PRIMARY_CHANNEL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.noti_icon)
                .setCustomContentView(simpleContentView)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(service.getMediaSession().getSessionToken())).build();

        setListeners(simpleContentView);

        builder.contentIntent = pi;
        builder.flags |= Notification.FLAG_NO_CLEAR;
        service.startForeground(NOTIFICATION_ID, builder);
    }

    public void cancelNotify() {

        service.stopForeground(true);
    }

    public void setListeners(RemoteViews view) {

        Intent stop = new Intent(service, RadioService.class);
        stop.setAction(RadioService.ACTION_STOP);
        PendingIntent pStop = PendingIntent.getService(service, NOTIFICATION_ID, stop, PendingIntent.FLAG_MUTABLE);
        view.setOnClickPendingIntent(R.id.btnCancel, pStop);

        Intent play = new Intent(service, RadioService.class);
        play.setAction(RadioService.ACTION_PLAY);
        PendingIntent pPlay = PendingIntent.getService(service, NOTIFICATION_ID, play, PendingIntent.FLAG_MUTABLE);
        view.setOnClickPendingIntent(R.id.btnPlay, pPlay);

        Intent pause = new Intent(service, RadioService.class);
        pause.setAction(RadioService.ACTION_PAUSE);
        PendingIntent pPause = PendingIntent.getService(service, NOTIFICATION_ID, pause, PendingIntent.FLAG_MUTABLE);
        view.setOnClickPendingIntent(R.id.btnStop, pPause);
    }

}
