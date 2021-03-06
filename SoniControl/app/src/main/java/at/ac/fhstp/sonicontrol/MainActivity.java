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

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import android.provider.Settings;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;


public class MainActivity extends BaseActivity implements Scan.DetectionListener {
    private static final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION/*, Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE*/};
    private static final int REQUEST_ALL_PERMISSIONS = 42;

    private static final String TAG = "MainActivity";
    static MainActivity mainIsMain;

    ImageButton btnStorLoc;
    ImageButton btnStart;
    ImageButton btnStop;
    ImageButton btnSettings;
    ImageButton btnExit;

    Button btnAlertReplay;
    Button btnAlertSpoof;
    Button btnAlertDismissThisTime;
    Button btnAlertDismissAlways;
    Button btnAlertSpoofThisTime;

    Scan detector;
    Location locationFinder;
    JSONManager jsonMan;

    AlertDialog alert;
    TextView txtSignalType;
    TextView txtAlertDate;
    TextView txtNoLocation;
    Technology sigType;
    View view;

    Random randomNotificationNumberGenerator = new Random();
/*
    private static final int NOTIFICATION_STATUS_REQUEST_CODE = 2;
    private static final int NOTIFICATION_DETECTION_REQUEST_CODE = 1;

    NotificationCompat.Builder detectionBuilder;
    NotificationManagerCompat mNotificationManager;
    int detectionNotificationId;

    NotificationCompat.Builder spoofingStatusBuilder;
    int spoofingStatusNotificationId = ConfigConstants.SPOOFING_NOTIFICATION_ID;
    NotificationCompat.Builder detectionAlertStatusBuilder;
    int detectionAlertStatusNotificationId = ConfigConstants.DETECTION_ALERT_STATUS_NOTIFICATION_ID;
    NotificationCompat.Builder onHoldStatusBuilder;
    int onHoldStatusNotificationId = ConfigConstants.ON_HOLD_NOTIFICATION_ID;
    NotificationCompat.Builder scanningStatusBuilder;
    int scanningStatusNotificationId = ConfigConstants.SCANNING_NOTIFICATION_ID;

    Notification notificationDetection;
    Notification notificationDetectionAlertStatus;
    Notification notificationSpoofingStatus;
    Notification notificationOnHoldStatus;
    Notification notificationScanningStatus;

    private boolean detectionNotitificationFirstBuild = true;
    private boolean spoofingStatusNotitificationFirstBuild = true;
    private boolean detectionAlertStatusNotitificationFirstBuild = true;
    private boolean onHoldStatusNotitificationFirstBuild = true;
    private boolean scanningStatusNotitificationFirstBuild = true;

*/
    private boolean isInBackground = false;

    boolean isSignalPlayerGenerated;

    AudioTrack sigPlayer;

    boolean saveJsonFile;
    String usedBlockingMethod;
    boolean preventiveSpoof;

    protected LocationManager locationManager;
    boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;
    double[] lastPosition;

    // Thread handling
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    public static final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(NUMBER_OF_CORES + 1);

    public Handler uiHandler = new Handler(Looper.getMainLooper());

    SharedPreferences sharedPref;

    AlertDialog alertLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /***
         * Android bug causes the launch of a new task when using the home screen or launcher icon.
         * See: https://stackoverflow.com/a/12983901/5232306
         */
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            String action = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && action != null && action.equals(Intent.ACTION_MAIN)) {
                finish();
                return;
            }
        }
        jsonMan = new JSONManager(this);


        setContentView(R.layout.activity_main);

        mainIsMain = this;

        //TODO: This should be ran only at first start, maybe move it to the Service or Application.
        detector = Scan.getInstance(); //Get Scan-object if no object is available yet make a new one
        detector.init(MainActivity.this); //initialize the detector with the main method
        detector.setDetectionListener(this); // MainActivity will be notified of detections (calls onDetection)

        locationFinder = Location.getInstanceLoc(); //Get LocationFinder-object if no object is available yet make a new one
        locationFinder.init(MainActivity.this); //initialize the location-object with the main method

        btnStop = (ImageButton) findViewById(R.id.btnStop); //Main button for stopping the whole process
        btnStart = (ImageButton) findViewById(R.id.btnStart); //Main button for starting the whole process
        btnStorLoc = (ImageButton) findViewById(R.id.btnStorLoc); //button for getting into the storedLocations activity
        btnSettings = (ImageButton) findViewById(R.id.btnSettings); //button for getting into the settings activity
        btnExit = (ImageButton) findViewById(R.id.btnExit); //button for exiting the application

        btnStop.setEnabled(false); //after the start of the app set the stop button to false because nothing is there to stop yet

        final AlertDialog.Builder openScanner = new AlertDialog.Builder(MainActivity.this); //AlertDialog for getting the alert message after detection
        openScanner.setCancelable(false); //the AlertDialog cannot be canceled because you have to choose an option for the found signal
        LayoutInflater inflater = getLayoutInflater(); //inflator for getting the custom alertDialog over the main activity
        final ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content);
        view = inflater.inflate(R.layout.alert_message, viewGroup , false); //put the alert_message layout on the inflator
        openScanner.setView(view); //set the view of the inflater
        alert = openScanner.create(); //create the AlertDialog

        txtSignalType = (TextView)view.findViewById(R.id.txtSignalType); //this line can be deleted it's only for debug in the alert
        txtAlertDate = (TextView)view.findViewById(R.id.txtAlertDate);
        txtNoLocation = (TextView)view.findViewById(R.id.txtNoLocation);

        btnAlertDismissAlways = (Button) view.findViewById(R.id.btnDismissAlwaysHere); //button of the alert for always dismiss the found signal
        btnAlertDismissAlways.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
            onAlertDismissAlways();
            }
        });

        btnAlertDismissThisTime = (Button) view.findViewById(R.id.btnDismissThisTime); //button of the alert for only dismiss the found signal this time
        btnAlertDismissThisTime.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
            onAlertDismissThisTime();
            }
        });

        btnAlertSpoof = (Button) view.findViewById(R.id.btnSpoof); //button of the alert for starting the spoofing process after finding a signal
        btnAlertSpoof.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
            onAlertBlockAlways();
            }
        });

        btnAlertSpoofThisTime = (Button) view.findViewById(R.id.btnBlockThisTime);
        btnAlertSpoofThisTime.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                onAlertBlockThisTime();
            }
        });

        btnAlertReplay = (Button) view.findViewById(R.id.btnPlay); //button of the alert for playing the found signal with fs/3
        btnAlertReplay.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
            onAlertPlayDetectedSignal();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                onBtnStartClick(v);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                stopApplicationProcesses();
            }
        });

        btnStorLoc.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
            openStoredLocations();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
            openSettings();
            }
        });

        btnExit.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){onBtnExitClick(v);
            }
        });

        // savedInstanceState will be null unless there is a configuration change
        if(savedInstanceState == null) {
        }
        getUpdatedSettings(); //get the settings
    }

    private void onBtnExitClick(View v) {
        if (SoniService.IS_SERVICE_RUNNING) {
            Intent service = new Intent(MainActivity.this, SoniService.class);
            service.setAction(ServiceConstants.ACTION.STOPFOREGROUND_ACTION);
            startService(service);
        }
        else {
            NotificationHelper.cancelStatusNotification(MainActivity.this);
        }
        
        // Reset state
        SharedPreferences sp = getSettingsObject();
        SharedPreferences.Editor ed = sp.edit();
        ed.remove(ConfigConstants.PREFERENCES_APP_STATE);
        // Clean the technology on disk
        ed.remove("lastDetectedTechnology");
        // Note: this is blocking the thread, but we want to be sure that it gets persisted.
        ed.commit();
/*
        NotificationHelper.mNotificationManager.cancelAll(); //cancel all notifications

        // Cancel the pending intent corresponding to the notification
        PendingIntent resultPendingIntent = NotificationHelper.getPendingIntentStatusNoCreate(getApplicationContext());
        if (resultPendingIntent != null)
            resultPendingIntent.cancel();
*/
        NotificationHelper.cancelDetectionAlertStatusNotification(getApplicationContext());

        locationFinder.removeGPSUpdates();

        detector.stopIO(); // release audio resources from the scanner
        Spoofer spoof = Spoofer.getInstance(); //get a spoofing object
        spoof.stopSpoofingComplete(); //stop the whole spoofing process
        MicCapture micCap = MicCapture.getInstance(); //get a microphone capture object
        micCap.stopMicCapturingComplete(); //stop the whole capturing process via the microphone
        usedBlockingMethod = null;

        // Stop all the background threads
        threadPool.shutdownNow();
        System.exit(0); //exit the application
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void onBtnStartClick(View v) {
        if(!hasPermissions(MainActivity.this, PERMISSIONS)){
            // If an explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.RECORD_AUDIO)) {

                Toast toast = Toast.makeText(MainActivity.this, R.string.permissionRequestExplanation, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER,0,0);
                toast.show();
                //showRequestPermissionExplanation();

                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS, REQUEST_ALL_PERMISSIONS);
                    }
                }, 2000);
            } else {
                // First time, no explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS, REQUEST_ALL_PERMISSIONS);
            }
        }
        else {
            if(locationFinder.validateMicAvailability()){
                startService();
                checkForActivatedLocation();
                startDetection();
                isLocationEnabled(this);
            }else{
                activateNoMicrophoneAccessAlertDialog();
            }
        }
    }

    private void startService() {
        if (!SoniService.IS_SERVICE_RUNNING) {
            Intent service = new Intent(this.getApplicationContext(), SoniService.class);
            service.setAction(ServiceConstants.ACTION.STARTFOREGROUND_ACTION);
            startService(service);
        }
    }

    private void showRequestPermissionExplanation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(R.string.permissionRequestExplanation);
        builder.setPositiveButton("Open the Permission Menu",new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setAction(ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                }
        );
        builder.setNegativeButton("Back to the main menu", null);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ALL_PERMISSIONS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0) {
                    //we will show an explanation next time the user click on start
                    showRequestPermissionExplanation();
                }
                else {
                    for (int i = 0; i < permissions.length; i++) {
                        if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                if(locationFinder.validateMicAvailability()){
                                    startService();
                                    checkForActivatedLocation();
                                    startDetection();
                                }else{
                                    activateNoMicrophoneAccessAlertDialog();
                                }
                            }
                            else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                                showRequestPermissionExplanation();
                            }
                        }
                    }
                }
            }
            case ConfigConstants.REQUEST_GPS_PERMISSION:{
                if (grantResults.length == 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, R.string.toastLocationAccessDenied, Toast.LENGTH_LONG).show();
                        }
                    });
                }
                else {
                    for (int i = 0; i < permissions.length; i++) {
                        if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])) {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                locationFinder.requestGPSUpdates();
                            }
                            else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                                runOnUiThread(new Runnable() {
                                      @Override
                                      public void run() {
                                          Toast.makeText(MainActivity.this, R.string.toastLocationAccessDenied, Toast.LENGTH_LONG).show();
                                      }
                                });
                            }
                        }
                    }
                }
            }
        }
    }


    public void activateAlert(Technology signalType){
        SharedPreferences settings = getSettingsObject(); //get the settings

        boolean gpsEnabled = settings.getBoolean(ConfigConstants.SETTING_GPS, ConfigConstants.SETTING_GPS_DEFAULT);
        boolean networkEnabled = settings.getBoolean(ConfigConstants.SETTING_NETWORK_USE, ConfigConstants.SETTING_NETWORK_USE_DEFAULT);
        locationManager = (LocationManager) this.getApplicationContext().getSystemService(LOCATION_SERVICE);
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        saveJsonFile = settings.getBoolean(ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE, ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE_DEFAULT);

        int status = ActivityCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION);

        if((!(isGPSEnabled && gpsEnabled) && !(isNetworkEnabled && networkEnabled)) || status != PackageManager.PERMISSION_GRANTED){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnAlertSpoof.setEnabled(false);
                    btnAlertDismissAlways.setEnabled(false);
                    txtNoLocation.setText(R.string.on_alert_no_location_message);
                }
            });
        }else if(!saveJsonFile){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnAlertSpoof.setEnabled(false);
                    btnAlertDismissAlways.setEnabled(false);
                    txtNoLocation.setText(R.string.alert_no_json_file_message);
                }
            });
        }else{
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnAlertSpoof.setEnabled(true);
                    btnAlertDismissAlways.setEnabled(true);
                    txtNoLocation.setText("");
                }
            });
        }

        sigType = signalType; //set the technology variable to the latest detected one

        boolean activityExists = settings.getBoolean("active", false);
        if (activityExists) {
            runOnUiThread(displayAlert);
            //uiHandler.post(displayAlert); NOTE: runOnUiThread will execute code directly, not post
        }
    }

    private Runnable displayAlert = new Runnable() {
        public void run() {
            txtAlertDate.setText(getString(R.string.alert_detection_date) + " " + getTimeAndDateForAlert());
            alert.show(); //open the alert
        }
    };

    /***
     *
     */
    private void stopAutomaticBlockingMethodOnAction(){
        //NotificationHelper.cancelSpoofingStatusNotification();
        if(usedBlockingMethod.equals(ConfigConstants.USED_BLOCKING_METHOD_SPOOFER)){
            Spoofer spoofBlock = Spoofer.getInstance();
            spoofBlock.stopSpoofingComplete();
        }else if(usedBlockingMethod.equals(ConfigConstants.USED_BLOCKING_METHOD_MICROPHONE)){
            MicCapture micBlock = MicCapture.getInstance();
            micBlock.stopMicCapturingComplete();
        }
        usedBlockingMethod = null;
    }


    public SharedPreferences getSettingsObject(){
        if (sharedPref == null)
            sharedPref = PreferenceManager.getDefaultSharedPreferences(this); //get the object with the settings
        return sharedPref;
    }

    public void getUpdatedSettings(){
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onResume(){ //override the onResume method for setting a variable for checking the background-status
        super.onResume();
        isInBackground = false;
        checkFirstRunForWelcomeShowing();

        PendingIntent detectionPendingIntent = NotificationHelper.getPendingIntentDetectionFlagNoCreate(getApplicationContext());

        // Store the app activeness and then check the state
        SharedPreferences sp = getSettingsObject();
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("active", true);
        ed.apply();
        String stateString = sp.getString(ConfigConstants.PREFERENCES_APP_STATE, StateEnum.ON_HOLD.toString());
        StateEnum state;
        try {
            state = StateEnum.fromString(stateString);
        }
        catch (IllegalArgumentException e) {
            //Log.d(TAG, "onResume: " + e.getMessage());
            state = StateEnum.ON_HOLD;
        }

        Intent intent = getIntent();
        switch (state) {
            case ON_HOLD:
                NotificationHelper.activateOnHoldStatusNotification(getApplicationContext());
                setGUIStateStopped();
                break;
            case JAMMING:
                setGUIStateStarted();
                NotificationHelper.activateSpoofingStatusNotification(getApplicationContext());

                if (intent.hasExtra(ConfigConstants.EXTRA_TECHNOLOGY_DETECTED)) {
                    //TODO: We might use directly the "lastDetectedTechnology", not using Extras ?

                    Technology technology = (Technology) intent.getExtras().get(ConfigConstants.EXTRA_TECHNOLOGY_DETECTED);
                    if (technology != null) {
                        activateAlert(technology);
                        intent.removeExtra(ConfigConstants.EXTRA_TECHNOLOGY_DETECTED);
                    }
                }
                else if (detectionPendingIntent != null) {
                    if (sigType != null) {
                        activateAlert(sigType);
                    }
                    else {
                        // in case the Activity was destroyed
                        String storedTechnology = sp.getString("lastDetectedTechnology", null);
                        if (storedTechnology != null) {
                            Technology lastDetectedTechnology = null;
                            try {
                                lastDetectedTechnology = Technology.fromString(storedTechnology);
                            }
                            catch (IllegalArgumentException e) {
                                //Log.d(TAG, "onResume: " + e.getMessage());
                            }
                            if (lastDetectedTechnology != null) {
                                activateAlert(lastDetectedTechnology);
                            }
                            else {
                                //Log.d(TAG, "onResume: Technology not stored correctly ?");
                            }
                        }
                    }
                }

                break;
            case SCANNING:
                setGUIStateStarted();

                if (intent.hasExtra(ConfigConstants.EXTRA_TECHNOLOGY_DETECTED)) {
                    //TODO: We might use directly the "lastDetectedTechnology", not using Extras ?

                    Technology technology = (Technology) intent.getExtras().get(ConfigConstants.EXTRA_TECHNOLOGY_DETECTED);
                    if (technology != null) {
                        activateAlert(technology);
                        intent.removeExtra(ConfigConstants.EXTRA_TECHNOLOGY_DETECTED);
                    }
                }
                else if (detectionPendingIntent != null) {
                    if (sigType != null) {
                        activateAlert(sigType);
                    }
                    else {
                        // in case the Activity was destroyed
                        String storedTechnology = sp.getString("lastDetectedTechnology", null);
                        if (storedTechnology != null) {
                            Technology lastDetectedTechnology = null;
                            try {
                                lastDetectedTechnology = Technology.fromString(storedTechnology);
                            }
                            catch (IllegalArgumentException e) {
                                //Log.d(TAG, "onResume: " + e.getMessage());
                            }
                            if (lastDetectedTechnology != null) {
                                activateAlert(lastDetectedTechnology);
                            }
                            else {
                                //Log.d(TAG, "onResume: Technology not stored correctly ?");
                            }
                        }
                    }
                }
                else {
                    NotificationHelper.activateScanningStatusNotification(getApplicationContext());
                }
                break;
            default:
                // Should not happen as a default value is set above
                break;
        }


        Snackbar.make(findViewById(android.R.id.content).getRootView(), state.toString(),
                Snackbar.LENGTH_SHORT)
                .show();

        SharedPreferences settings = getSettingsObject(); //get the settings
        saveJsonFile = settings.getBoolean(ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE, ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE_DEFAULT);

        if(saveJsonFile) {
            if (!jsonMan.checkIfJsonFileIsAvailable()) { //check if a JSON File is already there in the storage
                jsonMan.createJsonFile(); //create a JSON file
            }
            if (!jsonMan.checkIfSavefolderIsAvailable()) { //check if a folder for the audio files is already there in the storage
                jsonMan.createSaveFolder(); //create a folder for the audio files
            }
        }
    }

    @Override
    public void onPause(){ //override the onPause method for setting a variable for checking the background-status
        super.onPause();
        isInBackground = true;

        // Store our shared preference
        SharedPreferences sp = getSettingsObject();
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("active", false);
        // Clean the technology on disk
        ed.remove("lastDetectedTechnology");
        ed.apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // TODO: Release resources... But this should not be called as long as our Service runs.
        // Maybe threads, microphone, ... ?
        // threadPool.shutdownNow();
    }

    public static MainActivity getMainIsMain(){
        return mainIsMain;
    }

    public boolean getBackgroundStatus(){
        return isInBackground;
    } //get the background-status

    public boolean[] checkJsonAndLocationPermissions() {
        boolean[] saveJsonAndLocation = new boolean[2];
        SharedPreferences settings = getSettingsObject(); //get the settings
        saveJsonFile = settings.getBoolean(ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE, ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE_DEFAULT);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        boolean locationTrack = false;
        boolean locationTrackGps = settings.getBoolean(ConfigConstants.SETTING_GPS, ConfigConstants.SETTING_GPS_DEFAULT);
        boolean locationTrackNet = settings.getBoolean(ConfigConstants.SETTING_NETWORK_USE, ConfigConstants.SETTING_NETWORK_USE_DEFAULT);

        if((locationTrackGps&&isGPSEnabled)||(locationTrackNet&&isNetworkEnabled)){
            locationTrack = true;
        }

        saveJsonAndLocation[0] = saveJsonFile;
        saveJsonAndLocation[1] = locationTrack;

        return saveJsonAndLocation;
    }

    @Override
    public void onDetection(final Technology technology) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                checkTechnologyAndDoAccordingly(technology);
            }
        });
    }



    private void checkTechnologyAndDoAccordingly(Technology detectedTechnology){
        if(detectedTechnology == null) {
            // This case should not happen
           //Log.d(TAG, "checkTechnologyAndDoAccordingly: detectedTechnology is null !?");
        }
        else {
            switch (detectedTechnology) {
                case GOOGLE_NEARBY:
                    handleSignal(Technology.GOOGLE_NEARBY);
                case LISNR:
                    handleSignal(Technology.LISNR);
                case PRONTOLY:
                    handleSignal(Technology.PRONTOLY);
                case UNKNOWN:
                    //Log.d("Detected", "Unknown ultrasonic signal");
                    handleSignal(Technology.UNKNOWN);
            }
        }
    }

    private void handleSignal(Technology technology) {
    
        SharedPreferences sp = getSettingsObject();
        preventiveSpoof = sp.getBoolean(ConfigConstants.SETTING_PREVENTIVE_SPOOFING, ConfigConstants.SETTING_PREVENTIVE_SPOOFING_DEFAULT);
        if(preventiveSpoof) {
            if (usedBlockingMethod == null) {
                NotificationHelper.activateSpoofingStatusNotification(getApplicationContext());
                usedBlockingMethod = locationFinder.blockMicOrSpoof();
            }
        }

        // Stores the technology on disk in case the Activity was destroyed
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("lastDetectedTechnology", technology.toString());
        ed.apply();

        boolean locationTrack;
        locationFinder.saveSignalTypeForLater(technology);
        locationTrack = this.checkJsonAndLocationPermissions()[1];

        if (locationTrack) {
            lastPosition = locationFinder.getLocation(); //get the latest position
            locationFinder.saveLocationGPSTrackerObject();
        }


        if (this.getSettingsObject().getBoolean(ConfigConstants.SETTING_CONTINOUS_SPOOFING, false)) { //check if the settings are set to continous spoofing
            /*if (!this.getBackgroundStatus()) { //if the app is not in the background
                this.cancelDetectionNotification(); //cancel the detection notification
            }*/
            //Log.d("Spoof", "I spoof oontinuous");
            if (locationTrack) {
                locationFinder.setPositionForContinuousSpoofing(lastPosition); //set the position for distance calculation to the latest position
            }
            //NotificationHelper.cancelScanningStatusNotification(); //cancel the scanning-status notification
            NotificationHelper.activateSpoofingStatusNotification(getApplicationContext()); //activate the spoofing-status notification

            saveJsonFile = this.checkJsonAndLocationPermissions()[0];

            JSONManager jsonMan = new JSONManager(this);
            if (saveJsonFile && locationTrack) {
                jsonMan.addJsonObject(locationFinder.getDetectedDBEntry(), technology.toString(), ConfigConstants.DETECTION_TYPE_ALWAYS_BLOCKED_HERE, locationFinder.getDetectedDBEntryAddres()); //adding the found signal in the JSON file
            }
            if (saveJsonFile && !locationTrack) {
                double[] noLocation = new double[2];
                noLocation[0] = 0;
                noLocation[1] = 0;
                jsonMan.addJsonObject(noLocation, technology.toString(), ConfigConstants.DETECTION_TYPE_ALWAYS_BLOCKED_HERE, this.getResources().getString(R.string.addressData));
            }

            locationFinder.blockMicOrSpoof(); //try for microphone access and choose the blocking method
            //resetHandler(); // Should be handled by the cpp (just stop scanning)
        } else {
            if (!jsonMan.checkIfJsonFileIsAvailable()) { //check if the user has a JSON file
                /*if (this.getBackgroundStatus()) { //if the app is in the background
                    this.activateDetectionNotification(); //activate the notification for a detection
                }*/
                //NotificationHelper.cancelScanningStatusNotification(); //cancel the scanning-status notification
                NotificationHelper.activateDetectionAlertStatusNotification(getApplicationContext(), technology);
                this.activateAlert(technology); //open the alert dialog
            } else {
                if (locationTrack) {
                    locationFinder.checkExistingLocationDB(lastPosition, technology); //if a JSON file is available we check if the signal is a new one with position and technologytype
                } else {
                    /*if (this.getBackgroundStatus()) { //if the app is in the background
                        this.activateDetectionNotification(); //activate the notification for a detection
                    }*/
                    //NotificationHelper.cancelScanningStatusNotification(); //cancel the scanning-status notification
                    NotificationHelper.activateDetectionAlertStatusNotification(getApplicationContext(), technology); //activate the onHold-status notification
                    this.activateAlert(technology); //open the alert dialog
                    //resetHandler(); // Should be handled by the cpp (just stop scanning)
                }
            }
        }
    }

    public void startDetection(){
        SharedPreferences settings = getSettingsObject(); //get the settings
        saveJsonFile = settings.getBoolean(ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE, ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE_DEFAULT);

        if(saveJsonFile) {
            if (!jsonMan.checkIfJsonFileIsAvailable()) { //check if a JSON File is already there in the storage
                jsonMan.createJsonFile(); //create a JSON file
            }
            if (!jsonMan.checkIfSavefolderIsAvailable()) { //check if a folder for the audio files is already there in the storage
                jsonMan.createSaveFolder(); //create a folder for the audio files
            }
        }

        //NotificationHelper.cancelStatusNotification(); //cancel the onHold notification
        NotificationHelper.activateScanningStatusNotification(getApplicationContext()); //start the scanning-status notification
        setGUIStateStarted();
        detector.startScanning(); //start scanning for signals
    }

    /***
     * Called when clicking on the stop button
     */
    public void stopApplicationProcesses(){
        //NotificationHelper.mNotificationManager.cancelAll(); //cancel all active notifications
        NotificationHelper.activateOnHoldStatusNotification(getApplicationContext()); //activate only the onHold-status notification again
        detector.pause(); // stop scanning
        alert.cancel();
        Spoofer spoof = Spoofer.getInstance(); //get a spoofing object
        spoof.stopSpoofingComplete(); //stop the whole spoofing process
        MicCapture micCap = MicCapture.getInstance(); //get a microphone capture object
        micCap.stopMicCapturingComplete(); //stop the whole capturing process via the microphone
        usedBlockingMethod = null;

        setGUIStateStopped();
        SharedPreferences sp = getSettingsObject();
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(ConfigConstants.PREFERENCES_APP_STATE, StateEnum.ON_HOLD.toString());
        ed.apply();
    }

    public void setGUIStateStopped() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnStart.setBackgroundColor(0XFFAAAAAA);
                btnStart.setImageResource(R.drawable.ic_play_arrow_white_48dp);
                btnStop.setBackgroundColor(0XFFD4D4D4);
                btnStop.setImageResource(R.drawable.ic_pause_blue_48dp);

                btnStart.setEnabled(true); //enable the start button again
                btnStop.setEnabled(false); //disable the stop button
            }
        });
    }

    public void setGUIStateStarted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnStart.setBackgroundColor(0XFFD4D4D4);
                btnStart.setImageResource(R.drawable.ic_play_arrow_blue_48dp);
                btnStop.setBackgroundColor(0XFFAAAAAA);
                btnStop.setImageResource(R.drawable.ic_pause_white_48dp);

                btnStart.setEnabled(false); //enable the start button again
                btnStop.setEnabled(true); //disable the stop button
            }
        });
    }

    public void openStoredLocations(){
        if (jsonMan.checkIfJsonFileIsAvailable()) {
            Intent myIntent = new Intent(MainActivity.this, StoredLocations.class); //redirect to the stored locations activity
            startActivityForResult(myIntent, 0);
        }
        else {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setTitle(R.string.alert_no_json_file_title)
                    .setMessage(R.string.alert_no_json_file_message)
                    .setPositiveButton(R.string.alert_no_json_file_positive, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            getSettingsObject().edit().putBoolean(ConfigConstants.SETTING_SAVE_DATA_TO_JSON_FILE, true).apply();
                            jsonMan.createJsonFile();
                            Intent myIntent = new Intent(MainActivity.this, StoredLocations.class); //redirect to the stored locations activity
                            startActivityForResult(myIntent, 0);
                        }
                    })
                    .setNegativeButton(R.string.alert_no_json_file_negative, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    })
                    .create()
            .show();
        }
    }

    public void openSettings(){
        Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class); //redirect to the settings activity
        startActivityForResult(myIntent, 0);
        String uniqueID = UUID.randomUUID().toString();
        //Log.d("UUID", uniqueID);
    }

    public void displayToast(final String toastText,final int duration){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mainIsMain, toastText,duration).show();
            }
        });
    }

    public void onAlertChoice(int spoofDecision) {
        if(usedBlockingMethod != null) {
            stopAutomaticBlockingMethodOnAction();
        }

        // TODO: Function to be called in a thread, for IO (save json entry)

        saveJsonFile = checkJsonAndLocationPermissions()[0];
        boolean locationTrack = checkJsonAndLocationPermissions()[1];

        if(saveJsonFile && locationTrack) {
            //Log.d("SearchForJson", "addWithLoc");
            double[] detectedSignalPosition = locationFinder.getDetectedDBEntry();
            jsonMan.addJsonObject(detectedSignalPosition, sigType.toString(), spoofDecision, locationFinder.getDetectedDBEntryAddres()); //adding the found signal in the JSON file
            if (detectedSignalPosition[0] == 0 && detectedSignalPosition[1] == 0) {
                Toast toast = Toast.makeText(MainActivity.this, R.string.toast_no_location_text, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        }
        if(saveJsonFile&&!locationTrack){
            //showToastOnNoLocation(); NOTE: Already shown in the Always options
            double[] noLocation = new double[2];
            noLocation[0] = 0;
            noLocation[1] = 0;
            //Log.d("SearchForJson", "addWithoutLoc");
            jsonMan.addJsonObject(noLocation, sigType.toString(), spoofDecision, getString(R.string.noAddressForJsonFile));
        }
        alert.cancel(); //cancel the alert dialog
        txtSignalType.setText(""); //can be deleted it's only for debugging
        NotificationHelper.cancelDetectionAlertStatusNotification(getApplicationContext());

        // Clean the technology on disk
        SharedPreferences sp = getSettingsObject();
        SharedPreferences.Editor ed = sp.edit();
        ed.remove("lastDetectedTechnology");
        ed.apply();
    }

    public void onAlertPlayDetectedSignal(){
        if (sigPlayer == null && !isSignalPlayerGenerated){ //if no player for the signal is created yet and the boolean for generating is also false
            btnAlertReplay.setText(R.string.ButtonStopSignal); //set the button for playing/stopping to "stop"
            sigPlayer = locationFinder.generatePlayer(); //create a new player
            isSignalPlayerGenerated = true; //player is generated so it's true
            sigPlayer.play(); //start the player
        }else if(sigPlayer!=null && isSignalPlayerGenerated){ //if a player for the signal is created and the boolean for generating is true
            sigPlayer.stop(); //stop the player
            sigPlayer.release(); //release the resources of the player
            sigPlayer = null; //set the player variable to null
            btnAlertReplay.setText(R.string.ButtonPlaySignal); //set the button for playing/stopping to "play"
            isSignalPlayerGenerated = false; //now there is no player anymore so it's false
        }
        txtSignalType.setText(sigType.toString()); //can be deleted it's only for debugging
    }

    public void onAlertBlockAlways(){
        onAlertChoice(ConfigConstants.DETECTION_TYPE_ALWAYS_BLOCKED_HERE);
        showToastOnNoLocation();
        checkForActivatedLocation();
        locationFinder.blockMicOrSpoof();
        NotificationHelper.activateSpoofingStatusNotification(getApplicationContext()); //activates the notification for the spoofing process
    }

    public void onAlertBlockThisTime(){
        onAlertChoice(ConfigConstants.DETECTION_TYPE_THIS_TIME);
        locationFinder.blockMicOrSpoof();
        NotificationHelper.activateSpoofingStatusNotification(getApplicationContext());
    }

    public void onAlertDismissAlways(){
        onAlertChoice(ConfigConstants.DETECTION_TYPE_ALWAYS_DISMISSED_HERE);
        showToastOnNoLocation();
        checkForActivatedLocation();
        detector.startScanning(); //start scanning again
        NotificationHelper.activateScanningStatusNotification(getApplicationContext()); //activates the notification for the scanning process
    }

    public void onAlertDismissThisTime(){
        onAlertChoice(ConfigConstants.DETECTION_TYPE_THIS_TIME);
        detector.startScanning(); //start scanning again
        NotificationHelper.activateScanningStatusNotification(getApplicationContext()); //activates the notification for the scanning process
    }

    public void onFirstOpeningShowWelcome(){
        new AlertDialog.Builder(this).setTitle(R.string.instructionsTitle).setMessage(R.string.instructionsText)
            /*.setNeutralButton("Instuctions", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                checkFirstStartForInstructionsShowing();
            }
        })*/
            .setPositiveButton("OK", null).show();
    }

    public void checkFirstRunForWelcomeShowing() {
        SharedPreferences sp = getSettingsObject();
        boolean isFirstRun = sp.getBoolean("isFirstRun", true);
        //Log.d("I am in First run", String.valueOf(isFirstRun));
        if (isFirstRun){
            onFirstOpeningShowWelcome();
            sp
                    .edit()
                    .putBoolean("isFirstRun", false)
                    .apply();
        }
    }

    /*
    public void onFirstStartShowInstructions(){
        final Dialog instructionsDialog = new Dialog(this);
        instructionsDialog.setContentView(R.layout.about_us_activity);
        instructionsDialog.setTitle("Instructions");
        instructionsDialog.show();
    }

    public void checkFirstStartForInstructionsShowing() {
        SharedPreferences sp = getSettingsObject();
        boolean isFirstStarted = sp.getBoolean("isFirstStarted", true);
        Log.d("I am in First start", String.valueOf(isFirstStarted));
        if (isFirstStarted){
            onFirstStartShowInstructions();
            sp
                    .edit()
                    .putBoolean("isFirstStarted", false)
                    .apply();
        }
    }*/

/*
    public void updateDistance(final double distance) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView txtDistance = (TextView) mainIsMain.findViewById(R.id.txtDistance); //can be deleted only for debugging
                txtDistance.setText(String.valueOf(distance*1000)); //can be deleted only for debugging
            }
        });
    }*/

    public void checkForActivatedLocation(){
        locationManager = (LocationManager) this.getApplicationContext().getSystemService(LOCATION_SERVICE);
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        boolean locationTrackGps = sharedPref.getBoolean(ConfigConstants.SETTING_GPS, ConfigConstants.SETTING_GPS_DEFAULT);
        boolean locationTrackNet = sharedPref.getBoolean(ConfigConstants.SETTING_NETWORK_USE, ConfigConstants.SETTING_NETWORK_USE_DEFAULT);

        int status = ActivityCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION);

        if(locationTrackGps || locationTrackNet){
            if((!(isGPSEnabled && locationTrackGps) && !(isNetworkEnabled && locationTrackNet)) || status != PackageManager.PERMISSION_GRANTED){
                activateAlertNoLocationEnabled();
            }else{
                /*Toast toast = Toast.makeText(MainActivity.this, R.string.toast_location_is_on, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER,0,0);
                toast.show();*/
            }
        }
    }

    public void activateAlertNoLocationEnabled() {
        boolean skipMessage = sharedPref.getBoolean(ConfigConstants.SETTINGS_ALERT_LOCATION_IS_OFF_DONT_ASK_AGAIN, ConfigConstants.SETTINGS_ALERT_LOCATION_IS_OFF_DONT_ASK_AGAIN_DEFAULT);
        if (!skipMessage) {
            final AlertDialog.Builder activateLocationDialog = new AlertDialog.Builder(this);
            LayoutInflater locationAlertInflater = LayoutInflater.from(this);
            View cbLocationAlertLayout = locationAlertInflater.inflate(R.layout.alert_checkbox, null);
            final CheckBox dontShowAgain = (CheckBox)cbLocationAlertLayout.findViewById(R.id.cbDontAskAgain);
            activateLocationDialog.setView(cbLocationAlertLayout);
            activateLocationDialog.setTitle(R.string.alert_location_is_off_title)
                    .setMessage(R.string.alert_location_is_off_message)
                    .setCancelable(true)
                    .setPositiveButton(R.string.alert_location_is_off_positive, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (dontShowAgain.isChecked()) {
                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.putBoolean(ConfigConstants.SETTINGS_ALERT_LOCATION_IS_OFF_DONT_ASK_AGAIN, true);
                                editor.apply();
                            }
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.alert_location_is_off_negative, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (dontShowAgain.isChecked()) {
                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.putBoolean(ConfigConstants.SETTINGS_ALERT_LOCATION_IS_OFF_DONT_ASK_AGAIN, true);
                                editor.apply();
                            }
                            alertLocation.cancel();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_info);

            alertLocation = activateLocationDialog.show();
        }
    }

    public void showToastOnNoLocation(){
        locationManager = (LocationManager) this.getApplicationContext().getSystemService(LOCATION_SERVICE);
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        boolean locationTrackGps = sharedPref.getBoolean(ConfigConstants.SETTING_GPS, ConfigConstants.SETTING_GPS_DEFAULT);
        boolean locationTrackNet = sharedPref.getBoolean(ConfigConstants.SETTING_NETWORK_USE, ConfigConstants.SETTING_NETWORK_USE_DEFAULT);

        int status = ActivityCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION);

        if(locationTrackGps || locationTrackNet){
            if((!(isGPSEnabled && locationTrackGps) && !(isNetworkEnabled && locationTrackNet)) || status != PackageManager.PERMISSION_GRANTED){
                Toast toast = Toast.makeText(MainActivity.this, R.string.toast_no_location_text, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        }else{
            Toast toast = Toast.makeText(MainActivity.this, R.string.toast_no_location_text, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }
    }

    public String getTimeAndDateForAlert(){
        Long currentTime = Calendar.getInstance().getTimeInMillis();
        SimpleDateFormat rightFormat = new SimpleDateFormat("HH:mm:ss");
        SimpleDateFormat leftFormat = new SimpleDateFormat("yyyy-MM-dd");
        String dateLeft = String.valueOf(leftFormat.format(currentTime));
        String dateRight = String.valueOf(rightFormat.format(currentTime));
        String dateString = " " + dateRight + " - " + dateLeft;
        return dateString;
    }

    public void activateNoMicrophoneAccessAlertDialog(){
        final AlertDialog.Builder activateLocationDialog = new AlertDialog.Builder(this);
        activateLocationDialog.setTitle(R.string.alert_microphone_already_used_title)
                .setMessage(R.string.alert_microphone_already_used_message)
                .setCancelable(true)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        activateLocationDialog.show();
    }


    public static boolean isLocationEnabled(Context context) {
        int locationMode = 0;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }

            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        }else{
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }


    }
}
