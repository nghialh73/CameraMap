package tools.dslr.hdcamera;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.renderscript.RenderScript;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ZoomControls;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import tools.dslr.hdcamera.CameraController.Controller;
import tools.dslr.hdcamera.CameraController.ControllerManager2;
import tools.dslr.hdcamera.Preview.Preview;
import tools.dslr.hdcamera.UI.FolderChooserDialog;
import tools.dslr.hdcamera.UI.MainUI;
import tools.dslr.hdcamera.UI.location.LocationActivity;

public class HomeActivity extends AppCompatActivity implements AudioListener.AudioListenerCallback, LocationAddressListener, OnMapReadyCallback {

    private static final String TAG = "HomeActivity";
    private SensorManager sensormanager = null;
    private Sensor sensoraccelerometer = null;
    private Sensor MagneticSensor = null;
    private MainUI mainui = null;
    private MyApplicationInterface applicationInterface = null;
    private Preview preview = null;
    private OrientationEventListener orientationEventListener = null;
    private boolean supports_auto_stabilise = false;
    private boolean supports_force_video_4k = false;
    private boolean supports_camera2 = false;
    private LocationHistory save_location_history = null; // save location for non-SAF
    private LocationHistory save_location_history_saf = null; // save location for SAF (only initialised when SAF is used)
    private boolean saf_dialog_from_preferences = false; // if a SAF dialog is opened, this records whether we opened it from the Preferences
    private boolean camera_in_background = false; // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
    private GestureDetector gestureDetector;
    private boolean screen_is_locked = false; // whether screen is "locked" - this is Open Camera's own lock to guard against accidental presses, not the standard Android lock
    private Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<Integer, Bitmap>();
    private ValueAnimator gallery_save_anim = null;
    private SoundPool sound_pool = null;
    private SparseIntArray sound_ids = null;

    private TextToSpeech textToSpeech = null;
    private boolean textToSpeechSuccess = false;

    private AudioListener audio_listener = null;
    private int audio_noise_sensitivity = -1;
    private SpeechRecognizer speechRecognizer = null;
    private boolean speechRecognizerIsStarted = false;

    //private boolean ui_placement_right = true;

    private ToastBoxer switch_video_toast = new ToastBoxer();
    private ToastBoxer screen_locked_toast = new ToastBoxer();
    private ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();
    private ToastBoxer exposure_lock_toast = new ToastBoxer();
    private ToastBoxer audio_control_toast = new ToastBoxer();
    private boolean block_startup_toast = false; // used when returning from Settings/Popup - if we're displaying a toast anyway, don't want to display the info toast too

    private boolean keydown_volume_up = false;
    private boolean keydown_volume_down = false;

    // for testing:
    public boolean is_test = false; // whether called from OpenCamera.test testing
    public Bitmap g_bitmap = null;
    public boolean low_memory = false;
    public boolean have_angle = false;
    public float angle = 0.0f;
    public String last_saved_image = null;

    private TextView addressText;
    private TextView latText;
    private TextView lonText;
    private TextView dateTimeText;

    int fbads;
    private Context con;
    public static final String EXIT_ACTIVITY = "EXIT";
    private Exit_Receiver ExitReceiver;
    String APPLE_ADS_PATH;
    private MapView mMapView;
    private GoogleMap mGoogleMap;

    private Location mLocation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long debug_time = 0;
        if (Debug.LOG) {
            Log.d(TAG, "onCreate");
            debug_time = System.currentTimeMillis();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // initialise any unset preferences to their default values

        AppleSetContentView();

        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after setting default preference values: " + (System.currentTimeMillis() - debug_time));

        if (getIntent() != null && getIntent().getExtras() != null) {
            // whether called from testing
            is_test = getIntent().getExtras().getBoolean("test_project");
            if (Debug.LOG)
                Log.d(TAG, "is_test: " + is_test);
        }
        if (getIntent() != null && getIntent().getExtras() != null) {
            // whether called from Take Photo widget
            boolean take_photo = getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
            if (Debug.LOG)
                Log.d(TAG, "take_photo?: " + take_photo);
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // determine whether we should support "auto stabilise" feature
        // risk of running out of memory on lower end devices, due to manipulation of large bitmaps
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (Debug.LOG) {
            Log.d(TAG, "standard max memory = " + activityManager.getMemoryClass() + "MB");
            Log.d(TAG, "large max memory = " + activityManager.getLargeMemoryClass() + "MB");
        }
        //if( activityManager.getMemoryClass() >= 128 ) { // test
        if (activityManager.getLargeMemoryClass() >= 128) {
            supports_auto_stabilise = true;
        }
        if (Debug.LOG)
            Log.d(TAG, "supports_auto_stabilise? " + supports_auto_stabilise);

        // hack to rule out phones unlikely to have 4K video, so no point even offering the option!
        // both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL), as does Galaxy K Zoom
        // also added the check for having 128MB standard heap, to support modded LG G2, which has 128MB standard, 256MB large - see https://sourceforge.net/p/opencamera/tickets/9/
        if (activityManager.getMemoryClass() >= 128 || activityManager.getLargeMemoryClass() >= 512) {
            supports_force_video_4k = true;
        }
        if (Debug.LOG)
            Log.d(TAG, "supports_force_video_4k? " + supports_force_video_4k);

        // set up components
        mainui = new MainUI(this);
        applicationInterface = new MyApplicationInterface(this, savedInstanceState);
        initLocation();
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after creating application interface: " + (System.currentTimeMillis() - debug_time));

        // determine whether we support Camera2 API
        initCamera2Support();

        // set up window flags for normal operation
        setWindowFlagsForCamera();
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after setting window flags: " + (System.currentTimeMillis() - debug_time));

        save_location_history = new LocationHistory(this, "save_location_history", getStorageUtils().getSaveLocation());
        if (applicationInterface.getStorage().isUsingSAF()) {
            if (Debug.LOG)
                Log.d(TAG, "create new LocationHistory for SAF");
            save_location_history_saf = new LocationHistory(this, "save_location_history_saf", getStorageUtils().getSaveLocationSAF());
        }
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after updating folder history: " + (System.currentTimeMillis() - debug_time));

        // set up sensors
        sensormanager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // accelerometer sensor (for device orientation)
        if (sensormanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            if (Debug.LOG)
                Log.d(TAG, "found accelerometer");
            sensoraccelerometer = sensormanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        } else {
            if (Debug.LOG)
                Log.d(TAG, "no support for accelerometer");
        }
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after creating accelerometer sensor: " + (System.currentTimeMillis() - debug_time));

        // magnetic sensor (for compass direction)
        if (sensormanager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            if (Debug.LOG)
                Log.d(TAG, "found magnetic sensor");
            MagneticSensor = sensormanager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        } else {
            if (Debug.LOG)
                Log.d(TAG, "no support for magnetic sensor");
        }
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after creating magnetic sensor: " + (System.currentTimeMillis() - debug_time));

        // clear any seek bars (just in case??)
        mainui.clearSeekBar();

        // set up the camera and its preview
        preview = new Preview(applicationInterface, savedInstanceState, ((ViewGroup) this.findViewById(R.id.preview)));
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after creating preview: " + (System.currentTimeMillis() - debug_time));

        // initialise on-screen button visibility
        View switchCameraButton = (View) findViewById(R.id.switch_camera);
        switchCameraButton.setVisibility(preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE);
        View speechRecognizerButton = (View) findViewById(R.id.audio_control);
        speechRecognizerButton.setVisibility(View.GONE); // disabled by default, until the speech recognizer is created
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after setting button visibility: " + (System.currentTimeMillis() - debug_time));

        // listen for orientation event change
        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                HomeActivity.this.mainui.onOrientationChanged(orientation);
            }
        };
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after setting orientation event listener: " + (System.currentTimeMillis() - debug_time));

