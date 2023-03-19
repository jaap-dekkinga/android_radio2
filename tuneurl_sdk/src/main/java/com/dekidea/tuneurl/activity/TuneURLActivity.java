package com.dekidea.tuneurl.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.dekidea.tuneurl.R;
import com.dekidea.tuneurl.api.APIData;
import com.dekidea.tuneurl.util.Constants;
import com.dekidea.tuneurl.util.TuneURLManager;

import java.util.Timer;
import java.util.TimerTask;

public class TuneURLActivity extends AppCompatActivity implements Constants {

    private static final int REQUEST_PHONE_CALL = 1234;
    private static final long DEFAULT_CLOSE_INTERVAL = 10000L;

    private APIData apiData;


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

        setContentView(R.layout.activity_tune_urlactivity);

        try{

            apiData = getIntent().getParcelableExtra(APIDATA);
        }
        catch (Exception e){

            e.printStackTrace();
        }

        showTuneURLOptions(apiData);
    }


    @Override
    public void onResume() {

        super.onResume();

        scheduleDefaultClose();
    }


    @Override
    public void onPause() {

        super.onPause();
    }


    public void showTuneURLOptions(final APIData apiData) {

        try {

            String type = "Type: " + apiData.getType();
            String description = apiData.getDescription();
            String name = apiData.getName();
            String info = apiData.getInfo();

            System.out.println("type = " + type);
            System.out.println("name = " + name);
            System.out.println("description = " + description);
            System.out.println("info = " + info);

            TextView title = findViewById(R.id.title);
            title.setText(info);
            TextView details = findViewById(R.id.details);
            details.setText(description);

            TextView button_open = findViewById(R.id.button_open);
            TextView button_ignore = findViewById(R.id.button_ignore);

            button_open.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    doAction("yes", apiData);
                }
            });

            button_ignore.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    doAction("no", apiData);
                }
            });
        }
        catch (Exception e){

            e.printStackTrace();
        }
    }


    public void doAction(String user_response, APIData apiData){

        if(apiData != null) {

            String action = apiData.getType();
            String date = apiData.getDate();

            if (ACTION_POLL.equals(action)) {

                TuneURLManager.postPollAnswer(this, user_response, apiData.getDescription(), apiData.getDate());
            }
            else {

                if (USER_RESPONSE_YES.equals(user_response)) {

                    if (ACTION_SAVE_PAGE.equals(action)) {

                        TuneURLManager.addRecordOfInterest(this, String.valueOf(apiData.getId()), INTEREST_ACTION_ACTED, date);

                        saveInfo(apiData);
                    }
                    else if (ACTION_OPEN_PAGE.equals(action)) {

                        TuneURLManager.addRecordOfInterest(this, String.valueOf(apiData.getId()), INTEREST_ACTION_ACTED, date);

                        openPage( apiData);
                    }
                    else if (ACTION_PHONE.equals(action)) {

                        TuneURLManager.addRecordOfInterest(this, String.valueOf(apiData.getId()), INTEREST_ACTION_ACTED, date);

                        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {

                            callPhone(apiData);
                        }
                        else{

                            ActivityCompat.requestPermissions(TuneURLActivity.this, new String[]{Manifest.permission.CALL_PHONE}, REQUEST_PHONE_CALL);
                        }
                    }
                    else if (ACTION_COUPON.equals(action)) {

                        TuneURLManager.addRecordOfInterest(this, String.valueOf(apiData.getId()), INTEREST_ACTION_ACTED, date);

                        openPage( apiData);
                    }
                    else if (ACTION_CYOA.equals(action)) {

                        TuneURLManager.addRecordOfInterest(this, String.valueOf(apiData.getId()), INTEREST_ACTION_ACTED, date);

                    }
                    else {


                    }

                    if(!ACTION_PHONE.equals(action)){

                        this.finish();
                    }
                }
                else {

                    this.finish();
                }
            }
        }
        else{

            this.finish();
        }
    }


    private void openPage(APIData data){

        try{

            String url = data.getInfo();

            System.out.println("openPage: " + url);

            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(i);
        }
        catch(Exception e){

            e.printStackTrace();
        }
    }


    private void saveInfo(APIData data){

        openPage(data);
    }


    private void callPhone(APIData data){

        try {

            String phone_number = data.getInfo();

            if (phone_number != null) {

                try {

                    Intent i = new Intent(Intent.ACTION_CALL);

                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    String uri = "tel:" + phone_number.trim();

                    i.setData(Uri.parse(uri));

                    if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                            Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {

                        //TuneURLManager.stopScanning(this);

                        startActivity(i);
                    }
                    else{

                        //TuneURLManager.startScanning(this);

                        ActivityCompat.requestPermissions(TuneURLActivity.this, new String[]{Manifest.permission.CALL_PHONE}, REQUEST_PHONE_CALL);
                    }
                }
                catch (Exception e) {

                    e.printStackTrace();
                }
            }
        }
        catch (Exception e){

            e.printStackTrace();
        }

        this.finish();
    }


    private void scheduleDefaultClose(){

        TimerTask timerTask = new TimerTask() {

            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        doAction("no", apiData);
                    }
                });
            }
        };

        Timer timer = new Timer();
        timer.schedule(timerTask, DEFAULT_CLOSE_INTERVAL);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] results) {

        super.onRequestPermissionsResult(requestCode, permissions, results);

        switch (requestCode) {

            case REQUEST_PHONE_CALL: {

                if (results != null &&
                        results.length > 0 &&
                        results[0] == PackageManager.PERMISSION_GRANTED) {

                    System.out.println("results[0] = " + results[0]);

                    callPhone(apiData);
                }

                return;
            }
        }
    }
}