package com.hex.heapradio;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.RelativeLayout;


public class PageFragment extends Fragment {

    private WebView wv1;
    private RelativeLayout RL;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_page, container, false);
        assert this.getArguments() != null;
        String ARG_PARAM1 = this.getArguments().getString("content");
        String mParam1="";
        if(ARG_PARAM1=="about_content"){
            mParam1 = getResources().getString(R.string.about_content);
        }else if(ARG_PARAM1=="privacy_content"){
            mParam1 = getResources().getString(R.string.privacy_content);
        }
        wv1 = v.findViewById(R.id.webView);

        if (getString(R.string.showAds).equalsIgnoreCase("true") && getString(R.string.showBannerAd).equalsIgnoreCase("true")) {
            RL = v.findViewById(R.id.parentView);
            RL.setPaddingRelative(0,0,0,160);
        }
        wv1.loadData(mParam1,"text/html",null);
        return v;
    }

    @SuppressWarnings("deprecation")
    public static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(source);
        }
    }

}
