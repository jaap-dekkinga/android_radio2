package com.hex.heapradio.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.dekidea.tuneurl.util.TuneURLManager;

import org.greenrobot.eventbus.EventBus;

public class RadioManager {

    private static RadioManager instance = null;

    private static RadioService service;
    private static MediaNotificationManager mediaNotificationManager;

    private Context context;

    private boolean serviceBound;

    private RadioManager(Context context) {
        this.context = context;
        serviceBound = false;
    }

    public static RadioManager with(Context context) {

        if (instance == null)
            instance = new RadioManager(context);

        return instance;
    }

    public static RadioService getService(){
        return service;
    }

    public void playOrPause(String streamUrl){

        service.playOrPause(streamUrl);
    }

    public boolean isPlaying() {

        return service.isPlaying();
    }

    public void bind() {

        System.out.println("bind()");

        TuneURLManager.startTuneURLService(context);

        Intent intent = new Intent(context, RadioService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        if(service != null)
            EventBus.getDefault().post(service.getStatus());
    }

    public void unbind() {

        System.out.println("unbind()");

        TuneURLManager.stopTuneURLService(context);

        context.unbindService(serviceConnection);
        
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {

            service = ((RadioService.LocalBinder) binder).getService();
            EventBus.getDefault().post(PlaybackStatus.AUTOPLAY);
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

            serviceBound = false;
        }
    };

}
