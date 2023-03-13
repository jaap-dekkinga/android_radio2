package com.hex.heapradio;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.hex.heapradio.player.MediaNotificationManager;
import com.hex.heapradio.player.PlaybackStatus;
import com.hex.heapradio.player.RadioManager;
import com.hex.heapradio.player.RadioService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Objects;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, HomeFragment.SendMessages {
    // index to identify current nav menu item
    public static int navItemIndex = 0;
    public static int navPrevIndex = 0;
    public static int menuItemID=R.id.live_radio;
    ConnectivityManager cm;
    private AdView mAdView;
    private boolean appStarted = false;
    private Boolean showingLoader=false;

    DrawerLayout drawer;
    NavigationView navigationView;
    Toolbar toolbar;
    private String[] activityTitles;
    RadioManager radioManager;
    Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w("Instance Failed", "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String token = Objects.requireNonNull(task.getResult()).getToken();

                        // Log and toast
                        String msg = getString(R.string.msg_token_fmt, token);
                        Log.d("Token", msg);
                    }
                });
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        cm = (ConnectivityManager)this.getSystemService(CONNECTIVITY_SERVICE);
        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setItemIconTintList(null);
        navigationView.setNavigationItemSelectedListener(this);

        activityTitles = getResources().getStringArray(R.array.nav_item_activity_titles);

        setUpNavigationView();
        if (savedInstanceState == null) {
            navItemIndex = 0;
            navPrevIndex = 0;
            menuItemID=R.id.live_radio;
            loadHomeFragment();
        }

        //to hide menu items with no strings
        hideItem();

        if (getString(R.string.showAds).equalsIgnoreCase("true") && getString(R.string.showBannerAd).equalsIgnoreCase("true")) {
            mAdView = findViewById(R.id.adView);
            mAdView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder().addTestDevice(getString(R.string.testDevice)).build();
            mAdView.loadAd(adRequest);
        }

        //Bind Radio
        radioManager = RadioManager.with(this);

    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radioManager.unbind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        radioManager.bind();
    }

    @Override
    public void onBackPressed() {

        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawers();
        }else if (navItemIndex != 0 && navItemIndex!=2 && navItemIndex!=3 && navItemIndex!=10 && navItemIndex!=11) {
            navItemIndex = 0;
            navPrevIndex = 0;
            menuItemID=R.id.live_radio;
            loadHomeFragment();
            menuItemID=R.id.live_radio;
            loadHomeFragment();
        }else {
            new AlertDialog.Builder(this)
                    .setTitle("Exit Application")
                    .setMessage("Are you sure you want to exit?")
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    })
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                            /*moveTaskToBack(true);
                            if(PlaybackStatus.ISPLAYING){
                                playOrPause();
                            }*/
                            radioManager.unbind();
                            finish();
                        }
                    }).create().show();
        }

    }

    //Listening player events
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(String status){

        switch (status){

            case PlaybackStatus.LOADING:
                if(!showingLoader){
                    setDialog(true);
                }
                break;

            case PlaybackStatus.ERROR:
                showToast("Error in stream");
                setDialog(false);
                break;

            case PlaybackStatus.PLAYING:
                setDialog(false);
                break;

            case PlaybackStatus.AUTOPLAY:
                autoPlay();
                break;

        }

        PlaybackStatus.ISPLAYING = status.equals(PlaybackStatus.PLAYING);
        changeButton();

    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int itemID = item.getItemId();

        // if user select the current navigation menu again, don't do anything
        // just close the navigation drawer
        if(itemID==menuItemID && navItemIndex!=2 && navItemIndex!=3 && navItemIndex!=10 && navItemIndex!=11){
            drawer.closeDrawer(GravityCompat.START);
            return true;
        }
        menuItemID=itemID;
        switch (itemID) {
            case R.id.about_us:
                navItemIndex = 1;
                break;
            case R.id.call_us:
                navItemIndex = 2;
                break;
            case R.id.email:
                navItemIndex = 3;
                break;
            case R.id.contact_us:
                navItemIndex = 4;
                break;
            case R.id.website:
                navItemIndex = 5;
                break;
            case R.id.facebook:
                navItemIndex = 6;
                /*startActivity(new Intent(MainActivity.this, PrivacyPolicyActivity.class));
                drawer.closeDrawers();
                return true;
                */
                break;
            case R.id.instagram:
                navItemIndex = 7;
                break;
            case R.id.twitter:
                navItemIndex = 8;
                break;
            case R.id.youtube:
                navItemIndex = 9;
                break;
            case R.id.share:
                navItemIndex = 10;
                break;
            case R.id.rate_us:
                navItemIndex = 11;
                break;
            case R.id.privacy:
                navItemIndex = 12;
                break;
            default:
                navItemIndex = 0;
                menuItemID=R.id.live_radio;
        }

        drawer.closeDrawer(GravityCompat.START);

        return true;
    }

    private void setUpNavigationView() {

        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {

            @Override
            public void onDrawerClosed(View drawerView) {
                // Code here will be triggered once the drawer closes as we dont want anything to happen so we leave this blank
                super.onDrawerClosed(drawerView);

                // check network for menu with urls
                if((navItemIndex==4 || navItemIndex==5 || navItemIndex==6 || navItemIndex==7 || navItemIndex==8 || navItemIndex==9) && !checkNetwork()){
                    networkAlert();
                    return;
                }

                if(navItemIndex==2 || navItemIndex==3 || navItemIndex==10 || navItemIndex==11){
                    // check if nav action is not same and is direct action to dialog
                    loadHomeFragment();
                }else if(navPrevIndex!=navItemIndex){
                    // check if nav action is not same and is not a dialog
                    navPrevIndex=navItemIndex;
                    loadHomeFragment();
                }
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                // Code here will be triggered once the drawer open as we dont want anything to happen so we leave this blank
                super.onDrawerOpened(drawerView);
            }
        };

        //Setting the actionbarToggle to drawer layout
        drawer.addDrawerListener(actionBarDrawerToggle);
        //calling sync state is necessary or else your hamburger icon wont show up
        actionBarDrawerToggle.syncState();
    }

    private void loadHomeFragment() {
        // set toolbar title
        setToolbarTitle();
        if(navItemIndex==0){
            HomeFragment fragment = new HomeFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,fragment).commit();
        }else if(navItemIndex==1){
            PageFragment fragment = new PageFragment();
            Bundle arguments = new Bundle();
            arguments.putString("content", "about_content");
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,fragment).commit();
        }else if(navItemIndex==2){
            AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
            mBuilder.setTitle("Call Us");
            mBuilder.setMessage("Would you like to call us at "+getResources().getString(R.string.contact_no));
            mBuilder.setPositiveButton("Call Now", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + getString(R.string.contact_no)));
                    startActivity(intent);
                }
            });

            mBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            AlertDialog mDialog = mBuilder.create();
            mDialog.show();

        }else if(navItemIndex==3){
            Intent Email = new Intent(Intent.ACTION_SENDTO);
            Email.setData(Uri.parse("mailto:"));
            Email.putExtra(Intent.EXTRA_EMAIL  , new String[] { getResources().getString(R.string.support_email) });
            Email.putExtra(Intent.EXTRA_SUBJECT, "Feedback");
            Email.putExtra(Intent.EXTRA_TEXT, "Dear ...," + "");
            startActivity(Intent.createChooser(Email, "Send Feedback:"));
        }else if(navItemIndex==4){
            WebViewFragment fragment = new WebViewFragment();
            Bundle arguments = new Bundle();
            String url = getResources().getString(R.string.contact_us_url);
            if (URLUtil.isValidUrl(url)) {
                arguments.putString("url", url);
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            }

        }else if(navItemIndex==5){
            WebViewFragment fragment = new WebViewFragment();
            Bundle arguments = new Bundle();
            String url = getResources().getString(R.string.website_url);
            if (URLUtil.isValidUrl(url)) {
                arguments.putString("url", url);
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            }
        }else if(navItemIndex==6){
            WebViewFragment fragment = new WebViewFragment();
            Bundle arguments = new Bundle();
            String url = getResources().getString(R.string.facebook_url);
            if (URLUtil.isValidUrl(url)) {
                arguments.putString("url", url);
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,fragment).commit();
            }
        }else if(navItemIndex==7){
            WebViewFragment fragment = new WebViewFragment();
            Bundle arguments = new Bundle();
            String url = getResources().getString(R.string.instagram_url);
            if (URLUtil.isValidUrl(url)) {
                arguments.putString("url", url);
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            }
        }else if(navItemIndex==8){
            WebViewFragment fragment = new WebViewFragment();
            Bundle arguments = new Bundle();
            String url = getResources().getString(R.string.twitter_url);
            if (URLUtil.isValidUrl(url)) {
                arguments.putString("url", url);
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            }
        }else if(navItemIndex==9){
            WebViewFragment fragment = new WebViewFragment();
            Bundle arguments = new Bundle();
            String url = getResources().getString(R.string.youtube_url);
            if (URLUtil.isValidUrl(url)) {
                arguments.putString("url", url);
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            }
        }else if(navItemIndex==10){
            Intent sharingIntent = new Intent();
            sharingIntent.setAction(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");

            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getResources().getString(R.string.app_name));
            sharingIntent.putExtra(Intent.EXTRA_TEXT,
                    "Hey check out my app at: "+ Uri.parse("http://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID));

            startActivity(Intent.createChooser(sharingIntent, "Share app via"));
        }else if(navItemIndex==11){
            Uri uri = Uri.parse("market://details?id=" + getPackageName());
            Intent myAppLinkToMarket = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(myAppLinkToMarket);
            } catch (ActivityNotFoundException e) {
                //startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
                showToast("unable to find market app");
            }
        }else if(navItemIndex==12){
            PageFragment fragment = new PageFragment();
            Bundle arguments = new Bundle();
            arguments.putString("content", "privacy_content");
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,fragment).commit();
        }

        navigationView.setCheckedItem(menuItemID);
    }

    // change toolbar title
    private void setToolbarTitle() {
        if(navItemIndex!=2 && navItemIndex!=3 && navItemIndex!=10 && navItemIndex!=11){
            Objects.requireNonNull(getSupportActionBar()).setTitle(activityTitles[navItemIndex]);
        }
    }

    // Hide Menu Item
    private void hideItem() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu nav_Menu = navigationView.getMenu();
        if (getString(R.string.show_about_us).equalsIgnoreCase("false"))
            nav_Menu.findItem(R.id.about_us).setVisible(false);
        if (getString(R.string.contact_no).isEmpty())
            nav_Menu.findItem(R.id.call_us).setVisible(false);
        if (getString(R.string.support_email).isEmpty())
            nav_Menu.findItem(R.id.email).setVisible(false);
        if (getString(R.string.contact_us_url).isEmpty())
            nav_Menu.findItem(R.id.contact_us).setVisible(false);
        if (getString(R.string.website_url).isEmpty())
            nav_Menu.findItem(R.id.website).setVisible(false);
        if (getString(R.string.facebook_url).isEmpty())
            nav_Menu.findItem(R.id.facebook).setVisible(false);
        if (getString(R.string.instagram_url).isEmpty())
            nav_Menu.findItem(R.id.instagram).setVisible(false);
        if (getString(R.string.twitter_url).isEmpty())
            nav_Menu.findItem(R.id.twitter).setVisible(false);
        if (getString(R.string.youtube_url).isEmpty())
            nav_Menu.findItem(R.id.youtube).setVisible(false);
        if (getString(R.string.show_share).equalsIgnoreCase("false"))
            nav_Menu.findItem(R.id.share).setVisible(false);
        if (getString(R.string.show_rate_app).equalsIgnoreCase("false"))
            nav_Menu.findItem(R.id.rate_us).setVisible(false);
        if (getString(R.string.show_privacy).equalsIgnoreCase("false"))
            nav_Menu.findItem(R.id.privacy).setVisible(false);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private boolean checkNetwork(){
        boolean result = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cm != null) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        result = true;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        result = true;
                    }
                }
            }
        } else {
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null) {
                    // connected to the internet
                    result = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                }
            }
        }
        return result;

    }

    public void networkAlert(){
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
        mBuilder.setTitle("Network Error");
        mBuilder.setMessage("Please check your internet connection");
        mBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog mDialog = mBuilder.create();
        mDialog.show();
    }

    @Override
    public void playOrPause(){
        if (TextUtils.isEmpty(getResources().getString(R.string.radio))) return;
        if(!PlaybackStatus.ISPLAYING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForegroundService(new Intent(this,RadioService.class));
        }
        radioManager.playOrPause(getString(R.string.radio));
    }

    public void changeButton() {
        if(navPrevIndex==0){
            navItemIndex = 0;
            menuItemID=R.id.live_radio;
            loadHomeFragment();
        }
    }

    private void setDialog(boolean show){
        if (show){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setView(R.layout.loader);
            }else{
                @SuppressLint("InflateParams") View view = getLayoutInflater().inflate(R.layout.loader,null);
                builder.setView(view);
            }
            builder.setCancelable(false);
            dialog = builder.create();
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                layoutParams.copyFrom(window.getAttributes());
                layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
                layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                window.setAttributes(layoutParams);
                window.setGravity(Gravity.CENTER);
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();
            showingLoader=true;
        }
        else if(dialog!=null && dialog.isShowing()){
            dialog.dismiss();
            showingLoader=false;
        }
    }

    public void autoPlay(){
        if (getString(R.string.autoPlayOnAppStart).equalsIgnoreCase("true") && !appStarted) {
            if (TextUtils.isEmpty(getResources().getString(R.string.radio))) return;
            if(!PlaybackStatus.ISPLAYING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                startForegroundService(new Intent(this,RadioService.class));
            }
            radioManager.getService().playOrPause(getResources().getString(R.string.radio));
            appStarted=true;
        }
    }
}