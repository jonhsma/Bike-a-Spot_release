package com.pushpy.park_a_bike;

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;

/**
 * Created by Jonathan on 8/7/2017.
 */

@DynamoDBTable(tableName = "BikeParking_0")
public class BikeParking {
    private     String  uniqueID;
    private double  longitude, latitude;

    //Interfacing with dynamoDB
    @DynamoDBHashKey(attributeName = "ID")
    public  String  getID(){
        return this.uniqueID;
    }
    public void setID(String uniqueID){
        this.uniqueID=uniqueID;
    }

    @DynamoDBIndexHashKey(attributeName = "Latitude")
    public  double  getLatitude(){
        return this.latitude;
    }
    public void setLatitude(double latitude){
        this.latitude=latitude;
    }
    @DynamoDBIndexHashKey(attributeName = "Longitude")
    public double getLongitude(){
        return this.longitude;
    }
    public void setLongitude(double longitude){
        this.longitude=longitude;
    }
}
