/*
 * Copyright (c) 2018, 2019. Peter Kopciak, Kevin Pirner, Alexis Ringot, Florian Taurer, Matthias Zeppelzauer.
 *
 * This file is part of SoniControl app.
 *
 *     SoniControl app is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     SoniControl app is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with SoniControl app.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.ac.fhstp.sonicontrol;


import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


public class Location {
    private static final String TAG = "Location";

    MainActivity main;
    private Scan detector;

    private double longitude;
    private double latitude;

    private Spoofer spoof;
    private MicCapture micCap;

    private static Location instanceLoc;
    private GPSTracker locationData;

    private double[] detectedSignalPosition = new double[2];
    private double[] positionLatest = new double[2];
    private String addressLine;
    ArrayList<String[]> data = new ArrayList<String[]>();

    private String fileUrl;

    private Technology signalTech;

    byte[] noiseByteArray;

    int locationRadius;
    boolean micBlockPref;
    Technology signalType;

    private Location(){
    }

    public static Location getInstanceLoc() { //getInstance method for Singleton pattern
        if(instanceLoc == null) { //if no instance of MicCapture is there create a new one, otherwise return the existing
            instanceLoc = new Location();
        }
        return instanceLoc;
    }

    public void init(MainActivity main){
        this.main = main;
    }

    public double[] getLocation(){
        if (locationData == null) {
            locationData = new GPSTracker(main); //get a gpstracker for the location finding
        }
        longitude = locationData.getLongitude(); //get the longitude
        latitude = locationData.getLatitude(); //get the latitude


        positionLatest[0] = longitude; //save the longitude in the positionLatest variable
        positionLatest[1] = latitude; //save the latitude in the positionLatest variable



        addressLine = locationData.getAddressLine(main);

        return positionLatest;
    }


    public void checkExistingLocationDB(double[] position, Technology signalType){
        signalTech = signalType;
        boolean isNewSignal = true;
        double[] positionDBEntry = new double[2];
        boolean shouldBeSpoofed = false;

        JSONManager jsonMan = new JSONManager(main);
        data = jsonMan.getJsonData(); //get the Json data saved in "data"
        jsonMan = null;
        for (String[] array : data){ //iterate through the list
            positionDBEntry[0] = Double.valueOf(array[0]); //save the longitude in the positionDBEntry array
            positionDBEntry[1] = Double.valueOf(array[1]); //save the latitude in the positionDBEntry array

            double distance = getDistanceInMetres(positionDBEntry, position); //calculate the distance between the latest Position and the position from the JSON-file

            SharedPreferences sharedPref = main.getSettingsObject(); //get the settings
            locationRadius = Integer.valueOf(sharedPref.getString(ConfigConstants.SETTING_LOCATION_RADIUS, ConfigConstants.SETTING_LOCATION_RADIUS_DEFAULT)); //save the radius of the location in metres
            if(distance<locationRadius && array[2].equals(signalType.toString())){ //if in the location and the technologie is the same
                isNewSignal = false; //no new signal
                //Log.d(TAG, "Longitude: " + array[0]);
                //Log.d(TAG, "Latitude : " + array[1]);
                //Log.d(TAG, "Technology : " + array[2]);
                //Log.d(TAG, "ShouldSpoof : " + array[4]);

                fileUrl = array[6]; //get file from json and save in fileUrl variable

                if(Integer.valueOf(array[4]) == ConfigConstants.DETECTION_TYPE_ALWAYS_BLOCKED_HERE){ //if the spoofed parameter from the json file is 1 then it should be spoofed and if its 0 it shouldnt be spoofed
                    shouldBeSpoofed = true;
                }else{
                    shouldBeSpoofed = false;
                }
            }
        }

        decideIfNewSignalOrNot(isNewSignal, positionDBEntry, shouldBeSpoofed, position);
    }

    public double[] getDetectedDBEntry(){
        if(positionLatest[0]==detectedSignalPosition[0]&&positionLatest[1]==detectedSignalPosition[1]){
            if(locationData!=null) {
                detectedSignalPosition = locationData.getDetectedLocation();
            }else{
                detectedSignalPosition[0] = 0;
                detectedSignalPosition[1] = 0;
            }
        }
        return detectedSignalPosition;
    }

    public String getDetectedDBEntryAddres(){
        return addressLine;
    }

    public void setPositionForContinuousSpoofing(double[] positionLatest){
        detectedSignalPosition = positionLatest; //set the latest position to the detected position if the continuous spoofing setting is active
    }

    private void shouldDBEntrySpoofed(boolean shouldBeSpoofed, double[]position){
        JSONManager jsonMan = new JSONManager(main);
        jsonMan.setLatestDate(position, signalTech); //update the date in the json-file at the detected position
        if(shouldBeSpoofed){ //if it should be spoofed
            //Log.d("Location", "I should be spoofed");
            /*if (!main.getBackgroundStatus()) { //if the app is not in the background
                main.cancelDetectionNotification(); //cancel the detection notification
            }*/
            //main.cancelScanningStatusNotification(); //cancel the scanning status notification
            NotificationHelper.activateSpoofingStatusNotification(main.getApplicationContext()); //activate the spoofing status notification

            boolean locationTrack = main.checkJsonAndLocationPermissions()[1];
            boolean saveJsonFile = main.checkJsonAndLocationPermissions()[0];

            blockMicOrSpoof(); //try get the microphone access for choosing the blocking method
        }else { //if it should not be spoofed
            //Log.d("Location", "I shouldn't be spoofed");
            /*if (!main.getBackgroundStatus()) { //if the app is not in the background
                main.cancelDetectionNotification(); //cancel the detection notification
            }*/
            detector = Scan.getInstance(); //get an instance of the Scan
            detector.startScanning(); //start scanning again
        }
    }

    public double getDistanceInMetres(double[] positionOld, double[] positionNew){
        double distance = distance(positionOld[1],positionOld[0],positionNew[1],positionNew[0]); //calculate the distance between two distances
        //main.updateDistance(distance); //can be deleted only for debugging
        return (distance*1000);
    }

    private void blockBySpoofing() {
        boolean playingGlobal = true; //the global play status is now true after the start
        boolean playingHandler = true; //helpboolean for switching the playstatus in the puslinghandler
        spoof = Spoofer.getInstance(); //get an instance of the spoofer
        spoof.init(main, playingGlobal, playingHandler, signalType); //initialize the spoofer
        spoof.startSpoofing(); //start spoofing
    }

    private void blockMicrophone() {
        micCap = MicCapture.getInstance(); //get an instance of the microphone capture
        micCap.init(main);
        micCap.startCapturing(); //start capturing
    }

    public String blockMicOrSpoof(){
        String usedBlockingMethod = null;
        SharedPreferences sharedPref = main.getSettingsObject(); //get the settings
        micBlockPref = sharedPref.getBoolean(ConfigConstants.SETTING_MICROPHONE_FOR_BLOCKING, ConfigConstants.SETTING_MICROPHONE_FOR_BLOCKING_DEFAULT); //save the radius of the location in metres
        if(micBlockPref) {
            if (!validateMicAvailability()) { //if we don't have access to the microphone
                blockBySpoofing();
                usedBlockingMethod = ConfigConstants.USED_BLOCKING_METHOD_SPOOFER;
            } else {
                usedBlockingMethod = ConfigConstants.USED_BLOCKING_METHOD_MICROPHONE;
                blockMicrophone();
            }
        }else{
            blockBySpoofing();
            usedBlockingMethod = ConfigConstants.USED_BLOCKING_METHOD_SPOOFER;
        }
        // Stores the app state : JAMMING
        SharedPreferences.Editor ed = sharedPref.edit();
        ed.putString(ConfigConstants.PREFERENCES_APP_STATE, StateEnum.JAMMING.toString());
        ed.apply();

        return usedBlockingMethod;
    }


    public boolean validateMicAvailability(){
        Boolean available = true; //helpboolean for the availability of the microphone
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);  //set the thread to urgent for audio

        AudioRecord recorder = //create a new recorder
                new AudioRecord(MediaRecorder.AudioSource.MIC, 44100,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_DEFAULT, 44100);
        try{
            if(recorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED ){ //if recorder state is not stopped its not available
                available = false;
            }

            recorder.startRecording(); //start recording
            if(recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING){ //if the recorder is not recording its not available
                available = false;
            }
        } finally{
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop(); //stop the recorder
            }
            recorder.release(); //release the recorder resources
        }

        return available;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) { //distance calculation
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }



    public AudioTrack generatePlayer(){
        //TODO: Will be solved after we get the correct links, now it would have to be restructured for getting it from the ressources
        File file = new File("/storage/emulated/0/DCIM/lisnr_test4.wav"); //get the audio file //not working dynamically now because no dynamic links
        noiseByteArray = new byte[(int) file.length()]; //size & length of the file
        InputStream is = null;
        try{
            is = new FileInputStream(file);
        }
        catch(IOException ex){
            ex.printStackTrace();
        }
        BufferedInputStream bis = new BufferedInputStream(is, 44100);
        DataInputStream dis = new DataInputStream(bis);

        int i = 0;
        try {
            while (dis.available() > 0) {
                noiseByteArray[i] = dis.readByte(); //read every entry of the data input stream into the new byte array
                i++;
            }

            dis.close();
        }
        catch (IOException ex){
            ex.printStackTrace();
        }
        AudioTrack sigPlayer = new AudioTrack(AudioManager.STREAM_MUSIC,14700, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT,noiseByteArray.length,AudioTrack.MODE_STATIC); //create a new player with the byte array
        sigPlayer.write(noiseByteArray, 0, noiseByteArray.length); //write the byte array into the player
        return sigPlayer;
    }

    public void saveSignalTypeForLater(Technology sigType){
        this.signalType = sigType;
    }

    public void decideIfNewSignalOrNot(boolean isNewSignal, double[] positionDBEntry, boolean shouldBeSpoofed, double[] position){
        if(isNewSignal){ //if its a new signal
            //Log.d("Location", "I am a new Signal");
            detectedSignalPosition = position; //save the new signal as the detected Position
            //main.cancelScanningStatusNotification();
            NotificationHelper.activateDetectionAlertStatusNotification(main.getApplicationContext(), signalType);
            /*if(main.getBackgroundStatus()) { //if the app is in the background
                main.activateDetectionNotification(); //activate the detection notification
            }*/
            main.activateAlert(signalType); //open the alert dialog with the found technology

        }else{
            //Log.d("Location", "I am not a new Signal");
            detectedSignalPosition = positionDBEntry; //save the position from the json-file
            shouldDBEntrySpoofed(shouldBeSpoofed,positionDBEntry); //check the spoofed status from the json-file

        }
    }

    public void requestGPSUpdates() {
        if (locationData == null) {
            // Will call requestLocationUpdates
            locationData = new GPSTracker(main);
        }
        else {
            locationData.requestGPSUpdates();
        }
    }

    public void removeGPSUpdates(){
        if (locationData != null) {
            locationData.removeGPSUpdates();
        }
    }

    public void saveLocationGPSTrackerObject(){
        locationData.saveDetectedLocation();
    }
}
