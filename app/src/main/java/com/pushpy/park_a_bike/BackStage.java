package com.pushpy.park_a_bike;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.StreetViewPanoramaFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.List;

/**
 * Created by Jonathan on 8/5/2017.
 */

public class BackStage extends Fragment {

    //The last state
    public      int      lastState;

    //The fragment pointers
    MapFragment mapModule;
    StreetViewPanoramaFragment  streetViewModule;
    PlaceAutocompleteFragment   placeModule;

    //The credential manager
    public CognitoCachingCredentialsProvider credentialsProvider;

    //Database
    public AmazonDynamoDBClient dynamoClient;
    public DynamoDBMapper dbMapper;
    //Upload traffic control
    public boolean  uploadTrafficIsBusy=false;
    public LatLng   pendingLatLng;

    //Current list of locations
    List<Marker>        markerList;
    List<BikeParking>   parkingList;

    //Locations
    public LatLng   streetViewPosition;//This is a pointer by the way
    public LatLng   searchResultPosition;//The last search result

    //Interface with MainActivity
    public OnDatabaseClientReady databaseClientReady;
    Context     context;

    @Override
    public void onCreate(Bundle SavedInstance){
        super.onCreate(SavedInstance);

        // Initialize the Amazon Cognito credentials provider
        credentialsProvider= new CognitoCachingCredentialsProvider(
                getActivity().getApplicationContext(),
                getString(R.string.identity_pool_id), // Identity pool ID
                Regions.US_WEST_2 // Region
        );
        dynamoClient    =new AmazonDynamoDBClient(credentialsProvider);
        dynamoClient.setRegion(Region.getRegion(Regions.US_WEST_2));
        dbMapper        =new DynamoDBMapper(dynamoClient);

        setRetainInstance(true);
        //Evoke the call back function in Main Activity after MainActivity has been registered as  a listener
        databaseClientReady.onDatabaseClientReady(this);
    }

    @Override
    public void onAttach(Context context){
        //onAttach for API 23 and up
        super.onAttach(context);
        //Capture Main activity and make it a listener of the interface
        if(context instanceof OnDatabaseClientReady){
            databaseClientReady=(OnDatabaseClientReady) context;
            //This is actually not necessary as super.onAttach(context) would call onAttach(activity)
        }else{
            throw new ClassCastException(context.toString()
                    + " Could not reach main activity");
        }
    }

    @Override
    public void onAttach(Activity activity){
        //onAttach for API 15 and up
        super.onAttach(activity);
        //Capture Main activity and make it a listener of the interface
        if(android.os.Build.VERSION.SDK_INT<23) {
            if (activity instanceof OnDatabaseClientReady) {
                databaseClientReady = (OnDatabaseClientReady) activity;
            } else {
                throw new ClassCastException(context.toString()
                        + " Could not reach main activity");
            }
        }
    }

    public interface OnDatabaseClientReady{
        void    onDatabaseClientReady(BackStage backStage);
    }
}
