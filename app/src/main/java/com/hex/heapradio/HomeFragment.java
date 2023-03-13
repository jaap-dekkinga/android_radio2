package com.hex.heapradio;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.hex.heapradio.player.PlaybackStatus;


public class HomeFragment extends Fragment {

    private ImageView playBtn, pauseBtn;
    private SendMessages sendMessages;

    private boolean isPlaying = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            sendMessages = (SendMessages) context;
            // listener.showFormula(show?);
        } catch (ClassCastException castException) {
            /** The activity does not implement the listener. **/
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home,container,false);

        playBtn = v.findViewById(R.id.playButton);
        pauseBtn = v.findViewById(R.id.pauseButton);
        changeButtons();

        playBtn.setOnClickListener(onButtonClick());
        pauseBtn.setOnClickListener(onButtonClick());
        return v;

    }

    private void showToast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }

    private View.OnClickListener onButtonClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.playButton:
                        sendMessages.playOrPause();
                        changeButtons();
                        break;
                    case R.id.pauseButton:
                        sendMessages.playOrPause();
                        changeButtons();
                        break;
                }

            }
        };
    }

    public interface SendMessages {
        void playOrPause();
    }

    public void changeButtons() {
        if(PlaybackStatus.ISPLAYING){
            playBtn.setVisibility(View.INVISIBLE);
            pauseBtn.setVisibility(View.VISIBLE);
        }else{
            playBtn.setVisibility(View.VISIBLE);
            pauseBtn.setVisibility(View.INVISIBLE);
        }
    }
}
