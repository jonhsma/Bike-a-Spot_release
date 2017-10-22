package com.pushpy.park_a_bike;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBScanExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedScanList;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.StreetViewPanoramaFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.StreetViewPanoramaCamera;
import com.google.android.gms.maps.model.StreetViewPanoramaLocation;
import com.google.android.gms.tasks.OnSuccessListener;


import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This is the development branch of the app Park-a-Bike
 * Log calls and debugging options/echos are available
 */
public class MainActivity extends FragmentActivity
        implements
        OnMapReadyCallback
        ,BackStage.OnDatabaseClientReady
        ,OnStreetViewPanoramaReadyCallback
        ,SensorEventListener
{

    //Current state
    //0 :   nearby parking space search
    //1 :   planning (Street view filling upper half)
    //2 :   destination search (No street view)
    private int state = 0;

    //Some state frags
    private boolean mapIsRunning = false;
    private boolean streetViewIsRunning = false;

    //Fragments
    //Backstage
    private BackStage backStage;
    //Google maps fragment
    private MapFragment mapModule;
    private GoogleMap mMap;
    //Street View Fragment
    private StreetViewPanoramaFragment streetViewModule;
    private StreetViewPanorama mStreetViewPanorama;
    //Place auto complete fragment
    //private     PlaceAutocompleteFragment       placeModule;

    //Google maps/location stuff
    private boolean mLocationPermissionGranted = false;
    private final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 7200;
    private final int PLACE_AUTOCOMPLETE_REQ_CODE = 7201;
    private LatLng defaultLocation = new LatLng(37.8716, -122.2727), streetViewPosition, locationOfLastUpdate,searchResultPosition;
    private FusedLocationProviderClient mLocationClient;
    private Location lastKnownLocation = null;
    private Marker landingSpot, panoramaMarker,searchResultMarker,parkedBikeMarker;
    private List<Marker> markerList;
    private List<BikeParking> parkingList;
    private boolean mapCameraMoving=false;
    //Marker Configuration
    private BitmapDescriptor rackMarkerDescripter,parkedBikeDescripter;
    private final int RACK_MARKER_BITMAP_ID =R.drawable.rack_u;
    private final int PARKED_MARKER_BITMAP_ID=R.drawable.parked;
    private final int STREETVIEW_MARKER_BITMAP_ID =R.drawable.explorer;
    private final int SEARCH_DEST_MARKER_BITMAP_ID =R.drawable.destination;
    //Parked bike
    private ParkedBikeManager   parkedBikeManager;
    private LatLng              parkedLocation=null;

    //Sensors
    private GeomagneticField    localField;
    private float[]  phoneRM    =   new float[16];
    private SensorManager   sensorManager;//=(SensorManager) getSystemService(SENSOR_SERVICE);
    private Sensor          magSensor;//=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

    //Amazon interface
    CognitoCachingCredentialsProvider credentialsProvider;
    AmazonDynamoDBClient dynamoClient;
    DynamoDBMapper dbMapper;
    final static int AWS_DOWNLOAD_COMPLETE = 7210;

    //Multi threading handler
    Handler gPR;        //Specific for getting markers
    Handler handler;    //For general purpose


    //Layout elements
    FrameLayout     mapFrame, streetViewFrame;
    LinearLayout    buttonBar;
    Button          searchButton, parkButton;

    //Visuals
    public static float   textRatio=0.3f;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Location service
        mLocationClient = LocationServices.getFusedLocationProviderClient(this);

        //Capture the back stage
        backStage = (BackStage) getFragmentManager().findFragmentByTag("backStage");
        if (backStage == null) {
            backStage = new BackStage();
            getFragmentManager()
                    .beginTransaction()
                    .add(backStage, "backStage")
                    .commit();
            this.markerList = new LinkedList<Marker>();
            this.parkingList = new LinkedList<BikeParking>();
            state = 0;
        } else {
            //Capture the modules
            this.mapModule = backStage.mapModule;
            mapModule.getMapAsync(this);
            this.streetViewModule=backStage.streetViewModule;
            streetViewModule.getStreetViewPanoramaAsync(this);
            //Capture the amazon client
            this.credentialsProvider = backStage.credentialsProvider;
            this.dynamoClient = backStage.dynamoClient;
            this.dbMapper = backStage.dbMapper;
            //Other data of the app
            this.markerList = backStage.markerList;
            this.parkingList = backStage.parkingList;
            this.state = backStage.lastState;
            this.streetViewPosition = backStage.streetViewPosition;
            this.searchResultPosition=backStage.searchResultPosition;

        }


        //Capture the elements on the layout
        mapFrame = (FrameLayout) findViewById(R.id.mapFrame);
        streetViewFrame = (FrameLayout) findViewById(R.id.streetViewFrame);
        buttonBar   =(LinearLayout) findViewById(R.id.linLayout_buttonBar);
        searchButton = (Button) findViewById(R.id.button_searchButton);
        parkButton = (Button) findViewById(R.id.button_parkButton);

        //Attach components
        initMap();
        initPano();
        //initPlace();

        //Listeners
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPlaceAutoComplete("Search for your destination!");
            }
        });
        parkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(parkedLocation==null && mMap!=null && mLocationPermissionGranted){
                    boolean saveSuccess=parkedBikeManager.saveLocation(new LatLng(lastKnownLocation.getLatitude(),lastKnownLocation.getLongitude()));
                    if(saveSuccess) {
                        //Save the location
                        parkedLocation = new LatLng(lastKnownLocation.getLatitude(),lastKnownLocation.getLongitude());
                        parkButton.setText(getText(R.string.parkButtonRetrieveText));

                        //update the marker
                        parkedBikeMarker.setPosition(parkedLocation);
                        parkedBikeMarker.setVisible(true);
                    }
                }else{
                    parkedBikeManager.saveLocation((float)parkedBikeManager.NO_PARKED_BIKE_LOCATION);
                    parkedLocation=null;
                    parkedBikeMarker.setVisible(false);
                    parkButton.setText(getText(R.string.parkButtonText));
                }
            }
        });
        /**
         * Layout configuration postponed to when the map and streetviews are ready
        //Configure the layout
        switch (state) {
            case 0:
                setMode_Park();
                break;
            case 1:
                setMode_Plan(streetViewPosition);
                break;
            case 2:
                setMode_search(searchResultPosition,15);
        }*/

        //Sensors
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }catch(Exception e){
            e.printStackTrace();
        }

        //Parked bike Location
        parkedBikeManager=new ParkedBikeManager(this);
    }

    @Override
    protected void onResume(){
        super.onResume();
        //Register the Sensors
        sensorManager.registerListener(this,magSensor,SensorManager.SENSOR_DELAY_NORMAL);
        //See if the the user has a parked bike
        parkedLocation=parkedBikeManager.retrieveLocation();

        if(parkedLocation!=null){
            parkButton.setText(getText(R.string.parkButtonRetrieveText));
            if(parkedBikeMarker!=null){
                parkedBikeMarker.setPosition(parkedLocation);
                parkedBikeMarker.setVisible(true);
            }
        }else{
            parkButton.setText(getText(R.string.parkButtonText));
            if(parkedBikeMarker!=null){
                parkedBikeMarker.setVisible(false);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        //Update the backstage
        backStage.markerList = this.markerList;
        backStage.parkingList = this.parkingList;
        backStage.lastState = this.state;
        backStage.streetViewPosition = this.streetViewPosition;
        backStage.searchResultPosition=this.searchResultPosition;
        backStage.mapModule = this.mapModule;
        backStage.streetViewModule = this.streetViewModule;

        //Stop receiving sensor update
        sensorManager.unregisterListener(this);

    }

    private void initMap() {

        if (mapModule == null) {
            mapModule = MapFragment.newInstance();
            mapModule.getMapAsync(this);
            getFragmentManager().executePendingTransactions();
            getFragmentManager().beginTransaction()
                    .add(R.id.mapFrame, mapModule, "map")
                    .attach(mapModule)
                    .commit();
            backStage.mapModule = mapModule;
            mapModule.setRetainInstance(true);
        }
    }

    private void initPano() {
        if (streetViewModule == null) {
            getFragmentManager().executePendingTransactions();
            streetViewModule = StreetViewPanoramaFragment.newInstance();
            streetViewModule.getStreetViewPanoramaAsync(this);
            getFragmentManager().beginTransaction()
                    .add(R.id.streetViewFrame, streetViewModule, "streetView")
                    .commit();
            backStage.streetViewModule = streetViewModule;
            streetViewModule.setRetainInstance(true);
        }
    }

    private void setMode_Park() {
        //Attach the placeAutoComplete fragment
        //Commented out as the map will never be replaced
        /*
        Fragment    currFrag=getFragmentManager().findFragmentById(R.id.streetViewFrame);
        if (currFrag!=null && placeModule!=null && currFrag!=placeModule) {
            FragmentManager currFM = getFragmentManager();
            android.app.FragmentTransaction currFT  =   currFM.beginTransaction();
            currFT.attach(placeModule)
                    .detach(currFrag)
                    .commit();
        }*/

        //Layout
        Point pnt1 = new Point();
        getWindowManager().getDefaultDisplay().getSize(pnt1);
        mapFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.85)));
        streetViewFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.0)));
        buttonBar.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.1)));

        //Setting the sizes of the individual buttons
        searchButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x*0.5), (int) Math.round(pnt1.y * 0.1)));
        parkButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x*0.5), (int) Math.round(pnt1.y * 0.1)));
        searchButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.round(pnt1.y * 0.1*textRatio));
        parkButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.round(pnt1.y * 0.1*textRatio));

        //Set the map zoom and location
        if(mMap!=null) {
            if (mLocationPermissionGranted) {

                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.setMyLocationEnabled(true);
                //sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
                //sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

                landingSpot = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)));
                landingSpot.setVisible(false);
                mLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            updatedLocationListener(location);
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 17));
                        }
                    }
                });
            }else {
                LatLng camLocation = mMap.getCameraPosition().target;
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        camLocation,
                        18));
            }

            //Variables
            state = 0;
            if (panoramaMarker != null)
                panoramaMarker.setVisible(false);

        }
    }

    private void setMode_Plan(int subState) {
        //Attach the right fragment
        Fragment currFrag = getFragmentManager().findFragmentById(R.id.streetViewFrame);
        if (currFrag != null && streetViewModule != null && currFrag != streetViewModule) {
            FragmentManager currFM = getFragmentManager();
            android.app.FragmentTransaction currFT = currFM.beginTransaction();
            currFT.attach(streetViewModule)
                    .detach(currFrag)
                    .commit();
        }
        if (state == 1) {
            Point pnt1 = new Point();
            switch (subState) {
                case (0):
                    getWindowManager().getDefaultDisplay().getSize(pnt1);
                    mapFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.45)));
                    streetViewFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.4)));
                    buttonBar.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.1)));
                    break;
                case (1):
                    getWindowManager().getDefaultDisplay().getSize(pnt1);
                    mapFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.25)));
                    streetViewFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.6)));
                    buttonBar.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.1)));
            }
            //Setting the sizes of the individual buttons
            searchButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x), (int) Math.round(pnt1.y * 0.1)));
            parkButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x*0.0), (int) Math.round(pnt1.y * 0.1)));
        }
    }//sub mode switching is suspended

    private void setMode_Plan(LatLng target) {

        //Attach the right fragment
        Fragment currFrag = getFragmentManager().findFragmentById(R.id.streetViewFrame);
        if (currFrag != null && streetViewModule != null && currFrag != streetViewModule) {
            FragmentManager currFM = getFragmentManager();
            android.app.FragmentTransaction currFT = currFM.beginTransaction();
            currFT.attach(streetViewModule)
                    .detach(currFrag)
                    .commit();
        }
        //Layout
        Point pnt1 = new Point();
        getWindowManager().getDefaultDisplay().getSize(pnt1);
        mapFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.375)));
        streetViewFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.475)));
        buttonBar.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.1)));
        //Setting the sizes of the individual buttons
        searchButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x), (int) Math.round(pnt1.y * 0.1)));
        parkButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x*0.0), (int) Math.round(pnt1.y * 0.1)));
        searchButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.round(pnt1.y * 0.1*textRatio));
        parkButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.round(pnt1.y * 0.1*textRatio));


        //Variables
        state = 1;
        panoramaMarker.setVisible(true);

        //Configure the street view
        if (target != null) {
            try {
                mStreetViewPanorama.setPosition(target);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "We can't quite get there", Toast.LENGTH_SHORT).show();
            }
            //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(target.latitude,target.longitude),17));
        }
    }

    private void setMode_search(LatLng target,float zoom){
        //Layout
        Point pnt1 = new Point();
        getWindowManager().getDefaultDisplay().getSize(pnt1);
        mapFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.85)));
        streetViewFrame.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.0)));
        buttonBar.setLayoutParams(new LinearLayout.LayoutParams(pnt1.x, (int) Math.round(pnt1.y * 0.1)));
        //Setting the sizes of the individual buttons
        searchButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x), (int) Math.round(pnt1.y * 0.1)));
        parkButton.setLayoutParams(new LinearLayout.LayoutParams((int) Math.round(pnt1.x*0.0), (int) Math.round(pnt1.y * 0.1)));
        searchButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.round(pnt1.y * 0.1*textRatio));
        parkButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.round(pnt1.y * 0.1*textRatio));


        //set the mode
        state=2;
        //configure camera
        if(mMap!=null){
            CameraPosition pos = CameraPosition.builder().bearing(0).zoom(zoom).tilt(0).target(target).build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
            //Panorama marker
            panoramaMarker.setVisible(false);
            //search result marker
        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * If Google Play services is not installed on the device, the user will be prompted to install
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        updateLocationUI(true);

        mapIsRunning = true;
        if (streetViewIsRunning)
            onStreetViewAndMapReady();
    }

    //This function enables the app to get current location
    //and configures the map UI for real time location display

    private void updateLocationUI(boolean doRequest) {
        if (mMap == null) {
            return;
        }
        /**
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            if (doRequest)
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (mLocationPermissionGranted) {

            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            mMap.setMyLocationEnabled(true);
            //sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
            //sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            landingSpot = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)));
            landingSpot.setVisible(false);
            mLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        updatedLocationListener(location);
                        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 17));
                    } else {
                        requestPlaceAutoComplete("Failed to acquire location, sorry.. \n Search for destination!?");
                        mMap.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                }
            });

        } else {
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            requestPlaceAutoComplete("We respect your privacy.\n No location, we got it \n But where do you wanna go?");
        }
        //This thing enable/disables the button for location
    }

    private void updatedLocationListener(Location location){
        LatLng      currLocation=new LatLng(location.getLatitude(),location.getLongitude());
        lastKnownLocation=location;
        landingSpot.setPosition(new LatLng(location.getLatitude(),location.getLongitude()));
        //Toast.makeText(MainActivity.this,"getLocationSuccess",Toast.LENGTH_SHORT).show();
        //Just testing

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if(state==0&&mMap!=null&&localField!=null) {
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD && !mapCameraMoving) {
                SensorManager.getRotationMatrixFromVector(
                        phoneRM, event.values);
                float[] orientation = new float[3];
                SensorManager.getOrientation(phoneRM, orientation);
                float bearing = (float) (Math.toDegrees(orientation[0]) + localField.getDeclination());
                if (state == 0) {
                    CameraPosition currPos = mMap.getCameraPosition();
                    CameraPosition pos = CameraPosition.builder(currPos).bearing(bearing).build();
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
                }
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        return;
    }


    public void onDatabaseClientReady(BackStage backStage_echo){
        if(backStage_echo!=backStage){
            Toast.makeText(this,"Backstage is weird",Toast.LENGTH_SHORT).show();
        }
        this.credentialsProvider    =backStage_echo.credentialsProvider;
        this.dynamoClient           =backStage_echo.dynamoClient;
        this.dbMapper               =backStage_echo.dbMapper;
    }

    @Override
    public void onStreetViewPanoramaReady(final StreetViewPanorama streetViewPanorama) {
        mStreetViewPanorama = streetViewPanorama;
        //mStreetViewPanorama.setPosition(new LatLng(-33.87365, 151.20689));
        //Toast.makeText(this,"The panorama should be ready",Toast.LENGTH_SHORT).show();
        streetViewIsRunning=true;
        if(mapIsRunning)
            onStreetViewAndMapReady();
    }

    /**This method runs when both the panorama and the map are ready
    Some listeners of both of them depends on each other
     and can't be implemented until both are ready.
     To streamline the development, all listeners of the street view and the map is set in this method
     */
    private void onStreetViewAndMapReady(){
        //Toast.makeText(this,"Both map and street view initialized",Toast.LENGTH_SHORT).show();

        //Set the default marker
        rackMarkerDescripter    =   BitmapDescriptorFactory.fromResource(RACK_MARKER_BITMAP_ID);
        parkedBikeDescripter    =   BitmapDescriptorFactory.fromResource(PARKED_MARKER_BITMAP_ID);

        //Mark the position of the panorama
        panoramaMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(-33.87365, 151.20689))
                .icon(BitmapDescriptorFactory.fromResource(STREETVIEW_MARKER_BITMAP_ID)));
        panoramaMarker.setVisible(false);
        //Initiate the marker for search result
        searchResultMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(-33.87365, 151.20689))
                .icon(BitmapDescriptorFactory.fromResource(SEARCH_DEST_MARKER_BITMAP_ID)));
        searchResultMarker.setVisible(false);
        if(parkedLocation!=null) {
            parkedBikeMarker = mMap.addMarker(new MarkerOptions()
                    .position(parkedLocation)
                    .icon(parkedBikeDescripter)
                    .title("Where I parked my bike"));
            parkedBikeMarker.setVisible(true);
        }else {
            parkedBikeMarker = mMap.addMarker(new MarkerOptions()
                    .position(defaultLocation)
                    .icon(parkedBikeDescripter)
                    .title("Where I parked my bike"));
            parkedBikeMarker.setVisible(false);
        }



        //What happens when the position of the camera is street view changes
        mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(new StreetViewPanorama.OnStreetViewPanoramaChangeListener() {
            @Override
            public void onStreetViewPanoramaChange(StreetViewPanoramaLocation streetViewPanoramaLocation) {
                streetViewPosition = streetViewPanoramaLocation.position;
                panoramaMarker.setVisible(true);
                panoramaMarker.setPosition(streetViewPosition);
            }
        });

        mStreetViewPanorama.setOnStreetViewPanoramaCameraChangeListener(new StreetViewPanorama.OnStreetViewPanoramaCameraChangeListener() {
            @Override
            public void onStreetViewPanoramaCameraChange(StreetViewPanoramaCamera streetViewPanoramaCamera) {
                //setMode_Plan(1);
                //Set the orientation of the camera in the map
                LatLng  mapCamPos=mMap.getCameraPosition().target;

                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(mapCamPos)      // Sets the center of the map to Mountain View
                        .zoom(18)                   // Sets the zoom
                        .bearing(mStreetViewPanorama
                                .getPanoramaCamera().bearing)                // Sets the orientation of the camera to east
                        .tilt(30)                   // Sets the tilt of the camera to 30 degrees
                        .build();                   // Creates a CameraPosition from the builder
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        });

        mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                //Get the location of the camera
                mapCameraMoving=false;
                final LatLng camPos = mMap.getCameraPosition().target;
                //conversion factor
                final double lngFactor = Math.cos((camPos.latitude) / 2);
                //Get the current location
                if (ContextCompat.checkSelfPermission(MainActivity.this.getApplicationContext(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                updatedLocationListener(location);
                                //Update the UI
                                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                                mMap.setMyLocationEnabled(true);

                                //Calculate the distance from the last update
                                double distance = (camPos.latitude - lastKnownLocation.getLatitude()) * (camPos.latitude - lastKnownLocation.getLatitude());
                                distance = distance + (camPos.longitude - lastKnownLocation.getLongitude()) * (camPos.longitude - lastKnownLocation.getLongitude()) * lngFactor * lngFactor;
                                distance = Math.sqrt(distance);

                                //Determine if going to park mode or not
                                if (distance > 0.01) {
                                    switch(state) {
                                        case 0 :
                                            setMode_search(camPos,15);
                                            break;
                                    }
                                }
                                localField=new GeomagneticField(
                                        (float)location.getLatitude(),
                                        (float)location.getLongitude(),
                                        (float)location.getAltitude(),System.currentTimeMillis());
                            }else{
                                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                                mMap.setMyLocationEnabled(false);
                            }
                        }
                    });
                }
                double mDistance;
                if (locationOfLastUpdate == null)
                    mDistance = 100;
                else {
                    mDistance = (camPos.latitude - locationOfLastUpdate.latitude) * (camPos.latitude - locationOfLastUpdate.latitude);
                    mDistance = mDistance + (camPos.longitude - locationOfLastUpdate.longitude) * (camPos.longitude - locationOfLastUpdate.longitude) * lngFactor * lngFactor;
                }
                mDistance = Math.sqrt(mDistance);

                //Determine if another update is needed
                if (mDistance > 0.01)
                    try {
                        getParkingsNearby(camPos, 0.01);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }
        });

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                switch(state) {
                    case 1:
                        mStreetViewPanorama.setPosition(latLng);
                        break;
                    case 2:
                        setMode_Plan(latLng);
                }
            }
        });

        mMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int i) {
                //Sub state
                /*if(state==1)
                    setMode_Plan(0);*/
                mapCameraMoving=true;

            }
        });

        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                setMode_Park();
                return false;
            }
        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                switch (state) {
                    case 1:
                        mStreetViewPanorama.setPosition(marker.getPosition());
                        break;
                    case 2:
                        setMode_Plan(marker.getPosition());
                        mStreetViewPanorama.setPosition(marker.getPosition());
                }
                return false;
            }
        });


        //Set the layout mode
        //Configure the layout
        switch (state) {
            case 0:
                setMode_Park();
                break;
            case 1:
                setMode_Plan(streetViewPosition);
                break;
            case 2:
                setMode_search(searchResultPosition,15);
        }

    }

    //Back button means full screen map (search mode) in split screen (plan mode)
    @Override
    public void onBackPressed(){
        if(state==1){
            setMode_search(mMap.getCameraPosition().target,mMap.getCameraPosition().zoom);
        }else{
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQ_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                try {
                    //Get the camera and street view to the resultant location
                    LatLng target=place.getLatLng();
                    searchResultPosition=target;
                    if (target != null) {
                        try {
                            setMode_search(target,16.5f);
                            searchResultMarker.setTitle(place.getName().toString());
                            searchResultMarker.setPosition(target);
                            searchResultMarker.setVisible(true);
                            searchResultMarker.showInfoWindow();
                        }catch(Exception e){
                            Toast.makeText(MainActivity.this,"We can't quite get there",Toast.LENGTH_SHORT).show();
                        }
                        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(target.latitude,target.longitude),17));
                    }
                }catch(Exception ee){
                    ee.printStackTrace();
                }
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i("TAG", status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                updateLocationUI(false);
            }
        }
    }

    protected void requestPlaceAutoComplete(String messageText){
        try {
            PlaceAutocomplete.IntentBuilder builder=new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY);
            Intent intent =  builder.build(MainActivity.this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQ_CODE);
            Toast.makeText(this,messageText,Toast.LENGTH_SHORT).show();
        } catch (GooglePlayServicesRepairableException e) {
            // TODO: Handle the error.
        } catch (GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error.
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    protected void requestPlaceAutoComplete(){
        try {
            PlaceAutocomplete.IntentBuilder builder=new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY);
            Intent intent =  builder.build(MainActivity.this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQ_CODE);
        } catch (GooglePlayServicesRepairableException e) {
            // TODO: Handle the error.
        } catch (GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error.
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    protected boolean getParkingsNearby(LatLng  position,double range) throws InterruptedException {

        //Initialize local variables
        final LatLng    position_=position;
        final double    range_=range;
        //Update the location of last update (This line registers an attempt regardless of result)
        locationOfLastUpdate    =position;
        //Clear the location list (not the markers)
        parkingList.clear();

        //Check if there is network access
        if(!isNetworkAvailable()){
            Toast.makeText(this,"Please check internet connection",Toast.LENGTH_SHORT).show();
            return false;
        }

        //Handler for handling multiple threads
        gPR     =   new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message inputMessage){
                if(inputMessage.what==AWS_DOWNLOAD_COMPLETE){
                    //Unblock traffic
                    backStage.uploadTrafficIsBusy=false;
                    //Update the location of last update
                    locationOfLastUpdate    =position_;
                    //DEBUG
                    Toast.makeText(MainActivity.this, "Download completed", Toast.LENGTH_SHORT).show();
                    //Refresh the marker list
                    for( Marker mk : markerList)
                        mk.remove();
                    markerList.clear();
                    for (BikeParking pk: parkingList){
                        markerList.add(mMap.addMarker(new MarkerOptions()
                                .position((new LatLng(pk.getLatitude(),pk.getLongitude()))).icon(rackMarkerDescripter)));
                    }
                    //Clear the backlog
                    if (backStage.pendingLatLng!=null){
                        try {
                            //Clear the pending request
                            getParkingsNearby(backStage.pendingLatLng,range_);
                            //flush the waiting room
                            backStage.pendingLatLng=null;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        final Runnable    requestSequence=new Runnable() {
            @Override
            public void run() {
                //Stop on coming traffic
                backStage.uploadTrafficIsBusy=true;
                //The scan expression object
                DynamoDBScanExpression expression = new DynamoDBScanExpression();
                //The destionation of the scan
                PaginatedScanList<BikeParking> results = null;
                //The hash table for storing the scan filters
                Map<String, Condition> scanFilter = new HashMap<>();
                //Create the attributes (limits) for the conditions for each item in the filter
                //Latitude
                AttributeValue[] latRange = {new AttributeValue().withN(String.valueOf(position_.latitude - range_)),
                        new AttributeValue().withN(String.valueOf(position_.latitude + range_))};
                AttributeValue[] lngRange = {new AttributeValue().withN(String.valueOf(position_.longitude - range_ / Math.cos(Math.toRadians(position_.latitude)))),
                        new AttributeValue().withN(String.valueOf(position_.longitude + range_ / Math.cos(Math.toRadians(position_.latitude))))};
                //Construct the filter
                scanFilter.put("Longitude", new Condition()
                        .withComparisonOperator(ComparisonOperator.BETWEEN)
                        .withAttributeValueList(lngRange));
                scanFilter.put("Latitude", new Condition()
                        .withComparisonOperator(ComparisonOperator.BETWEEN)
                        .withAttributeValueList(latRange));
                //Set the filter
                expression.setScanFilter(scanFilter);

                try {
                    results = dbMapper.scan(BikeParking.class, expression);
                    for (BikeParking bp : results) {
                        parkingList.add(bp);
                    }

                } catch (AmazonServiceException e){
                    Toast.makeText(MainActivity.this, "We have problem talking to amazon", Toast.LENGTH_SHORT).show();
                } catch (AmazonClientException e) {
                    Log.e("", "Connection issue", e);
                } catch(Exception e) {
                    Toast.makeText(MainActivity.this, "Unknown issues encountered", Toast.LENGTH_SHORT).show();
                    Log.e("", "Unknown issue", e);
                }finally {
                    //Create the message to the handler
                    Message greenLight=new Message();
                    greenLight.what=AWS_DOWNLOAD_COMPLETE;
                    greenLight.obj=true;
                    //Send the message
                    MainActivity.this.gPR.sendMessage(greenLight);
                }
            }
        };

        Thread  requestThread=new Thread(requestSequence);
        //ZToast.makeText(MainActivity.this,"Location request initiated",Toast.LENGTH_SHORT).show();
        if(backStage.uploadTrafficIsBusy){
            //Replace whatever in the waiting room with the latest request
            backStage.pendingLatLng=position;
        }else {
            requestThread.start();
        }


        return true;
    }

    //Return if there is internet connection
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
