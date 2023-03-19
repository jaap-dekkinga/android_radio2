package com.dekidea.tuneurl.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dekidea.tuneurl.activity.CYOAActivity;
import com.dekidea.tuneurl.api.APIData;
import com.dekidea.tuneurl.service.APIService;
import com.dekidea.tuneurl.util.Constants;
import com.dekidea.tuneurl.util.TimeUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dekidea.tuneurl.activity.TuneURLActivity;

public class TuneURLReceiver extends BroadcastReceiver  implements Constants {

    private final int FINGERPRINT_MATCHING_THRESHOLD = 40;

    @Override
    public void onReceive(Context context, Intent intent) {

        try {

            if (intent != null) {

                String action = intent.getAction();

                if(action.equals(SEARCH_FINGERPRINT_RESULT_RECEIVED)){

                    String result = intent.getStringExtra(TUNEURL_RESULT);

                    try {

                        JsonObject jsonObject = new JsonParser().parse(result).getAsJsonObject();

                        processResult(context, jsonObject);
                    }
                    catch (Exception e){

                        e.printStackTrace();
                    }
                }
                else if(action.equals(GET_CYOA_RESULT_RECEIVED)){

                    String result = intent.getStringExtra(TUNEURL_RESULT);

                    startCYOAActivity(context, result);
                }
                else if(action.equals(GET_CYOA_RESULT_ERROR)){

                    //Do something
                }
                else if(action.equals(SEARCH_FINGERPRINT_RESULT_ERROR)){

                    String error = intent.getStringExtra(TUNEURL_RESULT);

                    //Do something

                }
                else if(action.equals(ADD_RECORD_OF_INTEREST_RESULT_RECEIVED)){

                    String result = intent.getStringExtra(TUNEURL_RESULT);

                    //Do something

                }
                else if(action.equals(ADD_RECORD_OF_INTEREST_RESULT_ERROR)){

                    String error = intent.getStringExtra(TUNEURL_RESULT);

                    //Do something


                }
                else if(action.equals(POST_POLL_ANSWER_RESULT_RECEIVED)){

                    String result = intent.getStringExtra(TUNEURL_RESULT);

                    //Do something


                }
                else if(action.equals(POST_POLL_ANSWER_RESULT_ERROR)){

                    String error = intent.getStringExtra(TUNEURL_RESULT);

                    //Do something


                }
            }
        }
        catch (Exception e){

            e.printStackTrace();
        }
    }


    private void processResult(Context context, JsonObject jsonObject){

        try {

            JsonArray result_array = jsonObject.getAsJsonArray("result");

            if (result_array != null && result_array.size() > 0) {

                JsonObject closest_match = null;

                for (int i = 0; i < result_array.size(); i++) {

                    try {

                        JsonObject current_match = result_array.get(i).getAsJsonObject();

                        if (closest_match == null) {

                            closest_match = current_match;
                        }
                        else {

                            int closest_matchPercentage = closest_match.get("matchPercentage").getAsInt();
                            int current_matchPercentage = current_match.get("matchPercentage").getAsInt();

                            if (closest_matchPercentage < current_matchPercentage) {

                                closest_match = current_match;
                            }
                        }
                    } catch (Exception e) {

                        e.printStackTrace();
                    }
                }

                if (closest_match != null) {

                    int matchPercentage = closest_match.get("matchPercentage").getAsInt();

                    if (matchPercentage >= FINGERPRINT_MATCHING_THRESHOLD) {

                        long id = closest_match.get("id").getAsLong();
                        String type = closest_match.get("type").getAsString();

                        String name = "";
                        try {

                            name = closest_match.get("name").getAsString();
                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        String info = closest_match.get("info").getAsString();

                        String description = "";
                        try {

                            description = closest_match.get("description").getAsString();
                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        APIData apiData = new APIData(id, name, description, type, info, matchPercentage);

                        String date = TimeUtils.getCurrentTimeAsFormattedString();
                        apiData.setDate(date);
                        apiData.setDateAbsolute(TimeUtils.getCurrentTimeInMillis());

                        System.out.println("id: " + id);
                        System.out.println("type: " + type);
                        System.out.println("info: " + info);

                        System.out.println("ACTION_CYOA.equals(type): " + ACTION_CYOA.equals(type));

                        if(ACTION_CYOA.equals(type)){

                            getCYOA(context, "" + id, info);
                        }
                        else{

                            startTuneURLActivity(context, apiData);
                        }
                    }
                    else {

                        //Do something
                    }
                }
                else {

                    //Do something
                }
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


    private void startCYOAActivity(Context context, String result){

        Intent intent = new Intent(context.getApplicationContext(), CYOAActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(TUNEURL_RESULT, result);

        context.getApplicationContext().startActivity(intent);
    }


    private void startTuneURLActivity(Context context, APIData apiData){

        Intent intent = new Intent(context.getApplicationContext(), TuneURLActivity.class);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(APIDATA, apiData);

        context.getApplicationContext().startActivity(intent);
    }


    private void getCYOA(Context context, String tuneurl_id, String default_mp3_url){

        Intent intent = new Intent(context.getApplicationContext(), APIService.class);

        intent.putExtra(TUNEURL_ACTION, ACTION_GET_CYOA);
        intent.putExtra(TUNEURL_ID, tuneurl_id);
        intent.putExtra(DEFAULT_MP3_URL, default_mp3_url);

        context.getApplicationContext().startService(intent);
    }
}

