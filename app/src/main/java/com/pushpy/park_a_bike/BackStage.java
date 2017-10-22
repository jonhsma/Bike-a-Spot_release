package com.pushpy.park_a_bike;

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
        databaseClientReady.onDatabaseClientReady(this);
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);

        //Capture Main activity and make it a listener of the interface
        if(context instanceof OnDatabaseClientReady){
            databaseClientReady=(OnDatabaseClientReady) context;
        }else{
            throw new ClassCastException(context.toString()
                    + " must implement GridGenerator.UpdateListener");
        }
    }

    public interface OnDatabaseClientReady{
        void    onDatabaseClientReady(BackStage backStage);
    }
}