        // set up gallery button long click
        View galleryButton = (View) findViewById(R.id.gallery);
        galleryButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //preview.showToast(null, "Long click");
                longClickedGallery();
                return true;
            }
        });
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after setting gallery long click listener: " + (System.currentTimeMillis() - debug_time));

        //setup address view
        getLocation();

        mMapView = findViewById(R.id.map_view);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(this);

        // listen for gestures
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after creating gesture detector: " + (System.currentTimeMillis() - debug_time));

        // set up listener to handle immersive mode options
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        // Note that system bars will only be "visible" if none of the
                        // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                        if (!usingKitKatImmersiveMode())
                            return;
                        if (Debug.LOG)
                            Log.d(TAG, "onSystemUiVisibilityChange: " + visibility);
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            if (Debug.LOG)
                                Log.d(TAG, "system bars now visible");
                            // The system bars are visible. Make any desired
                            // adjustments to your UI, such as showing the action bar or
                            // other navigational controls.
                            mainui.setImmersiveMode(false);
                            setImmersiveTimer();
                        } else {
                            if (Debug.LOG)
                                Log.d(TAG, "system bars now NOT visible");
                            // The system bars are NOT visible. Make any desired
                            // adjustments to your UI, such as hiding the action bar or
                            // other navigational controls.
                            mainui.setImmersiveMode(true);
                        }
                    }
                });
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after setting immersive mode listener: " + (System.currentTimeMillis() - debug_time));

        // show "about" dialog for first time use; also set some per-device defaults
        boolean has_done_first_time = sharedPreferences.contains(Keys.getFirstTimePreferenceKey());
        if (!has_done_first_time) {
            boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
            boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
            if (Debug.LOG) {
                Log.d(TAG, "running for first time");
                Log.d(TAG, "is_samsung? " + is_samsung);
                Log.d(TAG, "is_oneplus? " + is_oneplus);
            }
            if (is_samsung || is_oneplus) {
                // workaround needed for Samsung S7 at least (tested on Samsung RTL)
                // workaround needed for OnePlus 3 at least (see http://forum.xda-developers.com/oneplus-3/help/camera2-support-t3453103 )
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(Keys.getCamera2FakeFlashPreferenceKey(), true);
                editor.apply();
            }
        }
        if (!has_done_first_time && !is_test) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle(R.string.app_name);
            alertDialog.setMessage(R.string.intro_text);
            alertDialog.setPositiveButton(R.string.intro_ok, null);
            alertDialog.show();

            setFirstTimeFlag();
        }

        // load icons
        preloadIcons(R.array.flash_icons);
        preloadIcons(R.array.focus_mode_icons);
        if (Debug.LOG)
            Log.d(TAG, "onCreate: time after preloading icons: " + (System.currentTimeMillis() - debug_time));

        // initialise text to speech engine
        textToSpeechSuccess = false;
        // run in separate thread so as to not delay startup time
        new Thread(new Runnable() {
            public void run() {
                textToSpeech = new TextToSpeech(HomeActivity.this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (Debug.LOG)
                            Log.d(TAG, "TextToSpeech initialised");
                        if (status == TextToSpeech.SUCCESS) {
                            textToSpeechSuccess = true;
                            if (Debug.LOG)
                                Log.d(TAG, "TextToSpeech succeeded");
                        } else {
                            if (Debug.LOG)
                                Log.d(TAG, "TextToSpeech failed");
                        }
                    }
                });
            }
        }).start();

        if (Debug.LOG)
            Log.d(TAG, "onCreate: total time for Activity startup: " + (System.currentTimeMillis() - debug_time));
    }

    void getLocation() {
        Location location = getLocationSupplier().getLocation();
        mLocation = location;
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        if (mGoogleMap != null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 13f));
            mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)));
        }
        getLocationSupplier().getAddressFromLocation(lat, lon, this);
    }

    private void updateAddress(double lat, double lon, String address) {
        addressText = findViewById(R.id.address);
        latText = findViewById(R.id.lat);
        lonText = findViewById(R.id.lon);
        dateTimeText = findViewById(R.id.timezone);
        latText.setText(lat + "\u00B0");
        lonText.setText(lon + "\u00B0");
        addressText.setText(address);
        TimeZone tz = TimeZone.getDefault();
        dateTimeText.setText(tz.getDisplayName(false, TimeZone.SHORT));
    }

    /**
     * Determine whether we support Camera2 API.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initCamera2Support() {
        if (Debug.LOG)
            Log.d(TAG, "initCamera2Support");
        supports_camera2 = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ControllerManager2 manager2 = new ControllerManager2(this);
            supports_camera2 = true;
            if (manager2.getNumberOfCameras() == 0) {
                if (Debug.LOG)
                    Log.d(TAG, "Camera2 reports 0 cameras");
                supports_camera2 = false;
            }
            for (int i = 0; i < manager2.getNumberOfCameras() && supports_camera2; i++) {
                if (!manager2.allowCamera2Support(i)) {
                    if (Debug.LOG)
                        Log.d(TAG, "camera " + i + " doesn't have limited or full support for Camera2 API");
                    supports_camera2 = false;
                }
            }
        }
        if (Debug.LOG)
            Log.d(TAG, "supports_camera2? " + supports_camera2);
    }

    private void preloadIcons(int icons_id) {
        long debug_time = 0;
        if (Debug.LOG) {
            Log.d(TAG, "preloadIcons: " + icons_id);
            debug_time = System.currentTimeMillis();
        }
        String[] icons = getResources().getStringArray(icons_id);
        for (int i = 0; i < icons.length; i++) {
            int resource = getResources().getIdentifier(icons[i], null, this.getApplicationContext().getPackageName());
            if (Debug.LOG)
                Log.d(TAG, "load resource: " + resource);
            Bitmap bm = BitmapFactory.decodeResource(getResources(), resource);
            this.preloaded_bitmap_resources.put(resource, bm);
        }
        if (Debug.LOG) {
            Log.d(TAG, "preloadIcons: total time for preloadIcons: " + (System.currentTimeMillis() - debug_time));
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onDestroy() {
        if (Debug.LOG) {
            Log.d(TAG, "onDestroy");
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
        }
        if (applicationInterface != null) {
            applicationInterface.onDestroy();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // see note in HDR.onDestroy() - but from Android M, renderscript contexts are released with releaseAllContexts()
            // doc for releaseAllContexts() says "If no contexts have been created this function does nothing"
            RenderScript.releaseAllContexts();
        }
        // Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
        for (Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
            if (Debug.LOG)
                Log.d(TAG, "recycle: " + entry.getKey());
            entry.getValue().recycle();
        }
        preloaded_bitmap_resources.clear();
        if (textToSpeech != null) {
            // http://stackoverflow.com/questions/4242401/tts-error-leaked-serviceconnection-android-speech-tts-texttospeech-solved
            Log.d(TAG, "free textToSpeech");
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
        mMapView.onDestroy();
        unregisterReceiver(ExitReceiver);

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void setFirstTimeFlag() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Keys.getFirstTimePreferenceKey(), true);
        editor.apply();
    }

    // for audio "noise" trigger option
    private int last_level = -1;
    private long time_quiet_loud = -1;
    private long time_last_audio_trigger_photo = -1;

    /**
     * Listens to audio noise and decides when there's been a "loud" noise to trigger taking a photo.
     */
    public void onAudio(int level) {
        boolean audio_trigger = false;
		/*if( level > 150 ) {
			if( Debug.LOG )
				Log.d(TAG, "loud noise!: " + level);
			audio_trigger = true;
		}*/

        if (last_level == -1) {
            last_level = level;
            return;
        }
        int diff = level - last_level;

        if (Debug.LOG)
            Log.d(TAG, "noise_sensitivity: " + audio_noise_sensitivity);

        if (diff > audio_noise_sensitivity) {
            if (Debug.LOG)
                Log.d(TAG, "got louder!: " + last_level + " to " + level + " , diff: " + diff);
            time_quiet_loud = System.currentTimeMillis();
            if (Debug.LOG)
                Log.d(TAG, "    time: " + time_quiet_loud);
        } else if (diff < -audio_noise_sensitivity && time_quiet_loud != -1) {
            if (Debug.LOG)
                Log.d(TAG, "got quieter!: " + last_level + " to " + level + " , diff: " + diff);
            long time_now = System.currentTimeMillis();
            long duration = time_now - time_quiet_loud;
            if (Debug.LOG) {
                Log.d(TAG, "stopped being loud - was loud since :" + time_quiet_loud);
                Log.d(TAG, "    time_now: " + time_now);
                Log.d(TAG, "    duration: " + duration);
                if (duration < 1500) {
                    audio_trigger = true;
                }
            }
            time_quiet_loud = -1;
        } else {
            if (Debug.LOG)
                Log.d(TAG, "audio level: " + last_level + " to " + level + " , diff: " + diff);
        }

        last_level = level;

        if (audio_trigger) {
            if (Debug.LOG)
                Log.d(TAG, "audio trigger");
            // need to run on UI thread so that this function returns quickly (otherwise we'll have lag in processing the audio)
            // but also need to check we're not currently taking a photo or on timer, so we don't repeatedly queue up takePicture() calls, or cancel a timer
            long time_now = System.currentTimeMillis();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean want_audio_listener = sharedPreferences.getString(Keys.getAudioControlPreferenceKey(), "none").equals("noise");
            if (time_last_audio_trigger_photo != -1 && time_now - time_last_audio_trigger_photo < 5000) {
                // avoid risk of repeatedly being triggered - as well as problem of being triggered again by the camera's own "beep"!
                if (Debug.LOG)
                    Log.d(TAG, "ignore loud noise due to too soon since last audio triggerred photo:" + (time_now - time_last_audio_trigger_photo));
            } else if (!want_audio_listener) {
                // just in case this is a callback from an AudioListener before it's been freed (e.g., if there's a loud noise when exiting settings after turning the option off
                if (Debug.LOG)
                    Log.d(TAG, "ignore loud noise due to audio listener option turned off");
            } else {
                if (Debug.LOG)
                    Log.d(TAG, "audio trigger from loud noise");
                time_last_audio_trigger_photo = time_now;
                audioTrigger();
            }
        }
    }

    /* Audio trigger - either loud sound, or speech recognition.
     * This performs some additional checks before taking a photo.
     */
    private void audioTrigger() {
        if (Debug.LOG)
            Log.d(TAG, "ignore audio trigger due to popup open");
        if (popupIsOpen()) {
            if (Debug.LOG)
                Log.d(TAG, "ignore audio trigger due to popup open");
        } else if (camera_in_background) {
            if (Debug.LOG)
                Log.d(TAG, "ignore audio trigger due to camera in background");
        } else if (preview.isTakingPhotoOrOnTimer()) {
            if (Debug.LOG)
                Log.d(TAG, "ignore audio trigger due to already taking photo or on timer");
        } else {
            if (Debug.LOG)
                Log.d(TAG, "schedule take picture due to loud noise");
            //takePicture();
            this.runOnUiThread(new Runnable() {
                public void run() {
                    if (Debug.LOG)
                        Log.d(TAG, "taking picture due to audio trigger");
                    takePicture();
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Debug.LOG)
            Log.d(TAG, "onKeyDown: " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: // media codes are for "selfie sticks" buttons
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP: {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                    keydown_volume_up = true;
                else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                    keydown_volume_down = true;

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                String volume_keys = sharedPreferences.getString(Keys.getVolumeKeysPreferenceKey(), "volume_take_photo");

                if ((keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        || keyCode == KeyEvent.KEYCODE_MEDIA_STOP)
                        && !(volume_keys.equals("volume_take_photo"))) {
                    AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager == null) break;
                    if (!audioManager.isWiredHeadsetOn())
                        break; // isWiredHeadsetOn() is deprecated, but comment says "Use only to check is a headset is connected or not."
                }

                if (volume_keys.equals("volume_take_photo")) {
                    takePicture();
                    return true;
                } else if (volume_keys.equals("volume_focus")) {
                    if (keydown_volume_up && keydown_volume_down) {
                        if (Debug.LOG)
                            Log.d(TAG, "take photo rather than focus, as both volume keys are down");
                        takePicture();
                    } else if (preview.getCurrentFocusValue() != null && preview.getCurrentFocusValue().equals("focus_mode_manual2")) {
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                            this.changeFocusDistance(-1);
                        else
                            this.changeFocusDistance(1);
                    } else {
                        // important not to repeatedly request focus, even though preview.requestAutoFocus() will cancel, as causes problem if key is held down (e.g., flash gets stuck on)
                        // also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down
                        if (event.getDownTime() == event.getEventTime() && !preview.isFocusWaiting()) {
                            if (Debug.LOG)
                                Log.d(TAG, "request focus due to volume key");
                            preview.requestAutoFocus();
                        }
                    }
                    return true;
                } else if (volume_keys.equals("volume_zoom")) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                        this.zoomIn();
                    else
                        this.zoomOut();
                    return true;
                } else if (volume_keys.equals("volume_exposure")) {
                    if (preview.getCameraController() != null) {
                        String value = sharedPreferences.getString(Keys.getISOPreferenceKey(), preview.getCameraController().getDefaultISO());
                        boolean manual_iso = !value.equals(preview.getCameraController().getDefaultISO());
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            if (manual_iso) {
                                if (preview.supportsISORange())
                                    this.changeISO(1);
                            } else
                                this.changeExposure(1);
                        } else {
                            if (manual_iso) {
                                if (preview.supportsISORange())
                                    this.changeISO(-1);
                            } else
                                this.changeExposure(-1);
                        }
                    }
                    return true;
                } else if (volume_keys.equals("volume_auto_stabilise")) {
                    if (this.supports_auto_stabilise) {
                        boolean auto_stabilise = sharedPreferences.getBoolean(Keys.getAutoStabilisePreferenceKey(), false);
                        auto_stabilise = !auto_stabilise;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(Keys.getAutoStabilisePreferenceKey(), auto_stabilise);
                        editor.apply();
                        String message = getResources().getString(R.string.preference_auto_stabilise) + ": " + getResources().getString(auto_stabilise ? R.string.on : R.string.off);
                        preview.showToast(changed_auto_stabilise_toast, message);
                    } else {
                        preview.showToast(changed_auto_stabilise_toast, R.string.auto_stabilise_not_supported);
                    }
                    return true;
                } else if (volume_keys.equals("volume_really_nothing")) {
                    // do nothing, but still return true so we don't change volume either
                    return true;
                }
                // else do nothing here, but still allow changing of volume (i.e., the default behaviour)
                break;
            }
            case KeyEvent.KEYCODE_MENU: {
                // needed to support hardware menu button
                // tested successfully on Samsung S3 (via RTL)
                // see http://stackoverflow.com/questions/8264611/how-to-detect-when-user-presses-menu-key-on-their-android-device
                openSettings();
                return true;
            }
            case KeyEvent.KEYCODE_CAMERA: {
                if (event.getRepeatCount() == 0) {
                    takePicture();
                    return true;
                }
            }
            case KeyEvent.KEYCODE_FOCUS: {
                // important not to repeatedly request focus, even though preview.requestAutoFocus() will cancel - causes problem with hardware camera key where a half-press means to focus
                // also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down - see https://sourceforge.net/p/opencamera/tickets/174/ ,
                // or same issue above for volume key focus
                if (event.getDownTime() == event.getEventTime() && !preview.isFocusWaiting()) {
                    if (Debug.LOG)
                        Log.d(TAG, "request focus due to focus key");
                    preview.requestAutoFocus();
                }
                return true;
            }
            case KeyEvent.KEYCODE_ZOOM_IN: {
                this.zoomIn();
                return true;
            }
            case KeyEvent.KEYCODE_ZOOM_OUT: {
                this.zoomOut();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


    public boolean onKeyUp(int keyCode, KeyEvent event) {
        fbads = 4;
        return super.onKeyUp(keyCode, event);
    }

    public void zoomIn() {
        mainui.changeSeekbar(R.id.zoom_seekbar, -1);
    }

    public void zoomOut() {
        mainui.changeSeekbar(R.id.zoom_seekbar, 1);
    }

    public void changeExposure(int change) {
        mainui.changeSeekbar(R.id.exposure_seekbar, change);
    }

    public void changeISO(int change) {
        mainui.changeSeekbar(R.id.iso_seekbar, change);
    }

    void changeFocusDistance(int change) {
        mainui.changeSeekbar(R.id.focus_seekbar, change);
    }

    private SensorEventListener accelerometerListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            preview.onAccelerometerSensorChanged(event);
        }
    };

    private SensorEventListener magneticListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            preview.onMagneticSensorChanged(event);
        }
    };

    @Override
    protected void onResume() {
        long debug_time = 0;
        if (Debug.LOG) {
            Log.d(TAG, "onResume");
            debug_time = System.currentTimeMillis();
        }
        super.onResume();
        mMapView.onResume();
        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the PreferenceFragment settings
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);

        sensormanager.registerListener(accelerometerListener, sensoraccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensormanager.registerListener(magneticListener, MagneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
        orientationEventListener.enable();

        initSpeechRecognizer();
        initSound();
        loadSound(R.raw.beep);
        loadSound(R.raw.beep_hi);

        mainui.layoutUI();

        updateGalleryIcon(); // update in case images deleted whilst idle

        preview.onResume();

        if (Debug.LOG) {
            Log.d(TAG, "onResume: total time to resume: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (Debug.LOG)
            Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        if (!this.camera_in_background && hasFocus) {
            // low profile mode is cleared when app goes into background
            // and for Kit Kat immersive mode, we want to set up the timer
            // we do in onWindowFocusChanged rather than onResume(), to also catch when window lost focus due to notification bar being dragged down (which prevents resetting of immersive mode)
            initImmersiveMode();
        }
    }

    @Override
    protected void onPause() {
        long debug_time = 0;
        if (Debug.LOG) {
            Log.d(TAG, "onPause");
            debug_time = System.currentTimeMillis();
        }
        waitUntilImageQueueEmpty(); // so we don't risk losing any images
        super.onPause(); // docs say to call this before freeing other things
        mMapView.onPause();
        mainui.destroyPopup();
        sensormanager.unregisterListener(accelerometerListener);
        sensormanager.unregisterListener(magneticListener);
        orientationEventListener.disable();
        freeAudioListener(false);
        freeSpeechRecognizer();
        applicationInterface.getLocationSupplier().freeLocationListeners();
        releaseSound();
        applicationInterface.clearLastImages(); // this should happen when pausing the preview, but call explicitly just to be safe
        preview.onPause();
        if (Debug.LOG) {
            Log.d(TAG, "onPause: total time to pause: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (Debug.LOG)
            Log.d(TAG, "onConfigurationChanged()");
        // configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
        // needed if app is paused/resumed when settings is open and device is in portrait mode
        preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }

    public void waitUntilImageQueueEmpty() {
        if (Debug.LOG)
            Log.d(TAG, "waitUntilImageQueueEmpty");
        applicationInterface.getImageSaver().waitUntilDone();
    }

    public void clickedTakePhoto(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedTakePhoto");
        this.takePicture();
    }

    public void clickedAudioControl(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedAudioControl");
        // check hasAudioControl just in case!
        if (!hasAudioControl()) {
            if (Debug.LOG)
                Log.e(TAG, "clickedAudioControl, but hasAudioControl returns false!");
            return;
        }
        this.closePopup();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String audio_control = sharedPreferences.getString(Keys.getAudioControlPreferenceKey(), "none");
        if (audio_control.equals("voice") && speechRecognizer != null) {
            if (speechRecognizerIsStarted) {
                speechRecognizer.stopListening();
                speechRecognizerStopped();
            } else {
                preview.showToast(audio_control_toast, R.string.speech_recognizer_started);
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en_US"); // since we listen for "cheese", ensure this works even for devices with different language settings
                speechRecognizer.startListening(intent);
                speechRecognizerStarted();
            }
        } else if (audio_control.equals("noise")) {
            if (audio_listener != null) {
                freeAudioListener(false);
            } else {
                preview.showToast(audio_control_toast, R.string.audio_listener_started);
                startAudioListener();
            }
        }
    }

    private void speechRecognizerStarted() {
        if (Debug.LOG)
            Log.d(TAG, "speechRecognizerStarted");
        mainui.audioControlStarted();
        speechRecognizerIsStarted = true;
    }

    private void speechRecognizerStopped() {
        if (Debug.LOG)
            Log.d(TAG, "speechRecognizerStopped");
        mainui.audioControlStopped();
        speechRecognizerIsStarted = false;
    }

    /* Returns the cameraId that the "Switch camera" button will switch to.
     */
    public int getNextCameraId() {
        if (Debug.LOG)
            Log.d(TAG, "getNextCameraId");
        int cameraId = preview.getCameraId();
        if (Debug.LOG)
            Log.d(TAG, "current cameraId: " + cameraId);
        if (this.preview.canSwitchCamera()) {
            int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
            cameraId = (cameraId + 1) % n_cameras;
        }
        if (Debug.LOG)
            Log.d(TAG, "next cameraId: " + cameraId);
        return cameraId;
    }

    public void clickedSwitchCamera(View view) {
        fbads = 2;
        this.closePopup();
        if (this.preview.canSwitchCamera()) {
            int cameraId = getNextCameraId();
            View switchCameraButton = (View) findViewById(R.id.switch_camera);
            switchCameraButton.setEnabled(false); // prevent slowdown if user repeatedly clicks
            this.preview.setCamera(cameraId);
            switchCameraButton.setEnabled(true);
            mainui.setSwitchCameraContentDescription();
        }
    }


    public void clickedSwitchVideo(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedSwitchVideo");
        this.closePopup();
        View switchVideoButton = (View) findViewById(R.id.switch_video);
        switchVideoButton.setEnabled(false); // prevent slowdown if user repeatedly clicks
        this.preview.switchVideo(false);
        switchVideoButton.setEnabled(true);

        mainui.setTakePhotoIcon();
        if (!block_startup_toast) {
            this.showPhotoVideoToast(true);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void clickedExposure(View view) {
        fbads = 3;
        mainui.toggleExposureUI();
    }

    private static double seekbarScaling(double frac) {
        // For various seekbars, we want to use a non-linear scaling, so user has more control over smaller values
        double scaling = (Math.pow(100.0, frac) - 1.0) / 99.0;
        return scaling;
    }

    private static double seekbarScalingInverse(double scaling) {
        double frac = Math.log(99.0 * scaling + 1.0) / Math.log(100.0);
        return frac;
    }

    private void setProgressSeekbarScaled(SeekBar seekBar, double min_value, double max_value, double value) {
        seekBar.setMax(100);
        double scaling = (value - min_value) / (max_value - min_value);
        double frac = HomeActivity.seekbarScalingInverse(scaling);
        int percent = (int) (frac * 100.0 + 0.5); // add 0.5 for rounding
        if (percent < 0)
            percent = 0;
        else if (percent > 100)
            percent = 100;
        seekBar.setProgress(percent);
    }

    public void clickedExposureLock(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedExposureLock");
        this.preview.toggleExposureLock();
        ImageButton exposureLockButton = (ImageButton) findViewById(R.id.exposure_lock);
        exposureLockButton.setImageResource(preview.isExposureLocked() ? R.drawable.exposure_locked : R.drawable.exposure_unlocked);
        preview.showToast(exposure_lock_toast, preview.isExposureLocked() ? R.string.exposure_locked : R.string.exposure_unlocked);
    }

    public void clickedSettings(View view) {
        fbads = 1;
        openSettings();
    }

    public boolean popupIsOpen() {
        return mainui.popupIsOpen();
    }

    // for testing
    public View getPopupButton(String key) {
        return mainui.getPopupButton(key);
    }

    public void closePopup() {
        mainui.closePopup();
    }

    public Bitmap getPreloadedBitmap(int resource) {
        Bitmap bm = this.preloaded_bitmap_resources.get(resource);
        return bm;
    }

    public void clickedPopupSettings(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedPopupSettings");
        mainui.togglePopupSettings();
    }

    private void openSettings() {
        if (Debug.LOG)
            Log.d(TAG, "openSettings");
        waitUntilImageQueueEmpty(); // in theory not needed as we could continue running in the background, but best to be safe
        closePopup();
        preview.cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
        preview.stopVideo(false); // important to stop video, as we'll be changing camera parameters when the settings window closes
        stopAudioListeners();

        Bundle bundle = new Bundle();
        bundle.putInt("cameraId", this.preview.getCameraId());
        bundle.putString("camera_api", this.preview.getCameraAPI());
        bundle.putBoolean("using_android_l", this.preview.usingCamera2API());
        bundle.putBoolean("supports_auto_stabilise", this.supports_auto_stabilise);
        bundle.putBoolean("supports_force_video_4k", this.supports_force_video_4k);
        bundle.putBoolean("supports_camera2", this.supports_camera2);
        bundle.putBoolean("supports_face_detection", this.preview.supportsFaceDetection());
        bundle.putBoolean("supports_raw", this.preview.supportsRaw());
        bundle.putBoolean("supports_hdr", this.supportsHDR());
        bundle.putBoolean("supports_expo_bracketing", this.supportsExpoBracketing());
        bundle.putBoolean("supports_video_stabilization", this.preview.supportsVideoStabilization());
        bundle.putBoolean("can_disable_shutter_sound", this.preview.canDisableShutterSound());

        putBundleExtra(bundle, "color_effects", this.preview.getSupportedColorEffects());
        putBundleExtra(bundle, "scene_modes", this.preview.getSupportedSceneModes());
        putBundleExtra(bundle, "white_balances", this.preview.getSupportedWhiteBalances());
        putBundleExtra(bundle, "isos", this.preview.getSupportedISOs());
        bundle.putString("iso_key", this.preview.getISOKey());
        if (this.preview.getCameraController() != null) {
            bundle.putString("parameters_string", preview.getCameraController().getParametersString());
        }

        List<Controller.Size> preview_sizes = this.preview.getSupportedPreviewSizes();
        if (preview_sizes != null) {
            int[] widths = new int[preview_sizes.size()];
            int[] heights = new int[preview_sizes.size()];
            int i = 0;
            for (Controller.Size size : preview_sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                i++;
            }
            bundle.putIntArray("preview_widths", widths);
            bundle.putIntArray("preview_heights", heights);
        }
        bundle.putInt("preview_width", preview.getCurrentPreviewSize().width);
        bundle.putInt("preview_height", preview.getCurrentPreviewSize().height);

        List<Controller.Size> sizes = this.preview.getSupportedPictureSizes();
        if (sizes != null) {
            int[] widths = new int[sizes.size()];
            int[] heights = new int[sizes.size()];
            int i = 0;
            for (Controller.Size size : sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                i++;
            }
            bundle.putIntArray("resolution_widths", widths);
            bundle.putIntArray("resolution_heights", heights);
        }
        if (preview.getCurrentPictureSize() != null) {
            bundle.putInt("resolution_width", preview.getCurrentPictureSize().width);
            bundle.putInt("resolution_height", preview.getCurrentPictureSize().height);
        }

        List<String> video_quality = this.preview.getSupportedVideoQuality();
        if (video_quality != null && this.preview.getCameraController() != null) {
            String[] video_quality_arr = new String[video_quality.size()];
            String[] video_quality_string_arr = new String[video_quality.size()];
            int i = 0;
            for (String value : video_quality) {
                video_quality_arr[i] = value;
                video_quality_string_arr[i] = this.preview.getCamcorderProfileDescription(value);
                i++;
            }
            bundle.putStringArray("video_quality", video_quality_arr);
            bundle.putStringArray("video_quality_string", video_quality_string_arr);
        }
        if (preview.getCurrentVideoQuality() != null) {
            bundle.putString("current_video_quality", preview.getCurrentVideoQuality());
        }
        CamcorderProfile camcorder_profile = preview.getCamcorderProfile();
        bundle.putInt("video_frame_width", camcorder_profile.videoFrameWidth);
        bundle.putInt("video_frame_height", camcorder_profile.videoFrameHeight);
        bundle.putInt("video_bit_rate", camcorder_profile.videoBitRate);
        bundle.putInt("video_frame_rate", camcorder_profile.videoFrameRate);

        List<Controller.Size> video_sizes = this.preview.getSupportedVideoSizes();
        if (video_sizes != null) {
            int[] widths = new int[video_sizes.size()];
            int[] heights = new int[video_sizes.size()];
            int i = 0;
            for (Controller.Size size : video_sizes) {
                widths[i] = size.width;
                heights[i] = size.height;
                i++;
            }
            bundle.putIntArray("video_widths", widths);
            bundle.putIntArray("video_heights", heights);
        }

        putBundleExtra(bundle, "flash_values", this.preview.getSupportedFlashValues());
        putBundleExtra(bundle, "focus_values", this.preview.getSupportedFocusValues());

        setWindowFlagsForSettings();
        PreferenceFragment fragment = new PreferenceFragment();
        fragment.setArguments(bundle);
        // use commitAllowingStateLoss() instead of commit(), does to "java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState" crash seen on Google Play
        // see http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit
        getFragmentManager().beginTransaction().add(R.id.prefs_container, fragment, "PREFERENCE_FRAGMENT").addToBackStack(null).commitAllowingStateLoss();
    }

    public void updateForSettings() {
        updateForSettings(null);
    }

    public void updateForSettings(String toast_message) {
        if (Debug.LOG) {
            Log.d(TAG, "updateForSettings()");
            if (toast_message != null) {
                Log.d(TAG, "toast_message: " + toast_message);
            }
        }
        String saved_focus_value = null;
        if (preview.getCameraController() != null && preview.isVideo() && !preview.focusIsVideo()) {
            saved_focus_value = preview.getCurrentFocusValue(); // n.b., may still be null
            // make sure we're into continuous video mode
            // workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
            // so to be safe, we always reset to continuous video mode, and then reset it afterwards
            preview.updateFocusForVideo(false);
        }
        if (Debug.LOG)
            Log.d(TAG, "saved_focus_value: " + saved_focus_value);

        if (Debug.LOG)
            Log.d(TAG, "update folder history");
        save_location_history.updateFolderHistory(getStorageUtils().getSaveLocation(), true);
        // no need to update save_location_history_saf, as we always do this in onActivityResult()

        // update camera for changes made in prefs - do this without closing and reopening the camera app if possible for speed!
        // but need workaround for Nexus 7 bug, where scene mode doesn't take effect unless the camera is restarted - I can reproduce this with other 3rd party camera apps, so may be a Nexus 7 issue...
        boolean need_reopen = false;
        if (preview.getCameraController() != null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String scene_mode = preview.getCameraController().getSceneMode();
            if (Debug.LOG)
                Log.d(TAG, "scene mode was: " + scene_mode);
            String key = Keys.getSceneModePreferenceKey();
            String value = sharedPreferences.getString(key, preview.getCameraController().getDefaultSceneMode());
            if (!value.equals(scene_mode)) {
                if (Debug.LOG)
                    Log.d(TAG, "scene mode changed to: " + value);
                need_reopen = true;
            } else {
                if (applicationInterface.useCamera2()) {
                    // need to reopen if fake flash mode changed, as it changes the available camera features, and we can only set this after opening the camera
                    boolean camera2_fake_flash = preview.getCameraController().getUseCamera2FakeFlash();
                    if (Debug.LOG)
                        Log.d(TAG, "camera2_fake_flash was: " + camera2_fake_flash);
                    if (applicationInterface.useCamera2FakeFlash() != camera2_fake_flash) {
                        if (Debug.LOG)
                            Log.d(TAG, "camera2_fake_flash changed");
                        need_reopen = true;
                    }
                }
            }
        }

        mainui.layoutUI(); // needed in case we've changed left/right handed UI
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getString(Keys.getAudioControlPreferenceKey(), "none").equals("none")) {
            // ensure icon is invisible if switching from audio control enabled to disabled
            // (if enabling it, we'll make the icon visible later on)
            View speechRecognizerButton = (View) findViewById(R.id.audio_control);
            speechRecognizerButton.setVisibility(View.GONE);
        }
        initSpeechRecognizer(); // in case we've enabled or disabled speech recognizer
        initLocation(); // in case we've enabled or disabled GPS
        if (toast_message != null)
            block_startup_toast = true;
        if (need_reopen || preview.getCameraController() == null) { // if camera couldn't be opened before, might as well try again
            preview.onPause();
            preview.onResume();
        } else {
            preview.setCameraDisplayOrientation(); // need to call in case the preview rotation option was changed
            preview.pausePreview();
            preview.setupCamera(false);
        }
        block_startup_toast = false;
        if (toast_message != null && toast_message.length() > 0)
            preview.showToast(null, toast_message);

        if (saved_focus_value != null) {
            if (Debug.LOG)
                Log.d(TAG, "switch focus back to: " + saved_focus_value);
            preview.updateFocus(saved_focus_value, true, false);
        }
    }

    PreferenceFragment getPreferenceFragment() {
        PreferenceFragment fragment = (PreferenceFragment) getFragmentManager().findFragmentByTag("PREFERENCE_FRAGMENT");
        return fragment;
    }

    @Override
    public void onBackPressed() {
        final PreferenceFragment fragment = getPreferenceFragment();
        if (screen_is_locked) {
            preview.showToast(screen_locked_toast, R.string.screen_is_locked);
            return;
        }
        if (fragment != null) {
            if (Debug.LOG)
                Log.d(TAG, "close settings");
            setWindowFlagsForCamera();
            updateForSettings();
        } else {
            if (popupIsOpen()) {
                closePopup();
                return;
            }
        }
        super.onBackPressed();
        Intent intent = new Intent(con, MainScreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);


    }


    public boolean usingKitKatImmersiveMode() {
        // whether we are using a Kit Kat style immersive mode (either hiding GUI, or everything)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String immersive_mode = sharedPreferences.getString(Keys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
            if (immersive_mode.equals("immersive_mode_gui") || immersive_mode.equals("immersive_mode_everything"))
                return true;
        }
        return false;
    }

    private Handler immersive_timer_handler = null;
    private Runnable immersive_timer_runnable = null;

    private void setImmersiveTimer() {
        if (immersive_timer_handler != null && immersive_timer_runnable != null) {
            immersive_timer_handler.removeCallbacks(immersive_timer_runnable);
        }
        immersive_timer_handler = new Handler();
        immersive_timer_handler.postDelayed(immersive_timer_runnable = new Runnable() {
            @Override
            public void run() {
                if (Debug.LOG)
                    Log.d(TAG, "setImmersiveTimer: run");
                if (!camera_in_background && !popupIsOpen() && usingKitKatImmersiveMode())
                    setImmersiveMode(true);
            }
        }, 5000);
    }

    public void initImmersiveMode() {
        if (!usingKitKatImmersiveMode()) {
            setImmersiveMode(true);
        } else {
            // don't start in immersive mode, only after a timer
            setImmersiveTimer();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    void setImmersiveMode(boolean on) {
        if (Debug.LOG)
            Log.d(TAG, "setImmersiveMode: " + on);
        // n.b., preview.setImmersiveMode() is called from onSystemUiVisibilityChange()
        if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && usingKitKatImmersiveMode()) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
            } else {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                String immersive_mode = sharedPreferences.getString(Keys.getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
                if (immersive_mode.equals("immersive_mode_low_profile"))
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
                else
                    getWindow().getDecorView().setSystemUiVisibility(0);
            }
        } else
            getWindow().getDecorView().setSystemUiVisibility(0);
    }

    /**
     * Sets the brightness level for normal operation (when camera preview is visible).
     * If force_max is true, this always forces maximum brightness; otherwise this depends on user preference.
     */
    void setBrightnessForCamera(boolean force_max) {
        if (Debug.LOG)
            Log.d(TAG, "setBrightnessForCamera");
        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
        // done here rather than onCreate, so that changing it in preferences takes effect without restarting app
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        if (force_max || sharedPreferences.getBoolean(Keys.getMaxBrightnessPreferenceKey(), true)) {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        } else {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }
        getWindow().setAttributes(layout);
    }

    /**
     * Sets the window flags for normal operation (when camera preview is visible).
     */
    public void setWindowFlagsForCamera() {
        if (Debug.LOG)
            Log.d(TAG, "setWindowFlagsForCamera");
    	/*{
    		Intent intent = new Intent(this, WidgetProvider.class);
    		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    		AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
    		ComponentName widgetComponent = new ComponentName(this, WidgetProvider.class);
    		int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
    		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
    		sendBroadcast(intent);
    	}*/
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // force to landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE); // testing for devices with unusual sensor orientation (e.g., Nexus 5X)
        // keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
        if (sharedPreferences.getBoolean(Keys.getKeepDisplayOnPreferenceKey(), true)) {
            if (Debug.LOG)
                Log.d(TAG, "do keep screen on");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            if (Debug.LOG)
                Log.d(TAG, "don't keep screen on");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (sharedPreferences.getBoolean(Keys.getShowWhenLockedPreferenceKey(), true)) {
            if (Debug.LOG)
                Log.d(TAG, "do show when locked");
            // keep Open Camera on top of screen-lock (will still need to unlock when going to gallery or settings)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            if (Debug.LOG)
                Log.d(TAG, "don't show when locked");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setBrightnessForCamera(false);

        initImmersiveMode();
        camera_in_background = false;
    }

    /**
     * Sets the window flags for when the settings window is open.
     */
    public void setWindowFlagsForSettings() {
        // allow screen rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        // revert to standard screen blank behaviour
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // settings should still be protected by screen lock
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(layout);
        }

        setImmersiveMode(false);
        camera_in_background = true;
    }

    public void showPreview(boolean show) {
        if (Debug.LOG)
            Log.d(TAG, "showPreview: " + show);
        final ViewGroup container = (ViewGroup) findViewById(R.id.hide_container);
        container.setBackgroundColor(Color.BLACK);
        container.setAlpha(show ? 0.0f : 1.0f);
    }

    /**
     * Shows the default "blank" gallery icon, when we don't have a thumbnail available.
     */


    public void updateGalleryIconToBlank() {
        if (Debug.LOG)
            Log.d(TAG, "updateGalleryIconToBlank");


        ImageButton galleryButton = (ImageButton) this.findViewById(R.id.gallery);
        int bottom = galleryButton.getPaddingBottom();
        int top = galleryButton.getPaddingTop();
        int right = galleryButton.getPaddingRight();
        int left = galleryButton.getPaddingLeft();
	    /*if( Debug.LOG )
			Log.d(TAG, "padding: " + bottom);*/
        galleryButton.setImageBitmap(null);
        galleryButton.setImageResource(R.drawable.gallery);
        // workaround for setImageResource also resetting padding, Android bug
        galleryButton.setPadding(left, top, right, bottom);
        g_bitmap = null;
    }

    /**
     * Shows a thumbnail for the gallery icon.
     */
    void updateGalleryIcon(Bitmap thumbnail) {
        if (Debug.LOG)
            Log.d(TAG, "updateGalleryIcon: " + thumbnail);
        ImageButton galleryButton = (ImageButton) this.findViewById(R.id.gallery);
        galleryButton.setImageBitmap(thumbnail);
        g_bitmap = thumbnail;
    }


    Bitmap getBitmapFromLayoutAddress() {
        RelativeLayout layout = findViewById(R.id.location_layout);
        layout.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(layout.getDrawingCache());
        layout.setDrawingCacheEnabled(false);
        return bitmap;
    }

    /**
     * layout.setDrawingCacheEnabled(false);
     * Updates the gallery icon by searching for the most recent photo.
     * Launches the task in a separate thread.
     */
    public void updateGalleryIcon() {
        long debug_time = 0;
        if (Debug.LOG) {
            Log.d(TAG, "updateGalleryIcon");
            debug_time = System.currentTimeMillis();
        }

        new AsyncTask<Void, Void, Bitmap>() {
            private String TAG = "HomeActivity/updateGalleryIcon()/AsyncTask";

            /** The system calls this to perform work in a worker thread and
             * delivers it the parameters given to AsyncTask.execute() */
            protected Bitmap doInBackground(Void... params) {
                if (Debug.LOG)
                    Log.d(TAG, "doInBackground");
                Storage.Media media = applicationInterface.getStorage().getLatestMedia();
                Bitmap thumbnail = null;
                KeyguardManager keyguard_manager = (KeyguardManager) HomeActivity.this.getSystemService(Context.KEYGUARD_SERVICE);
                boolean is_locked = keyguard_manager != null && keyguard_manager.inKeyguardRestrictedInputMode();
                if (Debug.LOG)
                    Log.d(TAG, "is_locked?: " + is_locked);
                if (media != null && getContentResolver() != null && !is_locked) {
                    // check for getContentResolver() != null, as have had reported Google Play crashes
                    try {
                        if (media.video) {
                            thumbnail = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), media.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                        } else {
                            thumbnail = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), media.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                        }
                    } catch (NoClassDefFoundError exception) {
                        // have had Google Play crashes from new ExifInterface() for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn)
                        if (Debug.LOG)
                            Log.e(TAG, "exif orientation NoClassDefFoundError");
                        exception.printStackTrace();
                    }
                    if (thumbnail != null) {
                        if (media.orientation != 0) {
                            if (Debug.LOG)
                                Log.d(TAG, "thumbnail size is " + thumbnail.getWidth() + " x " + thumbnail.getHeight());
                            Matrix matrix = new Matrix();
                            matrix.setRotate(media.orientation, thumbnail.getWidth() * 0.5f, thumbnail.getHeight() * 0.5f);
                            try {
                                Bitmap rotated_thumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);
                                // careful, as rotated_thumbnail is sometimes not a copy!
                                if (rotated_thumbnail != thumbnail) {
                                    thumbnail.recycle();
                                    thumbnail = rotated_thumbnail;
                                }
                            } catch (Throwable t) {
                                if (Debug.LOG)
                                    Log.d(TAG, "failed to rotate thumbnail");
                            }
                        }
                    }
                }
                return thumbnail;
            }

            /** The system calls this to perform work in the UI thread and delivers
             * the result from doInBackground() */
            protected void onPostExecute(Bitmap thumbnail) {
                if (Debug.LOG)
                    Log.d(TAG, "onPostExecute");
                // since we're now setting the thumbnail to the latest media on disk, we need to make sure clicking the Gallery goes to this
                applicationInterface.getStorage().clearLastMediaScanned();
                if (thumbnail != null) {
                    if (Debug.LOG)
                        Log.d(TAG, "set gallery button to thumbnail");
                    updateGalleryIcon(thumbnail);
                } else {
                    if (Debug.LOG)
                        Log.d(TAG, "set gallery button to blank");
                    updateGalleryIconToBlank();
                }
            }
        }.execute();

        if (Debug.LOG)
            Log.d(TAG, "updateGalleryIcon: total time to update gallery icon: " + (System.currentTimeMillis() - debug_time));
    }

    void savingImage(final boolean started) {
        if (Debug.LOG)
            Log.d(TAG, "savingImage: " + started);

        this.runOnUiThread(new Runnable() {
            public void run() {
                final ImageButton galleryButton = (ImageButton) findViewById(R.id.gallery);
                if (started) {
                    //galleryButton.setColorFilter(0x80ffffff, PorterDuff.Mode.MULTIPLY);
                    if (gallery_save_anim == null) {
                        gallery_save_anim = ValueAnimator.ofInt(Color.argb(200, 255, 255, 255), Color.argb(63, 255, 255, 255));
                        gallery_save_anim.setEvaluator(new ArgbEvaluator());
                        gallery_save_anim.setRepeatCount(ValueAnimator.INFINITE);
                        gallery_save_anim.setRepeatMode(ValueAnimator.REVERSE);
                        gallery_save_anim.setDuration(500);
                    }
                    gallery_save_anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            galleryButton.setColorFilter((Integer) animation.getAnimatedValue(), PorterDuff.Mode.MULTIPLY);
                        }
                    });
                    gallery_save_anim.start();
                } else if (gallery_save_anim != null) {
                    gallery_save_anim.cancel();
                }
                galleryButton.setColorFilter(null);
            }
        });
    }

    public void clickedLocation(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickLocation");
        Intent intent = new Intent(this, LocationActivity.class);
        startActivity(intent);
    }

    public void clickedGallery(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedGallery");

        //Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Uri uri = applicationInterface.getStorage().getLastMediaScanned();
        if (uri == null) {
            if (Debug.LOG)
                Log.d(TAG, "go to latest media");
            Storage.Media media = applicationInterface.getStorage().getLatestMedia();
            if (media != null) {
                uri = media.uri;
            }
        }

        if (uri != null) {
            // check uri exists
            if (Debug.LOG)
                Log.d(TAG, "found most recent uri: " + uri);
            try {
                ContentResolver cr = getContentResolver();
                ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
                if (pfd == null) {
                    if (Debug.LOG)
                        Log.d(TAG, "uri no longer exists (1): " + uri);
                    uri = null;
                } else {
                    pfd.close();
                }
            } catch (IOException e) {
                if (Debug.LOG)
                    Log.d(TAG, "uri no longer exists (2): " + uri);
                uri = null;
            }
        }
        if (uri == null) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        if (!is_test) {
            // don't do if testing, as unclear how to exit activity to finish test (for testGallery())
            if (Debug.LOG)
                Log.d(TAG, "launch uri:" + uri);
            final String REVIEW_ACTION = "com.android.camera.action.REVIEW";
            try {
                // REVIEW_ACTION means we can view video files without autoplaying
                Intent intent = new Intent(REVIEW_ACTION, uri);
                this.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                if (Debug.LOG)
                    Log.d(TAG, "REVIEW_ACTION intent didn't work, try ACTION_VIEW");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                // from http://stackoverflow.com/questions/11073832/no-activity-found-to-handle-intent - needed to fix crash if no gallery app installed
                //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("blah")); // test
                if (intent.resolveActivity(getPackageManager()) != null) {
                    this.startActivity(intent);
                } else {
                    preview.showToast(null, R.string.no_gallery_app);
                }
            }
        }
    }

    /**
     * Opens the Storage Access Framework dialog to select a folder.
     *
     * @param from_preferences Whether called from the Preferences
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    void openFolderChooserDialogSAF(boolean from_preferences) {
        if (Debug.LOG)
            Log.d(TAG, "openFolderChooserDialogSAF: " + from_preferences);
        this.saf_dialog_from_preferences = from_preferences;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        //Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 42);
    }

    /**
     * Call when the SAF save history has been updated.
     * This is only public so we can call from testing.
     *
     * @param save_folder The new SAF save folder Uri.
     */
    public void updateFolderHistorySAF(String save_folder) {
        if (Debug.LOG)
            Log.d(TAG, "updateSaveHistorySAF");
        if (save_location_history_saf == null) {
            save_location_history_saf = new LocationHistory(this, "save_location_history_saf", save_folder);
        }
        save_location_history_saf.updateFolderHistory(save_folder, true);
    }

    /**
     * Listens for the response from the Storage Access Framework dialog to select a folder
     * (as opened with openFolderChooserDialogSAF()).
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (Debug.LOG)
            Log.d(TAG, "onActivityResult: " + requestCode);
        if (requestCode == 42) {
            if (resultCode == RESULT_OK && resultData != null) {
                Uri treeUri = resultData.getData();
                if (Debug.LOG)
                    Log.d(TAG, "returned treeUri: " + treeUri);
                // from https://developer.android.com/guide/topics/providers/document-provider.html#permissions :
                final int takeFlags = resultData.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                // Check for the freshest data.
                //getContentResolver().takePersistableUriPermission(treeUri, takeFlags);

                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(Keys.getSaveLocationSAFPreferenceKey(), treeUri.toString());
                editor.apply();

                if (Debug.LOG)
                    Log.d(TAG, "update folder history for saf");
                updateFolderHistorySAF(treeUri.toString());

                String filename = applicationInterface.getStorage().getImageFolderNameSAF();
                if (filename != null) {
                    preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + filename);
                }
            } else {
                if (Debug.LOG)
                    Log.d(TAG, "SAF dialog cancelled");
                // cancelled - if the user had yet to set a save location, make sure we switch SAF back off
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                String uri = sharedPreferences.getString(Keys.getSaveLocationSAFPreferenceKey(), "");
                if (uri.length() == 0) {
                    if (Debug.LOG)
                        Log.d(TAG, "no SAF save location was set");
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(Keys.getUsingSAFPreferenceKey(), false);
                    editor.apply();
                    preview.showToast(null, R.string.saf_cancelled);
                }
            }

            if (!saf_dialog_from_preferences) {
                setWindowFlagsForCamera();
                showPreview(true);
            }
        }
    }

    /**
     * Opens Open Camera's own (non-Storage Access Framework) dialog to select a folder.
     */
    private void openFolderChooserDialog() {
        if (Debug.LOG)
            Log.d(TAG, "openFolderChooserDialog");
        showPreview(false);
        setWindowFlagsForSettings();
        final String orig_save_location = applicationInterface.getStorage().getSaveLocation();
        FolderChooserDialog fragment = new FolderChooserDialog() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (Debug.LOG)
                    Log.d(TAG, "FolderChooserDialog dismissed");
                setWindowFlagsForCamera();
                showPreview(true);
                final String new_save_location = applicationInterface.getStorage().getSaveLocation();
                if (!orig_save_location.equals(new_save_location)) {
                    if (Debug.LOG)
                        Log.d(TAG, "changed save_folder to: " + applicationInterface.getStorage().getSaveLocation());
                    save_location_history.updateFolderHistory(getStorageUtils().getSaveLocation(), true);
                    preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + applicationInterface.getStorage().getSaveLocation());
                }
                super.onDismiss(dialog);
            }
        };
        fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
    }

    /**
     * User can long-click on gallery to select a recent save location from the history, of if not available,
     * go straight to the file dialog to pick a folder.
     */
    private void longClickedGallery() {


        if (Debug.LOG)
            Log.d(TAG, "longClickedGallery");
        if (applicationInterface.getStorage().isUsingSAF()) {
            if (save_location_history_saf == null || save_location_history_saf.size() <= 1) {
                if (Debug.LOG)
                    Log.d(TAG, "go straight to choose folder dialog for SAF");
                openFolderChooserDialogSAF(false);
                return;
            }
        } else {
            if (save_location_history.size() <= 1) {
                if (Debug.LOG)
                    Log.d(TAG, "go straight to choose folder dialog");
                openFolderChooserDialog();
                return;
            }
        }

        final LocationHistory history = applicationInterface.getStorage().isUsingSAF() ? save_location_history_saf : save_location_history;
        showPreview(false);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.choose_save_location);
        CharSequence[] items = new CharSequence[history.size() + 2];
        int index = 0;
        // history is stored in order most-recent-last
        for (int i = 0; i < history.size(); i++) {
            String folder_name = history.get(history.size() - 1 - i);
            if (applicationInterface.getStorage().isUsingSAF()) {
                // try to get human readable form if possible
                String filename = applicationInterface.getStorage().getImageFolderNameSAF(Uri.parse(folder_name));
                if (filename != null) {
                    folder_name = filename;
                }
            }
            items[index++] = folder_name;
        }
        final int clear_index = index;
        items[index++] = getResources().getString(R.string.clear_folder_history);
        final int new_index = index;
        items[index++] = getResources().getString(R.string.choose_another_folder);
        alertDialog.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == clear_index) {
                    if (Debug.LOG)
                        Log.d(TAG, "selected clear save history");
                    new AlertDialog.Builder(HomeActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.clear_folder_history)
                            .setMessage(R.string.clear_folder_history_question)
                            .setPositiveButton(R.string.answer_yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (Debug.LOG)
                                        Log.d(TAG, "confirmed clear save history");
                                    if (applicationInterface.getStorage().isUsingSAF())
                                        clearFolderHistorySAF();
                                    else
                                        clearFolderHistory();
                                    setWindowFlagsForCamera();
                                    showPreview(true);
                                }
                            })
                            .setNegativeButton(R.string.answer_no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (Debug.LOG)
                                        Log.d(TAG, "don't clear save history");
                                    setWindowFlagsForCamera();
                                    showPreview(true);
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface arg0) {
                                    if (Debug.LOG)
                                        Log.d(TAG, "cancelled clear save history");
                                    setWindowFlagsForCamera();
                                    showPreview(true);
                                }
                            })
                            .show();
                } else if (which == new_index) {
                    if (Debug.LOG)
                        Log.d(TAG, "selected choose new folder");
                    if (applicationInterface.getStorage().isUsingSAF()) {
                        openFolderChooserDialogSAF(false);
                    } else {
                        openFolderChooserDialog();
                    }
                } else {
                    if (Debug.LOG)
                        Log.d(TAG, "selected: " + which);
                    if (which >= 0 && which < history.size()) {
                        String save_folder = history.get(history.size() - 1 - which);
                        if (Debug.LOG)
                            Log.d(TAG, "changed save_folder from history to: " + save_folder);
                        String save_folder_name = save_folder;
                        if (applicationInterface.getStorage().isUsingSAF()) {
                            // try to get human readable form if possible
                            String filename = applicationInterface.getStorage().getImageFolderNameSAF(Uri.parse(save_folder));
                            if (filename != null) {
                                save_folder_name = filename;
                            }
                        }
                        preview.showToast(null, getResources().getString(R.string.changed_save_location) + "\n" + save_folder_name);
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(HomeActivity.this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        if (applicationInterface.getStorage().isUsingSAF())
                            editor.putString(Keys.getSaveLocationSAFPreferenceKey(), save_folder);
                        else
                            editor.putString(Keys.getSaveLocationPreferenceKey(), save_folder);
                        editor.apply();
                        history.updateFolderHistory(save_folder, true); // to move new selection to most recent
                    }
                    setWindowFlagsForCamera();
                    showPreview(true);
                }
            }
        });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                setWindowFlagsForCamera();
                showPreview(true);
            }
        });
        alertDialog.show();
        //getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        setWindowFlagsForSettings();
    }

    /**
     * Clears the non-SAF folder history.
     */
    public void clearFolderHistory() {
        if (Debug.LOG)
            Log.d(TAG, "clearFolderHistory");
        save_location_history.clearFolderHistory(getStorageUtils().getSaveLocation());
    }

    /**
     * Clears the SAF folder history.
     */
    public void clearFolderHistorySAF() {
        if (Debug.LOG)
            Log.d(TAG, "clearFolderHistorySAF");
        save_location_history_saf.clearFolderHistory(getStorageUtils().getSaveLocationSAF());
    }

    static private void putBundleExtra(Bundle bundle, String key, List<String> values) {
        if (values != null) {
            String[] values_arr = new String[values.size()];
            int i = 0;
            for (String value : values) {
                values_arr[i] = value;
                i++;
            }
            bundle.putStringArray(key, values_arr);
        }
    }

    public void clickedShare(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedShare");
        applicationInterface.shareLastImage();
    }

    public void clickedTrash(View view) {
        if (Debug.LOG)
            Log.d(TAG, "clickedTrash");
        applicationInterface.trashLastImage();
    }

    private void takePicture() {
        if (Debug.LOG)
            Log.d(TAG, "takePicture");
        closePopup();
        this.preview.takePicturePressed();
    }

    /**
     * Lock the screen - this is Open Camera's own lock to guard against accidental presses,
     * not the standard Android lock.
     */
    void lockScreen() {
        ((ViewGroup) findViewById(R.id.locker)).setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View arg0, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
                //return true;
            }
        });
        screen_is_locked = true;
    }

    /**
     * Unlock the screen (see lockScreen()).
     */
    void unlockScreen() {
        ((ViewGroup) findViewById(R.id.locker)).setOnTouchListener(null);
        screen_is_locked = false;
    }

    /**
     * Whether the screen is locked (see lockScreen()).
     */
    public boolean isScreenLocked() {
        return screen_is_locked;
    }

    @Override
    public void getLocationAddress(double lat, double lon, String address) {
        updateAddress(lat, lon, address);
    }

    @Override
    public void getLocation(double lat, double lon) {
        getLocationSupplier().getAddressFromLocation(lat, lon, this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;
        if (mLocation != null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()), 13f));
            mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(mLocation.getLatitude(), mLocation.getLongitude())));
        }
    }

    /**
     * Listen for gestures.
     * Doing a swipe will unlock the screen (see lockScreen()).
     */
    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Debug.LOG)
                    Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
                final ViewConfiguration vc = ViewConfiguration.get(HomeActivity.this);
                //final int swipeMinDistance = 4*vc.getScaledPagingTouchSlop();
                final float scale = getResources().getDisplayMetrics().density;
                final int swipeMinDistance = (int) (160 * scale + 0.5f); // convert dps to pixels
                final int swipeThresholdVelocity = vc.getScaledMinimumFlingVelocity();
                if (Debug.LOG) {
                    Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
                    Log.d(TAG, "swipeMinDistance: " + swipeMinDistance);
                }
                float xdist = e1.getX() - e2.getX();
                float ydist = e1.getY() - e2.getY();
                float dist2 = xdist * xdist + ydist * ydist;
                float vel2 = velocityX * velocityX + velocityY * velocityY;
                if (dist2 > swipeMinDistance * swipeMinDistance && vel2 > swipeThresholdVelocity * swipeThresholdVelocity) {
                    preview.showToast(screen_locked_toast, R.string.unlocked);
                    unlockScreen();
                }
            } catch (Exception e) {
            }
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            preview.showToast(screen_locked_toast, R.string.screen_is_locked);
            return true;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (Debug.LOG)
            Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(state);
        if (this.preview != null) {
            preview.onSaveInstanceState(state);
        }
        if (this.applicationInterface != null) {
            applicationInterface.onSaveInstanceState(state);
        }
    }

    public boolean supportsExposureButton() {
        if (preview.getCameraController() == null)
            return false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String iso_value = sharedPreferences.getString(Keys.getISOPreferenceKey(), preview.getCameraController().getDefaultISO());
        boolean manual_iso = !iso_value.equals(preview.getCameraController().getDefaultISO());
        boolean supports_exposure = preview.supportsExposures() || (manual_iso && preview.supportsISORange());
        return supports_exposure;
    }

    void cameraSetup() {
        long debug_time = 0;
        if (Debug.LOG) {
            Log.d(TAG, "cameraSetup");
            debug_time = System.currentTimeMillis();
        }
        if (this.supportsForceVideo4K() && preview.usingCamera2API()) {
            if (Debug.LOG)
                Log.d(TAG, "using Camera2 API, so can disable the force 4K option");
            this.disableForceVideo4K();
        }
        if (this.supportsForceVideo4K() && preview.getSupportedVideoSizes() != null) {
            for (Controller.Size size : preview.getSupportedVideoSizes()) {
                if (size.width >= 3840 && size.height >= 2160) {
                    if (Debug.LOG)
                        Log.d(TAG, "camera natively supports 4K, so can disable the force option");
                    this.disableForceVideo4K();
                }
            }
        }
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after handling Force 4K option: " + (System.currentTimeMillis() - debug_time));

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        {
            if (Debug.LOG)
                Log.d(TAG, "set up zoom");
            if (Debug.LOG)
                Log.d(TAG, "has_zoom? " + preview.supportsZoom());
            ZoomControls zoomControls = (ZoomControls) findViewById(R.id.zoom);
            SeekBar zoomSeekBar = (SeekBar) findViewById(R.id.zoom_seekbar);

            if (preview.supportsZoom()) {
                if (sharedPreferences.getBoolean(Keys.getShowZoomControlsPreferenceKey(), false)) {
                    zoomControls.setIsZoomInEnabled(true);
                    zoomControls.setIsZoomOutEnabled(true);
                    zoomControls.setZoomSpeed(20);

                    zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            zoomIn();
                        }
                    });
                    zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            zoomOut();
                        }
                    });
                    if (!mainui.inImmersiveMode()) {
                        zoomControls.setVisibility(View.VISIBLE);
                    }
                } else {
                    zoomControls.setVisibility(View.INVISIBLE); // must be INVISIBLE not GONE, so we can still position the zoomSeekBar relative to it
                }

                zoomSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                zoomSeekBar.setMax(preview.getMaxZoom());
                zoomSeekBar.setProgress(preview.getMaxZoom() - preview.getCameraController().getZoom());
                zoomSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (Debug.LOG)
                            Log.d(TAG, "zoom onProgressChanged: " + progress);
                        preview.zoomTo(preview.getMaxZoom() - progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                if (sharedPreferences.getBoolean(Keys.getShowZoomSliderControlsPreferenceKey(), true)) {
                    if (!mainui.inImmersiveMode()) {
                        zoomSeekBar.setVisibility(View.VISIBLE);
                    }
                } else {
                    zoomSeekBar.setVisibility(View.INVISIBLE);
                }
            } else {
                zoomControls.setVisibility(View.GONE);
                zoomSeekBar.setVisibility(View.GONE);
            }
            if (Debug.LOG)
                Log.d(TAG, "cameraSetup: time after setting up zoom: " + (System.currentTimeMillis() - debug_time));
        }
        {
            if (Debug.LOG)
                Log.d(TAG, "set up manual focus");
            SeekBar focusSeekBar = (SeekBar) findViewById(R.id.focus_seekbar);
            focusSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
            setProgressSeekbarScaled(focusSeekBar, 0.0, preview.getMinimumFocusDistance(), preview.getCameraController().getFocusDistance());
            focusSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    double frac = progress / (double) 100.0;
                    double scaling = HomeActivity.seekbarScaling(frac);
                    float focus_distance = (float) (scaling * preview.getMinimumFocusDistance());
                    preview.setFocusDistance(focus_distance);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            final int visibility = preview.getCurrentFocusValue() != null && this.getPreview().getCurrentFocusValue().equals("focus_mode_manual2") ? View.VISIBLE : View.INVISIBLE;
            focusSeekBar.setVisibility(visibility);
        }
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after setting up manual focus: " + (System.currentTimeMillis() - debug_time));
        {
            if (preview.supportsISORange()) {
                if (Debug.LOG)
                    Log.d(TAG, "set up iso");
                SeekBar iso_seek_bar = ((SeekBar) findViewById(R.id.iso_seekbar));
                iso_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                setProgressSeekbarScaled(iso_seek_bar, preview.getMinimumISO(), preview.getMaximumISO(), preview.getCameraController().getISO());
                iso_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (Debug.LOG)
                            Log.d(TAG, "iso seekbar onProgressChanged: " + progress);
                        double frac = progress / (double) 100.0;
                        if (Debug.LOG)
                            Log.d(TAG, "exposure_time frac: " + frac);
                        double scaling = HomeActivity.seekbarScaling(frac);
                        if (Debug.LOG)
                            Log.d(TAG, "exposure_time scaling: " + scaling);
                        int min_iso = preview.getMinimumISO();
                        int max_iso = preview.getMaximumISO();
                        int iso = min_iso + (int) (scaling * (max_iso - min_iso));
                        preview.setISO(iso);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                if (preview.supportsExposureTime()) {
                    if (Debug.LOG)
                        Log.d(TAG, "set up exposure time");
                    SeekBar exposure_time_seek_bar = ((SeekBar) findViewById(R.id.exposure_time_seekbar));
                    exposure_time_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                    setProgressSeekbarScaled(exposure_time_seek_bar, preview.getMinimumExposureTime(), preview.getMaximumExposureTime(), preview.getCameraController().getExposureTime());
                    exposure_time_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (Debug.LOG)
                                Log.d(TAG, "exposure_time seekbar onProgressChanged: " + progress);
                            double frac = progress / (double) 100.0;
                            if (Debug.LOG)
                                Log.d(TAG, "exposure_time frac: " + frac);
                            //long exposure_time = min_exposure_time + (long)(frac * (max_exposure_time - min_exposure_time));
                            //double exposure_time_r = min_exposure_time_r + (frac * (max_exposure_time_r - min_exposure_time_r));
                            //long exposure_time = (long)(1.0 / exposure_time_r);
                            // we use the formula: [100^(percent/100) - 1]/99.0 rather than a simple linear scaling
                            double scaling = HomeActivity.seekbarScaling(frac);
                            if (Debug.LOG)
                                Log.d(TAG, "exposure_time scaling: " + scaling);
                            long min_exposure_time = preview.getMinimumExposureTime();
                            long max_exposure_time = preview.getMaximumExposureTime();
                            long exposure_time = min_exposure_time + (long) (scaling * (max_exposure_time - min_exposure_time));
                            preview.setExposureTime(exposure_time);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
                }
            }
        }
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after setting up iso: " + (System.currentTimeMillis() - debug_time));
        {
            if (preview.supportsExposures()) {
                if (Debug.LOG)
                    Log.d(TAG, "set up exposure compensation");
                final int min_exposure = preview.getMinimumExposure();
                SeekBar exposure_seek_bar = ((SeekBar) findViewById(R.id.exposure_seekbar));
                exposure_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                exposure_seek_bar.setMax(preview.getMaximumExposure() - min_exposure);
                exposure_seek_bar.setProgress(preview.getCurrentExposure() - min_exposure);
                exposure_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (Debug.LOG)
                            Log.d(TAG, "exposure seekbar onProgressChanged: " + progress);
                        preview.setExposure(min_exposure + progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                ZoomControls seek_bar_zoom = (ZoomControls) findViewById(R.id.exposure_seekbar_zoom);
                seek_bar_zoom.setOnZoomInClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        changeExposure(1);
                    }
                });
                seek_bar_zoom.setOnZoomOutClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        changeExposure(-1);
                    }
                });
            }
        }
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after setting up exposure: " + (System.currentTimeMillis() - debug_time));

        View exposureButton = (View) findViewById(R.id.exposure);
        exposureButton.setVisibility(supportsExposureButton() && !mainui.inImmersiveMode() ? View.VISIBLE : View.GONE);

        ImageButton exposureLockButton = (ImageButton) findViewById(R.id.exposure_lock);
        exposureLockButton.setVisibility(preview.supportsExposureLock() && !mainui.inImmersiveMode() ? View.VISIBLE : View.GONE);
        if (preview.supportsExposureLock()) {
            exposureLockButton.setImageResource(preview.isExposureLocked() ? R.drawable.exposure_locked : R.drawable.exposure_unlocked);
        }
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after setting exposure lock button: " + (System.currentTimeMillis() - debug_time));

        mainui.setPopupIcon(); // needed so that the icon is set right even if no flash mode is set when starting up camera (e.g., switching to front camera with no flash)
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after setting popup icon: " + (System.currentTimeMillis() - debug_time));

        mainui.setTakePhotoIcon();
        mainui.setSwitchCameraContentDescription();
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: time after setting take photo icon: " + (System.currentTimeMillis() - debug_time));

        if (!block_startup_toast) {
            this.showPhotoVideoToast(false);
        }
        if (Debug.LOG)
            Log.d(TAG, "cameraSetup: total time for cameraSetup: " + (System.currentTimeMillis() - debug_time));
    }

    public boolean supportsAutoStabilise() {
        return this.supports_auto_stabilise;
    }

    public boolean supportsHDR() {
        // we also require the device have sufficient memory to do the processing, simplest to use the same test as we do for auto-stabilise...
        if (this.supportsAutoStabilise() && preview.supportsExpoBracketing())
            return true;
        return false;
    }

    public boolean supportsExpoBracketing() {
        if (preview.supportsExpoBracketing())
            return true;
        return false;
    }

    public boolean supportsForceVideo4K() {
        return this.supports_force_video_4k;
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    void disableForceVideo4K() {
        this.supports_force_video_4k = false;
    }

    /**
     * Return free memory in MB.
     */
    @SuppressWarnings("deprecation")
    public long freeMemory() { // return free memory in MB
        try {
            File folder = applicationInterface.getStorage().getImageFolder();
            if (folder == null) {
                throw new IllegalArgumentException(); // so that we fall onto the backup
            }
            StatFs statFs = new StatFs(folder.getAbsolutePath());
            // cast to long to avoid overflow!
            long blocks = statFs.getAvailableBlocks();
            long size = statFs.getBlockSize();
            long free = (blocks * size) / 1048576;
			/*if( Debug.LOG ) {
				Log.d(TAG, "freeMemory blocks: " + blocks + " size: " + size + " free: " + free);
			}*/
            return free;
        } catch (IllegalArgumentException e) {
            // this can happen if folder doesn't exist, or don't have read access
            // if the save folder is a subfolder of DCIM, we can just use that instead
            try {
                if (!applicationInterface.getStorage().isUsingSAF()) {
                    // Storage.getSaveLocation() only valid if !isUsingSAF()
                    String folder_name = applicationInterface.getStorage().getSaveLocation();
                    if (!folder_name.startsWith("/")) {
                        File folder = Storage.getBaseFolder();
                        StatFs statFs = new StatFs(folder.getAbsolutePath());
                        // cast to long to avoid overflow!
                        long blocks = statFs.getAvailableBlocks();
                        long size = statFs.getBlockSize();
                        long free = (blocks * size) / 1048576;
            			/*if( Debug.LOG ) {
            				Log.d(TAG, "freeMemory blocks: " + blocks + " size: " + size + " free: " + free);
            			}*/
                        return free;
                    }
                }
            } catch (IllegalArgumentException e2) {
                // just in case
            }
        }
        return -1;
    }

    public static String getDonateLink() {
        return "https://play.google.com/store/apps/details?id=harman.mark.donation";
    }

    /*public static String getDonateMarketLink() {
    	return "market://details?id=harman.mark.donation";
    }*/

    public Preview getPreview() {
        return this.preview;
    }

    public MainUI getMainui() {
        return this.mainui;
    }

    public MyApplicationInterface getApplicationInterface() {
        return this.applicationInterface;
    }

    public LocationSupplier getLocationSupplier() {
        return this.applicationInterface.getLocationSupplier();
    }

    public Storage getStorageUtils() {
        return this.applicationInterface.getStorage();
    }

    public File getImageFolder() {
        return this.applicationInterface.getStorage().getImageFolder();
    }

    public ToastBoxer getChangedAutoStabiliseToastBoxer() {
        return changed_auto_stabilise_toast;
    }

    /**
     * Displays a toast with information about the current preferences.
     * If always_show is true, the toast is always displayed; otherwise, we only display
     * a toast if it's important to notify the user (i.e., unusual non-default settings are
     * set). We want a balance between not pestering the user too much, whilst also reminding
     * them if certain settings are on.
     */
    private void showPhotoVideoToast(boolean always_show) {
        if (Debug.LOG) {
            Log.d(TAG, "showPhotoVideoToast");
            Log.d(TAG, "always_show? " + always_show);
        }
        Controller camera_controller = preview.getCameraController();
        if (camera_controller == null || this.camera_in_background) {
            if (Debug.LOG)
                Log.d(TAG, "camera not open or in background");
            return;
        }
        String toast_string = "";
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean simple = true;
        if (preview.isVideo()) {
            CamcorderProfile profile = preview.getCamcorderProfile();
            String bitrate_string = "";
            if (profile.videoBitRate >= 10000000)
                bitrate_string = profile.videoBitRate / 1000000 + "Mbps";
            else if (profile.videoBitRate >= 10000)
                bitrate_string = profile.videoBitRate / 1000 + "Kbps";
            else
                bitrate_string = profile.videoBitRate + "bps";

            toast_string = getResources().getString(R.string.video) + ": " + profile.videoFrameWidth + "x" + profile.videoFrameHeight + ", " + profile.videoFrameRate + "fps, " + bitrate_string;
            boolean record_audio = sharedPreferences.getBoolean(Keys.getRecordAudioPreferenceKey(), true);
            if (!record_audio) {
                toast_string += "\n" + getResources().getString(R.string.audio_disabled);
                simple = false;
            }
            String max_duration_value = sharedPreferences.getString(Keys.getVideoMaxDurationPreferenceKey(), "0");
            if (max_duration_value.length() > 0 && !max_duration_value.equals("0")) {
                String[] entries_array = getResources().getStringArray(R.array.preference_video_max_duration_entries);
                String[] values_array = getResources().getStringArray(R.array.preference_video_max_duration_values);
                int index = Arrays.asList(values_array).indexOf(max_duration_value);
                if (index != -1) { // just in case!
                    String entry = entries_array[index];
                    toast_string += "\n" + getResources().getString(R.string.max_duration) + ": " + entry;
                    simple = false;
                }
            }
            long max_filesize = applicationInterface.getVideoMaxFileSizeUserPref();
            if (max_filesize != 0) {
                long max_filesize_mb = max_filesize / (1024 * 1024);
                toast_string += "\n" + getResources().getString(R.string.max_filesize) + ": " + max_filesize_mb + getResources().getString(R.string.mb_abbreviation);
                simple = false;
            }
            if (sharedPreferences.getBoolean(Keys.getVideoFlashPreferenceKey(), false) && preview.supportsFlash()) {
                toast_string += "\n" + getResources().getString(R.string.preference_video_flash);
                simple = false;
            }
        } else {
            toast_string = getResources().getString(R.string.photo);
            Controller.Size current_size = preview.getCurrentPictureSize();
            toast_string += " " + current_size.width + "x" + current_size.height;
            if (preview.supportsFocus() && preview.getSupportedFocusValues().size() > 1) {
                String focus_value = preview.getCurrentFocusValue();
                if (focus_value != null && !focus_value.equals("focus_mode_auto") && !focus_value.equals("focus_mode_continuous_picture")) {
                    String focus_entry = preview.findFocusEntryForValue(focus_value);
                    if (focus_entry != null) {
                        toast_string += "\n" + focus_entry;
                    }
                }
            }
            if (sharedPreferences.getBoolean(Keys.getAutoStabilisePreferenceKey(), false)) {
                // important as users are sometimes confused at the behaviour if they don't realise the option is on
                toast_string += "\n" + getResources().getString(R.string.preference_auto_stabilise);
                simple = false;
            }
            String photo_mode_string = null;
            MyApplicationInterface.PhotoMode photo_mode = applicationInterface.getPhotoMode();
            if (photo_mode == MyApplicationInterface.PhotoMode.HDR) {
                photo_mode_string = getResources().getString(R.string.photo_mode_hdr);
            } else if (photo_mode == MyApplicationInterface.PhotoMode.ExpoBracketing) {
                photo_mode_string = getResources().getString(R.string.photo_mode_expo_bracketing_full);
            }
            if (photo_mode_string != null) {
                toast_string += "\n" + getResources().getString(R.string.photo_mode) + ": " + photo_mode_string;
                simple = false;
            }
        }
        if (applicationInterface.getFaceDetectionPref()) {
            // important so that the user realises why touching for focus/metering areas won't work - easy to forget that face detection has been turned on!
            toast_string += "\n" + getResources().getString(R.string.preference_face_detection);
            simple = false;
        }
        String iso_value = sharedPreferences.getString(Keys.getISOPreferenceKey(), camera_controller.getDefaultISO());
        if (!iso_value.equals(camera_controller.getDefaultISO())) {
            toast_string += "\nISO: " + iso_value;
            if (preview.supportsExposureTime()) {
                long exposure_time_value = sharedPreferences.getLong(Keys.getExposureTimePreferenceKey(), camera_controller.getDefaultExposureTime());
                toast_string += " " + preview.getExposureTimeString(exposure_time_value);
            }
            simple = false;
        }
        int current_exposure = camera_controller.getExposureCompensation();
        if (current_exposure != 0) {
            toast_string += "\n" + preview.getExposureCompensationString(current_exposure);
            simple = false;
        }
        String scene_mode = camera_controller.getSceneMode();
        if (scene_mode != null && !scene_mode.equals(camera_controller.getDefaultSceneMode())) {
            toast_string += "\n" + getResources().getString(R.string.scene_mode) + ": " + scene_mode;
            simple = false;
        }
        String white_balance = camera_controller.getWhiteBalance();
        if (white_balance != null && !white_balance.equals(camera_controller.getDefaultWhiteBalance())) {
            toast_string += "\n" + getResources().getString(R.string.white_balance) + ": " + white_balance;
            simple = false;
        }
        String color_effect = camera_controller.getColorEffect();
        if (color_effect != null && !color_effect.equals(camera_controller.getDefaultColorEffect())) {
            toast_string += "\n" + getResources().getString(R.string.color_effect) + ": " + color_effect;
            simple = false;
        }
        String lock_orientation = sharedPreferences.getString(Keys.getLockOrientationPreferenceKey(), "none");
        if (!lock_orientation.equals("none")) {
            String[] entries_array = getResources().getStringArray(R.array.preference_lock_orientation_entries);
            String[] values_array = getResources().getStringArray(R.array.preference_lock_orientation_values);
            int index = Arrays.asList(values_array).indexOf(lock_orientation);
            if (index != -1) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + entry;
                simple = false;
            }
        }
        String timer = sharedPreferences.getString(Keys.getTimerPreferenceKey(), "0");
        if (!timer.equals("0")) {
            String[] entries_array = getResources().getStringArray(R.array.preference_timer_entries);
            String[] values_array = getResources().getStringArray(R.array.preference_timer_values);
            int index = Arrays.asList(values_array).indexOf(timer);
            if (index != -1) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + getResources().getString(R.string.preference_timer) + ": " + entry;
                simple = false;
            }
        }
        String repeat = applicationInterface.getRepeatPref();
        if (!repeat.equals("1")) {
            String[] entries_array = getResources().getStringArray(R.array.preference_burst_mode_entries);
            String[] values_array = getResources().getStringArray(R.array.preference_burst_mode_values);
            int index = Arrays.asList(values_array).indexOf(repeat);
            if (index != -1) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + getResources().getString(R.string.preference_burst_mode) + ": " + entry;
                simple = false;
            }
        }
		/*if( audio_listener != null ) {
			toast_string += "\n" + getResources().getString(R.string.preference_audio_noise_control);
		}*/

        if (Debug.LOG) {
            Log.d(TAG, "toast_string: " + toast_string);
            Log.d(TAG, "simple?: " + simple);
        }
        if (!simple || always_show)
            preview.showToast(switch_video_toast, toast_string);
    }

    private void freeAudioListener(boolean wait_until_done) {
        if (Debug.LOG)
            Log.d(TAG, "freeAudioListener");
        if (audio_listener != null) {
            audio_listener.release();
            if (wait_until_done) {
                if (Debug.LOG)
                    Log.d(TAG, "wait until audio listener is freed");
                while (audio_listener.hasAudioRecorder()) {
                }
            }
            audio_listener = null;
        }
        mainui.audioControlStopped();
    }

    private void startAudioListener() {
        if (Debug.LOG)
            Log.d(TAG, "startAudioListener");
        audio_listener = new AudioListener(this);
        audio_listener.start();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String sensitivity_pref = sharedPreferences.getString(Keys.getAudioNoiseControlSensitivityPreferenceKey(), "0");
        if (sensitivity_pref.equals("3")) {
            audio_noise_sensitivity = 50;
        } else if (sensitivity_pref.equals("2")) {
            audio_noise_sensitivity = 75;
        } else if (sensitivity_pref.equals("1")) {
            audio_noise_sensitivity = 125;
        } else if (sensitivity_pref.equals("-1")) {
            audio_noise_sensitivity = 150;
        } else if (sensitivity_pref.equals("-2")) {
            audio_noise_sensitivity = 200;
        } else {
            // default
            audio_noise_sensitivity = 100;
        }
        mainui.audioControlStarted();
    }

    private void initSpeechRecognizer() {
        if (Debug.LOG)
            Log.d(TAG, "initSpeechRecognizer");
        // in theory we could create the speech recognizer always (hopefully it shouldn't use battery when not listening?), though to be safe, we only do this when the option is enabled (e.g., just in case this doesn't work on some devices!)
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean want_speech_recognizer = sharedPreferences.getString(Keys.getAudioControlPreferenceKey(), "none").equals("voice");
        if (speechRecognizer == null && want_speech_recognizer) {
            if (Debug.LOG)
                Log.d(TAG, "create new speechRecognizer");
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            if (speechRecognizer != null) {
                speechRecognizerIsStarted = false;
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onBeginningOfSpeech() {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onBeginningOfSpeech");
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onBufferReceived");
                    }

                    @Override
                    public void onEndOfSpeech() {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onEndOfSpeech");
                        speechRecognizerStopped();
                    }

                    @Override
                    public void onError(int error) {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onError: " + error);
                        if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                            // we sometime receive ERROR_NO_MATCH straight after listening starts
                            // it seems that the end is signalled either by ERROR_SPEECH_TIMEOUT or onEndOfSpeech()
                            speechRecognizerStopped();
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onEvent");
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onPartialResults");
                    }

                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onReadyForSpeech");
                    }

                    public void onResults(Bundle results) {
                        if (Debug.LOG)
                            Log.d(TAG, "RecognitionListener: onResults");
                        speechRecognizerStopped();
                        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                        boolean found = false;
                        final String trigger = "cheese";
                        //String debug_toast = "";
                        for (int i = 0; i < list.size(); i++) {
                            String text = list.get(i);
                            if (Debug.LOG)
                                Log.d(TAG, "text: " + text + " score: " + scores[i]);
							/*if( i > 0 )
								debug_toast += "\n";
							debug_toast += text + " : " + scores[i];*/
                            if (text.toLowerCase(Locale.US).contains(trigger)) {
                                found = true;
                            }
                        }
                        //preview.showToast(null, debug_toast); // debug only!
                        if (found) {
                            if (Debug.LOG)
                                Log.d(TAG, "audio trigger from speech recognition");
                            audioTrigger();
                        } else if (list.size() > 0) {
                            String toast = list.get(0) + "?";
                            if (Debug.LOG)
                                Log.d(TAG, "unrecognised: " + toast);
                            preview.showToast(audio_control_toast, toast);
                        }
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                    }
                });
                if (!mainui.inImmersiveMode()) {
                    View speechRecognizerButton = (View) findViewById(R.id.audio_control);
                    speechRecognizerButton.setVisibility(View.VISIBLE);
                }
            }
        } else if (speechRecognizer != null && !want_speech_recognizer) {
            if (Debug.LOG)
                Log.d(TAG, "free existing SpeechRecognizer");
            freeSpeechRecognizer();
        }
    }

    private void freeSpeechRecognizer() {
        if (Debug.LOG)
            Log.d(TAG, "freeSpeechRecognizer");
        if (speechRecognizer != null) {
            speechRecognizerStopped();
            View speechRecognizerButton = (View) findViewById(R.id.audio_control);
            speechRecognizerButton.setVisibility(View.GONE);
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    public boolean hasAudioControl() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String audio_control = sharedPreferences.getString(Keys.getAudioControlPreferenceKey(), "none");
        if (audio_control.equals("voice")) {
            return speechRecognizer != null;
        } else if (audio_control.equals("noise")) {
            return true;
        }
        return false;
    }

	/*void startAudioListeners() {
		initAudioListener();
		// no need to restart speech recognizer, as we didn't free it in stopAudioListeners(), and it's controlled by a user button
	}*/

    public void stopAudioListeners() {
        freeAudioListener(true);
        if (speechRecognizer != null) {
            // no need to free the speech recognizer, just stop it
            speechRecognizer.stopListening();
            speechRecognizerStopped();
        }
    }

    private void initLocation() {
        if (Debug.LOG)
            Log.d(TAG, "initLocation");
        if (!applicationInterface.getLocationSupplier().setupLocationListener(this)) {
            if (Debug.LOG)
                Log.d(TAG, "location permission not available, so request permission");
            requestLocationPermission();
        }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initSound() {
        if (sound_pool == null) {
            if (Debug.LOG)
                Log.d(TAG, "create new sound_pool");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audio_attributes = new AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_SYSTEM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                sound_pool = new SoundPool.Builder()
                        .setMaxStreams(1)
                        .setAudioAttributes(audio_attributes)
                        .build();
            } else {
                sound_pool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
            }
            sound_ids = new SparseIntArray();
        }
    }

    private void releaseSound() {
        if (sound_pool != null) {
            if (Debug.LOG)
                Log.d(TAG, "release sound_pool");
            sound_pool.release();
            sound_pool = null;
            sound_ids = null;
        }
    }

    // must be called before playSound (allowing enough time to load the sound)
    void loadSound(int resource_id) {
        if (sound_pool != null) {
            if (Debug.LOG)
                Log.d(TAG, "loading sound resource: " + resource_id);
            int sound_id = sound_pool.load(this, resource_id, 1);
            if (Debug.LOG)
                Log.d(TAG, "    loaded sound: " + sound_id);
            sound_ids.put(resource_id, sound_id);
        }
    }

    // must call loadSound first (allowing enough time to load the sound)
    void playSound(int resource_id) {
        if (sound_pool != null) {
            if (sound_ids.indexOfKey(resource_id) < 0) {
                if (Debug.LOG)
                    Log.d(TAG, "resource not loaded: " + resource_id);
            } else {
                int sound_id = sound_ids.get(resource_id);
                if (Debug.LOG)
                    Log.d(TAG, "play sound: " + sound_id);
                sound_pool.play(sound_id, 1.0f, 1.0f, 0, 0, 1);
            }
        }
    }

    @SuppressWarnings("deprecation")
    void speak(String text) {
        if (textToSpeech != null && textToSpeechSuccess) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // Android 6+ permission handling:

    final private int MY_PERMISSIONS_REQUEST_CAMERA = 0;
    final private int MY_PERMISSIONS_REQUEST_STORAGE = 1;
    final private int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 2;
    final private int MY_PERMISSIONS_REQUEST_LOCATION = 3;

    /**
     * Show a "rationale" to the user for needing a particular permission, then request that permission again
     * once they close the dialog.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void showRequestPermissionRationale(final int permission_code) {
        if (Debug.LOG)
            Log.d(TAG, "showRequestPermissionRational: " + permission_code);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Debug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        boolean ok = true;
        String[] permissions = null;
        int message_id = 0;
        if (permission_code == MY_PERMISSIONS_REQUEST_CAMERA) {
            if (Debug.LOG)
                Log.d(TAG, "display rationale for camera permission");
            permissions = new String[]{Manifest.permission.CAMERA};
            message_id = R.string.permission_rationale_camera;
        } else if (permission_code == MY_PERMISSIONS_REQUEST_STORAGE) {
            if (Debug.LOG)
                Log.d(TAG, "display rationale for storage permission");
            permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
            message_id = R.string.permission_rationale_storage;
        } else if (permission_code == MY_PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (Debug.LOG)
                Log.d(TAG, "display rationale for record audio permission");
            permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            message_id = R.string.permission_rationale_record_audio;
        } else if (permission_code == MY_PERMISSIONS_REQUEST_LOCATION) {
            if (Debug.LOG)
                Log.d(TAG, "display rationale for location permission");
            permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            message_id = R.string.permission_rationale_location;
        } else {
            if (Debug.LOG)
                Log.e(TAG, "showRequestPermissionRational unknown permission_code: " + permission_code);
            ok = false;
        }

        if (ok) {
            final String[] permissions_f = permissions;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(message_id)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(new OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            if (Debug.LOG)
                                Log.d(TAG, "requesting permission...");
                            ActivityCompat.requestPermissions(HomeActivity.this, permissions_f, permission_code);
                        }
                    }).show();
        }
    }

    void requestCameraPermission() {
        if (Debug.LOG)
            Log.d(TAG, "requestCameraPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Debug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_CAMERA);
        } else {
            // Can go ahead and request the permission
            if (Debug.LOG)
                Log.d(TAG, "requesting camera permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    void requestStoragePermission() {
        if (Debug.LOG)
            Log.d(TAG, "requestStoragePermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Debug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_STORAGE);
        } else {
            // Can go ahead and request the permission
            if (Debug.LOG)
                Log.d(TAG, "requesting storage permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_STORAGE);
        }
    }

    void requestRecordAudioPermission() {
        if (Debug.LOG)
            Log.d(TAG, "requestRecordAudioPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Debug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            // Can go ahead and request the permission
            if (Debug.LOG)
                Log.d(TAG, "requesting record audio permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    void requestLocationPermission() {
        if (Debug.LOG)
            Log.d(TAG, "requestLocationPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Debug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_LOCATION);
        } else {
            // Can go ahead and request the permission
            if (Debug.LOG)
                Log.d(TAG, "requesting loacation permissions...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (Debug.LOG)
            Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Debug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (Debug.LOG)
                        Log.d(TAG, "camera permission granted");
                    preview.retrygif();
                } else {
                    if (Debug.LOG)
                        Log.d(TAG, "camera permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (Debug.LOG)
                        Log.d(TAG, "storage permission granted");
                    preview.retrygif();
                } else {
                    if (Debug.LOG)
                        Log.d(TAG, "storage permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (Debug.LOG)
                        Log.d(TAG, "record audio permission granted");
                    // no need to do anything
                } else {
                    if (Debug.LOG)
                        Log.d(TAG, "record audio permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // no need to do anything
                    // note that we don't turn off record audio option, as user may then record video not realising audio won't be recorded - best to be explicit each time
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (Debug.LOG)
                        Log.d(TAG, "location permission granted");
                    initLocation();
                } else {
                    if (Debug.LOG)
                        Log.d(TAG, "location permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // for location, seems best to turn the option back off
                    if (Debug.LOG)
                        Log.d(TAG, "location permission not available, so switch location off");
                    preview.showToast(null, R.string.permission_location_not_available);
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean(Keys.getLocationPreferenceKey(), false);
                    editor.apply();
                }
                return;
            }
            default: {
                if (Debug.LOG)
                    Log.e(TAG, "unknown requestCode " + requestCode);
            }
        }
    }

    // for testing:
    public LocationHistory getSaveLocationHistory() {
        return this.save_location_history;
    }

    public LocationHistory getSaveLocationHistorySAF() {
        return this.save_location_history_saf;
    }

    public void usedFolderPicker() {
        if (applicationInterface.getStorage().isUsingSAF()) {
            save_location_history_saf.updateFolderHistory(getStorageUtils().getSaveLocationSAF(), true);
        } else {
            save_location_history.updateFolderHistory(getStorageUtils().getSaveLocation(), true);
        }
    }

    public boolean hasThumbnailAnimation() {
        return this.applicationInterface.hasThumbnailAnimation();
    }

    private void AppleSetContentView() {
        //Apple Inc creation

        con = this;

        IntentFilter filter = new IntentFilter(EXIT_ACTIVITY);
        ExitReceiver = new HomeActivity.Exit_Receiver();
        registerReceiver(ExitReceiver, filter);

    }


    class Exit_Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("Exit_Receiver", "Exit_Receiver");
            if (intent.getAction().equals(EXIT_ACTIVITY)) {
                HomeActivity.this.finish();
            }
        }
    }


}
