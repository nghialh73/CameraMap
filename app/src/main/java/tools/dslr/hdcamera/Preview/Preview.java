package tools.dslr.hdcamera.Preview;

import tools.dslr.hdcamera.CameraController.ControllerException;
import tools.dslr.hdcamera.CameraController.Controller;
import tools.dslr.hdcamera.CameraController.Controller1;
import tools.dslr.hdcamera.CameraController.ControllerManager1;
import tools.dslr.hdcamera.CameraController.ControllerManager2;
import tools.dslr.hdcamera.Debug;
import tools.dslr.hdcamera.Preview.CameraSurface.CameraSurface;
import tools.dslr.hdcamera.Preview.CameraSurface.MySurfaceView;
import tools.dslr.hdcamera.Preview.CameraSurface.MyTextureView;
import tools.dslr.hdcamera.R;
import tools.dslr.hdcamera.TakePhoto;
import tools.dslr.hdcamera.ToastBoxer;
import tools.dslr.hdcamera.CameraController.Controller2;
import tools.dslr.hdcamera.CameraController.ControllerManager;
import tools.dslr.hdcamera.Preview.ApplicationInterface.NoFreeStorageException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.MeasureSpec;
import android.widget.Toast;

/** This class was originally named due to encapsulating the camera preview,
 *  but in practice it's grown to more than this, and includes most of the
 *  operation of the camera. It exists at a higher level than Controller
 *  (i.e., this isn't merely a low level wrapper to the camera API, but
 *  supports much of the Open Camera logic and functionality). Communication to
 *  the rest of the application is available through ApplicationInterface.
 *  We could probably do with decoupling this class into separate components!
 */
public class Preview implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {
    private static final String TAG = "Preview";

    private boolean using_android_l = false;
    private boolean using_texture_view = false;

    private ApplicationInterface applicationInterface = null;
    private CameraSurface cameraSurface = null;
    private CanvasView canvasView = null;
    private boolean set_preview_size = false;
    private int preview_w = 0, preview_h = 0;
    private boolean set_textureview_size = false;
    private int textureview_w = 0, textureview_h = 0;

    private Matrix camera_to_preview_matrix = new Matrix();
    private Matrix preview_to_camera_matrix = new Matrix();
    //private RectF face_rect = new RectF();
    private double preview_targetRatio = 0.0;

    //private boolean ui_placement_right = true;

    private boolean app_is_paused = true;
    private boolean has_surface = false;
    private boolean has_aspect_ratio = false;
    private double aspect_ratio = 0.0f;
    private ControllerManager camera_controller_manager = null;
    private Controller camera_controller = null;
    private boolean has_permissions = true; // whether we have permissions necessary to operate the camera (camera, storage); assume true until we've been denied one of them
    private boolean is_video = false;
    private MediaRecorder video_recorder = null;
    private boolean video_start_time_set = false;
    private long video_start_time = 0;
    private long video_accumulated_time = 0;
    private boolean video_restart_on_max_filesize = false;
    private static final long min_safe_restart_video_time = 1000; // if the remaining max time after restart is less than this, don't restart
    private int video_method = ApplicationInterface.VIDEOMETHOD_FILE;
    private Uri video_uri = null; // for VIDEOMETHOD_SAF or VIDEOMETHOD_URI
    private String video_filename = null; // for VIDEOMETHOD_FILE

    private static final int PHASE_NORMAL = 0;
    private static final int PHASE_TIMER = 1;
    private static final int PHASE_TAKING_PHOTO = 2;
    private static final int PHASE_PREVIEW_PAUSED = 3; // the paused state after taking a photo
    private int phase = PHASE_NORMAL;
    private Timer takePictureTimer = new Timer();
    private TimerTask takePictureTimerTask = null;
    private Timer beepTimer = new Timer();
    private TimerTask beepTimerTask = null;
    private Timer restartVideoTimer = new Timer();
    private TimerTask restartVideoTimerTask = null;
    private Timer flashVideoTimer = new Timer();
    private TimerTask flashVideoTimerTask = null;
    private IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private Timer batteryCheckVideoTimer = new Timer();
    private TimerTask batteryCheckVideoTimerTask = null;
    private long take_photo_time = 0;
    private int remaining_burst_photos = 0;
    private int remaining_restart_video = 0;

    private boolean is_preview_started = false;

    private int current_orientation = 0; // orientation received by onOrientationChanged
    private int current_rotation = 0; // orientation relative to camera's orientation (used for parameters.setRotation())
    private boolean has_level_angle = false;
    private double level_angle = 0.0f;
    private double orig_level_angle = 0.0f;

    private boolean has_zoom = false;
    private int max_zoom_factor = 0;
    private GestureDetector gestureDetector = null;
    private ScaleGestureDetector scaleGestureDetector = null;
    private List<Integer> zoom_ratios = null;
    private float minimum_focus_distance = 0.0f;
    private boolean touch_was_multitouch = false;
    private float touch_orig_x = 0.0f;
    private float touch_orig_y = 0.0f;

    private List<String> supported_flash_values = null; // our "values" format
    private int current_flash_index = -1; // this is an index into the supported_flash_values array, or -1 if no flash modes available

    private List<String> supported_focus_values = null; // our "values" format
    private int current_focus_index = -1; // this is an index into the supported_focus_values array, or -1 if no focus modes available
    private int max_num_focus_areas = 0;
    private boolean continuous_focus_move_is_started = false;

    private boolean is_exposure_lock_supported = false;
    private boolean is_exposure_locked = false;

    private List<String> color_effects = null;
    private List<String> scene_modes = null;
    private List<String> white_balances = null;
    private List<String> isos = null;
    private boolean supports_iso_range = false;
    private int min_iso = 0;
    private int max_iso = 0;
    private boolean supports_exposure_time = false;
    private long min_exposure_time = 0l;
    private long max_exposure_time = 0l;
    private List<String> exposures = null;
    private int min_exposure = 0;
    private int max_exposure = 0;
    private float exposure_step = 0.0f;
    private boolean supports_expo_bracketing = false;
    private boolean supports_raw = false;

    private List<Controller.Size> supported_preview_sizes = null;

    private List<Controller.Size> sizes = null;
    private int current_size_index = -1; // this is an index into the sizes array, or -1 if sizes not yet set

    // video_quality can either be:
    // - an int, in which case it refers to a CamcorderProfile
    // - of the form [CamcorderProfile]_r[width]x[height] - we use the CamcorderProfile as a base, and override the video resolution - this is needed to support resolutions which don't have corresponding camcorder profiles
    private List<String> video_quality = null;
    private int current_video_quality = -1; // this is an index into the video_quality array, or -1 if not found (though this shouldn't happen?)
    private List<Controller.Size> video_sizes = null;

	/*private Bitmap location_bitmap = null;
	private Bitmap location_off_bitmap = null;
	private Rect location_dest = new Rect();*/

    private Toast last_toast = null;
    private ToastBoxer flash_toast = new ToastBoxer();
    private ToastBoxer focus_toast = new ToastBoxer();
    private ToastBoxer take_photo_toast = new ToastBoxer();
    private ToastBoxer seekbar_toast = new ToastBoxer();

    private int ui_rotation = 0;

    private boolean supports_face_detection = false;
    private boolean using_face_detection = false;
    private Controller.Face [] faces_detected = null;
    private boolean supports_video_stabilization = false;
    private boolean can_disable_shutter_sound = false;
    private boolean has_focus_area = false;
    private int focus_screen_x = 0;
    private int focus_screen_y = 0;
    private long focus_complete_time = -1;
    private long focus_started_time = -1;
    private int focus_success = FOCUS_DONE;
    private static final int FOCUS_WAITING = 0;
    private static final int FOCUS_SUCCESS = 1;
    private static final int FOCUS_FAILED = 2;
    private static final int FOCUS_DONE = 3;
    private String set_flash_value_after_autofocus = "";
    private boolean take_photo_after_autofocus = false; // set to take a photo when the in-progress autofocus has completed
    private boolean successfully_focused = false;
    private long successfully_focused_time = -1;

    // accelerometer and geomagnetic sensor info
    private static final float sensor_alpha = 0.8f; // for filter
    private boolean has_gravity = false;
    private float [] gravity = new float[3];
    private boolean has_geomagnetic = false;
    private float [] geomagnetic = new float[3];
    private float [] deviceRotation = new float[9];
    private float [] cameraRotation = new float[9];
    private float [] deviceInclination = new float[9];
    private boolean has_geo_direction = false;
    private float [] geo_direction = new float[3];

    private final DecimalFormat decimal_format_1dp = new DecimalFormat("#.#");
    private final DecimalFormat decimal_format_2dp = new DecimalFormat("#.##");

    /* If the user touches to focus in continuous mode, we switch the camera_controller to autofocus mode.
     * autofocus_in_continuous_mode is set to true when this happens; the runnable reset_continuous_focus_runnable
     * switches back to continuous mode.
     */
    private Handler reset_continuous_focus_handler = new Handler();
    private Runnable reset_continuous_focus_runnable = null;
    private boolean autofocus_in_continuous_mode = false;

    // for testing:
    public int count_cameraStartPreview = 0;
    public int count_cameraAutoFocus = 0;
    public int count_cameraTakePicture = 0;
    public int count_cameraContinuousFocusMoving = 0;
    public boolean test_fail_open_camera = false;
    public boolean test_video_failure = false;

    public Preview(ApplicationInterface applicationInterface, Bundle savedInstanceState, ViewGroup parent) {
        if( Debug.LOG ) {
            Log.d(TAG, "new Preview");
        }

        this.applicationInterface = applicationInterface;

        this.using_android_l = applicationInterface.useCamera2();
        if( Debug.LOG ) {
            Log.d(TAG, "using_android_l?: " + using_android_l);
        }

        if( using_android_l ) {
            // use a TextureView for Android L - had bugs with SurfaceView not resizing properly on Nexus 7; and good to use a TextureView anyway
            // ideally we'd use a TextureView for older camera API too, but sticking with SurfaceView to avoid risk of breaking behaviour
            this.using_texture_view = true;
        }

        if( using_texture_view ) {
            this.cameraSurface = new MyTextureView(getContext(), savedInstanceState, this);
            // a TextureView can't be used both as a camera preview, and used for drawing on, so we use a separate CanvasView
            this.canvasView = new CanvasView(getContext(), savedInstanceState, this);
            camera_controller_manager = new ControllerManager2(getContext());
        }
        else {
            this.cameraSurface = new MySurfaceView(getContext(), savedInstanceState, this);
            camera_controller_manager = new ControllerManager1();
        }

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener());
        gestureDetector.setOnDoubleTapListener(new DoubleTapListener());
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

