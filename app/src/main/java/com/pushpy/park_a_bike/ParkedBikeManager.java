package com.pushpy.park_a_bike;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by Jonathan on 8/14/2017.
 */

public class ParkedBikeManager {
    public final static int    NO_PARKED_BIKE_LOCATION=7290;


    private boolean isInitialized=false;
    private SharedPreferences           preference;
    private SharedPreferences.Editor    prefEditor;
    private Activity            activity;

    public boolean getState(){
        return isInitialized;
    }

    public ParkedBikeManager(Activity activity){
        preference=activity.getPreferences(Context.MODE_PRIVATE);
        this.activity=activity;
        isInitialized=true;
    }

    public boolean saveLocation(LatLng target){
        boolean firstGo=false;
        prefEditor=preference
                .edit()
                .putFloat(activity.getString(R.string.parked_latitude_key),(float)target.latitude);
        firstGo=prefEditor.commit();
        prefEditor=preference
                .edit()
                .putFloat(activity.getString(R.string.parked_longitude_key),(float)target.longitude);
        return prefEditor.commit()&&firstGo;
    }
    public boolean saveLocation(float option){
        boolean firstGo=false;
        prefEditor=preference
                .edit()
                .putFloat(activity.getString(R.string.parked_latitude_key),(float)option);
        firstGo=prefEditor.commit();
        prefEditor=preference
                .edit()
                .putFloat(activity.getString(R.string.parked_longitude_key),(float)option);
        return prefEditor.commit()&&firstGo;
    }

    @Nullable
    public LatLng retrieveLocation(){
        double  lat=preference.getFloat(activity.getString(R.string.parked_latitude_key),NO_PARKED_BIKE_LOCATION);
        double  lng=preference.getFloat(activity.getString(R.string.parked_longitude_key),NO_PARKED_BIKE_LOCATION);
        if(lat==NO_PARKED_BIKE_LOCATION||lng==NO_PARKED_BIKE_LOCATION)
            return null;
        else
            return new LatLng(lat,lng);
    }

    public class parkedBikeRecord{
        LatLng  position=new LatLng(0,0);
    }
}
