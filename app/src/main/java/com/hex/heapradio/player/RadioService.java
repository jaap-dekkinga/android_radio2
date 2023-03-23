package com.hex.heapradio.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.audiofx.NoiseSuppressor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.dekidea.tuneurl.receiver.TuneURLReceiver;
import com.dekidea.tuneurl.util.Constants;
import com.dekidea.tuneurl.util.TuneURLManager;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.hex.heapradio.BuildConfig;
import com.hex.heapradio.MainActivity;
import com.hex.heapradio.R;

import org.greenrobot.eventbus.EventBus;

import java.util.EventListener;
import java.util.Objects;

public class RadioService extends Service implements Player.EventListener,
        AudioManager.OnAudioFocusChangeListener,
        Constants {

    public static final String ACTION_PLAY = "com.hex.heapradio.player.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.hex.heapradio.player.ACTION_PAUSE";
    public static final String ACTION_STOP = "com.hex.heapradio.player.ACTION_STOP";
    public static final String ACTION_TOGGLE = "com.hex.heapradio.player.ACTION_TOGGLE";

    public static final int NOTIFICATION_ID = 555;
    private final String PRIMARY_CHANNEL = "PRIMARY_CHANNEL_ID";
    private final String PRIMARY_CHANNEL_NAME = "PRIMARY";

    private String strAppName, strLiveBroadcast;

    private final IBinder iBinder = new LocalBinder();

    private Handler handler;
    private final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private SimpleExoPlayer exoPlayer;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    private boolean onGoingCall = false;
    private TelephonyManager telephonyManager;

    private WifiManager.WifiLock wifiLock;

    private AudioManager audioManager;

    private String status;

    private String streamUrl;

    private TuneURLReceiver tuneURLReceiver;

    public class LocalBinder extends Binder {
        public RadioService getService() {
            return RadioService.this;
        }
    }

    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            pause();
        }
    };

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {

            if(state == TelephonyManager.CALL_STATE_OFFHOOK
                    || state == TelephonyManager.CALL_STATE_RINGING){

                if(!isPlaying()) return;

                onGoingCall = true;
                stop();

            } else if (state == TelephonyManager.CALL_STATE_IDLE){

                if(!onGoingCall) return;

                onGoingCall = false;
                resume();
            }
        }
    };

    private MediaSessionCompat.Callback mediasSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPause() {
            super.onPause();

            pause();
        }

        @Override
        public void onStop() {
            super.onStop();
            stop();
        }

        @Override
        public void onPlay() {
            super.onPlay();
            resume();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return iBinder;
    }

    @Override
    public void onCreate() {

        super.onCreate();

        strAppName = getResources().getString(R.string.app_name);
        strLiveBroadcast = getResources().getString(R.string.playingNow);

        onGoingCall = false;

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);


        wifiLock = ((WifiManager) Objects.requireNonNull(getApplicationContext().getSystemService(Context.WIFI_SERVICE)))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "mcScPAmpLock");

        mediaSession = new MediaSessionCompat(this, getClass().getSimpleName());
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "...")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, strAppName)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, strLiveBroadcast)
                .build());
        mediaSession.setCallback(mediasSessionCallback);

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        handler = new Handler();
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        exoPlayer = ExoPlayerFactory.newSimpleInstance(getApplicationContext(), trackSelector);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.CONTENT_TYPE_MUSIC)
                .build();

        exoPlayer.setAudioAttributes(audioAttributes);
        exoPlayer.addListener(this);

        registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        registerTuneURLReceiver();

        status = PlaybackStatus.IDLE;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent.getAction();
        if(TextUtils.isEmpty(action))
            return START_STICKY;

        if (!requestAudioFocus()) {
            //Could not gain focus
            stopSelf();
            stop();

            return START_STICKY;
        }
        if(action.equalsIgnoreCase(ACTION_PLAY)){

            transportControls.play();

        } else if(action.equalsIgnoreCase(ACTION_PAUSE)) {

            transportControls.pause();

        } else if(action.equalsIgnoreCase(ACTION_TOGGLE)) {
            if(PlaybackStatus.ISPLAYING){
                transportControls.pause();
            }else{
                transportControls.play();
            }
        } else if(action.equalsIgnoreCase(ACTION_STOP)){
            transportControls.stop();
            stopForeground(true);

            /*Intent mIntent = new Intent(this, MainActivity.class);
            mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mIntent.setAction(Intent.ACTION_MAIN);
            mIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            startActivity(mIntent);*/
        }

        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {

        if(status.equals(PlaybackStatus.IDLE))
            stopSelf();

        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(final Intent intent) {

    }

    @Override
    public void onDestroy() {

        pause();

        unregisterReceiver(tuneURLReceiver);

        exoPlayer.release();
        exoPlayer.removeListener(this);

        if(telephonyManager != null)
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);

        mediaSession.release();

        unregisterReceiver(becomingNoisyReceiver);

        super.onDestroy();
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        //Focus gained
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        //Could not gain focus
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:

                exoPlayer.setVolume(0.8f);

                resume();

                break;

            case AudioManager.AUDIOFOCUS_LOSS:

                stop();

                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:

                if (isPlaying()) pause();

                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:

                if (isPlaying())
                    exoPlayer.setVolume(0.1f);

                break;
        }

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        switch (playbackState) {
            case Player.STATE_BUFFERING:
                status = PlaybackStatus.LOADING;
                PlaybackStatus.ISPLAYING=false;
                break;
            case Player.STATE_ENDED:
                status = PlaybackStatus.STOPPED;
                PlaybackStatus.ISPLAYING=false;
                break;
            case Player.STATE_IDLE:
                status = PlaybackStatus.IDLE;
                PlaybackStatus.ISPLAYING=false;
                break;
            case Player.STATE_READY:
                status = playWhenReady ? PlaybackStatus.PLAYING : PlaybackStatus.PAUSED;
                PlaybackStatus.ISPLAYING = playWhenReady;
                break;
            default:
                status = PlaybackStatus.IDLE;
                PlaybackStatus.ISPLAYING=false;
                break;
        }

        if(!status.equals(PlaybackStatus.IDLE))
            startNotify(status);

        EventBus.getDefault().post(status);
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

        EventBus.getDefault().post(PlaybackStatus.ERROR);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }

    public void play(final String streamUrl) {

        this.streamUrl = streamUrl;

        if (wifiLock != null && !wifiLock.isHeld()) {

            wifiLock.acquire();

        }

//        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(getUserAgent());

        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this, getUserAgent(), BANDWIDTH_METER);

        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(Uri.parse(streamUrl));
        try {

            exoPlayer.prepare(mediaSource);
            exoPlayer.setPlayWhenReady(true);

            TuneURLManager.startScanning(this, streamUrl, 0);
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
        NoiseSuppressor ns = NoiseSuppressor.create(exoPlayer.getAudioSessionId());
        if (ns != null)
            ns.setEnabled(true);
        if (audioManager != null) {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }

    }

    public void resume() {

        if(streamUrl != null)
            play(streamUrl);
    }

    public void pause() {

        TuneURLManager.stopScanning(this);

        exoPlayer.setPlayWhenReady(false);

        audioManager.abandonAudioFocus(this);
        wifiLockRelease();
    }

    public void stop() {

        TuneURLManager.stopScanning(this);

        exoPlayer.stop();

        audioManager.abandonAudioFocus(this);
        wifiLockRelease();
    }

    public void playOrPause(String url){

        if(streamUrl != null && streamUrl.equals(url)){

            if(!isPlaying()){

                play(streamUrl);
            }
            else {

                pause();
            }

        } else {

            if(isPlaying()){

                pause();

            }

            play(url);
        }
    }

    public String getStatus(){

        return status;
    }

    public MediaSessionCompat getMediaSession(){

        return mediaSession;
    }

    public boolean isPlaying(){

        return this.status.equals(PlaybackStatus.PLAYING);
    }

    private void wifiLockRelease(){

        if (wifiLock != null && wifiLock.isHeld()) {

            wifiLock.release();
        }
    }

    private String getUserAgent(){

        return Util.getUserAgent(this, getClass().getSimpleName());
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
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        PendingIntent pi;
        Intent mIntent = new Intent(this, MainActivity.class);
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mIntent.setAction(Intent.ACTION_MAIN);
        mIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        pi = PendingIntent.getActivity(this, NOTIFICATION_ID, mIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        //cancelNotify(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(PRIMARY_CHANNEL, PRIMARY_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            assert manager != null;
            manager.createNotificationChannel(channel);
        }

        Notification builder = new NotificationCompat.Builder(this, PRIMARY_CHANNEL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.noti_icon)
                .setCustomContentView(simpleContentView)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(getMediaSession().getSessionToken())).build();

        setListeners(simpleContentView);

        builder.contentIntent = pi;
        builder.flags |= Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_ID, builder);
    }

    /*public void cancelNotify() {

        stopForeground(true);
    }
*/
    public void setListeners(RemoteViews view) {

        Intent stop = new Intent(this, RadioService.class);
        stop.setAction(RadioService.ACTION_STOP);
        PendingIntent pStop = PendingIntent.getService(this, NOTIFICATION_ID, stop,0);
        view.setOnClickPendingIntent(R.id.btnCancel, pStop);

        Intent play = new Intent(this, RadioService.class);
        play.setAction(RadioService.ACTION_PLAY);
        PendingIntent pPlay = PendingIntent.getService(this, NOTIFICATION_ID, play, 0);
        view.setOnClickPendingIntent(R.id.btnPlay, pPlay);

        Intent pause = new Intent(this, RadioService.class);
        pause.setAction(RadioService.ACTION_PAUSE);
        PendingIntent pPause = PendingIntent.getService(this, NOTIFICATION_ID, pause, 0);
        view.setOnClickPendingIntent(R.id.btnStop, pPause);
    }


    private void registerTuneURLReceiver(){

        tuneURLReceiver = new TuneURLReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SEARCH_FINGERPRINT_RESULT_RECEIVED);
        intentFilter.addAction(SEARCH_FINGERPRINT_RESULT_ERROR);
        intentFilter.addAction(ADD_RECORD_OF_INTEREST_RESULT_RECEIVED);
        intentFilter.addAction(ADD_RECORD_OF_INTEREST_RESULT_ERROR);
        intentFilter.addAction(POST_POLL_ANSWER_RESULT_RECEIVED);
        intentFilter.addAction(POST_POLL_ANSWER_RESULT_ERROR);
        intentFilter.addAction(GET_CYOA_RESULT_RECEIVED);
        intentFilter.addAction(GET_CYOA_RESULT_ERROR);

        registerReceiver(tuneURLReceiver, intentFilter);
    }
}
