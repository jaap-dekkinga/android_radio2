package com.hex.heapradio;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;


public class SplashActivity extends AppCompatActivity {

    Handler handler;
    private InterstitialAd mInterstitialAd;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
        setContentView(R.layout.activity_splash);


            mInterstitialAd = new InterstitialAd(this);
            mInterstitialAd.setAdUnitId(getResources().getString(R.string.interstitial_ad_id));
            mInterstitialAd.loadAd(new AdRequest.Builder().addTestDevice(getResources().getString(R.string.testDevice)).build());

            handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getString(R.string.showAds).equalsIgnoreCase("true") && getString(R.string.showSplashAd).equalsIgnoreCase("true")) {
                        SplashActivity.this.finish();
                        if (mInterstitialAd.isLoaded()) {
                            startMainActivity();
                            mInterstitialAd.show();
                            mInterstitialAd.setAdListener(new AdListener() {
                                @Override
                                public void onAdClosed() {
                                    // Create an Intent that will start the main activity.
                                    //startMainActivity();
                                }
                            });
                        } else {
                            startMainActivity();
                        }
                    }else{
                        startMainActivity();
                        SplashActivity.this.finish();
                    }
                }
            }, 5000);

    }

    private void startMainActivity() {
        Intent mainIntent = new Intent(SplashActivity.this, MainActivity.class);
        SplashActivity.this.startActivity(mainIntent);
    }
}
