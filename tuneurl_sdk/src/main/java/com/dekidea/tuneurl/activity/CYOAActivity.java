package com.dekidea.tuneurl.activity;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.dekidea.tuneurl.R;
import com.dekidea.tuneurl.api.CYOA;
import com.dekidea.tuneurl.util.Constants;
import com.dekidea.tuneurl.util.TimeUtils;
import com.dekidea.tuneurl.util.TuneURLManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class CYOAActivity extends AppCompatActivity implements Constants {

    private static final long DEFAULT_PLAY_DELAY = 3000L;

    private LinearLayout optionsLayout;
    private VideoView mediaplayerView;

    private long tuneurl_id;
    private String default_mp3_url;

    private boolean playDefault;

    private Handler handler;

    private HashMap<String, CYOA> optionMap;
    private HashMap<String, Button> buttonMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        keyguardManager.requestDismissKeyguard(this, null);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_cyoa_activity);

        handler = new Handler();

        optionMap = new HashMap<String, CYOA>();
        buttonMap = new HashMap<String, Button>();

        optionsLayout = (LinearLayout) findViewById(R.id.optionsLayout);
        mediaplayerView = (VideoView) findViewById(R.id.mediaplayerView);

        try{

            String result  = getIntent().getStringExtra(TUNEURL_RESULT);

            processCYOAResult(result);
        }
        catch (Exception e){

            e.printStackTrace();
        }
    }


    @Override
    public void onResume() {

        super.onResume();

        //scheduleDefaultClose();
    }


    @Override
    public void onPause() {

        super.onPause();
    }


    private void scheduleDefaultClose(){

        TimerTask timerTask = new TimerTask() {

            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        CYOAActivity.this.finish();
                    }
                });
            }
        };

        Timer timer = new Timer();
        timer.schedule(timerTask, DEFAULT_PLAY_DELAY);
    }


    public void doAction(String user_response){

        TuneURLManager.addRecordOfInterest(this, "" + tuneurl_id, INTEREST_ACTION_ACTED, TimeUtils.getCurrentTimeAsFormattedString());

        this.finish();
    }


    private void processCYOAResult(String result){

        try {

            ArrayList<CYOA> CYOAArray = new ArrayList<CYOA>();

            JsonObject object = new JsonParser().parse(result).getAsJsonObject();

            tuneurl_id = object.get(TUNEURL_ID).getAsLong();
            default_mp3_url = object.get(DEFAULT_MP3_URL).getAsString();

            JsonArray jsonArray = object.getAsJsonArray("result");

            if (jsonArray != null && jsonArray.size() > 0) {

                for (int i = 0; i < jsonArray.size(); i++) {

                    try {

                        JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();

                        String mp3_url = jsonObject.get("mp3_url").getAsString();

                        if(URLUtil.isValidUrl(mp3_url)){

                            long tuneurl_id = jsonObject.get("tuneurl_id").getAsLong();
                            String option = jsonObject.get("options").getAsString();

                            CYOA cyoa = new CYOA(tuneurl_id, option, mp3_url);

                            CYOAArray.add(cyoa);
                        }
                    }
                    catch (Exception e) {

                        e.printStackTrace();
                    }
                }

                playDefault = true;

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            CYOA cyoa = new CYOA(tuneurl_id, "", default_mp3_url);

                            if (playDefault) {

                                playOption(cyoa);
                            }
                        }
                        catch (Exception e){

                            e.printStackTrace();
                        }
                    }
                }, DEFAULT_PLAY_DELAY);

                showOptions(CYOAArray);
            }
            else {

                //Do something
            }
        }
        catch (Exception e){

            e.printStackTrace();

            //Do something
        }
    }


    private void showOptions(ArrayList<CYOA> CYOAArray){

        if(CYOAArray != null && !CYOAArray.isEmpty()){

            try{

                for(CYOA option: CYOAArray){

                    Button button = getOptionButton(option);

                    if(button != null) {

                        optionMap.put(option.getOption(), option);

                        buttonMap.put(option.getOption(), button);

                        optionsLayout.addView(button);
                    }
                }
            }
            catch (Exception e){

                e.printStackTrace();
            }
        }
    }


    private Button getOptionButton(final CYOA option){

        try {

            Button button = new Button(this);

            button.setPadding(10, 10, 10, 10);
            button.setTextSize(24);
            button.setBackgroundResource(R.drawable.round_button_option);
            button.setText(option.getOption());
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    playOption(option);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(40, 40, 40, 40);
            button.setLayoutParams(params);

            return button;
        }
        catch (Exception e){

            e.printStackTrace();
        }

        return null;
    }


    private void markButtons(CYOA option){

        for (String key : buttonMap.keySet()) {

            try {

                if (key.equals(option.getOption())) {

                    buttonMap.get(key).setBackgroundResource(R.drawable.round_button_option_selected);
                }
                else {

                    buttonMap.get(key).setBackgroundResource(R.drawable.round_button_option);
                }
            }
            catch (Exception e){

                e.printStackTrace();
            }
        }
    }


    private void playOption(CYOA option){

        playDefault = false;

        try {

            Uri uri = Uri.parse(option.getMp3Url());

            MediaController mediaController = new MediaController(this);

            mediaController.setEnabled(true);
            mediaController.setAnchorView(mediaplayerView);
            mediaController.setMediaPlayer(mediaplayerView);

            mediaplayerView.setMediaController(mediaController);

            mediaplayerView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

                @Override
                public void onPrepared(MediaPlayer mp) {

                    try{

                        handler.postDelayed(
                                new Runnable() {
                                    public void run() {
                                        mediaController.show(0);
                                    }},
                                100);
                    }
                    catch (Exception e){

                        e.printStackTrace();
                    }
                }
            });

            mediaplayerView.setVideoURI(uri);
            mediaplayerView.start();

            Toast.makeText(this, "Loading ...", Toast.LENGTH_SHORT).show();
        }
        catch (Exception e){

            e.printStackTrace();
        }

        markButtons(option);
    }
}