        parent.addView(cameraSurface.getView());
        if( canvasView != null ) {
            parent.addView(canvasView);
        }
    }

	/*private void previewToCamera(float [] coords) {
		float alpha = coords[0] / (float)this.getWidth();
		float beta = coords[1] / (float)this.getHeight();
		coords[0] = 2000.0f * alpha - 1000.0f;
		coords[1] = 2000.0f * beta - 1000.0f;
	}*/

	/*private void cameraToPreview(float [] coords) {
		float alpha = (coords[0] + 1000.0f) / 2000.0f;
		float beta = (coords[1] + 1000.0f) / 2000.0f;
		coords[0] = alpha * (float)this.getWidth();
		coords[1] = beta * (float)this.getHeight();
	}*/

    private Resources getResources() {
        return cameraSurface.getView().getResources();
    }

    public View getView() {
        return cameraSurface.getView();
    }

    // If this code is changed, important to test that face detection and touch to focus still works as expected, for front and back
    // cameras, for old and new API, including with zoom. Also test with HomeActivity.setWindowFlagsForCamera() setting orientation as SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
    // and/or set "Rotate preview" option to 180 degrees.
    private void calculateCameraToPreviewMatrix() {
        if( Debug.LOG )
            Log.d(TAG, "calculateCameraToPreviewMatrix");
        if( camera_controller == null )
            return;
        camera_to_preview_matrix.reset();
        if( !using_android_l ) {
            // from http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
            // Need mirror for front camera
            boolean mirror = camera_controller.isFrontFacing();
            camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
            // This is the value for android.hardware.Camera.setDisplayOrientation.
            int display_orientation = camera_controller.getDisplayOrientation();
            if( Debug.LOG ) {
                Log.d(TAG, "orientation of display relative to camera orientaton: " + display_orientation);
            }
            camera_to_preview_matrix.postRotate(display_orientation);
        }
        else {
            // Unfortunately the transformation for Android L API isn't documented, but this seems to work for Nexus 6.
            // This is the equivalent code for android.hardware.Camera.setDisplayOrientation, but we don't actually use setDisplayOrientation()
            // for Controller2, so instead this is the equivalent code to https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int),
            // except testing on Nexus 6 shows that we shouldn't change "result" for front facing camera.
            boolean mirror = camera_controller.isFrontFacing();
            camera_to_preview_matrix.setScale(1, mirror ? -1 : 1);
            int degrees = getDisplayRotationDegrees();
            int result = (camera_controller.getCameraOrientation() - degrees + 360) % 360;
            if( Debug.LOG ) {
                Log.d(TAG, "orientation of display relative to natural orientaton: " + degrees);
                Log.d(TAG, "orientation of display relative to camera orientaton: " + result);
            }
            camera_to_preview_matrix.postRotate(result);
        }
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        camera_to_preview_matrix.postScale(cameraSurface.getView().getWidth() / 2000f, cameraSurface.getView().getHeight() / 2000f);
        camera_to_preview_matrix.postTranslate(cameraSurface.getView().getWidth() / 2f, cameraSurface.getView().getHeight() / 2f);
    }

    private void calculatePreviewToCameraMatrix() {
        if( camera_controller == null )
            return;
        calculateCameraToPreviewMatrix();
        if( !camera_to_preview_matrix.invert(preview_to_camera_matrix) ) {
            if( Debug.LOG )
                Log.d(TAG, "calculatePreviewToCameraMatrix failed to invert matrix!?");
        }
    }

    public Matrix getCameraToPreviewMatrix() {
        calculateCameraToPreviewMatrix();
        return camera_to_preview_matrix;
    }

    Matrix getPreviewToCameraMatrix() {
        calculatePreviewToCameraMatrix();
        return preview_to_camera_matrix;
    }

    private ArrayList<Controller.Area> getAreas(float x, float y) {
        float [] coords = {x, y};
        calculatePreviewToCameraMatrix();
        preview_to_camera_matrix.mapPoints(coords);
        float focus_x = coords[0];
        float focus_y = coords[1];

        int focus_size = 50;
        if( Debug.LOG ) {
            Log.d(TAG, "x, y: " + x + ", " + y);
            Log.d(TAG, "focus x, y: " + focus_x + ", " + focus_y);
        }
        Rect rect = new Rect();
        rect.left = (int)focus_x - focus_size;
        rect.right = (int)focus_x + focus_size;
        rect.top = (int)focus_y - focus_size;
        rect.bottom = (int)focus_y + focus_size;
        if( rect.left < -1000 ) {
            rect.left = -1000;
            rect.right = rect.left + 2*focus_size;
        }
        else if( rect.right > 1000 ) {
            rect.right = 1000;
            rect.left = rect.right - 2*focus_size;
        }
        if( rect.top < -1000 ) {
            rect.top = -1000;
            rect.bottom = rect.top + 2*focus_size;
        }
        else if( rect.bottom > 1000 ) {
            rect.bottom = 1000;
            rect.top = rect.bottom - 2*focus_size;
        }

        ArrayList<Controller.Area> areas = new ArrayList<Controller.Area>();
        areas.add(new Controller.Area(rect, 1000));
        return areas;
    }

    public boolean touchEvent(MotionEvent event) {
        if( Debug.LOG )
            Log.d(TAG, "touch event at : " + event.getX() + " , " + event.getY() + " at time " + event.getEventTime());
        if( gestureDetector.onTouchEvent(event) ) {
            if( Debug.LOG )
                Log.d(TAG, "touch event handled by gestureDetector");
            return true;
        }
        scaleGestureDetector.onTouchEvent(event);
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "try to reopen camera due to touch");
            this.gif();
            return true;
        }
        applicationInterface.touchEvent(event);
        //invalidate();
		/*if( Debug.LOG ) {
			Log.d(TAG, "touch event: " + event.getAction());
		}*/
        if( event.getPointerCount() != 1 ) {
            //multitouch_time = System.currentTimeMillis();
            touch_was_multitouch = true;
            return true;
        }
        if( event.getAction() != MotionEvent.ACTION_UP ) {
            if( event.getAction() == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1 ) {
                touch_was_multitouch = false;
                if( event.getAction() == MotionEvent.ACTION_DOWN ) {
                    touch_orig_x = event.getX();
                    touch_orig_y = event.getY();
                    if( Debug.LOG )
                        Log.d(TAG, "touch down at " + touch_orig_x + " , " + touch_orig_y);
                }
            }
            return true;
        }
        // now only have to handle MotionEvent.ACTION_UP from this point onwards

        if( touch_was_multitouch ) {
            return true;
        }
        if( !this.is_video && this.isTakingPhotoOrOnTimer() ) {
            // if video, okay to refocus when recording
            return true;
        }

        // ignore swipes
        {
            float x = event.getX();
            float y = event.getY();
            float diff_x = x - touch_orig_x;
            float diff_y = y - touch_orig_y;
            float dist2 = diff_x*diff_x + diff_y*diff_y;
            float scale = getResources().getDisplayMetrics().density;
            float tol = 31 * scale + 0.5f; // convert dps to pixels (about 0.5cm)
            if( Debug.LOG ) {
                Log.d(TAG, "touched from " + touch_orig_x + " , " + touch_orig_y + " to " + x + " , " + y);
                Log.d(TAG, "dist: " + Math.sqrt(dist2));
                Log.d(TAG, "tol: " + tol);
            }
            if( dist2 > tol*tol ) {
                if( Debug.LOG )
                    Log.d(TAG, "touch was a swipe");
                return true;
            }
        }

        // note, we always try to force start the preview (in case is_preview_paused has become false)
        // except if recording video (firstly, the preview should be running; secondly, we don't want to reset the phase!)
        if( !this.is_video ) {
            startCameraPreview();
        }
        cancelAutoFocus();

        if( camera_controller != null && !this.using_face_detection ) {
            this.has_focus_area = false;
            ArrayList<Controller.Area> areas = getAreas(event.getX(), event.getY());
            if( camera_controller.setFocusAndMeteringArea(areas) ) {
                if( Debug.LOG )
                    Log.d(TAG, "set focus (and metering?) area");
                this.has_focus_area = true;
                this.focus_screen_x = (int)event.getX();
                this.focus_screen_y = (int)event.getY();
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "didn't set focus area in this mode, may have set metering");
                // don't set has_focus_area in this mode
            }
        }

        if( !this.is_video && applicationInterface.getTouchCapturePref() ) {
            if( Debug.LOG )
                Log.d(TAG, "touch to capture");
            // interpret as if user had clicked take photo/video button, except that we set the focus/metering areas
            this.takePicturePressed();
            return true;
        }

        tryAutoFocus(false, true);
        return true;
    }

    //@SuppressLint("ClickableViewAccessibility") @Override

    /** Handle multitouch zoom.
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if( Preview.this.camera_controller != null && Preview.this.has_zoom ) {
                Preview.this.scaleZoom(detector.getScaleFactor());
            }
            return true;
        }
    }

    public boolean onDoubleTap() {
        if( Debug.LOG )
            Log.d(TAG, "onDoubleTap()");
        if( !is_video && applicationInterface.getDoubleTapCapturePref() ) {
            if( Debug.LOG )
                Log.d(TAG, "double-tap to capture");
            // interpret as if user had clicked take photo/video button (don't need to set focus/metering, as this was done in touchEvent() for the first touch of the double-tap)
            takePicturePressed();
        }
        return true;
    }

    private class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if( Debug.LOG )
                Log.d(TAG, "onDoubleTap()");
            return Preview.this.onDoubleTap();
        }
    }

    public void clearFocusAreas() {
        if( Debug.LOG )
            Log.d(TAG, "clearFocusAreas()");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        // don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
        camera_controller.clearFocusAndMetering();
        has_focus_area = false;
        focus_success = FOCUS_DONE;
        successfully_focused = false;
    }

    public void getMeasureSpec(int [] spec, int widthSpec, int heightSpec) {
        if( !this.hasAspectRatio() ) {
            spec[0] = widthSpec;
            spec[1] = heightSpec;
            return;
        }
        double aspect_ratio = this.getAspectRatio();

        int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);

        // Get the padding of the border background.
        int hPadding = cameraSurface.getView().getPaddingLeft() + cameraSurface.getView().getPaddingRight();
        int vPadding = cameraSurface.getView().getPaddingTop() + cameraSurface.getView().getPaddingBottom();

        // Resize the preview frame with correct aspect ratio.
        previewWidth -= hPadding;
        previewHeight -= vPadding;

        boolean widthLonger = previewWidth > previewHeight;
        int longSide = (widthLonger ? previewWidth : previewHeight);
        int shortSide = (widthLonger ? previewHeight : previewWidth);
        if (longSide > shortSide * aspect_ratio) {
            longSide = (int) ((double) shortSide * aspect_ratio);
        } else {
            shortSide = (int) ((double) longSide / aspect_ratio);
        }
        if (widthLonger) {
            previewWidth = longSide;
            previewHeight = shortSide;
        } else {
            previewWidth = shortSide;
            previewHeight = longSide;
        }

        // Add the padding of the border.
        previewWidth += hPadding;
        previewHeight += vPadding;

        spec[0] = MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY);
        spec[1] = MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY);
    }

    private void mySurfaceCreated() {
        this.has_surface = true;
        this.gif();
    }

    private void mySurfaceDestroyed() {
        this.has_surface = false;
        this.closeCamera();
    }

    private void mySurfaceChanged() {
        // surface size is now changed to match the aspect ratio of camera preview - so we shouldn't change the preview to match the surface size, so no need to restart preview here
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }

        // need to force a layoutUI update (e.g., so UI is oriented correctly when app goes idle, device is then rotated, and app is then resumed)
        applicationInterface.layoutUI();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if( Debug.LOG )
            Log.d(TAG, "surfaceCreated()");
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        mySurfaceCreated();
        cameraSurface.getView().setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if( Debug.LOG )
            Log.d(TAG, "surfaceDestroyed()");
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
        mySurfaceDestroyed();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if( Debug.LOG )
            Log.d(TAG, "surfaceChanged " + w + ", " + h);
        if( holder.getSurface() == null ) {
            // preview surface does not exist
            return;
        }
        mySurfaceChanged();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width, int height) {
        if( Debug.LOG )
            Log.d(TAG, "onSurfaceTextureAvailable()");
        this.set_textureview_size = true;
        this.textureview_w = width;
        this.textureview_h = height;
        mySurfaceCreated();
        configureTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
        if( Debug.LOG )
            Log.d(TAG, "onSurfaceTextureDestroyed()");
        this.set_textureview_size = false;
        this.textureview_w = 0;
        this.textureview_h = 0;
        mySurfaceDestroyed();
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width, int height) {
        if( Debug.LOG )
            Log.d(TAG, "onSurfaceTextureSizeChanged " + width + ", " + height);
        this.set_textureview_size = true;
        this.textureview_w = width;
        this.textureview_h = height;
        mySurfaceChanged();
        configureTransform();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
    }

    private void configureTransform() {
        if( Debug.LOG )
            Log.d(TAG, "configureTransform");
        if( camera_controller == null || !this.set_preview_size || !this.set_textureview_size )
            return;
        if( Debug.LOG )
            Log.d(TAG, "textureview size: " + textureview_w + ", " + textureview_h);
        int rotation = getDisplayRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, this.textureview_w, this.textureview_h);
        RectF bufferRect = new RectF(0, 0, this.preview_h, this.preview_w);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if( Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation ) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) textureview_h / preview_h,
                    (float) textureview_w / preview_w);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        cameraSurface.setTransform(matrix);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void stopVideo(boolean from_restart) {
        if( Debug.LOG )
            Log.d(TAG, "stopVideo()");
        if( video_recorder == null ) {
            // no need to do anything if not recording
            // (important to exit, otherwise we'll momentarily switch the take photo icon to video mode in MyApplicationInterface.stoppingVideo() when opening the settings in landscape mode
            if( Debug.LOG )
                Log.d(TAG, "video wasn't recording anyway");
            return;
        }
        applicationInterface.stoppingVideo();
        if( restartVideoTimerTask != null ) {
            restartVideoTimerTask.cancel();
            restartVideoTimerTask = null;
        }
        if( flashVideoTimerTask != null ) {
            flashVideoTimerTask.cancel();
            flashVideoTimerTask = null;
        }
        if( batteryCheckVideoTimerTask != null ) {
            batteryCheckVideoTimerTask.cancel();
            batteryCheckVideoTimerTask = null;
        }
        if( !from_restart ) {
            remaining_restart_video = 0;
        }
        if( video_recorder != null ) { // check again, just to be safe
            if( Debug.LOG )
                Log.d(TAG, "stop video recording");
			/*is_taking_photo = false;
			is_taking_photo_on_timer = false;*/
            this.phase = PHASE_NORMAL;
            try {
                video_recorder.setOnErrorListener(null);
                video_recorder.setOnInfoListener(null);
                if( Debug.LOG )
                    Log.d(TAG, "about to call video_recorder.stop()");
                video_recorder.stop();
                if( Debug.LOG )
                    Log.d(TAG, "done video_recorder.stop()");
            }
            catch(RuntimeException e) {
                // stop() can throw a RuntimeException if stop is called too soon after start - this indicates the video file is corrupt, and should be deleted
                if( Debug.LOG )
                    Log.d(TAG, "runtime exception when stopping video");
                if( video_method == ApplicationInterface.VIDEOMETHOD_SAF ) {
                    if( video_uri != null ) {
                        if( Debug.LOG )
                            Log.d(TAG, "delete corrupt video: " + video_uri);
                        try {
                            DocumentsContract.deleteDocument(getContext().getContentResolver(), video_uri);
                        } catch (FileNotFoundException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                else if( video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
                    if( video_filename != null ) {
                        if( Debug.LOG )
                            Log.d(TAG, "delete corrupt video: " + video_filename);
                        File file = new File(video_filename);
                        if( !file.delete() ) {
                            if( Debug.LOG )
                                Log.e(TAG, "failed to delete corrupt video: " + video_filename);
                        }
                    }
                }
                else {
                    // don't delete if a plain Uri
                }
                video_method = ApplicationInterface.VIDEOMETHOD_FILE;
                video_uri = null;
                video_filename = null;
                // if video recording is stopped quickly after starting, it's normal that we might not have saved a valid file, so no need to display a message
                if( !video_start_time_set || System.currentTimeMillis() - video_start_time > 2000 ) {
                    CamcorderProfile profile = getCamcorderProfile();
                    applicationInterface.onVideoRecordStopError(profile);
                }
            }
            if( Debug.LOG )
                Log.d(TAG, "reset video_recorder");
            video_recorder.reset();
            if( Debug.LOG )
                Log.d(TAG, "release video_recorder");
            video_recorder.release();
            video_recorder = null;
            reconnectCamera(false); // n.b., if something went wrong with video, then we reopen the camera - which may fail (or simply not reopen, e.g., if app is now paused)
            applicationInterface.stoppedVideo(video_method, video_uri, video_filename);
            video_method = ApplicationInterface.VIDEOMETHOD_FILE;
            video_uri = null;
            video_filename = null;
        }
    }

    private Context getContext() {
        return applicationInterface.getContext();
    }

    /** Restart video - either due to hitting maximum filesize, or maximum duration.
     */
    private void restartVideo(boolean due_to_max_filesize) {
        if( Debug.LOG )
            Log.d(TAG, "restartVideo()");
        if( video_recorder != null ) {
            if( due_to_max_filesize ) {
                long last_time = System.currentTimeMillis() - video_start_time;
                video_accumulated_time += last_time;
                if( Debug.LOG ) {
                    Log.d(TAG, "last_time: " + last_time);
                    Log.d(TAG, "video_accumulated_time is now: " + video_accumulated_time);
                }
            }
            else {
                video_accumulated_time = 0;
            }
            stopVideo(true); // this will also stop the timertask

            // handle restart
            if( Debug.LOG ) {
                if( due_to_max_filesize )
                    Log.d(TAG, "restarting due to maximum filesize");
                else
                    Log.d(TAG, "remaining_restart_video is: " + remaining_restart_video);
            }
            if( due_to_max_filesize ) {
                long video_max_duration = applicationInterface.getVideoMaxDurationPref();
                if( video_max_duration > 0 ) {
                    video_max_duration -= video_accumulated_time;
                    if( video_max_duration < min_safe_restart_video_time ) {
                        // if there's less than 1s to go, ignore it - don't want to risk the resultant video being corrupt or throwing error, due to stopping too soon
                        // so instead just pretend we hit the max duration instead
                        if( Debug.LOG )
                            Log.d(TAG, "hit max filesize, but max time duration is also set, with remaining time less than 1s: " + video_max_duration);
                        due_to_max_filesize = false;
                    }
                }
            }
            if( due_to_max_filesize || remaining_restart_video > 0 ) {
                if( is_video ) {
                    String toast = null;
                    if( !due_to_max_filesize )
                        toast = remaining_restart_video + " " + getContext().getResources().getString(R.string.repeats_to_go);
                    takePicture(due_to_max_filesize);
                    if( !due_to_max_filesize ) {
                        showToast(null, toast); // show the toast afterwards, as we're hogging the UI thread here, and media recorder takes time to start up
                        // must decrement after calling takePicture(), so that takePicture() doesn't reset the value of remaining_restart_video
                        remaining_restart_video--;
                    }
                }
                else {
                    remaining_restart_video = 0;
                }
            }
        }
    }

    private void reconnectCamera(boolean quiet) {
        if( Debug.LOG )
            Log.d(TAG, "reconnectCamera()");
        if( camera_controller != null ) { // just to be safe
            try {
                camera_controller.reconnect();
                this.setPreviewPaused(false);
            }
            catch(ControllerException e) {
                if( Debug.LOG )
                    Log.e(TAG, "failed to reconnect to camera");
                e.printStackTrace();
                applicationInterface.onFailedReconnectError();
                closeCamera();
            }
            try {
                tryAutoFocus(false, false);
            }
            catch(RuntimeException e) {
                if( Debug.LOG )
                    Log.e(TAG, "tryAutoFocus() threw exception: " + e.getMessage());
                e.printStackTrace();
                // this happens on Nexus 7 if trying to record video at bitrate 50Mbits or higher - it's fair enough that it fails, but we need to recover without a crash!
                // not safe to call closeCamera, as any call to getParameters may cause a RuntimeException
                // update: can no longer reproduce failures on Nexus 7?!
                this.is_preview_started = false;
                if( !quiet ) {
                    CamcorderProfile profile = getCamcorderProfile();
                    applicationInterface.onVideoRecordStopError(profile);
                }
                camera_controller.release();
                camera_controller = null;
                gif();
            }
        }
    }

    private void closeCamera() {
        long debug_time = 0;
        if( Debug.LOG ) {
            Log.d(TAG, "closeCamera()");
            debug_time = System.currentTimeMillis();
        }
        removePendingContinuousFocusReset();
        has_focus_area = false;
        focus_success = FOCUS_DONE;
        focus_started_time = -1;
        synchronized( this ) {
            // synchronise for consistency (keep FindBugs happy)
            take_photo_after_autofocus = false;
        }
        set_flash_value_after_autofocus = "";
        successfully_focused = false;
        preview_targetRatio = 0.0;
        // n.b., don't reset has_set_location, as we can remember the location when switching camera
        if( continuous_focus_move_is_started ) {
            continuous_focus_move_is_started = false;
            applicationInterface.onContinuousFocusMove(false);
        }
        applicationInterface.cameraClosed();
        cancelTimer();
        if( camera_controller != null ) {
            if( video_recorder != null ) {
                stopVideo(false);
            }
            if( this.is_video ) {
                // make sure we're into continuous video mode for closing
                // workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
                // so to be safe, we always reset to continuous video mode
                this.updateFocusForVideo(false);
            }
            // need to check for camera being non-null again - if an error occurred stopping the video, we will have closed the camera, and may not be able to reopen
            if( camera_controller != null ) {
                //camera.setPreviewCallback(null);
                if( Debug.LOG ) {
                    Log.d(TAG, "closeCamera: about to pause preview: " + (System.currentTimeMillis() - debug_time));
                }
                pausePreview();
                if( Debug.LOG ) {
                    Log.d(TAG, "closeCamera: about to release camera controller: " + (System.currentTimeMillis() - debug_time));
                }
                camera_controller.release();
                camera_controller = null;
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "closeCamera: total time: " + (System.currentTimeMillis() - debug_time));
        }
    }

    public void cancelTimer() {
        if( Debug.LOG )
            Log.d(TAG, "cancelTimer()");
        if( this.isOnTimer() ) {
            takePictureTimerTask.cancel();
            takePictureTimerTask = null;
            if( beepTimerTask != null ) {
                beepTimerTask.cancel();
                beepTimerTask = null;
            }
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            if( Debug.LOG )
                Log.d(TAG, "cancelled camera timer");
        }
    }

    public void pausePreview() {
        long debug_time = 0;
        if( Debug.LOG ) {
            Log.d(TAG, "pausePreview()");
            debug_time = System.currentTimeMillis();
        }
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        if( this.is_video ) {
            // make sure we're into continuous video mode
            // workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
            // so to be safe, we always reset to continuous video mode
            // although I've now fixed this at the level where we close the settings, I've put this guard here, just in case the problem occurs from elsewhere
            this.updateFocusForVideo(false);
        }
        this.setPreviewPaused(false);
        if( Debug.LOG ) {
            Log.d(TAG, "pausePreview: about to stop preview: " + (System.currentTimeMillis() - debug_time));
        }
        camera_controller.stopPreview();
        this.phase = PHASE_NORMAL;
        this.is_preview_started = false;
        if( Debug.LOG ) {
            Log.d(TAG, "pausePreview: about to call cameraInOperation: " + (System.currentTimeMillis() - debug_time));
        }
        applicationInterface.cameraInOperation(false);
        if( Debug.LOG ) {
            Log.d(TAG, "pausePreview: total time: " + (System.currentTimeMillis() - debug_time));
        }
    }

    //private int debug_count_gif = 0; // see usage below

    /** Try to open the camera. Should only be called if camera_controller==null.
     */
    private void gif() {
        long debug_time = 0;
        if( Debug.LOG ) {
            Log.d(TAG, "gif()");
            debug_time = System.currentTimeMillis();
        }
        // need to init everything now, in case we don't open the camera (but these may already be initialised from an earlier call - e.g., if we are now switching to another camera)
        // n.b., don't reset has_set_location, as we can remember the location when switching camera
        is_preview_started = false; // theoretically should be false anyway, but I had one RuntimeException from surfaceCreated()->gif()->setupCamera()->setPreviewSize() because is_preview_started was true, even though the preview couldn't have been started
        set_preview_size = false;
        preview_w = 0;
        preview_h = 0;
        has_focus_area = false;
        focus_success = FOCUS_DONE;
        focus_started_time = -1;
        synchronized( this ) {
            // synchronise for consistency (keep FindBugs happy)
            take_photo_after_autofocus = false;
        }
        set_flash_value_after_autofocus = "";
        successfully_focused = false;
        preview_targetRatio = 0.0;
        scene_modes = null;
        has_zoom = false;
        max_zoom_factor = 0;
        minimum_focus_distance = 0.0f;
        zoom_ratios = null;
        faces_detected = null;
        supports_face_detection = false;
        using_face_detection = false;
        supports_video_stabilization = false;
        can_disable_shutter_sound = false;
        color_effects = null;
        white_balances = null;
        isos = null;
        supports_iso_range = false;
        min_iso = 0;
        max_iso = 0;
        supports_exposure_time = false;
        min_exposure_time = 0l;
        max_exposure_time = 0l;
        exposures = null;
        min_exposure = 0;
        max_exposure = 0;
        exposure_step = 0.0f;
        supports_expo_bracketing = false;
        supports_raw = false;
        sizes = null;
        current_size_index = -1;
        video_quality = null;
        current_video_quality = -1;
        supported_flash_values = null;
        current_flash_index = -1;
        supported_focus_values = null;
        current_focus_index = -1;
        max_num_focus_areas = 0;
        applicationInterface.cameraInOperation(false);
        if( Debug.LOG )
            Log.d(TAG, "done showGUI");
        if( !this.has_surface ) {
            if( Debug.LOG ) {
                Log.d(TAG, "preview surface not yet available");
            }
            return;
        }
        if( this.app_is_paused ) {
            if( Debug.LOG ) {
                Log.d(TAG, "don't open camera as app is paused");
            }
            return;
        }

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
            if( Debug.LOG )
                Log.d(TAG, "check for permissions");
            if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ) {
                if( Debug.LOG )
                    Log.d(TAG, "camera permission not available");
                has_permissions = false;
                applicationInterface.requestCameraPermission();
                // return for now - the application should try to reopen the camera if permission is granted
                return;
            }
            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
                if( Debug.LOG )
                    Log.d(TAG, "storage permission not available");
                has_permissions = false;
                applicationInterface.requestStoragePermission();
                // return for now - the application should try to reopen the camera if permission is granted
                return;
            }
            if( Debug.LOG )
                Log.d(TAG, "permissions available");
        }
        // set in case this was previously set to false
        has_permissions = true;

		/*{
			// debug
			if( debug_count_gif++ == 0 ) {
				if( Debug.LOG )
					Log.d(TAG, "debug: don't open camera yet");
				return;
			}
		}*/
        try {
            int cameraId = applicationInterface.getCameraIdPref();
            if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
                if( Debug.LOG )
                    Log.d(TAG, "invalid cameraId: " + cameraId);
                cameraId = 0;
                applicationInterface.setCameraIdPref(cameraId);
            }
            if( Debug.LOG ) {
                Log.d(TAG, "try to open camera: " + cameraId);
                Log.d(TAG, "gif: time before opening camera: " + (System.currentTimeMillis() - debug_time));
            }
            if( test_fail_open_camera ) {
                if( Debug.LOG )
                    Log.d(TAG, "test failing to open camera");
                throw new ControllerException();
            }
            if( using_android_l ) {
                Controller.ErrorCallback previewErrorCallback = new Controller.ErrorCallback() {
                    public void onError() {
                        if( Debug.LOG )
                            Log.e(TAG, "error from Controller: preview failed to start");
                        applicationInterface.onFailedStartPreview();
                    }
                };
                camera_controller = new Controller2(this.getContext(), cameraId, previewErrorCallback);
                if( applicationInterface.useCamera2FakeFlash() ) {
                    camera_controller.setUseCamera2FakeFlash(true);
                }
            }
            else
                camera_controller = new Controller1(cameraId);
            //throw new ControllerException(); // uncomment to test camera not opening
        }
        catch(ControllerException e) {
            if( Debug.LOG )
                Log.e(TAG, "Failed to open camera: " + e.getMessage());
            e.printStackTrace();
            camera_controller = null;
        }
        if( Debug.LOG ) {
            Log.d(TAG, "gif: time after opening camera: " + (System.currentTimeMillis() - debug_time));
        }
        boolean take_photo = false;
        if( camera_controller != null ) {
            Activity activity = (Activity)this.getContext();
            if( Debug.LOG )
                Log.d(TAG, "intent: " + activity.getIntent());
            if( activity.getIntent() != null && activity.getIntent().getExtras() != null ) {
                take_photo = activity.getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
                activity.getIntent().removeExtra(TakePhoto.TAKE_PHOTO);
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "no intent data");
            }
            if( Debug.LOG )
                Log.d(TAG, "take_photo?: " + take_photo);

            this.setCameraDisplayOrientation();
            new OrientationEventListener(activity) {
                @Override
                public void onOrientationChanged(int orientation) {
                    Preview.this.onOrientationChanged(orientation);
                }
            }.enable();
            if( Debug.LOG ) {
                Log.d(TAG, "gif: time after setting orientation: " + (System.currentTimeMillis() - debug_time));
            }

            if( Debug.LOG )
                Log.d(TAG, "call setPreviewDisplay");
            cameraSurface.setPreviewDisplay(camera_controller);
            if( Debug.LOG ) {
                Log.d(TAG, "gif: time after setting preview display: " + (System.currentTimeMillis() - debug_time));
            }

            setupCamera(take_photo);
        }

        if( Debug.LOG ) {
            Log.d(TAG, "gif: total time to open camera: " + (System.currentTimeMillis() - debug_time));
        }
    }

    /** Try to reopen the camera, if not currently open (e.g., permission wasn't granted, but now it is).
     */
    public void retrygif() {
        if( Debug.LOG )
            Log.d(TAG, "retrygif()");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "try to reopen camera");
            this.gif();
        }
        else {
            if( Debug.LOG )
                Log.d(TAG, "camera already open");
        }
    }

    /** Returns false if we failed to open the camera because camera or storage permission wasn't available.
     */
    public boolean hasPermissions() {
        return has_permissions;
    }

    /* Should only be called after camera first opened, or after preview is paused.
     * take_photo is true if we have been called from the TakePhoto widget (which means
     * we'll take a photo immediately after startup).
     */
    public void setupCamera(boolean take_photo) {
        if( Debug.LOG )
            Log.d(TAG, "setupCamera()");
        long debug_time = 0;
        if( Debug.LOG ) {
            debug_time = System.currentTimeMillis();
        }
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        boolean do_startup_focus = !take_photo && applicationInterface.getStartupFocusPref();
        if( Debug.LOG ) {
            Log.d(TAG, "take_photo? " + take_photo);
            Log.d(TAG, "do_startup_focus? " + do_startup_focus);
        }
        if( this.is_video ) {
            // make sure we're into continuous video mode for reopening
            // workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
            // so to be safe, we always reset to continuous video mode
            // although I've now fixed this at the level where we close the settings, I've put this guard here, just in case the problem occurs from elsewhere
            this.updateFocusForVideo(false);
        }

        setupCameraParameters();

        // now switch to video if saved
        boolean saved_is_video = applicationInterface.isVideoPref();
        if( Debug.LOG ) {
            Log.d(TAG, "saved_is_video: " + saved_is_video);
        }
        if( saved_is_video != this.is_video ) {
            this.switchVideo(true);
        }

        if( do_startup_focus && using_android_l && camera_controller.supportsAutoFocus() ) {
            // need to switch flash off for autofocus - and for Android L, need to do this before starting preview (otherwise it won't work in time); for old camera API, need to do this after starting preview!
            set_flash_value_after_autofocus = "";
            String old_flash_value = camera_controller.getFlashValue();
            // getFlashValue() may return "" if flash not supported!
            // also set flash_torch - otherwise we get bug where torch doesn't turn on when starting up in video mode (and it's not like we want to turn torch off for startup focus, anyway)
            if( old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch") ) {
                set_flash_value_after_autofocus = old_flash_value;
                camera_controller.setFlashValue("flash_off");
            }
            if( Debug.LOG )
                Log.d(TAG, "set_flash_value_after_autofocus is now: " + set_flash_value_after_autofocus);
        }

        if( this.supports_raw && applicationInterface.isRawPref() ) {
            camera_controller.setRaw(true);
        }
        else {
            camera_controller.setRaw(false);
        }

        if( this.supports_expo_bracketing && applicationInterface.isExpoBracketingPref() ) {
            camera_controller.setExpoBracketing(true);
            camera_controller.setExpoBracketingNImages( applicationInterface.getExpoBracketingNImagesPref() );
            camera_controller.setExpoBracketingStops( applicationInterface.getExpoBracketingStopsPref() );
        }
        else {
            camera_controller.setExpoBracketing(false);
        }

        // Must set preview size before starting camera preview
        // and must do it after setting photo vs video mode
        setPreviewSize(); // need to call this when we switch cameras, not just when we run for the first time
        if( Debug.LOG ) {
            Log.d(TAG, "setupCamera: time after setting preview size: " + (System.currentTimeMillis() - debug_time));
        }
        // Must call startCameraPreview after checking if face detection is present - probably best to call it after setting all parameters that we want
        startCameraPreview();
        if( Debug.LOG ) {
            Log.d(TAG, "setupCamera: time after starting camera preview: " + (System.currentTimeMillis() - debug_time));
        }

        // must be done after setting parameters, as this function may set parameters
        // also needs to be done after starting preview for some devices (e.g., Nexus 7)
        if( this.has_zoom && applicationInterface.getZoomPref() != 0 ) {
            zoomTo(applicationInterface.getZoomPref());
            if( Debug.LOG ) {
                Log.d(TAG, "setupCamera: total time after zoomTo: " + (System.currentTimeMillis() - debug_time));
            }
        }

        if( take_photo ) {
            if( this.is_video ) {
                this.switchVideo(false); // set during_startup to false, as we now need to reset the preview
            }
        }

        applicationInterface.cameraSetup(); // must call this after the above take_photo code for calling switchVideo
        if( Debug.LOG ) {
            Log.d(TAG, "setupCamera: total time after cameraSetup: " + (System.currentTimeMillis() - debug_time));
        }

        if( take_photo ) {
            // take photo after a delay - otherwise we sometimes get a black image?!
            // also need a longer delay for continuous picture focus, to allow a chance to focus - 1000ms seems to work okay for Nexus 6, put 1500ms to be safe
            String focus_value = getCurrentFocusValue();
            final int delay = ( focus_value != null && focus_value.equals("focus_mode_continuous_picture") ) ? 1500 : 500;
            if( Debug.LOG )
                Log.d(TAG, "delay for take photo: " + delay);
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if( Debug.LOG )
                        Log.d(TAG, "do automatic take picture");
                    takePicture(false);
                }
            }, delay);
        }

        if( do_startup_focus ) {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if( Debug.LOG )
                        Log.d(TAG, "do startup autofocus");
                    tryAutoFocus(true, false); // so we get the autofocus when starting up - we do this on a delay, as calling it immediately means the autofocus doesn't seem to work properly sometimes (at least on Galaxy Nexus)
                }
            }, 500);
        }

        if( Debug.LOG ) {
            Log.d(TAG, "setupCamera: total time after setupCamera: " + (System.currentTimeMillis() - debug_time));
        }
    }

    private void setupCameraParameters() {
        if( Debug.LOG )
            Log.d(TAG, "setupCameraParameters()");
        long debug_time = 0;
        if( Debug.LOG ) {
            debug_time = System.currentTimeMillis();
        }
        {
            // get available scene modes
            // important, from docs:
            // "Changing scene mode may override other parameters (such as flash mode, focus mode, white balance).
            // For example, suppose originally flash mode is on and supported flash modes are on/off. In night
            // scene mode, both flash mode and supported flash mode may be changed to off. After setting scene
            // mode, applications should call getParameters to know if some parameters are changed."
            if( Debug.LOG )
                Log.d(TAG, "set up scene mode");
            String value = applicationInterface.getSceneModePref();
            if( Debug.LOG )
                Log.d(TAG, "saved scene mode: " + value);

            Controller.SupportedValues supported_values = camera_controller.setSceneMode(value);
            if( supported_values != null ) {
                scene_modes = supported_values.values;
                // now save, so it's available for PreferenceActivity
                applicationInterface.setSceneModePref(supported_values.selected_value);
            }
            else {
                // delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
                applicationInterface.clearSceneModePref();
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after setting scene mode: " + (System.currentTimeMillis() - debug_time));
        }

        {
            // grab all read-only info from parameters
            if( Debug.LOG )
                Log.d(TAG, "grab info from parameters");
            Controller.CameraFeatures camera_features = camera_controller.getCameraFeatures();
            this.has_zoom = camera_features.is_zoom_supported;
            if( this.has_zoom ) {
                this.max_zoom_factor = camera_features.max_zoom;
                this.zoom_ratios = camera_features.zoom_ratios;
            }
            this.minimum_focus_distance = camera_features.minimum_focus_distance;
            this.supports_face_detection = camera_features.supports_face_detection;
            this.sizes = camera_features.picture_sizes;
            supported_flash_values = camera_features.supported_flash_values;
            supported_focus_values = camera_features.supported_focus_values;
            this.max_num_focus_areas = camera_features.max_num_focus_areas;
            this.is_exposure_lock_supported = camera_features.is_exposure_lock_supported;
            this.supports_video_stabilization = camera_features.is_video_stabilization_supported;
            this.can_disable_shutter_sound = camera_features.can_disable_shutter_sound;
            this.supports_iso_range = camera_features.supports_iso_range;
            this.min_iso = camera_features.min_iso;
            this.max_iso = camera_features.max_iso;
            this.supports_exposure_time = camera_features.supports_exposure_time;
            this.min_exposure_time = camera_features.min_exposure_time;
            this.max_exposure_time = camera_features.max_exposure_time;
            this.min_exposure = camera_features.min_exposure;
            this.max_exposure = camera_features.max_exposure;
            this.exposure_step = camera_features.exposure_step;
            this.supports_expo_bracketing = camera_features.supports_expo_bracketing;
            this.supports_raw = camera_features.supports_raw;
            this.video_sizes = camera_features.video_sizes;
            this.supported_preview_sizes = camera_features.preview_sizes;
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after getting read only info: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up face detection");
            // get face detection supported
            this.faces_detected = null;
            if( this.supports_face_detection ) {
                this.using_face_detection = applicationInterface.getFaceDetectionPref();
            }
            else {
                this.using_face_detection = false;
            }
            if( Debug.LOG ) {
                Log.d(TAG, "supports_face_detection?: " + supports_face_detection);
                Log.d(TAG, "using_face_detection?: " + using_face_detection);
            }
            if( this.using_face_detection ) {
                class MyFaceDetectionListener implements Controller.FaceDetectionListener {
                    @Override
                    public void onFaceDetection(Controller.Face[] faces) {
                        faces_detected = new Controller.Face[faces.length];
                        System.arraycopy(faces, 0, faces_detected, 0, faces.length);
                    }
                }
                camera_controller.setFaceDetectionListener(new MyFaceDetectionListener());
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after setting face detection: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up video stabilization");
            if( this.supports_video_stabilization ) {
                boolean using_video_stabilization = applicationInterface.getVideoStabilizationPref();
                if( Debug.LOG )
                    Log.d(TAG, "using_video_stabilization?: " + using_video_stabilization);
                camera_controller.setVideoStabilization(using_video_stabilization);
            }
            if( Debug.LOG )
                Log.d(TAG, "supports_video_stabilization?: " + supports_video_stabilization);
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after video stabilization: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up color effect");
            String value = applicationInterface.getColorEffectPref();
            if( Debug.LOG )
                Log.d(TAG, "saved color effect: " + value);

            Controller.SupportedValues supported_values = camera_controller.setColorEffect(value);
            if( supported_values != null ) {
                color_effects = supported_values.values;
                // now save, so it's available for PreferenceActivity
                applicationInterface.setColorEffectPref(supported_values.selected_value);
            }
            else {
                // delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
                applicationInterface.clearColorEffectPref();
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after color effect: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up white balance");
            String value = applicationInterface.getWhiteBalancePref();
            if( Debug.LOG )
                Log.d(TAG, "saved white balance: " + value);

            Controller.SupportedValues supported_values = camera_controller.setWhiteBalance(value);
            if( supported_values != null ) {
                white_balances = supported_values.values;
                // now save, so it's available for PreferenceActivity
                applicationInterface.setWhiteBalancePref(supported_values.selected_value);
            }
            else {
                // delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
                applicationInterface.clearWhiteBalancePref();
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after white balance: " + (System.currentTimeMillis() - debug_time));
        }

        // must be done before setting flash modes, as we may remove flash modes if in manual mode
        boolean has_manual_iso = false;
        {
            if( Debug.LOG )
                Log.d(TAG, "set up iso");
            String value = applicationInterface.getISOPref();
            if( Debug.LOG )
                Log.d(TAG, "saved iso: " + value);

            Controller.SupportedValues supported_values = camera_controller.setISO(value);
            if( supported_values != null ) {
                isos = supported_values.values;
                if( !supported_values.selected_value.equals(camera_controller.getDefaultISO()) ) {
                    if( Debug.LOG )
                        Log.d(TAG, "has manual iso");
                    has_manual_iso = true;
                }
                // now save, so it's available for PreferenceActivity
                applicationInterface.setISOPref(supported_values.selected_value);

                if( has_manual_iso ) {
                    if( supports_exposure_time ) {
                        long exposure_time_value = applicationInterface.getExposureTimePref();
                        if( Debug.LOG )
                            Log.d(TAG, "saved exposure_time: " + exposure_time_value);
                        if( exposure_time_value < min_exposure_time )
                            exposure_time_value = min_exposure_time;
                        else if( exposure_time_value > max_exposure_time )
                            exposure_time_value = max_exposure_time;
                        camera_controller.setExposureTime(exposure_time_value);
                        // now save
                        applicationInterface.setExposureTimePref(exposure_time_value);
                    }
                    else {
                        // delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
                        applicationInterface.clearExposureTimePref();
                    }

                    if( this.using_android_l && supported_flash_values != null ) {
                        // flash modes not supported when using Android L's
                        // (it's unclear flash is useful - ideally we'd at least offer torch, but ISO seems to reset to 100 when flash/torch is on!)
                        supported_flash_values = null;
                        if( Debug.LOG )
                            Log.d(TAG, "flash not supported in Camera2 manual mode");
                    }
                }
            }
            else {
                // delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
                applicationInterface.clearISOPref();
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after manual iso: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG ) {
                Log.d(TAG, "set up exposure compensation");
                Log.d(TAG, "min_exposure: " + min_exposure);
                Log.d(TAG, "max_exposure: " + max_exposure);
            }
            // get min/max exposure
            exposures = null;
            if( min_exposure != 0 || max_exposure != 0 ) {
                exposures = new ArrayList<String>();
                for(int i=min_exposure;i<=max_exposure;i++) {
                    exposures.add("" + i);
                }
                // if in manual ISO mode, we still want to get the valid exposure compensations, but shouldn't set exposure compensation
                if( !has_manual_iso ) {
                    int exposure = applicationInterface.getExposureCompensationPref();
                    if( exposure < min_exposure || exposure > max_exposure ) {
                        exposure = 0;
                        if( Debug.LOG )
                            Log.d(TAG, "saved exposure not supported, reset to 0");
                        if( exposure < min_exposure || exposure > max_exposure ) {
                            if( Debug.LOG )
                                Log.d(TAG, "zero isn't an allowed exposure?! reset to min " + min_exposure);
                            exposure = min_exposure;
                        }
                    }
                    camera_controller.setExposureCompensation(exposure);
                    // now save, so it's available for PreferenceActivity
                    applicationInterface.setExposureCompensationPref(exposure);
                }
            }
            else {
                // delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
                applicationInterface.clearExposureCompensationPref();
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after exposures: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up picture sizes");
            if( Debug.LOG ) {
                for(int i=0;i<sizes.size();i++) {
                    Controller.Size size = sizes.get(i);
                    Log.d(TAG, "supported picture size: " + size.width + " , " + size.height);
                }
            }
            current_size_index = -1;
            Pair<Integer, Integer> resolution = applicationInterface.getCameraResolutionPref();
            if( resolution != null ) {
                int resolution_w = resolution.first;
                int resolution_h = resolution.second;
                // now find size in valid list
                for(int i=0;i<sizes.size() && current_size_index==-1;i++) {
                    Controller.Size size = sizes.get(i);
                    if( size.width == resolution_w && size.height == resolution_h ) {
                        current_size_index = i;
                        if( Debug.LOG )
                            Log.d(TAG, "set current_size_index to: " + current_size_index);
                    }
                }
                if( current_size_index == -1 ) {
                    if( Debug.LOG )
                        Log.e(TAG, "failed to find valid size");
                }
            }

            if( current_size_index == -1 ) {
                // set to largest
                Controller.Size current_size = null;
                for(int i=0;i<sizes.size();i++) {
                    Controller.Size size = sizes.get(i);
                    if( current_size == null || size.width*size.height > current_size.width*current_size.height ) {
                        current_size_index = i;
                        current_size = size;
                    }
                }
            }
            if( current_size_index != -1 ) {
                Controller.Size current_size = sizes.get(current_size_index);
                if( Debug.LOG )
                    Log.d(TAG, "Current size index " + current_size_index + ": " + current_size.width + ", " + current_size.height);

                // now save, so it's available for PreferenceActivity
                applicationInterface.setCameraResolutionPref(current_size.width, current_size.height);
            }
            // size set later in setPreviewSize()
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after picture sizes: " + (System.currentTimeMillis() - debug_time));
        }

        {
            int image_quality = applicationInterface.getImageQualityPref();
            if( Debug.LOG )
                Log.d(TAG, "set up jpeg quality: " + image_quality);
            camera_controller.setJpegQuality(image_quality);
            if( Debug.LOG )
                Log.d(TAG, "image quality: " + image_quality);
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after jpeg quality: " + (System.currentTimeMillis() - debug_time));
        }

        // get available sizes
        initialiseVideoSizes();
        initialiseVideoQuality();
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after video sizes: " + (System.currentTimeMillis() - debug_time));
        }

        current_video_quality = -1;
        String video_quality_value_s = applicationInterface.getVideoQualityPref();
        if( Debug.LOG )
            Log.d(TAG, "video_quality_value: " + video_quality_value_s);
        if( video_quality_value_s.length() > 0 ) {
            // parse the saved video quality, and make sure it is still valid
            // now find value in valid list
            for(int i=0;i<video_quality.size() && current_video_quality==-1;i++) {
                if( video_quality.get(i).equals(video_quality_value_s) ) {
                    current_video_quality = i;
                    if( Debug.LOG )
                        Log.d(TAG, "set current_video_quality to: " + current_video_quality);
                }
            }
            if( current_video_quality == -1 ) {
                if( Debug.LOG )
                    Log.e(TAG, "failed to find valid video_quality");
            }
        }
        if( current_video_quality == -1 && video_quality.size() > 0 ) {
            // default to FullHD if available, else pick highest quality
            // (FullHD will give smaller file sizes and generally give better performance than 4K so probably better for most users; also seems to suffer from less problems when using manual ISO in Camera2 API)
            current_video_quality = 0; // start with highest quality
            for(int i=0;i<video_quality.size();i++) {
                if( Debug.LOG )
                    Log.d(TAG, "check video quality: " + video_quality.get(i));
                CamcorderProfile profile = getCamcorderProfile(video_quality.get(i));
                if( profile.videoFrameWidth == 1920 && profile.videoFrameHeight == 1080 ) {
                    current_video_quality = i;
                    break;
                }
            }
            if( Debug.LOG )
                Log.d(TAG, "set video_quality value to " + video_quality.get(current_video_quality));
        }
        if( current_video_quality != -1 ) {
            // now save, so it's available for PreferenceActivity
            applicationInterface.setVideoQualityPref(video_quality.get(current_video_quality));
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after handling video quality: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG ) {
                Log.d(TAG, "set up flash");
                Log.d(TAG, "flash values: " + supported_flash_values);
            }
            current_flash_index = -1;
            if( supported_flash_values != null && supported_flash_values.size() > 1 ) {

                String flash_value = applicationInterface.getFlashPref();
                if( flash_value.length() > 0 ) {
                    if( Debug.LOG )
                        Log.d(TAG, "found existing flash_value: " + flash_value);
                    if( !updateFlash(flash_value, false) ) { // don't need to save, as this is the value that's already saved
                        if( Debug.LOG )
                            Log.d(TAG, "flash value no longer supported!");
                        updateFlash(0, true);
                    }
                }
                else {
                    if( Debug.LOG )
                        Log.d(TAG, "found no existing flash_value");
                    // whilst devices with flash should support flash_auto, we'll also be in this codepath for front cameras with
                    // no flash, as instead the available options will be flash_off, flash_frontscreen_auto, flash_frontscreen_on
                    // see testTakePhotoFrontCameraScreenFlash
                    if( supported_flash_values.contains("flash_auto") )
                        updateFlash("flash_auto", true);
                    else
                        updateFlash("flash_off", true);
                }
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "flash not supported");
                supported_flash_values = null;
            }
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after setting up flash: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up focus");
            current_focus_index = -1;
            if( supported_focus_values != null && supported_focus_values.size() > 1 ) {
                if( Debug.LOG )
                    Log.d(TAG, "focus values: " + supported_focus_values);

                setFocusPref(true);
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "focus not supported");
                supported_focus_values = null;
            }
			/*supported_focus_values = new ArrayList<String>();
			supported_focus_values.add("focus_mode_auto");
			supported_focus_values.add("focus_mode_infinity");
			supported_focus_values.add("focus_mode_macro");
			supported_focus_values.add("focus_mode_locked");
			supported_focus_values.add("focus_mode_manual2");
			supported_focus_values.add("focus_mode_fixed");
			supported_focus_values.add("focus_mode_edof");
			supported_focus_values.add("focus_mode_continuous_video");*/
		    /*View focusModeButton = (View) activity.findViewById(R.id.focus_mode);
			focusModeButton.setVisibility(supported_focus_values != null && !immersive_mode ? View.VISIBLE : View.GONE);*/
        }

        {
            float focus_distance_value = applicationInterface.getFocusDistancePref();
            if( Debug.LOG )
                Log.d(TAG, "saved focus_distance: " + focus_distance_value);
            if( focus_distance_value < 0.0f )
                focus_distance_value = 0.0f;
            else if( focus_distance_value > minimum_focus_distance )
                focus_distance_value = minimum_focus_distance;
            camera_controller.setFocusDistance(focus_distance_value);
            // now save
            applicationInterface.setFocusDistancePref(focus_distance_value);
        }
        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: time after setting up focus: " + (System.currentTimeMillis() - debug_time));
        }

        {
            if( Debug.LOG )
                Log.d(TAG, "set up exposure lock");
            // exposure lock should always default to false, as doesn't make sense to save it - we can't really preserve a "lock" after the camera is reopened
            // also note that it isn't safe to lock the exposure before starting the preview
            is_exposure_locked = false;
        }

        if( Debug.LOG ) {
            Log.d(TAG, "setupCameraParameters: total time for setting up camera parameters: " + (System.currentTimeMillis() - debug_time));
        }
    }

    private void setPreviewSize() {
        if( Debug.LOG )
            Log.d(TAG, "setPreviewSize()");
        // also now sets picture size
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        if( is_preview_started ) {
            if( Debug.LOG )
                Log.d(TAG, "setPreviewSize() shouldn't be called when preview is running");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        if( !using_android_l ) {
            // don't do for Android L, else this means we get flash on startup autofocus if flash is on
            this.cancelAutoFocus();
        }
        // first set picture size (for photo mode, must be done now so we can set the picture size from this; for video, doesn't really matter when we set it)
        Controller.Size new_size = null;
        if( this.is_video ) {
            // In theory, the picture size shouldn't matter in video mode, but the stock Android camera sets a picture size
            // which is the largest that matches the video's aspect ratio.
            // This seems necessary to work around an aspect ratio bug introduced in Android 4.4.3 (on Nexus 7 at least): http://code.google.com/p/android/issues/detail?id=70830
            // which results in distorted aspect ratio on preview and recorded video!
            CamcorderProfile profile = getCamcorderProfile();
            if( Debug.LOG )
                Log.d(TAG, "video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
            double targetRatio = ((double)profile.videoFrameWidth) / (double)profile.videoFrameHeight;
            new_size = getOptimalVideoPictureSize(sizes, targetRatio);
        }
        else {
            if( current_size_index != -1 ) {
                new_size = sizes.get(current_size_index);
            }
        }
        if( new_size != null ) {
            camera_controller.setPictureSize(new_size.width, new_size.height);
        }
        // set optimal preview size
        if( supported_preview_sizes != null && supported_preview_sizes.size() > 0 ) {
	        /*Controller.Size best_size = supported_preview_sizes.get(0);
	        for(Controller.Size size : supported_preview_sizes) {
	    		if( Debug.LOG )
	    			Log.d(TAG, "    supported preview size: " + size.width + ", " + size.height);
	        	if( size.width*size.height > best_size.width*best_size.height ) {
	        		best_size = size;
	        	}
	        }*/
            Controller.Size best_size = getOptimalPreviewSize(supported_preview_sizes);
            camera_controller.setPreviewSize(best_size.width, best_size.height);
            this.set_preview_size = true;
            this.preview_w = best_size.width;
            this.preview_h = best_size.height;
            this.setAspectRatio( ((double)best_size.width) / (double)best_size.height );
        }
    }

    // Android docs and FindBugs recommend that Comparators also be Serializable
    private static class SortVideoSizesComparator implements Comparator<Controller.Size>, Serializable {
        private static final long serialVersionUID = 5802214721033718212L;

        @Override
        public int compare(final Controller.Size a, final Controller.Size b) {
            return b.width * b.height - a.width * a.height;
        }
    }

    private void sortVideoSizes() {
        if( Debug.LOG )
            Log.d(TAG, "sortVideoSizes()");
        Collections.sort(this.video_sizes, new SortVideoSizesComparator());
    }

    // for testing
    public void setVideoSizes(List<Controller.Size> video_sizes) {
        this.video_sizes = video_sizes;
        this.sortVideoSizes();
    }

    private void initialiseVideoSizes() {
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        this.sortVideoSizes();
        if( Debug.LOG ) {
            for(Controller.Size size : video_sizes) {
                Log.d(TAG, "    supported video size: " + size.width + ", " + size.height);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initialiseVideoQuality() {
        int cameraId = camera_controller.getCameraId();
        SparseArray<Pair<Integer, Integer>> profiles = new SparseArray<Pair<Integer, Integer>>();
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            profiles.put(CamcorderProfile.QUALITY_HIGH, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
            if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P) ) {
                CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
                profiles.put(CamcorderProfile.QUALITY_2160P, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
            }
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
            profiles.put(CamcorderProfile.QUALITY_1080P, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
            profiles.put(CamcorderProfile.QUALITY_720P, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
            profiles.put(CamcorderProfile.QUALITY_480P, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_CIF);
            profiles.put(CamcorderProfile.QUALITY_CIF, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
            profiles.put(CamcorderProfile.QUALITY_QVGA, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QCIF) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QCIF);
            profiles.put(CamcorderProfile.QUALITY_QCIF, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW) ) {
            CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            profiles.put(CamcorderProfile.QUALITY_LOW, new Pair<Integer, Integer>(profile.videoFrameWidth, profile.videoFrameHeight));
        }
        initialiseVideoQualityFromProfiles(profiles);
    }

    private void addVideoResolutions(boolean done_video_size[], int base_profile, int min_resolution_w, int min_resolution_h) {
        if( video_sizes == null ) {
            return;
        }
        if( Debug.LOG )
            Log.d(TAG, "profile " + base_profile + " is resolution " + min_resolution_w + " x " + min_resolution_h);
        for(int i=0;i<video_sizes.size();i++) {
            if( done_video_size[i] )
                continue;
            Controller.Size size = video_sizes.get(i);
            if( size.width == min_resolution_w && size.height == min_resolution_h ) {
                String str = "" + base_profile;
                video_quality.add(str);
                done_video_size[i] = true;
                if( Debug.LOG )
                    Log.d(TAG, "added: " + str);
            }
            else if( base_profile == CamcorderProfile.QUALITY_LOW || size.width * size.height >= min_resolution_w*min_resolution_h ) {
                String str = "" + base_profile + "_r" + size.width + "x" + size.height;
                video_quality.add(str);
                done_video_size[i] = true;
                if( Debug.LOG )
                    Log.d(TAG, "added: " + str);
            }
        }
    }

    public void initialiseVideoQualityFromProfiles(SparseArray<Pair<Integer, Integer>> profiles) {
        if( Debug.LOG )
            Log.d(TAG, "initialiseVideoQualityFromProfiles()");
        video_quality = new ArrayList<String>();
        boolean done_video_size[] = null;
        if( video_sizes != null ) {
            done_video_size = new boolean[video_sizes.size()];
            for(int i=0;i<video_sizes.size();i++)
                done_video_size[i] = false;
        }
        if( profiles.get(CamcorderProfile.QUALITY_HIGH) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_HIGH");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_HIGH);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_HIGH, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_1080P) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_1080P");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_1080P);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_1080P, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_720P) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_720P");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_720P);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_720P, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_480P) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_480P");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_480P);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_480P, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_CIF) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_CIF");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_CIF);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_CIF, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_QVGA) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_QVGA");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_QVGA);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_QVGA, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_QCIF) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_QCIF");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_QCIF);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_QCIF, pair.first, pair.second);
        }
        if( profiles.get(CamcorderProfile.QUALITY_LOW) != null ) {
            if( Debug.LOG )
                Log.d(TAG, "supports QUALITY_LOW");
            Pair<Integer, Integer> pair = profiles.get(CamcorderProfile.QUALITY_LOW);
            addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_LOW, pair.first, pair.second);
        }
        if( Debug.LOG ) {
            for(int i=0;i<video_quality.size();i++) {
                Log.d(TAG, "supported video quality: " + video_quality.get(i));
            }
        }
    }

    private CamcorderProfile getCamcorderProfile(String quality) {
        if( Debug.LOG )
            Log.d(TAG, "getCamcorderProfile(): " + quality);
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return CamcorderProfile.get(0, CamcorderProfile.QUALITY_HIGH);
        }
        int cameraId = camera_controller.getCameraId();
        CamcorderProfile camcorder_profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH); // default
        try {
            String profile_string = quality;
            int index = profile_string.indexOf('_');
            if( index != -1 ) {
                profile_string = quality.substring(0, index);
                if( Debug.LOG )
                    Log.d(TAG, "    profile_string: " + profile_string);
            }
            int profile = Integer.parseInt(profile_string);
            camcorder_profile = CamcorderProfile.get(cameraId, profile);
            if( index != -1 && index+1 < quality.length() ) {
                String override_string = quality.substring(index+1);
                if( Debug.LOG )
                    Log.d(TAG, "    override_string: " + override_string);
                if( override_string.charAt(0) == 'r' && override_string.length() >= 4 ) {
                    index = override_string.indexOf('x');
                    if( index == -1 ) {
                        if( Debug.LOG )
                            Log.d(TAG, "override_string invalid format, can't find x");
                    }
                    else {
                        String resolution_w_s = override_string.substring(1, index); // skip first 'r'
                        String resolution_h_s = override_string.substring(index+1);
                        if( Debug.LOG ) {
                            Log.d(TAG, "resolution_w_s: " + resolution_w_s);
                            Log.d(TAG, "resolution_h_s: " + resolution_h_s);
                        }
                        // copy to local variable first, so that if we fail to parse height, we don't set the width either
                        int resolution_w = Integer.parseInt(resolution_w_s);
                        int resolution_h = Integer.parseInt(resolution_h_s);
                        camcorder_profile.videoFrameWidth = resolution_w;
                        camcorder_profile.videoFrameHeight = resolution_h;
                    }
                }
                else {
                    if( Debug.LOG )
                        Log.d(TAG, "unknown override_string initial code, or otherwise invalid format");
                }
            }
        }
        catch(NumberFormatException e) {
            if( Debug.LOG )
                Log.e(TAG, "failed to parse video quality: " + quality);
            e.printStackTrace();
        }
        return camcorder_profile;
    }

    public CamcorderProfile getCamcorderProfile() {
        // 4K UHD video is not yet supported by Android API (at least testing on Samsung S5 and Note 3, they do not return it via getSupportedVideoSizes(), nor via a CamcorderProfile (either QUALITY_HIGH, or anything else)
        // but it does work if we explicitly set the resolution (at least tested on an S5)
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return CamcorderProfile.get(0, CamcorderProfile.QUALITY_HIGH);
        }
        CamcorderProfile profile = null;
        int cameraId = camera_controller.getCameraId();
        if( applicationInterface.getForce4KPref() ) {
            if( Debug.LOG )
                Log.d(TAG, "force 4K UHD video");
            profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
            profile.videoFrameWidth = 3840;
            profile.videoFrameHeight = 2160;
            profile.videoBitRate = (int)(profile.videoBitRate*2.8); // need a higher bitrate for the better quality - this is roughly based on the bitrate used by an S5's native camera app at 4K (47.6 Mbps, compared to 16.9 Mbps which is what's returned by the QUALITY_HIGH profile)
        }
        else if( current_video_quality != -1 ) {
            profile = getCamcorderProfile(video_quality.get(current_video_quality));
        }
        else {
            profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
        }

        String bitrate_value = applicationInterface.getVideoBitratePref();
        if( !bitrate_value.equals("default") ) {
            try {
                int bitrate = Integer.parseInt(bitrate_value);
                if( Debug.LOG )
                    Log.d(TAG, "bitrate: " + bitrate);
                profile.videoBitRate = bitrate;
            }
            catch(NumberFormatException exception) {
                if( Debug.LOG )
                    Log.d(TAG, "bitrate invalid format, can't parse to int: " + bitrate_value);
            }
        }
        String fps_value = applicationInterface.getVideoFPSPref();
        if( !fps_value.equals("default") ) {
            try {
                int fps = Integer.parseInt(fps_value);
                if( Debug.LOG )
                    Log.d(TAG, "fps: " + fps);
                profile.videoFrameRate = fps;
            }
            catch(NumberFormatException exception) {
                if( Debug.LOG )
                    Log.d(TAG, "fps invalid format, can't parse to int: " + fps_value);
            }
        }
        return profile;
    }

    private static String formatFloatToString(final float f) {
        final int i=(int)f;
        if( f == i )
            return Integer.toString(i);
        return String.format(Locale.getDefault(), "%.2f", f);
    }

    private static int greatestCommonFactor(int a, int b) {
        while( b > 0 ) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    private static String getAspectRatio(int width, int height) {
        int gcf = greatestCommonFactor(width, height);
        if( gcf > 0 ) {
            // had a Google Play crash due to gcf being 0!? Implies width must be zero
            width /= gcf;
            height /= gcf;
        }
        return width + ":" + height;
    }

    public static String getMPString(int width, int height) {
        float mp = (width*height)/1000000.0f;
        return formatFloatToString(mp) + "MP";
    }

    public static String getAspectRatioMPString(int width, int height) {
        return "(" + getAspectRatio(width, height) + ", " + getMPString(width, height) + ")";
    }

    public String getCamcorderProfileDescriptionShort(String quality) {
        if( camera_controller == null )
            return "";
        CamcorderProfile profile = getCamcorderProfile(quality);
        String desc = profile.videoFrameWidth + "x" + profile.videoFrameHeight + " " + getMPString(profile.videoFrameWidth, profile.videoFrameHeight);
        return desc;
    }

    public String getCamcorderProfileDescription(String quality) {
        if( camera_controller == null )
            return "";
        CamcorderProfile profile = getCamcorderProfile(quality);
        String highest = "";
        if( profile.quality == CamcorderProfile.QUALITY_HIGH ) {
            highest = "Highest: ";
        }
        String type = "";
        if( profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160 ) {
            type = "4K Ultra HD ";
        }
        else if( profile.videoFrameWidth == 1920 && profile.videoFrameHeight == 1080 ) {
            type = "Full HD ";
        }
        else if( profile.videoFrameWidth == 1280 && profile.videoFrameHeight == 720 ) {
            type = "HD ";
        }
        else if( profile.videoFrameWidth == 720 && profile.videoFrameHeight == 480 ) {
            type = "SD ";
        }
        else if( profile.videoFrameWidth == 640 && profile.videoFrameHeight == 480 ) {
            type = "VGA ";
        }
        else if( profile.videoFrameWidth == 352 && profile.videoFrameHeight == 288 ) {
            type = "CIF ";
        }
        else if( profile.videoFrameWidth == 320 && profile.videoFrameHeight == 240 ) {
            type = "QVGA ";
        }
        else if( profile.videoFrameWidth == 176 && profile.videoFrameHeight == 144 ) {
            type = "QCIF ";
        }
        String desc = highest + type + profile.videoFrameWidth + "x" + profile.videoFrameHeight + " " + getAspectRatioMPString(profile.videoFrameWidth, profile.videoFrameHeight);
        return desc;
    }

    public double getTargetRatio() {
        return preview_targetRatio;
    }

    private double calculateTargetRatioForPreview(Point display_size) {
        double targetRatio = 0.0f;
        String preview_size = applicationInterface.getPreviewSizePref();
        // should always use wysiwig for video mode, otherwise we get incorrect aspect ratio shown when recording video (at least on Galaxy Nexus, e.g., at 640x480)
        // also not using wysiwyg mode with video caused corruption on Samsung cameras (tested with Samsung S3, Android 4.3, front camera, infinity focus)
        if( preview_size.equals("preference_preview_size_wysiwyg") || this.is_video ) {
            if( this.is_video ) {
                if( Debug.LOG )
                    Log.d(TAG, "set preview aspect ratio from video size (wysiwyg)");
                CamcorderProfile profile = getCamcorderProfile();
                if( Debug.LOG )
                    Log.d(TAG, "video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
                targetRatio = ((double)profile.videoFrameWidth) / (double)profile.videoFrameHeight;
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "set preview aspect ratio from photo size (wysiwyg)");
                Controller.Size picture_size = camera_controller.getPictureSize();
                if( Debug.LOG )
                    Log.d(TAG, "picture_size: " + picture_size.width + " x " + picture_size.height);
                targetRatio = ((double)picture_size.width) / (double)picture_size.height;
            }
        }
        else {
            if( Debug.LOG )
                Log.d(TAG, "set preview aspect ratio from display size");
            // base target ratio from display size - means preview will fill the device's display as much as possible
            // but if the preview's aspect ratio differs from the actual photo/video size, the preview will show a cropped version of what is actually taken
            targetRatio = ((double)display_size.x) / (double)display_size.y;
        }
        this.preview_targetRatio = targetRatio;
        if( Debug.LOG )
            Log.d(TAG, "targetRatio: " + targetRatio);
        return targetRatio;
    }

    public Controller.Size getClosestSize(List<Controller.Size> sizes, double targetRatio) {
        if( Debug.LOG )
            Log.d(TAG, "getClosestSize()");
        Controller.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for(Controller.Size size : sizes) {
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
            }
        }
        return optimalSize;
    }

    public Controller.Size getOptimalPreviewSize(List<Controller.Size> sizes) {
        if( Debug.LOG )
            Log.d(TAG, "getOptimalPreviewSize()");
        final double ASPECT_TOLERANCE = 0.05;
        if( sizes == null )
            return null;
        Controller.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        Point display_size = new Point();
        Activity activity = (Activity)this.getContext();
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getSize(display_size);
            if( Debug.LOG )
                Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        double targetRatio = calculateTargetRatioForPreview(display_size);
        int targetHeight = Math.min(display_size.y, display_size.x);
        if( targetHeight <= 0 ) {
            targetHeight = display_size.y;
        }
        // Try to find the size which matches the aspect ratio, and is closest match to display height
        for(Controller.Size size : sizes) {
            if( Debug.LOG )
                Log.d(TAG, "    supported preview size: " + size.width + ", " + size.height);
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
                continue;
            if( Math.abs(size.height - targetHeight) < minDiff ) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if( optimalSize == null ) {
            // can't find match for aspect ratio, so find closest one
            if( Debug.LOG )
                Log.d(TAG, "no preview size matches the aspect ratio");
            optimalSize = getClosestSize(sizes, targetRatio);
        }
        if( Debug.LOG ) {
            Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x " + optimalSize.height);
            Log.d(TAG, "optimalSize ratio: " + ((double)optimalSize.width / optimalSize.height));
        }
        return optimalSize;
    }

    public Controller.Size getOptimalVideoPictureSize(List<Controller.Size> sizes, double targetRatio) {
        if( Debug.LOG )
            Log.d(TAG, "getOptimalVideoPictureSize()");
        final double ASPECT_TOLERANCE = 0.05;
        if( sizes == null )
            return null;
        Controller.Size optimalSize = null;
        // Try to find largest size that matches aspect ratio
        for(Controller.Size size : sizes) {
            if( Debug.LOG )
                Log.d(TAG, "    supported preview size: " + size.width + ", " + size.height);
            double ratio = (double)size.width / size.height;
            if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
                continue;
            if( optimalSize == null || size.width > optimalSize.width ) {
                optimalSize = size;
            }
        }
        if( optimalSize == null ) {
            // can't find match for aspect ratio, so find closest one
            if( Debug.LOG )
                Log.d(TAG, "no picture size matches the aspect ratio");
            optimalSize = getClosestSize(sizes, targetRatio);
        }
        if( Debug.LOG ) {
            Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x " + optimalSize.height);
            Log.d(TAG, "optimalSize ratio: " + ((double)optimalSize.width / optimalSize.height));
        }
        return optimalSize;
    }

    private void setAspectRatio(double ratio) {
        if( ratio <= 0.0 )
            throw new IllegalArgumentException();

        has_aspect_ratio = true;
        if( aspect_ratio != ratio ) {
            aspect_ratio = ratio;
            if( Debug.LOG )
                Log.d(TAG, "new aspect ratio: " + aspect_ratio);
            cameraSurface.getView().requestLayout();
            if( canvasView != null ) {
                canvasView.requestLayout();
            }
        }
    }

    private boolean hasAspectRatio() {
        return has_aspect_ratio;
    }

    private double getAspectRatio() {
        return aspect_ratio;
    }

    /** Returns the ROTATION_* enum of the display relative to the natural device orientation.
     */
    public int getDisplayRotation() {
        // gets the display rotation (as a Surface.ROTATION_* constant), taking into account the getRotatePreviewPreferenceKey() setting
        Activity activity = (Activity)this.getContext();
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        String rotate_preview = applicationInterface.getPreviewRotationPref();
        if( Debug.LOG )
            Log.d(TAG, "    rotate_preview = " + rotate_preview);
        if( rotate_preview.equals("180") ) {
            switch (rotation) {
                case Surface.ROTATION_0: rotation = Surface.ROTATION_180; break;
                case Surface.ROTATION_90: rotation = Surface.ROTATION_270; break;
                case Surface.ROTATION_180: rotation = Surface.ROTATION_0; break;
                case Surface.ROTATION_270: rotation = Surface.ROTATION_90; break;
                default:
                    break;
            }
        }

        return rotation;
    }

    /** Returns the rotation in degrees of the display relative to the natural device orientation.
     */
    private int getDisplayRotationDegrees() {
        if( Debug.LOG )
            Log.d(TAG, "getDisplayRotationDegrees");
        int rotation = getDisplayRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
            default:
                break;
        }
        if( Debug.LOG )
            Log.d(TAG, "    degrees = " + degrees);
        return degrees;
    }

    // for the Preview - from http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
    // note, if orientation is locked to landscape this is only called when setting up the activity, and will always have the same orientation
    public void setCameraDisplayOrientation() {
        if( Debug.LOG )
            Log.d(TAG, "setCameraDisplayOrientation()");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        if( using_android_l ) {
            // need to configure the textureview
            configureTransform();
        }
        else {
            int degrees = getDisplayRotationDegrees();
            if( Debug.LOG )
                Log.d(TAG, "    degrees = " + degrees);
            // note the code to make the rotation relative to the camera sensor is done in camera_controller.setDisplayOrientation()
            camera_controller.setDisplayOrientation(degrees);
        }
    }

    // for taking photos - from http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)
    private void onOrientationChanged(int orientation) {
		/*if( Debug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
		}*/
        if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
            return;
        if( camera_controller == null ) {
			/*if( Debug.LOG )
				Log.d(TAG, "camera not opened!");*/
            return;
        }
        orientation = (orientation + 45) / 90 * 90;
        this.current_orientation = orientation % 360;
        int new_rotation = 0;
        int camera_orientation = camera_controller.getCameraOrientation();
        if( camera_controller.isFrontFacing() ) {
            new_rotation = (camera_orientation - orientation + 360) % 360;
        }
        else {
            new_rotation = (camera_orientation + orientation) % 360;
        }
        if( new_rotation != current_rotation ) {
			/*if( Debug.LOG ) {
				Log.d(TAG, "    current_orientation is " + current_orientation);
				Log.d(TAG, "    info orientation is " + camera_orientation);
				Log.d(TAG, "    set Camera rotation from " + current_rotation + " to " + new_rotation);
			}*/
            this.current_rotation = new_rotation;
        }
    }

    private int getDeviceDefaultOrientation() {
        WindowManager windowManager = (WindowManager)this.getContext().getSystemService(Context.WINDOW_SERVICE);
        Configuration config = getResources().getConfiguration();
        int rotation = windowManager.getDefaultDisplay().getRotation();
        if( ( (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE )
                || ( (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
                config.orientation == Configuration.ORIENTATION_PORTRAIT ) ) {
            return Configuration.ORIENTATION_LANDSCAPE;
        }
        else {
            return Configuration.ORIENTATION_PORTRAIT;
        }
    }

    /* Returns the rotation (in degrees) to use for images/videos, taking the preference_lock_orientation into account.
     */
    private int getImageVideoRotation() {
        if( Debug.LOG )
            Log.d(TAG, "getImageVideoRotation() from current_rotation " + current_rotation);
        String lock_orientation = applicationInterface.getLockOrientationPref();
        if( lock_orientation.equals("landscape") ) {
            int camera_orientation = camera_controller.getCameraOrientation();
            int device_orientation = getDeviceDefaultOrientation();
            int result = 0;
            if( device_orientation == Configuration.ORIENTATION_PORTRAIT ) {
                // should be equivalent to onOrientationChanged(270)
                if( camera_controller.isFrontFacing() ) {
                    result = (camera_orientation + 90) % 360;
                }
                else {
                    result = (camera_orientation + 270) % 360;
                }
            }
            else {
                // should be equivalent to onOrientationChanged(0)
                result = camera_orientation;
            }
            if( Debug.LOG )
                Log.d(TAG, "getImageVideoRotation() lock to landscape, returns " + result);
            return result;
        }
        else if( lock_orientation.equals("portrait") ) {
            int camera_orientation = camera_controller.getCameraOrientation();
            int result = 0;
            int device_orientation = getDeviceDefaultOrientation();
            if( device_orientation == Configuration.ORIENTATION_PORTRAIT ) {
                // should be equivalent to onOrientationChanged(0)
                result = camera_orientation;
            }
            else {
                // should be equivalent to onOrientationChanged(90)
                if( camera_controller.isFrontFacing() ) {
                    result = (camera_orientation + 270) % 360;
                }
                else {
                    result = (camera_orientation + 90) % 360;
                }
            }
            if( Debug.LOG )
                Log.d(TAG, "getImageVideoRotation() lock to portrait, returns " + result);
            return result;
        }
        if( Debug.LOG )
            Log.d(TAG, "getImageVideoRotation() returns current_rotation " + current_rotation);
        return this.current_rotation;
    }

    public void draw(Canvas canvas) {
		/*if( Debug.LOG )
			Log.d(TAG, "draw()");*/
        if( this.app_is_paused ) {
    		/*if( Debug.LOG )
    			Log.d(TAG, "draw(): app is paused");*/
            return;
        }
		/*if( true ) // test
			return;*/
		/*if( Debug.LOG )
			Log.d(TAG, "ui_rotation: " + ui_rotation);*/
		/*if( Debug.LOG )
			Log.d(TAG, "canvas size " + canvas.getWidth() + " x " + canvas.getHeight());*/
		/*if( Debug.LOG )
			Log.d(TAG, "surface frame " + mHolder.getSurfaceFrame().width() + ", " + mHolder.getSurfaceFrame().height());*/

        if( this.focus_success != FOCUS_DONE ) {
            if( focus_complete_time != -1 && System.currentTimeMillis() > focus_complete_time + 1000 ) {
                focus_success = FOCUS_DONE;
            }
        }
        applicationInterface.onDrawPreview(canvas);
    }

    public void scaleZoom(float scale_factor) {
        if( Debug.LOG )
            Log.d(TAG, "scaleZoom() " + scale_factor);
        if( this.camera_controller != null && this.has_zoom ) {
            int zoom_factor = camera_controller.getZoom();
            float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
            zoom_ratio *= scale_factor;

            int new_zoom_factor = zoom_factor;
            if( zoom_ratio <= 1.0f ) {
                new_zoom_factor = 0;
            }
            else if( zoom_ratio >= zoom_ratios.get(max_zoom_factor)/100.0f ) {
                new_zoom_factor = max_zoom_factor;
            }
            else {
                // find the closest zoom level
                if( scale_factor > 1.0f ) {
                    // zooming in
                    for(int i=zoom_factor;i<zoom_ratios.size();i++) {
                        if( zoom_ratios.get(i)/100.0f >= zoom_ratio ) {
                            if( Debug.LOG )
                                Log.d(TAG, "zoom int, found new zoom by comparing " + zoom_ratios.get(i)/100.0f + " >= " + zoom_ratio);
                            new_zoom_factor = i;
                            break;
                        }
                    }
                }
                else {
                    // zooming out
                    for(int i=zoom_factor;i>=0;i--) {
                        if( zoom_ratios.get(i)/100.0f <= zoom_ratio ) {
                            if( Debug.LOG )
                                Log.d(TAG, "zoom out, found new zoom by comparing " + zoom_ratios.get(i)/100.0f + " <= " + zoom_ratio);
                            new_zoom_factor = i;
                            break;
                        }
                    }
                }
            }
            if( Debug.LOG ) {
                Log.d(TAG, "ScaleListener.onScale zoom_ratio is now " + zoom_ratio);
                Log.d(TAG, "    old zoom_factor " + zoom_factor + " ratio " + zoom_ratios.get(zoom_factor)/100.0f);
                Log.d(TAG, "    chosen new zoom_factor " + new_zoom_factor + " ratio " + zoom_ratios.get(new_zoom_factor)/100.0f);
            }
            zoomTo(new_zoom_factor);
            applicationInterface.multitouchZoom(new_zoom_factor);
        }
    }

    public void zoomTo(int new_zoom_factor) {
        if( Debug.LOG )
            Log.d(TAG, "ZoomTo(): " + new_zoom_factor);
        if( new_zoom_factor < 0 )
            new_zoom_factor = 0;
        else if( new_zoom_factor > max_zoom_factor )
            new_zoom_factor = max_zoom_factor;
        // problem where we crashed due to calling this function with null camera should be fixed now, but check again just to be safe
        if( camera_controller != null ) {
            if( this.has_zoom ) {
                // don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
                camera_controller.setZoom(new_zoom_factor);
                applicationInterface.setZoomPref(new_zoom_factor);
                clearFocusAreas();
            }
        }
    }

    public void setFocusDistance(float new_focus_distance) {
        if( Debug.LOG )
            Log.d(TAG, "setFocusDistance: " + new_focus_distance);
        if( camera_controller != null ) {
            if( new_focus_distance < 0.0f )
                new_focus_distance = 0.0f;
            else if( new_focus_distance > minimum_focus_distance )
                new_focus_distance = minimum_focus_distance;
            if( camera_controller.setFocusDistance(new_focus_distance) ) {
                // now save
                applicationInterface.setFocusDistancePref(new_focus_distance);
                {
                    String focus_distance_s = "";
                    if( new_focus_distance > 0.0f ) {
                        float real_focus_distance = 1.0f / new_focus_distance;
                        focus_distance_s = decimal_format_2dp.format(real_focus_distance) + getResources().getString(R.string.metres_abbreviation);
                    }
                    else {
                        focus_distance_s = getResources().getString(R.string.infinite);
                    }
                    showToast(seekbar_toast, getResources().getString(R.string.focus_distance) + " " + focus_distance_s);
                }
            }
        }
    }

    public void setExposure(int new_exposure) {
        if( Debug.LOG )
            Log.d(TAG, "setExposure(): " + new_exposure);
        if( camera_controller != null && ( min_exposure != 0 || max_exposure != 0 ) ) {
            cancelAutoFocus();
            if( new_exposure < min_exposure )
                new_exposure = min_exposure;
            else if( new_exposure > max_exposure )
                new_exposure = max_exposure;
            if( camera_controller.setExposureCompensation(new_exposure) ) {
                // now save
                applicationInterface.setExposureCompensationPref(new_exposure);
                showToast(seekbar_toast, getExposureCompensationString(new_exposure), 96);
            }
        }
    }

    public void setISO(int new_iso) {
        if( Debug.LOG )
            Log.d(TAG, "setISO(): " + new_iso);
        if( camera_controller != null && supports_iso_range ) {
            if( new_iso < min_iso )
                new_iso = min_iso;
            else if( new_iso > max_iso )
                new_iso = max_iso;
            if( camera_controller.setISO(new_iso) ) {
                // now save
                applicationInterface.setISOPref("" + new_iso);
                showToast(seekbar_toast, getISOString(new_iso), 96);
            }
        }
    }

    public void setExposureTime(long new_exposure_time) {
        if( Debug.LOG )
            Log.d(TAG, "setExposureTime(): " + new_exposure_time);
        if( camera_controller != null && supports_exposure_time ) {
            if( new_exposure_time < min_exposure_time )
                new_exposure_time = min_exposure_time;
            else if( new_exposure_time > max_exposure_time )
                new_exposure_time = max_exposure_time;
            if( camera_controller.setExposureTime(new_exposure_time) ) {
                // now save
                applicationInterface.setExposureTimePref(new_exposure_time);
                showToast(seekbar_toast, getExposureTimeString(new_exposure_time), 96);
            }
        }
    }

    public String getExposureCompensationString(int exposure) {
        float exposure_ev = exposure * exposure_step;
        return getResources().getString(R.string.exposure_compensation) + " " + (exposure > 0 ? "+" : "") + decimal_format_2dp.format(exposure_ev) + " EV";
    }

    public String getISOString(int iso) {
        return getResources().getString(R.string.iso) + " " + iso;
    }

    public String getExposureTimeString(long exposure_time) {
        double exposure_time_s = exposure_time/1000000000.0;
        String string = "";
        if( exposure_time >= 1000000000 ) {
            // show exposure times of more than 1s directly
            string = decimal_format_1dp.format(exposure_time_s) + getResources().getString(R.string.seconds_abbreviation);
        }
        else {
            double exposure_time_r = 1.0/exposure_time_s;
            string = " 1/" + decimal_format_1dp.format(exposure_time_r) + getResources().getString(R.string.seconds_abbreviation);
        }
        return string;
    }

	/*public String getFrameDurationString(long frame_duration) {
		double frame_duration_s = frame_duration/1000000000.0;
		double frame_duration_r = 1.0/frame_duration_s;
		return getResources().getString(R.string.fps) + " " + decimal_format_1dp.format(frame_duration_r);
	}*/

	/*private String getFocusOneDistanceString(float dist) {
		if( dist == 0.0f )
			return "inf.";
		float real_dist = 1.0f/dist;
		return decimal_format_2dp.format(real_dist) + getResources().getString(R.string.metres_abbreviation);
	}

	public String getFocusDistanceString(float dist_min, float dist_max) {
		String f_s = "f ";
		//if( dist_min == dist_max )
		//	return f_s + getFocusOneDistanceString(dist_min);
		//return f_s + getFocusOneDistanceString(dist_min) + "-" + getFocusOneDistanceString(dist_max);
		// just always show max for now
		return f_s + getFocusOneDistanceString(dist_max);
	}*/

    public boolean canSwitchCamera() {
        if( this.phase == PHASE_TAKING_PHOTO ) {
            // just to be safe - risk of cancelling the autofocus before taking a photo, or otherwise messing things up
            if( Debug.LOG )
                Log.d(TAG, "currently taking a photo");
            return false;
        }
        int n_cameras = camera_controller_manager.getNumberOfCameras();
        if( Debug.LOG )
            Log.d(TAG, "found " + n_cameras + " cameras");
        if( n_cameras == 0 )
            return false;
        return true;
    }

	/*public int getNextCameraId() {
		int n_cameras = camera_controller_manager.getNumberOfCameras();
		if( n_cameras > 0 ) {
			return (cameraId+1) % n_cameras;
		}
		else
			return cameraId;
	}

	public void switchCamera() {
		if( Debug.LOG )
			Log.d(TAG, "switchCamera()");
		if( canSwitchCamera() ) {
			int n_cameras = camera_controller_manager.getNumberOfCameras();
			if( Debug.LOG )
				Log.d(TAG, "found " + n_cameras + " cameras");
			closeCamera();
			cameraId = (cameraId+1) % n_cameras;
			this.gif();

			// we update the focus, in case we weren't able to do it when switching video with a camera that didn't support focus modes
			updateFocusForVideo(true);
		}
	}*/

    public void setCamera(int cameraId) {
        if( Debug.LOG )
            Log.d(TAG, "setCamera()");
        if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
            if( Debug.LOG )
                Log.d(TAG, "invalid cameraId: " + cameraId);
            cameraId = 0;
        }
        if( canSwitchCamera() ) {
            closeCamera();
            applicationInterface.setCameraIdPref(cameraId);
            this.gif();
        }
    }

    public int [] matchPreviewFpsToVideo(List<int []> fps_ranges, int video_frame_rate) {
        if( Debug.LOG )
            Log.d(TAG, "matchPreviewFpsToVideo()");
        int selected_min_fps = -1, selected_max_fps = -1, selected_diff = -1;
        for(int [] fps_range : fps_ranges) {
            if( Debug.LOG ) {
                Log.d(TAG, "    supported fps range: " + fps_range[0] + " to " + fps_range[1]);
            }
            int min_fps = fps_range[0];
            int max_fps = fps_range[1];
            if( min_fps <= video_frame_rate && max_fps >= video_frame_rate ) {
                int diff = max_fps - min_fps;
                if( selected_diff == -1 || diff < selected_diff ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_diff = diff;
                }
            }
        }
        if( selected_min_fps != -1 ) {
            if( Debug.LOG ) {
                Log.d(TAG, "    chosen fps range: " + selected_min_fps + " to " + selected_max_fps);
            }
        }
        else {
            selected_diff = -1;
            int selected_dist = -1;
            for(int [] fps_range : fps_ranges) {
                int min_fps = fps_range[0];
                int max_fps = fps_range[1];
                int diff = max_fps - min_fps;
                int dist = -1;
                if( max_fps < video_frame_rate )
                    dist = video_frame_rate - max_fps;
                else
                    dist = min_fps - video_frame_rate;
                if( Debug.LOG ) {
                    Log.d(TAG, "    supported fps range: " + min_fps + " to " + max_fps + " has dist " + dist + " and diff " + diff);
                }
                if( selected_dist == -1 || dist < selected_dist || ( dist == selected_dist && diff < selected_diff ) ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_dist = dist;
                    selected_diff = diff;
                }
            }
            if( Debug.LOG )
                Log.d(TAG, "    can't find match for fps range, so choose closest: " + selected_min_fps + " to " + selected_max_fps);
        }
        return new int[]{selected_min_fps, selected_max_fps};
    }

    public int [] chooseBestPreviewFps(List<int []> fps_ranges) {
        if( Debug.LOG )
            Log.d(TAG, "chooseBestPreviewFps()");

        // find value with lowest min that has max >= 30; if more than one of these, pick the one with highest max
        int selected_min_fps = -1, selected_max_fps = -1;
        for(int [] fps_range : fps_ranges) {
            if( Debug.LOG ) {
                Log.d(TAG, "    supported fps range: " + fps_range[0] + " to " + fps_range[1]);
            }
            int min_fps = fps_range[0];
            int max_fps = fps_range[1];
            if( max_fps >= 30000 ) {
                if( selected_min_fps == -1 || min_fps < selected_min_fps ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                }
                else if( min_fps == selected_min_fps && max_fps > selected_max_fps ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                }
            }
        }

        if( selected_min_fps != -1 ) {
            if( Debug.LOG ) {
                Log.d(TAG, "    chosen fps range: " + selected_min_fps + " to " + selected_max_fps);
            }
        }
        else {
            // just pick the widest range; if more than one, pick the one with highest max
            int selected_diff = -1;
            for(int [] fps_range : fps_ranges) {
                int min_fps = fps_range[0];
                int max_fps = fps_range[1];
                int diff = max_fps - min_fps;
                if( selected_diff == -1 || diff > selected_diff ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_diff = diff;
                }
                else if( diff == selected_diff && max_fps > selected_max_fps ) {
                    selected_min_fps = min_fps;
                    selected_max_fps = max_fps;
                    selected_diff = diff;
                }
            }
            if( Debug.LOG )
                Log.d(TAG, "    can't find fps range 30fps or better, so picked widest range: " + selected_min_fps + " to " + selected_max_fps);
        }
        return new int[]{selected_min_fps, selected_max_fps};
    }

    /* It's important to set a preview FPS using chooseBestPreviewFps() rather than just leaving it to the default, as some devices
     * have a poor choice of default - e.g., Nexus 5 and Nexus 6 on original Camera API default to (15000, 15000), which means very dark
     * preview and photos in low light, as well as a less smooth framerate in good light.
     * See http://stackoverflow.com/questions/18882461/why-is-the-default-android-camera-preview-smoother-than-my-own-camera-preview .
     */
    private void setPreviewFps() {
        if( Debug.LOG )
            Log.d(TAG, "setPreviewFps()");
        CamcorderProfile profile = getCamcorderProfile();
        List<int []> fps_ranges = camera_controller.getSupportedPreviewFpsRange();
        if( fps_ranges == null || fps_ranges.size() == 0 ) {
            if( Debug.LOG )
                Log.d(TAG, "fps_ranges not available");
            return;
        }
        int [] selected_fps = null;
        if( this.is_video ) {
            // For Nexus 5 and Nexus 6, we need to set the preview fps using matchPreviewFpsToVideo to avoid problem of dark preview in low light, as described above.
            // When the video recording starts, the preview automatically adjusts, but still good to avoid too-dark preview before the user starts recording.
            // However I'm wary of changing the behaviour for all devices at the moment, since some devices can be
            // very picky about what works when it comes to recording video - e.g., corruption in preview or resultant video.
            // So for now, I'm just fixing the Nexus 5/6 behaviour without changing behaviour for other devices. Later we can test on other devices, to see if we can
            // use chooseBestPreviewFps() more widely.
            // Update for v1.31: we no longer seem to need this - I no longer get a dark preview in photo or video mode if we don't set the fps range;
            // but leaving the code as it is, to be safe.
            boolean preview_too_dark = Build.MODEL.equals("Nexus 5") || Build.MODEL.equals("Nexus 6");
            String fps_value = applicationInterface.getVideoFPSPref();
            if( Debug.LOG ) {
                Log.d(TAG, "preview_too_dark? " + preview_too_dark);
                Log.d(TAG, "fps_value: " + fps_value);
            }
            if( fps_value.equals("default") && preview_too_dark ) {
                selected_fps = chooseBestPreviewFps(fps_ranges);
            }
            else {
                selected_fps = matchPreviewFpsToVideo(fps_ranges, profile.videoFrameRate*1000);
            }
        }
        else {
            // note that setting an fps here in continuous video focus mode causes preview to not restart after taking a photo on Galaxy Nexus
            // but we need to do this, to get good light for Nexus 5 or 6
            // we could hardcode behaviour like we do for video, but this is the same way that Google Camera chooses preview fps for photos
            // or I could hardcode behaviour for Galaxy Nexus, but since it's an old device (and an obscure bug anyway - most users don't really need continuous focus in photo mode), better to live with the bug rather than complicating the code
            // Update for v1.29: this doesn't seem to happen on Galaxy Nexus with continuous picture focus mode, which is what we now use
            // Update for v1.31: we no longer seem to need this - I no longer get a dark preview in photo or video mode if we don't set the fps range;
            // but leaving the code as it is, to be safe.
            selected_fps = chooseBestPreviewFps(fps_ranges);
        }
        camera_controller.setPreviewFpsRange(selected_fps[0], selected_fps[1]);
    }

    public void switchVideo(boolean during_startup) {
        if( Debug.LOG )
            Log.d(TAG, "switchVideo()");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        boolean old_is_video = is_video;
        if( this.is_video ) {
            if( video_recorder != null ) {
                stopVideo(false);
            }
            this.is_video = false;
        }
        else {
            if( this.isOnTimer() ) {
                cancelTimer();
                this.is_video = true;
            }
            //else if( this.is_taking_photo ) {
            else if( this.phase == PHASE_TAKING_PHOTO ) {
                // wait until photo taken
                if( Debug.LOG )
                    Log.d(TAG, "wait until photo taken");
            }
            else {
                this.is_video = true;
            }
        }

        if( is_video != old_is_video ) {
            setFocusPref(false); // first restore the saved focus for the new photo/video mode; don't do autofocus, as it'll be cancelled when restarting preview
            updateFocusForVideo(false); // don't do autofocus, as it'll be cancelled when restarting preview
			/*if( !is_video ) {
				// changing from video to photo mode
				setFocusPref(false); // first restore the saved focus for the new photo/video mode; don't do autofocus, as it'll be cancelled when restarting preview
			}*/

            if( !during_startup ) {
                // now save
                applicationInterface.setVideoPref(is_video);
            }

            if( !during_startup ) {
                String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
                if( Debug.LOG )
                    Log.d(TAG, "focus_value is " + focus_value);
                if( !is_video && focus_value != null && focus_value.equals("focus_mode_continuous_picture") ) {
                    if( Debug.LOG )
                        Log.d(TAG, "restart camera due to returning to continuous picture mode from video mode");
                    // workaround for bug on Nexus 6 at least where switching to video and back to photo mode causes continuous picture mode to stop
                    this.onPause();
                    this.onResume();
                }
                else {
                    if( this.is_preview_started ) {
                        camera_controller.stopPreview();
                        this.is_preview_started = false;
                    }
                    setPreviewSize();
                    // always start the camera preview, even if it was previously paused (also needed to update preview fps)
                    this.startCameraPreview();
                }
            }

			/*if( is_video ) {
				// changing from photo to video mode
				setFocusPref(false);
				updateFocusForVideo(false);
			}*/
            if( is_video ) {
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    // check for audio permission now, rather than when user starts video recording
                    // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
                    if( Debug.LOG )
                        Log.d(TAG, "check for record audio permission");
                    if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                        if( Debug.LOG )
                            Log.d(TAG, "record audio permission not available");
                        applicationInterface.requestRecordAudioPermission();
                        // we can now carry on - if the user starts recording video, we'll check then if the permission was granted
                    }
                }
            }
        }
    }

    public boolean focusIsVideo() {
        if( camera_controller != null ) {
            return camera_controller.focusIsVideo();
        }
        return false;
    }

    private void setFocusPref(boolean auto_focus) {
        if( Debug.LOG )
            Log.d(TAG, "setFocusPref()");
        String focus_value = applicationInterface.getFocusPref(is_video);
        if( focus_value.length() > 0 ) {
            if( Debug.LOG )
                Log.d(TAG, "found existing focus_value: " + focus_value);
            if( !updateFocus(focus_value, true, false, auto_focus) ) { // don't need to save, as this is the value that's already saved
                if( Debug.LOG )
                    Log.d(TAG, "focus value no longer supported!");
                updateFocus(0, true, true, auto_focus);
            }
        }
        else {
            if( Debug.LOG )
                Log.d(TAG, "found no existing focus_value");
            updateFocus(is_video ? "focus_mode_continuous_video" : "focus_mode_auto", true, true, auto_focus);
        }
    }

    public void updateFocusForVideo(boolean auto_focus) {
        if( Debug.LOG )
            Log.d(TAG, "updateFocusForVideo()");
        if( this.supported_focus_values != null && camera_controller != null && is_video ) { // originally we reset focus mode for photo mode too, but now we only do this for video mode (so if user wants to use continuous video mode for photo mode, that's fine, and we don't reset it)
            boolean focus_is_video = focusIsVideo();
            if( Debug.LOG ) {
                Log.d(TAG, "focus_is_video: " + focus_is_video + " , is_video: " + is_video);
            }
            if( focus_is_video != is_video ) {
                if( Debug.LOG )
                    Log.d(TAG, "need to change focus mode");
                updateFocus(is_video ? "focus_mode_continuous_video" : "focus_mode_auto", true, true, auto_focus);
            }
        }
    }

    public String getErrorFeatures(CamcorderProfile profile) {
        boolean was_4k = false, was_bitrate = false, was_fps = false;
        if( profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160 && applicationInterface.getForce4KPref() ) {
            was_4k = true;
        }
        String bitrate_value = applicationInterface.getVideoBitratePref();
        if( !bitrate_value.equals("default") ) {
            was_bitrate = true;
        }
        String fps_value = applicationInterface.getVideoFPSPref();
        if( !fps_value.equals("default") ) {
            was_fps = true;
        }
        String features = "";
        if( was_4k || was_bitrate || was_fps ) {
            if( was_4k ) {
                features = "4K UHD";
            }
            if( was_bitrate ) {
                if( features.length() == 0 )
                    features = "Bitrate";
                else
                    features += "/Bitrate";
            }
            if( was_fps ) {
                if( features.length() == 0 )
                    features = "Frame rate";
                else
                    features += "/Frame rate";
            }
        }
        return features;
    }

    public void updateFlash(String focus_value) {
        if( Debug.LOG )
            Log.d(TAG, "updateFlash(): " + focus_value);
        if( this.phase == PHASE_TAKING_PHOTO && !is_video ) {
            // just to be safe - risk of cancelling the autofocus before taking a photo, or otherwise messing things up
            if( Debug.LOG )
                Log.d(TAG, "currently taking a photo");
            return;
        }
        updateFlash(focus_value, true);
    }

    private boolean updateFlash(String flash_value, boolean save) {
        if( Debug.LOG )
            Log.d(TAG, "updateFlash(): " + flash_value);
        if( supported_flash_values != null ) {
            int new_flash_index = supported_flash_values.indexOf(flash_value);
            if( Debug.LOG )
                Log.d(TAG, "new_flash_index: " + new_flash_index);
            if( new_flash_index != -1 ) {
                updateFlash(new_flash_index, save);
                return true;
            }
        }
        return false;
    }

    private void updateFlash(int new_flash_index, boolean save) {
        if( Debug.LOG )
            Log.d(TAG, "updateFlash(): " + new_flash_index);
        // updates the Flash button, and Flash camera mode
        if( supported_flash_values != null && new_flash_index != current_flash_index ) {
            boolean initial = current_flash_index==-1;
            current_flash_index = new_flash_index;
            if( Debug.LOG )
                Log.d(TAG, "    current_flash_index is now " + current_flash_index + " (initial " + initial + ")");

            //Activity activity = (Activity)this.getContext();
            String [] flash_entries = getResources().getStringArray(R.array.flash_entries);
            //String [] flash_icons = getResources().getStringArray(R.array.flash_icons);
            String flash_value = supported_flash_values.get(current_flash_index);
            if( Debug.LOG )
                Log.d(TAG, "    flash_value: " + flash_value);
            String [] flash_values = getResources().getStringArray(R.array.flash_values);
            for(int i=0;i<flash_values.length;i++) {
				/*if( Debug.LOG )
					Log.d(TAG, "    compare to: " + flash_values[i]);*/
                if( flash_value.equals(flash_values[i]) ) {
                    if( Debug.LOG )
                        Log.d(TAG, "    found entry: " + i);
                    if( !initial ) {
                        showToast(flash_toast, flash_entries[i]);
                    }
                    break;
                }
            }
            this.setFlash(flash_value);
            if( save ) {
                // now save
                applicationInterface.setFlashPref(flash_value);
            }
        }
    }

    private void setFlash(String flash_value) {
        if( Debug.LOG )
            Log.d(TAG, "setFlash() " + flash_value);
        set_flash_value_after_autofocus = ""; // this overrides any previously saved setting, for during the startup autofocus
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        cancelAutoFocus();
        camera_controller.setFlashValue(flash_value);
    }

    // this returns the flash value indicated by the UI, rather than from the camera parameters (may be different, e.g., in startup autofocus!)
    public String getCurrentFlashValue() {
        if( this.current_flash_index == -1 )
            return null;
        return this.supported_flash_values.get(current_flash_index);
    }

    // this returns the flash mode indicated by the UI, rather than from the camera parameters (may be different, e.g., in startup autofocus!)
	/*public String getCurrentFlashMode() {
		if( current_flash_index == -1 )
			return null;
		String flash_value = supported_flash_values.get(current_flash_index);
		String flash_mode = convertFlashValueToMode(flash_value);
		return flash_mode;
	}*/

    public void updateFocus(String focus_value, boolean quiet, boolean auto_focus) {
        if( Debug.LOG )
            Log.d(TAG, "updateFocus(): " + focus_value);
        if( this.phase == PHASE_TAKING_PHOTO ) {
            // just to be safe - otherwise problem that changing the focus mode will cancel the autofocus before taking a photo, so we never take a photo, but is_taking_photo remains true!
            if( Debug.LOG )
                Log.d(TAG, "currently taking a photo");
            return;
        }
        updateFocus(focus_value, quiet, true, auto_focus);
    }

    private boolean supportedFocusValue(String focus_value) {
        if( Debug.LOG )
            Log.d(TAG, "supportedFocusValue(): " + focus_value);
        if( this.supported_focus_values != null ) {
            int new_focus_index = supported_focus_values.indexOf(focus_value);
            if( Debug.LOG )
                Log.d(TAG, "new_focus_index: " + new_focus_index);
            return new_focus_index != -1;
        }
        return false;
    }

    private boolean updateFocus(String focus_value, boolean quiet, boolean save, boolean auto_focus) {
        if( Debug.LOG )
            Log.d(TAG, "updateFocus(): " + focus_value);
        if( this.supported_focus_values != null ) {
            int new_focus_index = supported_focus_values.indexOf(focus_value);
            if( Debug.LOG )
                Log.d(TAG, "new_focus_index: " + new_focus_index);
            if( new_focus_index != -1 ) {
                updateFocus(new_focus_index, quiet, save, auto_focus);
                return true;
            }
        }
        return false;
    }

    private String findEntryForValue(String value, int entries_id, int values_id) {
        String [] entries = getResources().getStringArray(entries_id);
        String [] values = getResources().getStringArray(values_id);
        for(int i=0;i<values.length;i++) {
            if( Debug.LOG )
                Log.d(TAG, "    compare to value: " + values[i]);
            if( value.equals(values[i]) ) {
                if( Debug.LOG )
                    Log.d(TAG, "    found entry: " + i);
                return entries[i];
            }
        }
        return null;
    }

    public String findFocusEntryForValue(String focus_value) {
        return findEntryForValue(focus_value, R.array.focus_mode_entries, R.array.focus_mode_values);
    }

    private void updateFocus(int new_focus_index, boolean quiet, boolean save, boolean auto_focus) {
        if( Debug.LOG )
            Log.d(TAG, "updateFocus(): " + new_focus_index + " current_focus_index: " + current_focus_index);
        // updates the Focus button, and Focus camera mode
        if( this.supported_focus_values != null && new_focus_index != current_focus_index ) {
            current_focus_index = new_focus_index;
            if( Debug.LOG )
                Log.d(TAG, "    current_focus_index is now " + current_focus_index);

            String focus_value = supported_focus_values.get(current_focus_index);
            if( Debug.LOG )
                Log.d(TAG, "    focus_value: " + focus_value);
            if( !quiet ) {
                String focus_entry = findFocusEntryForValue(focus_value);
                if( focus_entry != null ) {
                    showToast(focus_toast, focus_entry);
                }
            }
            this.setFocusValue(focus_value, auto_focus);

            if( save ) {
                // now save
                applicationInterface.setFocusPref(focus_value, is_video);
            }
        }
    }

    /** This returns the flash mode indicated by the UI, rather than from the camera parameters.
     */
    public String getCurrentFocusValue() {
        if( Debug.LOG )
            Log.d(TAG, "getCurrentFocusValue()");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return null;
        }
        if( this.supported_focus_values != null && this.current_focus_index != -1 )
            return this.supported_focus_values.get(current_focus_index);
        return null;
    }

    private void setFocusValue(String focus_value, boolean auto_focus) {
        if( Debug.LOG )
            Log.d(TAG, "setFocusValue() " + focus_value);
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        cancelAutoFocus();
        removePendingContinuousFocusReset(); // this isn't strictly needed as the reset_continuous_focus_runnable will check the ui focus mode when it runs, but good to remove it anyway
        autofocus_in_continuous_mode = false;
        camera_controller.setFocusValue(focus_value);
        setupContinuousFocusMove();
        clearFocusAreas();
        if( auto_focus && !focus_value.equals("focus_mode_locked") ) {
            tryAutoFocus(false, false);
        }
    }

    private void setupContinuousFocusMove() {
        if( Debug.LOG )
            Log.d(TAG, "setupContinuousFocusMove()" );
        if( continuous_focus_move_is_started ) {
            continuous_focus_move_is_started = false;
            applicationInterface.onContinuousFocusMove(false);
        }
        String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
        if( Debug.LOG )
            Log.d(TAG, "focus_value is " + focus_value);
        if( camera_controller != null && focus_value != null && focus_value.equals("focus_mode_continuous_picture") && !this.is_video ) {
            if( Debug.LOG )
                Log.d(TAG, "set continuous picture focus move callback");
            camera_controller.setContinuousFocusMoveCallback(new Controller.ContinuousFocusMoveCallback() {
                @Override
                public void onContinuousFocusMove(boolean start) {
                    if( start != continuous_focus_move_is_started ) { // filter out repeated calls with same start value
                        continuous_focus_move_is_started = start;
                        count_cameraContinuousFocusMoving++;
                        applicationInterface.onContinuousFocusMove(start);
                    }
                }
            });
        }
        else if( camera_controller != null ) {
            if( Debug.LOG )
                Log.d(TAG, "remove continuous picture focus move callback");
            camera_controller.setContinuousFocusMoveCallback(null);
        }
    }

    public void toggleExposureLock() {
        if( Debug.LOG )
            Log.d(TAG, "toggleExposureLock()");
        // n.b., need to allow when recording video, so no check on PHASE_TAKING_PHOTO
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return;
        }
        if( is_exposure_lock_supported ) {
            is_exposure_locked = !is_exposure_locked;
            cancelAutoFocus();
            camera_controller.setAutoExposureLock(is_exposure_locked);
        }
    }

    /** User has clicked the "take picture" button (or equivalent GUI operation).
     */
    public void takePicturePressed() {
        if( Debug.LOG )
            Log.d(TAG, "takePicturePressed");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            return;
        }
        if( !this.has_surface ) {
            if( Debug.LOG )
                Log.d(TAG, "preview surface not yet available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            return;
        }
        //if( is_taking_photo_on_timer ) {
        if( this.isOnTimer() ) {
            cancelTimer();
            showToast(take_photo_toast, R.string.cancelled_timer);
            return;
        }
        //if( is_taking_photo ) {
        if( this.phase == PHASE_TAKING_PHOTO ) {
            if( is_video ) {
                if( !video_start_time_set || System.currentTimeMillis() - video_start_time < 500 ) {
                    // if user presses to stop too quickly, we ignore
                    // firstly to reduce risk of corrupt video files when stopping too quickly (see RuntimeException we have to catch in stopVideo),
                    // secondly, to reduce a backlog of events which slows things down, if user presses start/stop repeatedly too quickly
                    if( Debug.LOG )
                        Log.d(TAG, "ignore pressing stop video too quickly after start");
                }
                else {
                    stopVideo(false);
                }
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "already taking a photo");
                if( remaining_burst_photos != 0 ) {
                    remaining_burst_photos = 0;
                    showToast(take_photo_toast, R.string.cancelled_burst_mode);
                }
            }
            return;
        }

        // make sure that preview running (also needed to hide trash/share icons)
        this.startCameraPreview();

        //is_taking_photo = true;
        long timer_delay = applicationInterface.getTimerPref();

        String burst_mode_value = applicationInterface.getRepeatPref();
        int n_burst = 1;
        if( burst_mode_value.equals("unlimited") ) {
            if( Debug.LOG )
                Log.d(TAG, "unlimited burst");
            n_burst = -1;
            remaining_burst_photos = -1;
        }
        else {
            try {
                n_burst = Integer.parseInt(burst_mode_value);
                if( Debug.LOG )
                    Log.d(TAG, "n_burst: " + n_burst);
            }
            catch(NumberFormatException e) {
                if( Debug.LOG )
                    Log.e(TAG, "failed to parse preference_burst_mode value: " + burst_mode_value);
                e.printStackTrace();
                n_burst = 1;
            }
            remaining_burst_photos = n_burst-1;
        }

        if( timer_delay == 0 ) {
            takePicture(false);
        }
        else {
            takePictureOnTimer(timer_delay, false);
        }
        if( Debug.LOG )
            Log.d(TAG, "takePicturePressed exit");
    }

    private void takePictureOnTimer(final long timer_delay, boolean repeated) {
        if( Debug.LOG ) {
            Log.d(TAG, "takePictureOnTimer");
            Log.d(TAG, "timer_delay: " + timer_delay);
        }
        this.phase = PHASE_TIMER;
        class TakePictureTimerTask extends TimerTask {
            public void run() {
                if( beepTimerTask != null ) {
                    beepTimerTask.cancel();
                    beepTimerTask = null;
                }
                Activity activity = (Activity)Preview.this.getContext();
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        // we run on main thread to avoid problem of camera closing at the same time
                        // but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
                        if( camera_controller != null && takePictureTimerTask != null )
                            takePicture(false);
                        else {
                            if( Debug.LOG )
                                Log.d(TAG, "takePictureTimerTask: don't take picture, as already cancelled");
                        }
                    }
                });
            }
        }
        take_photo_time = System.currentTimeMillis() + timer_delay;
        if( Debug.LOG )
            Log.d(TAG, "take photo at: " + take_photo_time);
		/*if( !repeated ) {
			showToast(take_photo_toast, R.string.started_timer);
		}*/
        takePictureTimer.schedule(takePictureTimerTask = new TakePictureTimerTask(), timer_delay);

        class BeepTimerTask extends TimerTask {
            long remaining_time = timer_delay;
            public void run() {
                if( remaining_time > 0 ) { // check in case this isn't cancelled by time we take the photo
                    applicationInterface.timerBeep(remaining_time);
                }
                remaining_time -= 1000;
            }
        }
        beepTimer.schedule(beepTimerTask = new BeepTimerTask(), 0, 1000);
    }

    private void flashVideo() {
        if( Debug.LOG )
            Log.d(TAG, "flashVideo");
        // getFlashValue() may return "" if flash not supported!
        String flash_value = camera_controller.getFlashValue();
        if( flash_value.length() == 0 )
            return;
        String flash_value_ui = getCurrentFlashValue();
        if( flash_value_ui == null )
            return;
        if( flash_value_ui.equals("flash_torch") )
            return;
        if( flash_value.equals("flash_torch") ) {
            // shouldn't happen? but set to what the UI is
            cancelAutoFocus();
            camera_controller.setFlashValue(flash_value_ui);
            return;
        }
        // turn on torch
        cancelAutoFocus();
        camera_controller.setFlashValue("flash_torch");
        try {
            Thread.sleep(100);
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }
        // turn off torch
        cancelAutoFocus();
        camera_controller.setFlashValue(flash_value_ui);
    }

    private void onVideoInfo(int what, int extra) {
        if( Debug.LOG )
            Log.d(TAG, "onVideoInfo: " + what + " extra: " + extra);
        if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED && video_restart_on_max_filesize ) {
            if( Debug.LOG )
                Log.d(TAG, "restart due to max filesize reached");
            Activity activity = (Activity)Preview.this.getContext();
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    // we run on main thread to avoid problem of camera closing at the same time
                    // but still need to check that the camera hasn't closed
                    if( camera_controller != null )
                        restartVideo(true);
                    else {
                        if( Debug.LOG )
                            Log.d(TAG, "don't restart video, as already cancelled");
                    }
                }
            });
        }
        else if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
            stopVideo(false);
        }
        applicationInterface.onVideoInfo(what, extra); // call this last, so that toasts show up properly (as we're hogging the UI thread here, and mediarecorder takes time to stop)
    }

    private void onVideoError(int what, int extra) {
        if( Debug.LOG )
            Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
        stopVideo(false);
        applicationInterface.onVideoError(what, extra); // call this last, so that toasts show up properly (as we're hogging the UI thread here, and mediarecorder takes time to stop)
    }

    /** Initiate "take picture" command. In video mode this means starting video command. In photo mode this may involve first
     * autofocusing.
     */
    private void takePicture(boolean max_filesize_restart) {
        if( Debug.LOG )
            Log.d(TAG, "takePicture");
        //this.thumbnail_anim = false;
        this.phase = PHASE_TAKING_PHOTO;
        synchronized( this ) {
            // synchronise for consistency (keep FindBugs happy)
            take_photo_after_autofocus = false;
        }
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            applicationInterface.cameraInOperation(false);
            return;
        }
        if( !this.has_surface ) {
            if( Debug.LOG )
                Log.d(TAG, "preview surface not yet available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            applicationInterface.cameraInOperation(false);
            return;
        }

        boolean store_location = applicationInterface.getGeotaggingPref();
        if( store_location ) {
            boolean require_location = applicationInterface.getRequireLocationPref();
            if( require_location ) {
                if( applicationInterface.getLocation() != null ) {
                    // fine, we have location
                }
                else {
                    if( Debug.LOG )
                        Log.d(TAG, "location data required, but not available");
                    showToast(null, R.string.location_not_available);
                    this.phase = PHASE_NORMAL;
                    applicationInterface.cameraInOperation(false);
                    return;
                }
            }
        }

        if( is_video ) {
            if( Debug.LOG )
                Log.d(TAG, "start video recording");
            startVideoRecording(max_filesize_restart);
            return;
        }

        takePhoto(false);
        if( Debug.LOG )
            Log.d(TAG, "takePicture exit");
    }

    /** Start video recording.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startVideoRecording(boolean max_filesize_restart) {
        focus_success = FOCUS_DONE; // clear focus rectangle (don't do for taking photos yet)
        // initialise just in case:
        boolean created_video_file = false;
        video_method = ApplicationInterface.VIDEOMETHOD_FILE;
        video_uri = null;
        video_filename = null;
        ParcelFileDescriptor pfd_saf = null;
        try {
            video_method = applicationInterface.createOutputVideoMethod();
            if( Debug.LOG )
                Log.e(TAG, "video_method? " + video_method);
            if( video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
                File videoFile = applicationInterface.createOutputVideoFile();
                video_filename = videoFile.getAbsolutePath();
                created_video_file = true;
                if( Debug.LOG )
                    Log.d(TAG, "save to: " + video_filename);
            }
            else {
                if( video_method == ApplicationInterface.VIDEOMETHOD_SAF ) {
                    video_uri = applicationInterface.createOutputVideoSAF();
                }
                else {
                    video_uri = applicationInterface.createOutputVideoUri();
                }
                created_video_file = true;
                if( Debug.LOG )
                    Log.d(TAG, "save to: " + video_uri);
                pfd_saf = getContext().getContentResolver().openFileDescriptor(video_uri, "rw");
            }
        }
        catch(IOException e) {
            if( Debug.LOG )
                Log.e(TAG, "Couldn't create media video file; check storage permissions?");
            e.printStackTrace();
            applicationInterface.onFailedCreateVideoFileError();
            this.phase = PHASE_NORMAL;
            applicationInterface.cameraInOperation(false);
        }
        if( created_video_file ) {
            CamcorderProfile profile = getCamcorderProfile();
            if( Debug.LOG ) {
                Log.d(TAG, "current_video_quality: " + current_video_quality);
                if( current_video_quality != -1 )
                    Log.d(TAG, "current_video_quality value: " + video_quality.get(current_video_quality));
                Log.d(TAG, "resolution " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
                Log.d(TAG, "bit rate " + profile.videoBitRate);
            }

            video_recorder = new MediaRecorder();
            this.camera_controller.unlock();
            if( Debug.LOG )
                Log.d(TAG, "set video listeners");
            video_recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if( Debug.LOG )
                        Log.d(TAG, "MediaRecorder info: " + what + " extra: " + extra);
                    final int final_what = what;
                    final int final_extra = extra;
                    Activity activity = (Activity)Preview.this.getContext();
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            // we run on main thread to avoid problem of camera closing at the same time
                            onVideoInfo(final_what, final_extra);
                        }
                    });
                }
            });
            video_recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                public void onError(MediaRecorder mr, int what, int extra) {
                    final int final_what = what;
                    final int final_extra = extra;
                    Activity activity = (Activity)Preview.this.getContext();
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            // we run on main thread to avoid problem of camera closing at the same time
                            onVideoError(final_what, final_extra);
                        }
                    });
                }
            });
            camera_controller.initVideoRecorderPrePrepare(video_recorder);
            boolean record_audio = applicationInterface.getRecordAudioPref();
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                // needed for Android 6, in case users deny storage permission, otherwise we'll crash
                // see https://developer.android.com/training/permissions/requesting.html
                // we request permission when switching to video mode - if it wasn't granted, here we just switch it off
                // we restrict check to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
                if( Debug.LOG )
                    Log.e(TAG, "don't have RECORD_AUDIO permission");
                showToast(null, R.string.permission_record_audio_not_available);
                record_audio = false;
            }
            if( record_audio ) {
                String pref_audio_src = applicationInterface.getRecordAudioSourcePref();
                if( Debug.LOG )
                    Log.d(TAG, "pref_audio_src: " + pref_audio_src);
                int audio_source = MediaRecorder.AudioSource.CAMCORDER;
                if( pref_audio_src.equals("audio_src_mic") ) {
                    audio_source = MediaRecorder.AudioSource.MIC;
                }
                else if( pref_audio_src.equals("audio_src_default") ) {
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if( pref_audio_src.equals("audio_src_voice_communication") ) {
                    audio_source = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
                }
                if( Debug.LOG )
                    Log.d(TAG, "audio_source: " + audio_source);
                video_recorder.setAudioSource(audio_source);
            }
            if( Debug.LOG )
                Log.d(TAG, "set video source");
            video_recorder.setVideoSource(using_android_l ? MediaRecorder.VideoSource.SURFACE : MediaRecorder.VideoSource.CAMERA);

            boolean store_location = applicationInterface.getGeotaggingPref();
            if( store_location && applicationInterface.getLocation() != null ) {
                Location location = applicationInterface.getLocation();
                if( Debug.LOG ) {
                    Log.d(TAG, "set video location: lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy());
                }
                video_recorder.setLocation((float)location.getLatitude(), (float)location.getLongitude());
            }

            if( Debug.LOG )
                Log.d(TAG, "set video profile");
            if( record_audio ) {
                video_recorder.setProfile(profile);
                String pref_audio_channels = applicationInterface.getRecordAudioChannelsPref();
                if( Debug.LOG )
                    Log.d(TAG, "pref_audio_channels: " + pref_audio_channels);
                if( pref_audio_channels.equals("audio_mono") ) {
                    video_recorder.setAudioChannels(1);
                }
                else if( pref_audio_channels.equals("audio_stereo") ) {
                    video_recorder.setAudioChannels(2);
                }
            }
            else {
                // from http://stackoverflow.com/questions/5524672/is-it-possible-to-use-camcorderprofile-without-audio-source
                video_recorder.setOutputFormat(profile.fileFormat);
                video_recorder.setVideoFrameRate(profile.videoFrameRate);
                video_recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
                video_recorder.setVideoEncodingBitRate(profile.videoBitRate);
                video_recorder.setVideoEncoder(profile.videoCodec);
            }
            if( Debug.LOG ) {
                Log.d(TAG, "video fileformat: " + profile.fileFormat);
                Log.d(TAG, "video framerate: " + profile.videoFrameRate);
                Log.d(TAG, "video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
                Log.d(TAG, "video bitrate: " + profile.videoBitRate);
                Log.d(TAG, "video codec: " + profile.videoCodec);
            }
            try {
                //video_recorder.setMaxFileSize(15*1024*1024); // test
                ApplicationInterface.VideoMaxFileSize video_max_filesize = applicationInterface.getVideoMaxFileSizePref();
                long max_filesize = video_max_filesize.max_filesize;
                if( max_filesize > 0 ) {
                    if( Debug.LOG )
                        Log.d(TAG, "set max file size of: " + max_filesize);
                    try {
                        video_recorder.setMaxFileSize(max_filesize);
                    }
                    catch(RuntimeException e) {
                        // Google Camera warns this can happen - for example, if 64-bit filesizes not supported
                        if( Debug.LOG )
                            Log.e(TAG, "failed to set max filesize of: " + max_filesize);
                        e.printStackTrace();
                    }
                }
                video_restart_on_max_filesize = video_max_filesize.auto_restart; // note, we set this even if max_filesize==0, as it will still apply when hitting device max filesize limit

                if( video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
                    video_recorder.setOutputFile(video_filename);
                }
                else {
                    video_recorder.setOutputFile(pfd_saf.getFileDescriptor());
                }

                applicationInterface.cameraInOperation(true);
                applicationInterface.startingVideo();
        		/*if( true ) // test
        			throw new IOException();*/
                cameraSurface.setVideoRecorder(video_recorder);
                video_recorder.setOrientationHint(getImageVideoRotation());
                if( Debug.LOG )
                    Log.d(TAG, "about to prepare video recorder");
                video_recorder.prepare();
                camera_controller.initVideoRecorderPostPrepare(video_recorder);
                if( Debug.LOG )
                    Log.d(TAG, "about to start video recorder");
                video_recorder.start();
                if( Debug.LOG )
                    Log.d(TAG, "video recorder started");
                if( test_video_failure ) {
                    if( Debug.LOG )
                        Log.d(TAG, "test_video_failure is true");
                    throw new RuntimeException();
                }
                video_start_time = System.currentTimeMillis();
                video_start_time_set = true;
                //showToast(stopstart_video_toast, R.string.started_recording_video);
                // don't send intent for ACTION_MEDIA_SCANNER_SCAN_FILE yet - wait until finished, so we get completed file

                // handle restarts
                if( remaining_restart_video == 0 && !max_filesize_restart ) {
                    remaining_restart_video = applicationInterface.getVideoRestartTimesPref();
                    if( Debug.LOG )
                        Log.d(TAG, "initialised remaining_restart_video to: " + remaining_restart_video);
                }

                // handle restart timer
                long video_max_duration = applicationInterface.getVideoMaxDurationPref();
                if( Debug.LOG )
                    Log.d(TAG, "video_max_duration: " + video_max_duration);
                if( max_filesize_restart ) {
                    if( video_max_duration > 0 ) {
                        video_max_duration -= video_accumulated_time;
                        // this should be greater or equal to min_safe_restart_video_time, as too short remaining time should have been caught in restartVideo()
                        if( video_max_duration < min_safe_restart_video_time ) {
                            if( Debug.LOG )
                                Log.e(TAG, "trying to restart video with too short a time: " + video_max_duration);
                            video_max_duration = min_safe_restart_video_time;
                        }
                    }
                }
                else {
                    video_accumulated_time = 0;
                }

                if( video_max_duration > 0 ) {
                    class RestartVideoTimerTask extends TimerTask {
                        public void run() {
                            if( Debug.LOG )
                                Log.d(TAG, "stop video on timer");
                            Activity activity = (Activity)Preview.this.getContext();
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    // we run on main thread to avoid problem of camera closing at the same time
                                    // but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
                                    if( camera_controller != null && restartVideoTimerTask != null )
                                        restartVideo(false);
                                    else {
                                        if( Debug.LOG )
                                            Log.d(TAG, "restartVideoTimerTask: don't restart video, as already cancelled");
                                    }
                                }
                            });
                        }
                    }
                    restartVideoTimer.schedule(restartVideoTimerTask = new RestartVideoTimerTask(), video_max_duration);
                }

                if( applicationInterface.getVideoFlashPref() && supportsFlash() ) {
                    class FlashVideoTimerTask extends TimerTask {
                        public void run() {
                            if( Debug.LOG )
                                Log.e(TAG, "FlashVideoTimerTask");
                            Activity activity = (Activity)Preview.this.getContext();
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    // we run on main thread to avoid problem of camera closing at the same time
                                    // but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
                                    if( camera_controller != null && flashVideoTimerTask != null )
                                        flashVideo();
                                    else {
                                        if( Debug.LOG )
                                            Log.d(TAG, "flashVideoTimerTask: don't flash video, as already cancelled");
                                    }
                                }
                            });
                        }
                    }
                    flashVideoTimer.schedule(flashVideoTimerTask = new FlashVideoTimerTask(), 0, 1000);
                }

                if( applicationInterface.getVideoLowPowerCheckPref() ) {
                    /* When a device shuts down due to power off, the application will receive shutdown signals, and normally the video
                     * should stop and be valid. However it can happen that the video ends up corrupted (I've had people telling me this
                     * can happen; Googling finds plenty of stories of this happening on Android devices). I think the issue is that for
                     * very large videos, a lot of time is spent processing during the MediaRecorder.stop() call - if that doesn't complete
                     * by the time the device switches off, the video may be corrupt.
                     * So we add an extra safety net - devices typically turn off abou 1%, but we stop video at 3% to be safe. The user
                     * can try recording more videos after that if the want, but this reduces the risk that really long videos are entirely
                     * lost.
                     */
                    class BatteryCheckVideoTimerTask extends TimerTask {
                        public void run() {
                            if( Debug.LOG )
                                Log.d(TAG, "BatteryCheckVideoTimerTask");

                            // only check periodically - unclear if checking is costly in any way
                            // note that it's fine to call registerReceiver repeatedly - we pass a null receiver, so this is fine as a "one shot" use
                            Intent batteryStatus = getContext().registerReceiver(null, battery_ifilter);
                            int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            double battery_frac = battery_level/(double)battery_scale;
                            if( Debug.LOG )
                                Log.d(TAG, "batteryCheckVideoTimerTask: battery level at: " + battery_frac);

                            if( battery_frac <= 0.03 ) {
                                if( Debug.LOG )
                                    Log.d(TAG, "batteryCheckVideoTimerTask: battery at critical level, switching off video");
                                Activity activity = (Activity)Preview.this.getContext();
                                activity.runOnUiThread(new Runnable() {
                                    public void run() {
                                        // we run on main thread to avoid problem of camera closing at the same time
                                        // but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
                                        if( camera_controller != null && batteryCheckVideoTimerTask != null ) {
                                            stopVideo(false);
                                            String toast = getContext().getResources().getString(R.string.video_power_critical);
                                            showToast(null, toast); // show the toast afterwards, as we're hogging the UI thread here, and media recorder takes time to stop
                                        }
                                        else {
                                            if( Debug.LOG )
                                                Log.d(TAG, "batteryCheckVideoTimerTask: don't stop video, as already cancelled");
                                        }
                                    }
                                });
                            }
                        }
                    }
                    final long battery_check_interval_ms = 60 * 1000;
                    // Since we only first check after battery_check_interval_ms, this means users will get some video recorded even if the battery is already too low.
                    // But this is fine, as typically short videos won't be corrupted if the device shuts off, and good to allow users to try to record a bit more if they want.
                    batteryCheckVideoTimer.schedule(batteryCheckVideoTimerTask = new BatteryCheckVideoTimerTask(), battery_check_interval_ms, battery_check_interval_ms);
                }
            }
            catch(IOException e) {
                if( Debug.LOG )
                    Log.e(TAG, "failed to save video");
                e.printStackTrace();
                applicationInterface.onFailedCreateVideoFileError();
                video_recorder.reset();
                video_recorder.release();
                video_recorder = null;
                this.phase = PHASE_NORMAL;
                applicationInterface.cameraInOperation(false);
                this.reconnectCamera(true);
            }
            catch(RuntimeException e) {
                // needed for emulator at least - although MediaRecorder not meant to work with emulator, it's good to fail gracefully
                if( Debug.LOG )
                    Log.e(TAG, "runtime exception starting video recorder");
                e.printStackTrace();
                failedToStartVideoRecorder(profile);
            }
            catch(ControllerException e) {
                if( Debug.LOG )
                    Log.e(TAG, "camera exception starting video recorder");
                e.printStackTrace();
                failedToStartVideoRecorder(profile);
            }
            catch(NoFreeStorageException e) {
                if( Debug.LOG )
                    Log.e(TAG, "nofreestorageexception starting video recorder");
                e.printStackTrace();
                video_recorder.reset();
                video_recorder.release();
                video_recorder = null;
                this.phase = PHASE_NORMAL;
                applicationInterface.cameraInOperation(false);
                this.reconnectCamera(true);
                this.showToast(null, R.string.video_no_free_space);
            }
        }
    }

    private void failedToStartVideoRecorder(CamcorderProfile profile) {
        applicationInterface.onVideoRecordStartError(profile);
        video_recorder.reset();
        video_recorder.release();
        video_recorder = null;
        this.phase = PHASE_NORMAL;
        applicationInterface.cameraInOperation(false);
        this.reconnectCamera(true);
    }

    /** Take photo. The caller should aready have set the phase to PHASE_TAKING_PHOTO.
     */
    private void takePhoto(boolean skip_autofocus) {
        if( Debug.LOG )
            Log.d(TAG, "takePhoto");
        applicationInterface.cameraInOperation(true);
        String current_ui_focus_value = getCurrentFocusValue();
        if( Debug.LOG )
            Log.d(TAG, "current_ui_focus_value is " + current_ui_focus_value);

        if( autofocus_in_continuous_mode ) {
            if( Debug.LOG )
                Log.d(TAG, "continuous mode where user touched to focus");
            synchronized(this) {
                // as below, if an autofocus is in progress, then take photo when it's completed
                if( focus_success == FOCUS_WAITING ) {
                    if( Debug.LOG )
                        Log.d(TAG, "autofocus_in_continuous_mode: take photo after current focus");
                    take_photo_after_autofocus = true;
                }
                else {
                    // when autofocus_in_continuous_mode==true, it means the user recently touched to focus in continuous focus mode, so don't do another focus
                    if( Debug.LOG )
                        Log.d(TAG, "autofocus_in_continuous_mode: no need to refocus");
                    takePhotoWhenFocused();
                }
            }
        }
        else if( camera_controller.focusIsContinuous() ) {
            if( Debug.LOG )
                Log.d(TAG, "call autofocus for continuous focus mode");
            // we call via autoFocus(), to avoid risk of taking photo while the continuous focus is focusing - risk of blurred photo, also sometimes get bug in such situations where we end of repeatedly focusing
            // this is the case even if skip_autofocus is true (as we still can't guarantee that continuous focusing might be occurring)
            // note: if the user touches to focus in continuous mode, we camera controller may be in auto focus mode, so we should only enter this codepath if the camera_controller is in continuous focus mode
            Controller.AutoFocusCallback autoFocusCallback = new Controller.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success) {
                    if( Debug.LOG )
                        Log.d(TAG, "continuous mode autofocus complete: " + success);
                    takePhotoWhenFocused();
                }
            };
            camera_controller.autoFocus(autoFocusCallback);
        }
        else if( skip_autofocus || this.recentlyFocused() ) {
            if( Debug.LOG ) {
                if( skip_autofocus ) {
                    Log.d(TAG, "skip_autofocus flag set");
                }
                else {
                    Log.d(TAG, "recently focused successfully, so no need to refocus");
                }
            }
            takePhotoWhenFocused();
        }
        else if( current_ui_focus_value != null && ( current_ui_focus_value.equals("focus_mode_auto") || current_ui_focus_value.equals("focus_mode_macro") ) ) {
            // n.b., we check focus_value rather than camera_controller.supportsAutoFocus(), as we want to discount focus_mode_locked
            synchronized(this) {
                if( focus_success == FOCUS_WAITING ) {
                    // Needed to fix bug (on Nexus 6, old camera API): if flash was on, pointing at a dark scene, and we take photo when already autofocusing, the autofocus never returned so we got stuck!
                    // In general, probably a good idea to not redo a focus - just use the one that's already in progress
                    if( Debug.LOG )
                        Log.d(TAG, "take photo after current focus");
                    take_photo_after_autofocus = true;
                }
                else {
                    focus_success = FOCUS_DONE; // clear focus rectangle for new refocus
                    Controller.AutoFocusCallback autoFocusCallback = new Controller.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success) {
                            if( Debug.LOG )
                                Log.d(TAG, "autofocus complete: " + success);
                            ensureFlashCorrect(); // need to call this in case user takes picture before startup focus completes!
                            prepareAutoFocusPhoto();
                            takePhotoWhenFocused();
                        }
                    };
                    if( Debug.LOG )
                        Log.d(TAG, "start autofocus to take picture");
                    camera_controller.autoFocus(autoFocusCallback);
                    count_cameraAutoFocus++;
                }
            }
        }
        else {
            takePhotoWhenFocused();
        }
    }

    /** Should be called when taking a photo immediately after an autofocus.
     *  This is needed for a workaround for Camera2 bug (at least on Nexus 6) where photos sometimes come out dark when using flash
     *  auto, when the flash fires. This happens when taking a photo in autofocus mode (including when continuous mode has
     *  transitioned to autofocus mode due to touching to focus). Seems to happen with scenes that have bright and dark regions,
     *  i.e., on verge of flash firing.
     *  Seems to be fixed if we have a short delay...
     */
    private void prepareAutoFocusPhoto() {
        if( Debug.LOG )
            Log.d(TAG, "prepareAutoFocusPhoto");
        if( using_android_l ) {
            String flash_value = camera_controller.getFlashValue();
            // getFlashValue() may return "" if flash not supported!
            if( flash_value.length() > 0 && ( flash_value.equals("flash_auto") || flash_value.equals("flash_red_eye") ) ) {
                if( Debug.LOG )
                    Log.d(TAG, "wait for a bit...");
                try {
                    Thread.sleep(100);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Take photo, assumes any autofocus has already been taken care of, and that applicationInterface.cameraInOperation(true) has
     *  already been called.
     *  Note that even if a caller wants to take a photo without focusing, you probably want to call takePhoto() with skip_autofocus
     *  set to true (so that things work okay in continuous picture focus mode).
     */
    private void takePhotoWhenFocused() {
        // should be called when auto-focused
        if( Debug.LOG )
            Log.d(TAG, "takePhotoWhenFocused");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            applicationInterface.cameraInOperation(false);
            return;
        }
        if( !this.has_surface ) {
            if( Debug.LOG )
                Log.d(TAG, "preview surface not yet available");
			/*is_taking_photo_on_timer = false;
			is_taking_photo = false;*/
            this.phase = PHASE_NORMAL;
            applicationInterface.cameraInOperation(false);
            return;
        }

        final String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
        if( Debug.LOG ) {
            Log.d(TAG, "focus_value is " + focus_value);
            Log.d(TAG, "focus_success is " + focus_success);
        }

        if( focus_value != null && focus_value.equals("focus_mode_locked") && focus_success == FOCUS_WAITING ) {
            // make sure there isn't an autofocus in progress - can happen if in locked mode we take a photo while autofocusing - see testTakePhotoLockedFocus() (although that test doesn't always properly test the bug...)
            // we only cancel when in locked mode and if still focusing, as I had 2 bug reports for v1.16 that the photo was being taken out of focus; both reports said it worked fine in 1.15, and one confirmed that it was due to the cancelAutoFocus() line, and that it's now fixed with this fix
            // they said this happened in every focus mode, including locked - so possible that on some devices, cancelAutoFocus() actually pulls the camera out of focus, or reverts to preview focus?
            cancelAutoFocus();
        }
        removePendingContinuousFocusReset(); // to avoid switching back to continuous focus mode while taking a photo - instead we'll always make sure we switch back after taking a photo
        updateParametersFromLocation(); // do this now, not before, so we don't set location parameters during focus (sometimes get RuntimeException)

        focus_success = FOCUS_DONE; // clear focus rectangle if not already done
        successfully_focused = false; // so next photo taken will require an autofocus
        if( Debug.LOG )
            Log.d(TAG, "remaining_burst_photos: " + remaining_burst_photos);

        Controller.PictureCallback pictureCallback = new Controller.PictureCallback() {
            private boolean success = false; // whether jpeg callback succeeded
            private boolean has_date = false;
            private Date current_date = null;

            public void onCompleted() {
                if( Debug.LOG )
                    Log.d(TAG, "onCompleted");
                applicationInterface.onPictureCompleted();
                if( !using_android_l ) {
                    is_preview_started = false; // preview automatically stopped due to taking photo on original Camera API
                }
                phase = PHASE_NORMAL; // need to set this even if remaining burst photos, so we can restart the preview
                if( remaining_burst_photos == -1 || remaining_burst_photos > 0 ) {
                    if( !is_preview_started ) {
                        // we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
                        // (otherwise this can fail, at least on Nexus 7)
                        startCameraPreview();
                        if( Debug.LOG )
                            Log.d(TAG, "burst mode photos remaining: onPictureTaken started preview: " + remaining_burst_photos);
                    }
                }
                else {
                    phase = PHASE_NORMAL;
                    boolean pause_preview = applicationInterface.getPausePreviewPref();
                    if( Debug.LOG )
                        Log.d(TAG, "pause_preview? " + pause_preview);
                    if( pause_preview && success ) {
                        if( is_preview_started ) {
                            // need to manually stop preview on Android L Camera2
                            camera_controller.stopPreview();
                            is_preview_started = false;
                        }
                        setPreviewPaused(true);
                    }
                    else {
                        if( !is_preview_started ) {
                            // we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
                            // (otherwise this can fail, at least on Nexus 7)
                            startCameraPreview();
                        }
                        applicationInterface.cameraInOperation(false);
                        if( Debug.LOG )
                            Log.d(TAG, "onPictureTaken started preview");
                    }
                }
                continuousFocusReset(); // in case we took a photo after user had touched to focus (causing us to switch from continuous to autofocus mode)
                if( camera_controller != null && focus_value != null && ( focus_value.equals("focus_mode_continuous_picture") || focus_value.equals("focus_mode_continuous_video") ) ) {
                    if( Debug.LOG )
                        Log.d(TAG, "cancelAutoFocus to restart continuous focusing");
                    camera_controller.cancelAutoFocus(); // needed to restart continuous focusing
                }

                if( Debug.LOG )
                    Log.d(TAG, "remaining_burst_photos: " + remaining_burst_photos);
                if( remaining_burst_photos == -1 || remaining_burst_photos > 0 ) {
                    if( remaining_burst_photos > 0 )
                        remaining_burst_photos--;

                    long timer_delay = applicationInterface.getRepeatIntervalPref();
                    if( timer_delay == 0 ) {
                        // we set skip_autofocus to go straight to taking a photo rather than refocusing, for speed
                        // need to manually set the phase
                        phase = PHASE_TAKING_PHOTO;
                        takePhoto(true);
                    }
                    else {
                        takePictureOnTimer(timer_delay, true);
                    }
                }
            }

            /** Ensures we get the same date for both JPEG and RAW; and that we set the date ASAP so that it corresponds to actual
             *  photo time.
             */
            private void initDate() {
                if( !has_date ) {
                    has_date = true;
                    current_date = new Date();
                    if( Debug.LOG )
                        Log.d(TAG, "picture taken on date: " + current_date);
                }
            }

            public void onPictureTaken(byte[] data) {
                if( Debug.LOG )
                    Log.d(TAG, "onPictureTaken");
                // n.b., this is automatically run in a different thread
                initDate();
                if( !applicationInterface.onPictureTaken(data, current_date) ) {
                    if( Debug.LOG )
                        Log.e(TAG, "applicationInterface.onPictureTaken failed");
                    success = false;
                }
                else {
                    success = true;
                }
            }

            public void onRawPictureTaken(DngCreator dngCreator, Image image) {
                if( Debug.LOG )
                    Log.d(TAG, "onRawPictureTaken");
                initDate();
                if( !applicationInterface.onRawPictureTaken(dngCreator, image, current_date) ) {
                    if( Debug.LOG )
                        Log.e(TAG, "applicationInterface.onRawPictureTaken failed");
                }
            }

            public void onBurstPictureTaken(List<byte[]> images) {
                if( Debug.LOG )
                    Log.d(TAG, "onBurstPictureTaken");
                // n.b., this is automatically run in a different thread
                initDate();

                success = true;
                if( !applicationInterface.onBurstPictureTaken(images, current_date) ) {
                    if( Debug.LOG )
                        Log.e(TAG, "applicationInterface.onBurstPictureTaken failed");
                    success = false;
                }
            }

            public void onFrontScreenTurnOn() {
                if( Debug.LOG )
                    Log.d(TAG, "onFrontScreenTurnOn");
                applicationInterface.turnFrontScreenFlashOn();
            }
        };
        Controller.ErrorCallback errorCallback = new Controller.ErrorCallback() {
            public void onError() {
                if( Debug.LOG )
                    Log.e(TAG, "error from takePicture");
                count_cameraTakePicture--; // cancel out the increment from after the takePicture() call
                applicationInterface.onPhotoError();
                phase = PHASE_NORMAL;
                startCameraPreview();
                applicationInterface.cameraInOperation(false);
            }
        };
        {
            camera_controller.setRotation(getImageVideoRotation());

            boolean enable_sound = applicationInterface.getShutterSoundPref();
            if( Debug.LOG )
                Log.d(TAG, "enable_sound? " + enable_sound);
            camera_controller.enableShutterSound(enable_sound);
            if( Debug.LOG )
                Log.d(TAG, "about to call takePicture");
            camera_controller.takePicture(pictureCallback, errorCallback);
            count_cameraTakePicture++;
        }
        if( Debug.LOG )
            Log.d(TAG, "takePhotoWhenFocused exit");
    }

	/*void clickedShare() {
		if( Debug.LOG )
			Log.d(TAG, "clickedShare");
		//if( is_preview_paused ) {
		if( this.phase == PHASE_PREVIEW_PAUSED ) {
			if( preview_image_name != null ) {
				if( Debug.LOG )
					Log.d(TAG, "Share: " + preview_image_name);
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + preview_image_name));
				Activity activity = (Activity)this.getContext();
				activity.startActivity(Intent.createChooser(intent, "Photo"));
			}
			startCameraPreview();
			tryAutoFocus(false, false);
		}
	}

	void clickedTrash() {
		if( Debug.LOG )
			Log.d(TAG, "clickedTrash");
		//if( is_preview_paused ) {
		if( this.phase == PHASE_PREVIEW_PAUSED ) {
			if( preview_image_name != null ) {
				if( Debug.LOG )
					Log.d(TAG, "Delete: " + preview_image_name);
				File file = new File(preview_image_name);
				if( !file.delete() ) {
					if( Debug.LOG )
						Log.e(TAG, "failed to delete " + preview_image_name);
				}
				else {
					if( Debug.LOG )
						Log.d(TAG, "successfully deleted " + preview_image_name);
    	    	    showToast(null, R.string.photo_deleted);
					applicationInterface.broadcastFile(file, false, false);
				}
			}
			startCameraPreview();
			tryAutoFocus(false, false);
		}
    }*/

    public void requestAutoFocus() {
        if( Debug.LOG )
            Log.d(TAG, "requestAutoFocus");
        cancelAutoFocus();
        tryAutoFocus(false, true);
    }

    private void tryAutoFocus(final boolean startup, final boolean manual) {
        // manual: whether user has requested autofocus (e.g., by touching screen, or volume focus, or hardware focus button)
        // consider whether you want to call requestAutoFocus() instead (which properly cancels any in-progress auto-focus first)
        if( Debug.LOG ) {
            Log.d(TAG, "tryAutoFocus");
            Log.d(TAG, "startup? " + startup);
            Log.d(TAG, "manual? " + manual);
        }
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
        }
        else if( !this.has_surface ) {
            if( Debug.LOG )
                Log.d(TAG, "preview surface not yet available");
        }
        else if( !this.is_preview_started ) {
            if( Debug.LOG )
                Log.d(TAG, "preview not yet started");
        }
        //else if( is_taking_photo ) {
        else if( !(manual && this.is_video) && this.isTakingPhotoOrOnTimer() ) {
            // if taking a video, we allow manual autofocuses
            // autofocus may cause problem if there is a video corruption problem, see testTakeVideoBitrate() on Nexus 7 at 30Mbs or 50Mbs, where the startup autofocus would cause a problem here
            if( Debug.LOG )
                Log.d(TAG, "currently taking a photo");
        }
        else {
            if( manual ) {
                // remove any previous request to switch back to continuous
                removePendingContinuousFocusReset();
            }
            if( manual && !is_video && camera_controller.focusIsContinuous() && supportedFocusValue("focus_mode_auto") ) {
                if( Debug.LOG )
                    Log.d(TAG, "switch from continuous to autofocus mode for touch focus");
                camera_controller.setFocusValue("focus_mode_auto"); // switch to autofocus
                autofocus_in_continuous_mode = true;
                // we switch back to continuous via a new reset_continuous_focus_runnable in autoFocusCompleted()
            }
            // it's only worth doing autofocus when autofocus has an effect (i.e., auto or macro mode)
            // but also for continuous focus mode, triggering an autofocus is still important to fire flash when touching the screen
            if( camera_controller.supportsAutoFocus() ) {
                if( Debug.LOG )
                    Log.d(TAG, "try to start autofocus");
                if( !using_android_l ) {
                    set_flash_value_after_autofocus = "";
                    String old_flash_value = camera_controller.getFlashValue();
                    // getFlashValue() may return "" if flash not supported!
                    if( startup && old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch") ) {
                        set_flash_value_after_autofocus = old_flash_value;
                        camera_controller.setFlashValue("flash_off");
                    }
                    if( Debug.LOG )
                        Log.d(TAG, "set_flash_value_after_autofocus is now: " + set_flash_value_after_autofocus);
                }
                Controller.AutoFocusCallback autoFocusCallback = new Controller.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success) {
                        if( Debug.LOG )
                            Log.d(TAG, "autofocus complete: " + success);
                        autoFocusCompleted(manual, success, false);
                    }
                };

                this.focus_success = FOCUS_WAITING;
                if( Debug.LOG )
                    Log.d(TAG, "set focus_success to " + focus_success);
                this.focus_complete_time = -1;
                this.successfully_focused = false;
                camera_controller.autoFocus(autoFocusCallback);
                count_cameraAutoFocus++;
                this.focus_started_time = System.currentTimeMillis();
                if( Debug.LOG )
                    Log.d(TAG, "autofocus started, count now: " + count_cameraAutoFocus);
            }
            else if( has_focus_area ) {
                // do this so we get the focus box, for focus modes that support focus area, but don't support autofocus
                focus_success = FOCUS_SUCCESS;
                focus_complete_time = System.currentTimeMillis();
                // n.b., don't set focus_started_time as that may be used for application to show autofocus animation
            }
        }
    }

    /** If the user touches the screen in continuous focus mode, we switch the camera_controller to autofocus mode.
     *  After the autofocus completes, we set a reset_continuous_focus_runnable to switch back to the camera_controller
     *  back to continuous focus after a short delay.
     *  This function removes any pending reset_continuous_focus_runnable.
     */
    private void removePendingContinuousFocusReset() {
        if( Debug.LOG )
            Log.d(TAG, "removePendingContinuousFocusReset");
        if( reset_continuous_focus_runnable != null ) {
            if( Debug.LOG )
                Log.d(TAG, "remove pending reset_continuous_focus_runnable");
            reset_continuous_focus_handler.removeCallbacks(reset_continuous_focus_runnable);
            reset_continuous_focus_runnable = null;
        }
    }

    /** If the user touches the screen in continuous focus mode, we switch the camera_controller to autofocus mode.
     *  This function is called to see if we should switch from autofocus mode back to continuous focus mode.
     *  If this isn't required, calling this function does nothing.
     */
    private void continuousFocusReset() {
        if( Debug.LOG )
            Log.d(TAG, "switch back to continuous focus after autofocus?");
        if( camera_controller != null && autofocus_in_continuous_mode ) {
            autofocus_in_continuous_mode = false;
            // check again
            String current_ui_focus_value = getCurrentFocusValue();
            if( current_ui_focus_value != null && !camera_controller.getFocusValue().equals(current_ui_focus_value) && camera_controller.getFocusValue().equals("focus_mode_auto") ) {
                camera_controller.cancelAutoFocus();
                camera_controller.setFocusValue(current_ui_focus_value);
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "no need to switch back to continuous focus after autofocus, mode already changed");
            }
        }
    }

    private void cancelAutoFocus() {
        if( Debug.LOG )
            Log.d(TAG, "cancelAutoFocus");
        if( camera_controller != null ) {
            camera_controller.cancelAutoFocus();
            autoFocusCompleted(false, false, true);
        }
    }

    private void ensureFlashCorrect() {
        // ensures flash is in correct mode, in case where we had to turn flash temporarily off for startup autofocus
        if( set_flash_value_after_autofocus.length() > 0 && camera_controller != null ) {
            if( Debug.LOG )
                Log.d(TAG, "set flash back to: " + set_flash_value_after_autofocus);
            camera_controller.setFlashValue(set_flash_value_after_autofocus);
            set_flash_value_after_autofocus = "";
        }
    }

    private void autoFocusCompleted(boolean manual, boolean success, boolean cancelled) {
        if( Debug.LOG ) {
            Log.d(TAG, "autoFocusCompleted");
            Log.d(TAG, "    manual? " + manual);
            Log.d(TAG, "    success? " + success);
            Log.d(TAG, "    cancelled? " + cancelled);
        }
        if( cancelled ) {
            focus_success = FOCUS_DONE;
        }
        else {
            focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
            focus_complete_time = System.currentTimeMillis();
        }
        if( manual && !cancelled && ( success || applicationInterface.isTestAlwaysFocus() ) ) {
            successfully_focused = true;
            successfully_focused_time = focus_complete_time;
        }
        if( manual && camera_controller != null && autofocus_in_continuous_mode ) {
            String current_ui_focus_value = getCurrentFocusValue();
            if( Debug.LOG )
                Log.d(TAG, "current_ui_focus_value: " + current_ui_focus_value);
            if( current_ui_focus_value != null && !camera_controller.getFocusValue().equals(current_ui_focus_value) && camera_controller.getFocusValue().equals("focus_mode_auto") ) {
                reset_continuous_focus_runnable = new Runnable() {
                    @Override
                    public void run() {
                        if( Debug.LOG )
                            Log.d(TAG, "reset_continuous_focus_runnable running...");
                        reset_continuous_focus_runnable = null;
                        continuousFocusReset();
                    }
                };
                reset_continuous_focus_handler.postDelayed(reset_continuous_focus_runnable, 3000);
            }
        }
        ensureFlashCorrect();
        if( this.using_face_detection && !cancelled ) {
            // On some devices such as mtk6589, face detection does not resume as written in documentation so we have
            // to cancelfocus when focus is finished
            if( camera_controller != null ) {
                camera_controller.cancelAutoFocus();
            }
        }
        synchronized(this) {
            if( take_photo_after_autofocus ) {
                if( Debug.LOG )
                    Log.d(TAG, "take_photo_after_autofocus is set");
                take_photo_after_autofocus = false;
                prepareAutoFocusPhoto();
                takePhotoWhenFocused();
            }
        }
        if( Debug.LOG )
            Log.d(TAG, "autoFocusCompleted exit");
    }

    public void startCameraPreview() {
        long debug_time = 0;
        if( Debug.LOG ) {
            Log.d(TAG, "startCameraPreview");
            debug_time = System.currentTimeMillis();
        }
        //if( camera != null && !is_taking_photo && !is_preview_started ) {
        if( camera_controller != null && !this.isTakingPhotoOrOnTimer() && !is_preview_started ) {
            if( Debug.LOG )
                Log.d(TAG, "starting the camera preview");
            {
                if( Debug.LOG )
                    Log.d(TAG, "setRecordingHint: " + is_video);
                camera_controller.setRecordingHint(this.is_video);
            }
            setPreviewFps();
            try {
                camera_controller.startPreview();
                count_cameraStartPreview++;
            }
            catch(ControllerException e) {
                if( Debug.LOG )
                    Log.d(TAG, "ControllerException trying to startPreview");
                e.printStackTrace();
                applicationInterface.onFailedStartPreview();
                return;
            }
            this.is_preview_started = true;
            if( Debug.LOG ) {
                Log.d(TAG, "startCameraPreview: time after starting camera preview: " + (System.currentTimeMillis() - debug_time));
            }
            if( this.using_face_detection ) {
                if( Debug.LOG )
                    Log.d(TAG, "start face detection");
                camera_controller.startFaceDetection();
                faces_detected = null;
            }
        }
        this.setPreviewPaused(false);
        this.setupContinuousFocusMove();
        if( Debug.LOG ) {
            Log.d(TAG, "startCameraPreview: total time for startCameraPreview: " + (System.currentTimeMillis() - debug_time));
        }
    }

    private void setPreviewPaused(boolean paused) {
        if( Debug.LOG )
            Log.d(TAG, "setPreviewPaused: " + paused);
        applicationInterface.hasPausedPreview(paused);
        if( paused ) {
            this.phase = PHASE_PREVIEW_PAUSED;
            // shouldn't call applicationInterface.cameraInOperation(true), as should already have done when we started to take a photo (or above when exiting immersive mode)
        }
        else {
            this.phase = PHASE_NORMAL;
            applicationInterface.cameraInOperation(false);
        }
    }

    public void onAccelerometerSensorChanged(SensorEvent event) {
		/*if( Debug.LOG )
    	Log.d(TAG, "onAccelerometerSensorChanged: " + event.values[0] + ", " + event.values[1] + ", " + event.values[2]);*/

        this.has_gravity = true;
        for(int i=0;i<3;i++) {
            //this.gravity[i] = event.values[i];
            this.gravity[i] = sensor_alpha * this.gravity[i] + (1.0f-sensor_alpha) * event.values[i];
        }
        calculateGeoDirection();

        double x = gravity[0];
        double y = gravity[1];
        this.has_level_angle = true;
        this.level_angle = Math.atan2(-x, y) * 180.0 / Math.PI;
        if( this.level_angle < -0.0 ) {
            this.level_angle += 360.0;
        }
        this.orig_level_angle = this.level_angle;
        this.level_angle -= (float)this.current_orientation;
        if( this.level_angle < -180.0 ) {
            this.level_angle += 360.0;
        }
        else if( this.level_angle > 180.0 ) {
            this.level_angle -= 360.0;
        }

        cameraSurface.getView().invalidate();
    }

    public boolean hasLevelAngle() {
        return this.has_level_angle;
    }

    public double getLevelAngle() {
        return this.level_angle;
    }

    public double getOrigLevelAngle() {
        return this.orig_level_angle;
    }

    public void onMagneticSensorChanged(SensorEvent event) {
        this.has_geomagnetic = true;
        for(int i=0;i<3;i++) {
            //this.geomagnetic[i] = event.values[i];
            this.geomagnetic[i] = sensor_alpha * this.geomagnetic[i] + (1.0f-sensor_alpha) * event.values[i];
        }
        calculateGeoDirection();
    }

    private void calculateGeoDirection() {
        if( !this.has_gravity || !this.has_geomagnetic ) {
            return;
        }
        if( !SensorManager.getRotationMatrix(this.deviceRotation, this.deviceInclination, this.gravity, this.geomagnetic) ) {
            return;
        }
        SensorManager.remapCoordinateSystem(this.deviceRotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, this.cameraRotation);
        this.has_geo_direction = true;
        SensorManager.getOrientation(cameraRotation, geo_direction);
        //SensorManager.getOrientation(deviceRotation, geo_direction);
		/*if( Debug.LOG ) {
			Log.d(TAG, "geo_direction: " + (geo_direction[0]*180/Math.PI) + ", " + (geo_direction[1]*180/Math.PI) + ", " + (geo_direction[2]*180/Math.PI));
		}*/
    }

    public boolean hasGeoDirection() {
        return has_geo_direction;
    }

    public double getGeoDirection() {
        return geo_direction[0];
    }

    public boolean supportsFaceDetection() {
        if( Debug.LOG )
            Log.d(TAG, "supportsFaceDetection");
        return supports_face_detection;
    }

    public boolean supportsVideoStabilization() {
        if( Debug.LOG )
            Log.d(TAG, "supportsVideoStabilization");
        return supports_video_stabilization;
    }

    public boolean canDisableShutterSound() {
        if( Debug.LOG )
            Log.d(TAG, "canDisableShutterSound");
        return can_disable_shutter_sound;
    }

    public List<String> getSupportedColorEffects() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedColorEffects");
        return this.color_effects;
    }

    public List<String> getSupportedSceneModes() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedSceneModes");
        return this.scene_modes;
    }

    public List<String> getSupportedWhiteBalances() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedWhiteBalances");
        return this.white_balances;
    }

    public String getISOKey() {
        if( Debug.LOG )
            Log.d(TAG, "getISOKey");
        return camera_controller == null ? "" : camera_controller.getISOKey();
    }

    public List<String> getSupportedISOs() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedISOs");
        return this.isos;
    }

    public boolean supportsISORange() {
        if( Debug.LOG )
            Log.d(TAG, "supportsISORange");
        return this.supports_iso_range;
    }

    public int getMinimumISO() {
        if( Debug.LOG )
            Log.d(TAG, "getMinimumISO");
        return this.min_iso;
    }

    public int getMaximumISO() {
        if( Debug.LOG )
            Log.d(TAG, "getMaximumISO");
        return this.max_iso;
    }

    public float getMinimumFocusDistance() {
        return this.minimum_focus_distance;
    }

    public boolean supportsExposureTime() {
        if( Debug.LOG )
            Log.d(TAG, "supportsExposureTime");
        return this.supports_exposure_time;
    }

    public long getMinimumExposureTime() {
        if( Debug.LOG )
            Log.d(TAG, "getMinimumExposureTime");
        return this.min_exposure_time;
    }

    public long getMaximumExposureTime() {
        if( Debug.LOG )
            Log.d(TAG, "getMaximumExposureTime");
        return this.max_exposure_time;
    }

    public boolean supportsExposures() {
        if( Debug.LOG )
            Log.d(TAG, "supportsExposures");
        return this.exposures != null;
    }

    public int getMinimumExposure() {
        if( Debug.LOG )
            Log.d(TAG, "getMinimumExposure");
        return this.min_exposure;
    }

    public int getMaximumExposure() {
        if( Debug.LOG )
            Log.d(TAG, "getMaximumExposure");
        return this.max_exposure;
    }

    public int getCurrentExposure() {
        if( Debug.LOG )
            Log.d(TAG, "getCurrentExposure");
        if( camera_controller == null ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not opened!");
            return 0;
        }
        int current_exposure = camera_controller.getExposureCompensation();
        return current_exposure;
    }

    List<String> getSupportedExposures() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedExposures");
        return this.exposures;
    }

    public boolean supportsExpoBracketing() {
        if( Debug.LOG )
            Log.d(TAG, "supportsExpoBracketing");
        return this.supports_expo_bracketing;
    }

    public boolean supportsRaw() {
        if( Debug.LOG )
            Log.d(TAG, "supportsRaw");
        return this.supports_raw;
    }

    public List<Controller.Size> getSupportedPreviewSizes() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedPreviewSizes");
        return this.supported_preview_sizes;
    }

    public Controller.Size getCurrentPreviewSize() {
        return new Controller.Size(preview_w, preview_h);
    }

    public List<Controller.Size> getSupportedPictureSizes() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedPictureSizes");
        return this.sizes;
    }

    public int getCurrentPictureSizeIndex() {
        if( Debug.LOG )
            Log.d(TAG, "getCurrentPictureSizeIndex");
        return this.current_size_index;
    }

    public Controller.Size getCurrentPictureSize() {
        if( current_size_index == -1 || sizes == null )
            return null;
        return sizes.get(current_size_index);
    }

    public List<String> getSupportedVideoQuality() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedVideoQuality");
        return this.video_quality;
    }

    public int getCurrentVideoQualityIndex() {
        if( Debug.LOG )
            Log.d(TAG, "getCurrentVideoQualityIndex");
        return this.current_video_quality;
    }

    public String getCurrentVideoQuality() {
        if( current_video_quality == -1 )
            return null;
        return video_quality.get(current_video_quality);
    }

    public List<Controller.Size> getSupportedVideoSizes() {
        if( Debug.LOG )
            Log.d(TAG, "getSupportedVideoSizes");
        return this.video_sizes;
    }

    public List<String> getSupportedFlashValues() {
        return supported_flash_values;
    }

    public List<String> getSupportedFocusValues() {
        return supported_focus_values;
    }

    public List<Integer> getSupportedZoomRatios() {
        return zoom_ratios;
    }

    public int getCameraId() {
        if( camera_controller == null )
            return 0;
        return camera_controller.getCameraId();
    }

    public String getCameraAPI() {
        if( camera_controller == null )
            return "None";
        return camera_controller.getAPI();
    }

    public void onResume() {
        if( Debug.LOG )
            Log.d(TAG, "onResume");
        this.app_is_paused = false;
        this.gif();
    }

    public void onPause() {
        if( Debug.LOG )
            Log.d(TAG, "onPause");
        this.app_is_paused = true;
        this.closeCamera();
    }

    /*void updateUIPlacement() {
    	// we cache the preference_ui_placement to save having to check it in the draw() method
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String ui_placement = sharedPreferences.getString(HomeActivity.getUIPlacementPreferenceKey(), "ui_right");
		this.ui_placement_right = ui_placement.equals("ui_right");
    }*/

    public void onSaveInstanceState(Bundle state) {
        if( Debug.LOG )
            Log.d(TAG, "onSaveInstanceState");
    }

    public void showToast(final ToastBoxer clear_toast, final int message_id) {
        showToast(clear_toast, getResources().getString(message_id));
    }

    public void showToast(final ToastBoxer clear_toast, final String message) {
        showToast(clear_toast, message, 32);
    }

    public void showToast(final ToastBoxer clear_toast, final String message, final int offset_y_dp) {
        if( !applicationInterface.getShowToastsPref() ) {
            return;
        }

        class RotatedTextView extends View {
            private String [] lines = null;
            private Paint paint = new Paint();
            private Rect bounds = new Rect();
            private Rect sub_bounds = new Rect();
            private RectF rect = new RectF();

            public RotatedTextView(String text, Context context) {
                super(context);

                this.lines = text.split("\n");
            }

            void setText(String text) {
                this.lines = text.split("\n");
            }

            @Override
            protected void onDraw(Canvas canvas) {
                final float scale = Preview.this.getResources().getDisplayMetrics().density;
                paint.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                paint.setShadowLayer(1, 0, 1, Color.BLACK);
                //paint.getTextBounds(text, 0, text.length(), bounds);
                boolean first_line = true;
                for(String line : lines) {
                    paint.getTextBounds(line, 0, line.length(), sub_bounds);
					/*if( Debug.LOG ) {
						Log.d(TAG, "line: " + line + " sub_bounds: " + sub_bounds);
					}*/
                    if( first_line ) {
                        bounds.set(sub_bounds);
                        first_line = false;
                    }
                    else {
                        bounds.top = Math.min(sub_bounds.top, bounds.top);
                        bounds.bottom = Math.max(sub_bounds.bottom, bounds.bottom);
                        bounds.left = Math.min(sub_bounds.left, bounds.left);
                        bounds.right = Math.max(sub_bounds.right, bounds.right);
                    }
                }
				/*if( Debug.LOG ) {
					Log.d(TAG, "bounds: " + bounds);
				}*/
                int height = bounds.bottom - bounds.top + 2;
                bounds.bottom += ((lines.length-1) * height)/2;
                bounds.top -= ((lines.length-1) * height)/2;
                final int padding = (int) (14 * scale + 0.5f); // convert dps to pixels
                final int offset_y = (int) (offset_y_dp * scale + 0.5f); // convert dps to pixels
                canvas.save();
                canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

                rect.left = canvas.getWidth()/2 - bounds.width()/2 + bounds.left - padding;
                rect.top = canvas.getHeight()/2 + bounds.top - padding + offset_y;
                rect.right = canvas.getWidth()/2 - bounds.width()/2 + bounds.right + padding;
                rect.bottom = canvas.getHeight()/2 + bounds.bottom + padding + offset_y;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(50, 50, 50));
                //canvas.drawRect(rect, paint);
                final float radius = (24 * scale + 0.5f); // convert dps to pixels
                canvas.drawRoundRect(rect, radius, radius, paint);

                paint.setColor(Color.WHITE);
                int ypos = canvas.getHeight()/2 + offset_y - ((lines.length-1) * height)/2;
                for(String line : lines) {
                    canvas.drawText(line, canvas.getWidth()/2 - bounds.width()/2, ypos, paint);
                    ypos += height;
                }
                canvas.restore();
            }
        }

        if( Debug.LOG )
            Log.d(TAG, "showToast: " + message);
        final Activity activity = (Activity)this.getContext();
        // We get a crash on emulator at least if Toast constructor isn't run on main thread (e.g., the toast for taking a photo when on timer).
        // Also see http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
        activity.runOnUiThread(new Runnable() {
            public void run() {
				/*if( clear_toast != null && clear_toast.toast != null )
					clear_toast.toast.cancel();

				Toast toast = new Toast(activity);
				if( clear_toast != null )
					clear_toast.toast = toast;*/
                // This method is better, as otherwise a previous toast (with different or no clear_toast) never seems to clear if we repeatedly issue new toasts - this doesn't happen if we reuse existing toasts if possible
                // However should only do this if the previous toast was the most recent toast (to avoid messing up ordering)
                Toast toast = null;
                if( clear_toast != null && clear_toast.toast != null && clear_toast.toast == last_toast ) {
                    if( Debug.LOG )
                        Log.d(TAG, "reuse last toast: " + last_toast);
                    toast = clear_toast.toast;
                    // for performance, important to reuse the same view, instead of creating a new one (otherwise we get jerky preview update e.g. for changing manual focus slider)
                    RotatedTextView view = (RotatedTextView)toast.getView();
                    view.setText(message);
                    view.invalidate(); // make sure the toast is redrawn
                    toast.setView(view);
                }
                else {
                    if( clear_toast != null && clear_toast.toast != null ) {
                        if( Debug.LOG )
                            Log.d(TAG, "cancel last toast: " + clear_toast.toast);
                        clear_toast.toast.cancel();
                    }
                    toast = new Toast(activity);
                    if( Debug.LOG )
                        Log.d(TAG, "created new toast: " + toast);
                    if( clear_toast != null )
                        clear_toast.toast = toast;
                    View text = new RotatedTextView(message, activity);
                    toast.setView(text);
                }
                toast.setDuration(Toast.LENGTH_SHORT);
                toast.show();
                last_toast = toast;
            }
        });
    }

    public void setUIRotation(int ui_rotation) {
        if( Debug.LOG )
            Log.d(TAG, "setUIRotation");
        this.ui_rotation = ui_rotation;
    }

    public int getUIRotation() {
        return this.ui_rotation;
    }

    /** If geotagging is enabled, pass the location info to the camera controller (for photos).
     */
    private void updateParametersFromLocation() {
        if( Debug.LOG )
            Log.d(TAG, "updateParametersFromLocation");
        if( camera_controller != null ) {
            boolean store_location = applicationInterface.getGeotaggingPref();
            if( store_location && applicationInterface.getLocation() != null ) {
                Location location = applicationInterface.getLocation();
                if( Debug.LOG ) {
                    Log.d(TAG, "updating parameters from location...");
                    Log.d(TAG, "lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy() + " timestamp " + location.getTime());
                }
                camera_controller.setLocationInfo(location);
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "removing location data from parameters...");
                camera_controller.removeLocationInfo();
            }
        }
    }

    public boolean isVideo() {
        return is_video;
    }

    public boolean isVideoRecording() {
        return video_recorder != null && video_start_time_set;
    }

    public int getRemainingRestartVideo() {
        return remaining_restart_video;
    }

    public long getVideoTime() {
        long time_now = System.currentTimeMillis();
        return time_now - video_start_time + video_accumulated_time;
    }

    public long getVideoAccumulatedTime() {
        return video_accumulated_time;
    }

    public boolean isTakingPhoto() {
        if( Debug.LOG )
            Log.d(TAG, "isTakingPhoto: phase " + this.phase);
        return this.phase == PHASE_TAKING_PHOTO;
    }

    public boolean usingCamera2API() {
        return this.using_android_l;
    }

    public Controller getCameraController() {
        return this.camera_controller;
    }

    public ControllerManager getCameraControllerManager() {
        return this.camera_controller_manager;
    }

    public boolean supportsFocus() {
        return this.supported_focus_values != null;
    }

    public boolean supportsFlash() {
        return this.supported_flash_values != null;
    }

    public boolean supportsExposureLock() {
        return this.is_exposure_lock_supported;
    }

    public boolean isExposureLocked() {
        return this.is_exposure_locked;
    }

    public boolean supportsZoom() {
        return this.has_zoom;
    }

    public int getMaxZoom() {
        return this.max_zoom_factor;
    }

    public boolean hasFocusArea() {
        return this.has_focus_area;
    }

    public Pair<Integer, Integer> getFocusPos() {
        return new Pair<Integer, Integer>(focus_screen_x, focus_screen_y);
    }

    public int getMaxNumFocusAreas() {
        return this.max_num_focus_areas;
    }

    public boolean isTakingPhotoOrOnTimer() {
        //return this.is_taking_photo;
        return this.phase == PHASE_TAKING_PHOTO || this.phase == PHASE_TIMER;
    }

    public boolean isOnTimer() {
        //return this.is_taking_photo_on_timer;
        return this.phase == PHASE_TIMER;
    }

    public long getTimerEndTime() {
        return take_photo_time;
    }

    public boolean isPreviewPaused() {
        return this.phase == PHASE_PREVIEW_PAUSED;
    }

    public boolean isPreviewStarted() {
        return this.is_preview_started;
    }

    public boolean isFocusWaiting() {
        return focus_success == FOCUS_WAITING;
    }

    public boolean isFocusRecentSuccess() {
        return focus_success == FOCUS_SUCCESS;
    }

    public long timeSinceStartedAutoFocus() {
        if( focus_started_time != -1 )
            return System.currentTimeMillis() - focus_started_time;
        return 0;
    }

    public boolean isFocusRecentFailure() {
        return focus_success == FOCUS_FAILED;
    }

    /** Whether we can skip the autofocus before taking a photo.
     */
    private boolean recentlyFocused() {
        return this.successfully_focused && System.currentTimeMillis() < this.successfully_focused_time + 5000;
    }

    public Controller.Face [] getFacesDetected() {
        // FindBugs warns about returning the array directly, but in fact we need to return direct access rather than copying, so that the on-screen display of faces rectangles updates
        return this.faces_detected;
    }

    public float getZoomRatio() {
        int zoom_factor = camera_controller.getZoom();
        float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
        return zoom_ratio;
    }
}
