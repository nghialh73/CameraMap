package tools.dslr.hdcamera.CameraController;

import tools.dslr.hdcamera.Debug;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;

/** Provides support using Android 5's Camera 2 API
 *  android.hardware.camera2.*.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Controller2 extends Controller {
    private static final String TAG = "Controller2";

    private Context context = null;
    private CameraDevice camera = null;
    private String cameraIdS = null;
    private CameraCharacteristics characteristics = null;
    private List<Integer> zoom_ratios = null;
    private int current_zoom_value = 0;
    private ErrorCallback preview_error_cb = null;
    private CameraCaptureSession captureSession = null;
    private CaptureRequest.Builder previewBuilder = null;
    private AutoFocusCallback autofocus_cb = null;
    private FaceDetectionListener face_detection_listener = null;
    private Object image_reader_lock = new Object(); // lock to make sure we only handle one image being available at a time
    private ImageReader imageReader = null;
    private boolean want_expo_bracketing = false;
    private int expo_bracketing_n_images = 3;
    private double expo_bracketing_stops = 2.0;
    private boolean want_raw = false;
    //private boolean want_raw = true;
    private android.util.Size raw_size = null;
    private ImageReader imageReaderRaw = null;
    private OnRawImageAvailableListener onRawImageAvailableListener = null;
    private PictureCallback jpeg_cb = null;
    private PictureCallback raw_cb = null;
    private int n_burst = 0;
    private List<byte []> pending_burst_images = new ArrayList<byte []>();
    private DngCreator pending_dngCreator = null;
    private Image pending_image = null;
    private ErrorCallback take_picture_error_cb = null;
    //private ImageReader previewImageReader = null;
    private SurfaceTexture texture = null;
    private Surface surface_texture = null;
    private HandlerThread thread = null;
    Handler handler = null;

    private int preview_width = 0;
    private int preview_height = 0;

    private int picture_width = 0;
    private int picture_height = 0;

    private static final int STATE_NORMAL = 0;
    private static final int STATE_WAITING_AUTOFOCUS = 1;
    private static final int STATE_WAITING_PRECAPTURE_START = 2;
    private static final int STATE_WAITING_PRECAPTURE_DONE = 3;
    private static final int STATE_WAITING_FAKE_PRECAPTURE_START = 4;
    private static final int STATE_WAITING_FAKE_PRECAPTURE_DONE = 5;
    private int state = STATE_NORMAL;
    private long precapture_state_change_time_ms = -1; // time we changed state for precapture modes
    private static final long precapture_start_timeout_c = 2000;
    private static final long precapture_done_timeout_c = 3000;
    private boolean ready_for_capture = false;

    private boolean use_fake_precapture = false; // see Controller.setUseCamera2FakeFlash() for details - this is the user/application setting, see use_fake_precapture_mode for whether fake precapture is enabled (as we may do this for other purposes, e.g., front screen flash)
    private boolean use_fake_precapture_mode = false; // true if either use_fake_precapture is true, or we're temporarily using fake precapture mode (e.g., for front screen flash or exposure bracketing)
    private boolean fake_precapture_torch_performed = false; // whether we turned on torch to do a fake precapture
    private boolean fake_precapture_torch_focus_performed = false; // whether we turned on torch to do an autofocus, in fake precapture mode
    private boolean fake_precapture_use_flash = false; // whether we decide to use flash in auto mode (if fake_precapture_use_autoflash_time_ms != -1)
    private long fake_precapture_use_flash_time_ms = -1; // when we last checked to use flash in auto mode

    private ContinuousFocusMoveCallback continuous_focus_move_callback = null;

    private MediaActionSound media_action_sound = new MediaActionSound();
    private boolean sounds_enabled = true;

    private boolean capture_result_is_ae_scanning = false;
    private boolean capture_result_has_iso = false;
    private int capture_result_iso = 0;
    private boolean capture_result_has_exposure_time = false;
    private long capture_result_exposure_time = 0;
    private boolean capture_result_has_frame_duration = false;
    private long capture_result_frame_duration = 0;
    private boolean capture_result_has_focus_distance = false;
    private float capture_result_focus_distance_min = 0.0f;
    private float capture_result_focus_distance_max = 0.0f;

    private static enum RequestTag {
        CAPTURE
    }

    private class CameraSettings {
        // keys that we need to store, to pass to the stillBuilder, but doesn't need to be passed to previewBuilder (should set sensible defaults)
        private int rotation = 0;
        private Location location = null;
        private byte jpeg_quality = 90;

        // keys that we have passed to the previewBuilder, that we need to store to also pass to the stillBuilder (should set sensible defaults, or use a has_ boolean if we don't want to set a default)
        private int scene_mode = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
        private int color_effect = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
        private int white_balance = CameraMetadata.CONTROL_AWB_MODE_AUTO;
        private String flash_value = "flash_off";
        private boolean has_iso = false;
        //private int ae_mode = CameraMetadata.CONTROL_AE_MODE_ON;
        //private int flash_mode = CameraMetadata.FLASH_MODE_OFF;
        private int iso = 0;
        private long exposure_time = EXPOSURE_TIME_DEFAULT;
        private Rect scalar_crop_region = null; // no need for has_scalar_crop_region, as we can set to null instead
        private boolean has_ae_exposure_compensation = false;
        private int ae_exposure_compensation = 0;
        private boolean has_af_mode = false;
        private int af_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        private float focus_distance = 0.0f; // actual value passed to camera device (set to 0.0 if in infinity mode)
        private float focus_distance_manual = 0.0f; // saved setting when in manual mode
        private boolean ae_lock = false;
        private MeteringRectangle [] af_regions = null; // no need for has_scalar_crop_region, as we can set to null instead
        private MeteringRectangle [] ae_regions = null; // no need for has_scalar_crop_region, as we can set to null instead
        private boolean has_face_detect_mode = false;
        private int face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
        private boolean video_stabilization = false;

        private int getExifOrientation() {
            int exif_orientation = ExifInterface.ORIENTATION_NORMAL;
            switch( (rotation + 360) % 360 ) {
                case 0:
                    exif_orientation = ExifInterface.ORIENTATION_NORMAL;
                    break;
                case 90:
                    exif_orientation = isFrontFacing() ?
                            ExifInterface.ORIENTATION_ROTATE_270 :
                            ExifInterface.ORIENTATION_ROTATE_90;
                    break;
                case 180:
                    exif_orientation = ExifInterface.ORIENTATION_ROTATE_180;
                    break;
                case 270:
                    exif_orientation = isFrontFacing() ?
                            ExifInterface.ORIENTATION_ROTATE_90 :
                            ExifInterface.ORIENTATION_ROTATE_270;
                    break;
                default:
                    // leave exif_orientation unchanged
                    if( Debug.LOG )
                        Log.e(TAG, "unexpected rotation: " + rotation);
                    break;
            }
            if( Debug.LOG ) {
                Log.d(TAG, "rotation: " + rotation);
                Log.d(TAG, "exif_orientation: " + exif_orientation);
            }
            return exif_orientation;
        }

        private void setupBuilder(CaptureRequest.Builder builder, boolean is_still) {
            //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            //builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            //builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);

            setSceneMode(builder);
            setColorEffect(builder);
            setWhiteBalance(builder);
            setAEMode(builder, is_still);
            setCropRegion(builder);
            setExposureCompensation(builder);
            setFocusMode(builder);
            setFocusDistance(builder);
            setAutoExposureLock(builder);
            setAFRegions(builder);
            setAERegions(builder);
            setFaceDetectMode(builder);
            setRawMode(builder);
            setVideoStabilization(builder);

            if( is_still ) {
                if( location != null ) {
                    builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
                }
                builder.set(CaptureRequest.JPEG_ORIENTATION, rotation);
                builder.set(CaptureRequest.JPEG_QUALITY, jpeg_quality);
            }
        }

        private boolean setSceneMode(CaptureRequest.Builder builder) {
            if( Debug.LOG ) {
                Log.d(TAG, "setSceneMode");
                Log.d(TAG, "builder: " + builder);
            }
			/*if( builder.get(CaptureRequest.CONTROL_SCENE_MODE) == null && scene_mode == CameraMetadata.CONTROL_SCENE_MODE_DISABLED ) {
				// can leave off
			}
			else*/ if( builder.get(CaptureRequest.CONTROL_SCENE_MODE) == null || builder.get(CaptureRequest.CONTROL_SCENE_MODE) != scene_mode ) {
                if( Debug.LOG )
                    Log.d(TAG, "setting scene mode: " + scene_mode);
                if( scene_mode == CameraMetadata.CONTROL_SCENE_MODE_DISABLED ) {
                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                }
                else {
                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
                }
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, scene_mode);
                return true;
            }
            return false;
        }

        private boolean setColorEffect(CaptureRequest.Builder builder) {
			/*if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null && color_effect == CameraMetadata.CONTROL_EFFECT_MODE_OFF ) {
				// can leave off
			}
			else*/ if( builder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null || builder.get(CaptureRequest.CONTROL_EFFECT_MODE) != color_effect ) {
                if( Debug.LOG )
                    Log.d(TAG, "setting color effect: " + color_effect);
                builder.set(CaptureRequest.CONTROL_EFFECT_MODE, color_effect);
                return true;
            }
            return false;
        }

        private boolean setWhiteBalance(CaptureRequest.Builder builder) {
			/*if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null && white_balance == CameraMetadata.CONTROL_AWB_MODE_AUTO ) {
				// can leave off
			}
			else*/ if( builder.get(CaptureRequest.CONTROL_AWB_MODE) == null || builder.get(CaptureRequest.CONTROL_AWB_MODE) != white_balance ) {
                if( Debug.LOG )
                    Log.d(TAG, "setting white balance: " + white_balance);
                builder.set(CaptureRequest.CONTROL_AWB_MODE, white_balance);
                return true;
            }
            return false;
        }

        private boolean setAEMode(CaptureRequest.Builder builder, boolean is_still) {
            if( Debug.LOG )
                Log.d(TAG, "setAEMode");
            if( has_iso ) {
                if( Debug.LOG ) {
                    Log.d(TAG, "manual mode");
                    Log.d(TAG, "iso: " + iso);
                    Log.d(TAG, "exposure_time: " + exposure_time);
                }
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
                // for now, flash is disabled when using manual iso - it seems to cause ISO level to jump to 100 on Nexus 6 when flash is turned on!
                // if we enable this ever, remember to still keep disabled for expo bracketing (unless we've added support for flash with expo by then)
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                // set flash via CaptureRequest.FLASH
		    	/*if( flash_value.equals("flash_off") ) {
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
		    	}
		    	else if( flash_value.equals("flash_auto") ) {
					builder.set(CaptureRequest.FLASH_MODE, is_still ? CameraMetadata.FLASH_MODE_SINGLE : CameraMetadata.FLASH_MODE_OFF);
		    	}
		    	else if( flash_value.equals("flash_on") ) {
					builder.set(CaptureRequest.FLASH_MODE, is_still ? CameraMetadata.FLASH_MODE_SINGLE : CameraMetadata.FLASH_MODE_OFF);
		    	}
		    	else if( flash_value.equals("flash_torch") ) {
					builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
		    	}
		    	else if( flash_value.equals("flash_red_eye") ) {
					builder.set(CaptureRequest.FLASH_MODE, is_still ? CameraMetadata.FLASH_MODE_SINGLE : CameraMetadata.FLASH_MODE_OFF);
		    	}*/
            }
            else {
                if( Debug.LOG ) {
                    Log.d(TAG, "auto mode");
                    Log.d(TAG, "flash_value: " + flash_value);
                }
                // prefer to set flash via the ae mode (otherwise get even worse results), except for torch which we can't
                if( flash_value.equals("flash_off") ) {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
                else if( flash_value.equals("flash_auto") ) {
                    if( use_fake_precapture || Controller2.this.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
                else if( flash_value.equals("flash_on") ) {
                    if( use_fake_precapture || Controller2.this.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
                else if( flash_value.equals("flash_torch") ) {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                }
                else if( flash_value.equals("flash_red_eye") ) {
                    // not supported for expo bracketing
                    if( Controller2.this.want_expo_bracketing )
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    else
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
                else if( flash_value.equals("flash_frontscreen_auto") || flash_value.equals("flash_frontscreen_on") ) {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
            }
            return true;
        }

        private void setCropRegion(CaptureRequest.Builder builder) {
            if( scalar_crop_region != null ) {
                builder.set(CaptureRequest.SCALER_CROP_REGION, scalar_crop_region);
            }
        }

        private boolean setExposureCompensation(CaptureRequest.Builder builder) {
            if( !has_ae_exposure_compensation )
                return false;
            if( has_iso ) {
                if( Debug.LOG )
                    Log.d(TAG, "don't set exposure compensation in manual iso mode");
                return false;
            }
            if( builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null || ae_exposure_compensation != builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ) {
                if( Debug.LOG )
                    Log.d(TAG, "change exposure to " + ae_exposure_compensation);
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae_exposure_compensation);
                return true;
            }
            return false;
        }

        private void setFocusMode(CaptureRequest.Builder builder) {
            if( has_af_mode ) {
                if( Debug.LOG )
                    Log.d(TAG, "change af mode to " + af_mode);
                builder.set(CaptureRequest.CONTROL_AF_MODE, af_mode);
            }
        }

        private void setFocusDistance(CaptureRequest.Builder builder) {
            if( Debug.LOG )
                Log.d(TAG, "change focus distance to " + focus_distance);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus_distance);
        }

        private void setAutoExposureLock(CaptureRequest.Builder builder) {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, ae_lock);
        }

        private void setAFRegions(CaptureRequest.Builder builder) {
            if( af_regions != null && characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, af_regions);
            }
        }

        private void setAERegions(CaptureRequest.Builder builder) {
            if( ae_regions != null && characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, ae_regions);
            }
        }

        private void setFaceDetectMode(CaptureRequest.Builder builder) {
            if( has_face_detect_mode )
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, face_detect_mode);
            else
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
        }

        private void setRawMode(CaptureRequest.Builder builder) {
            // DngCreator says "For best quality DNG files, it is strongly recommended that lens shading map output is enabled if supported"
            // docs also say "ON is always supported on devices with the RAW capability", so we don't check for STATISTICS_LENS_SHADING_MAP_MODE_ON being available
            if( want_raw ) {
                builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            }
        }

        private void setVideoStabilization(CaptureRequest.Builder builder) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, video_stabilization ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        }

        // n.b., if we add more methods, remember to update setupBuilder() above!
    }

    class OnRawImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private CaptureResult capture_result = null;
        private Image image = null;

        void setCaptureResult(CaptureResult capture_result) {
            if( Debug.LOG )
                Log.d(TAG, "setCaptureResult()");
            synchronized( image_reader_lock ) {
                /* synchronize, as we don't want to set the capture_result, at the same time that onImageAvailable() is called, as
                 * we'll end up calling processImage() both in onImageAvailable() and here.
                 */
                this.capture_result = capture_result;
                if( image != null ) {
                    if( Debug.LOG )
                        Log.d(TAG, "can now process the image");
                    processImage();
                }
            }
        }

        void clear() {
            if( Debug.LOG )
                Log.d(TAG, "clear()");
            synchronized( image_reader_lock ) {
                // synchronize just to be safe?
                capture_result = null;
                image = null;
            }
        }

        private void processImage() {
            if( Debug.LOG )
                Log.d(TAG, "processImage()");
            if( capture_result == null ) {
                if( Debug.LOG )
                    Log.d(TAG, "don't yet have still_capture_result");
                return;
            }
            if( image == null ) {
                if( Debug.LOG )
                    Log.d(TAG, "don't have image?!");
                return;
            }
            if( Debug.LOG )
                Log.d(TAG, "now have all info to process raw image");
            DngCreator dngCreator = new DngCreator(characteristics, capture_result);
            // set fields
            dngCreator.setOrientation(camera_settings.getExifOrientation());
            if( camera_settings.location != null ) {
                dngCreator.setLocation(camera_settings.location);
            }

            pending_dngCreator = dngCreator;
            pending_image = image;

            PictureCallback cb = raw_cb;
            if( jpeg_cb == null ) {
                if( Debug.LOG )
                    Log.d(TAG, "jpeg callback already done, so can go ahead with raw callback");
                takePendingRaw();
                if( Debug.LOG )
                    Log.d(TAG, "all image callbacks now completed");
                cb.onCompleted();
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "need to wait for jpeg callback");
            }
            if( Debug.LOG )
                Log.d(TAG, "done processImage");
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            if( Debug.LOG )
                Log.d(TAG, "new still raw image available");
            if( raw_cb == null ) {
                if( Debug.LOG )
                    Log.d(TAG, "no picture callback available");
                return;
            }
            synchronized( image_reader_lock ) {
                // see comment above in setCaptureResult() for why we sychonize
                image = reader.acquireNextImage();
                processImage();
            }
            if( Debug.LOG )
                Log.d(TAG, "done onImageAvailable");
        }
    }

    private CameraSettings camera_settings = new CameraSettings();
    private boolean push_repeating_request_when_torch_off = false;
    private CaptureRequest push_repeating_request_when_torch_off_id = null;
	/*private boolean push_set_ae_lock = false;
	private CaptureRequest push_set_ae_lock_id = null;*/

    public Controller2(Context context, int cameraId, ErrorCallback preview_error_cb) throws ControllerException {
        super(cameraId);
        if( Debug.LOG )
            Log.d(TAG, "create new Controller2: " + cameraId);

        this.context = context;
        this.preview_error_cb = preview_error_cb;

        thread = new HandlerThread("CameraBackground");
        thread.start();
        handler = new Handler(thread.getLooper());

        final CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);

        class MyStateCallback extends CameraDevice.StateCallback {
            boolean callback_done = false; // must sychronize on this and notifyAll when setting to true
            boolean first_callback = true; // Google Camera says we may get multiple callbacks, but only the first indicates the status of the camera opening operation
            @Override
            public void onOpened(CameraDevice cam) {
                if( Debug.LOG )
                    Log.d(TAG, "camera opened, first_callback? " + first_callback);
                if( first_callback ) {
                    first_callback = false;

                    try {
                        // we should be able to get characteristics at any time, but Google Camera only does so when camera opened - so do so similarly to be safe
                        if( Debug.LOG )
                            Log.d(TAG, "try to get camera characteristics");
                        characteristics = manager.getCameraCharacteristics(cameraIdS);
                        if( Debug.LOG )
                            Log.d(TAG, "successfully obtained camera characteristics");

                        Controller2.this.camera = cam;

                        // note, this won't start the preview yet, but we create the previewBuilder in order to start setting camera parameters
                        createPreviewRequest();
                    }
                    catch(CameraAccessException e) {
                        if( Debug.LOG ) {
                            Log.e(TAG, "failed to get camera characteristics");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                        // don't throw ControllerException here - instead error is handled by setting callback_done to callback_done, and the fact that camera will still be null
                    }

                    if( Debug.LOG )
                        Log.d(TAG, "about to synchronize to say callback done");
                    synchronized( this ) {
                        callback_done = true;
                        if( Debug.LOG )
                            Log.d(TAG, "callback done, about to notify");
                        this.notifyAll();
                        if( Debug.LOG )
                            Log.d(TAG, "callback done, notification done");
                    }
                }
            }

            @Override
            public void onClosed(CameraDevice cam) {
                if( Debug.LOG )
                    Log.d(TAG, "camera closed, first_callback? " + first_callback);
                // caller should ensure camera variables are set to null
                if( first_callback ) {
                    first_callback = false;
                }
            }

            @Override
            public void onDisconnected(CameraDevice cam) {
                if( Debug.LOG )
                    Log.d(TAG, "camera disconnected, first_callback? " + first_callback);
                if( first_callback ) {
                    first_callback = false;
                    // must call close() if disconnected before camera was opened
                    // need to set the camera to null first, as closing the camera may take some time, and we don't want any other operations to continue (if called from main thread)
                    Controller2.this.camera = null;
                    if( Debug.LOG )
                        Log.d(TAG, "onDisconnected: camera is now set to null");
                    cam.close();
                    if( Debug.LOG )
                        Log.d(TAG, "onDisconnected: camera is now closed");
                    if( Debug.LOG )
                        Log.d(TAG, "about to synchronize to say callback done");
                    synchronized( this ) {
                        callback_done = true;
                        if( Debug.LOG )
                            Log.d(TAG, "callback done, about to notify");
                        this.notifyAll();
                        if( Debug.LOG )
                            Log.d(TAG, "callback done, notification done");
                    }
                }
            }

            @Override
            public void onError(CameraDevice cam, int error) {
                if( Debug.LOG ) {
                    Log.d(TAG, "camera error: " + error);
                    Log.d(TAG, "received camera: " + cam);
                    Log.d(TAG, "actual camera: " + Controller2.this.camera);
                    Log.d(TAG, "first_callback? " + first_callback);
                }
                if( first_callback ) {
                    first_callback = false;
                }
                else {
                    if( Debug.LOG )
                        Log.d(TAG, "error occurred after camera was opened");
                }
                // need to set the camera to null first, as closing the camera may take some time, and we don't want any other operations to continue (if called from main thread)
                Controller2.this.camera = null;
                if( Debug.LOG )
                    Log.d(TAG, "onError: camera is now set to null");
                cam.close();
                if( Debug.LOG )
                    Log.d(TAG, "onError: camera is now closed");
                if( Debug.LOG )
                    Log.d(TAG, "about to synchronize to say callback done");
                synchronized( this ) {
                    callback_done = true;
                    if( Debug.LOG )
                        Log.d(TAG, "callback done, about to notify");
                    this.notifyAll();
                    if( Debug.LOG )
                        Log.d(TAG, "callback done, notification done");
                }
            }
        };
        MyStateCallback myStateCallback = new MyStateCallback();

        try {
            if( Debug.LOG )
                Log.d(TAG, "get camera id list");
            this.cameraIdS = manager.getCameraIdList()[cameraId];
            if( Debug.LOG )
                Log.d(TAG, "about to open camera: " + cameraIdS);
            manager.openCamera(cameraIdS, myStateCallback, handler);
            if( Debug.LOG )
                Log.d(TAG, "open camera request complete");
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to open camera: CameraAccessException");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new ControllerException();
        }
        catch(UnsupportedOperationException e) {
            // Google Camera catches UnsupportedOperationException
            if( Debug.LOG ) {
                Log.e(TAG, "failed to open camera: UnsupportedOperationException");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new ControllerException();
        }
        catch(SecurityException e) {
            // Google Camera catches SecurityException
            if( Debug.LOG ) {
                Log.e(TAG, "failed to open camera: SecurityException");
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new ControllerException();
        }

        if( Debug.LOG )
            Log.d(TAG, "wait until camera opened...");
        // need to wait until camera is opened
        synchronized( myStateCallback ) {
            while( !myStateCallback.callback_done ) {
                try {
                    // release the myStateCallback lock, and wait until myStateCallback calls notifyAll()
                    myStateCallback.wait();
                }
                catch(InterruptedException e) {
                    if( Debug.LOG )
                        Log.d(TAG, "interrupted while waiting until camera opened");
                    e.printStackTrace();
                }
            }
        }
        if( camera == null ) {
            if( Debug.LOG )
                Log.e(TAG, "camera failed to open");
            throw new ControllerException();
        }
        if( Debug.LOG )
            Log.d(TAG, "camera now opened: " + camera);

		/*CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
	    StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
	    android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
		imageReader = ImageReader.newInstance(camera_picture_sizes[0].getWidth(), , ImageFormat.JPEG, 2);*/

        // preload sounds to reduce latency - important so that START_VIDEO_RECORDING sound doesn't play after video has started (which means it'll be heard in the resultant video)
        media_action_sound.load(MediaActionSound.START_VIDEO_RECORDING);
        media_action_sound.load(MediaActionSound.STOP_VIDEO_RECORDING);
        media_action_sound.load(MediaActionSound.SHUTTER_CLICK);
    }

    @Override
    public void release() {
        if( Debug.LOG )
            Log.d(TAG, "release");
        if( thread != null ) {
            thread.quitSafely();
            try {
                thread.join();
                thread = null;
                handler = null;
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
        if( captureSession != null ) {
            captureSession.close();
            captureSession = null;
        }
        previewBuilder = null;
        if( camera != null ) {
            camera.close();
            camera = null;
        }
        closePictureImageReader();
		/*if( previewImageReader != null ) {
			previewImageReader.close();
			previewImageReader = null;
		}*/
    }

    private void closePictureImageReader() {
        if( Debug.LOG )
            Log.d(TAG, "closePictureImageReader()");
        if( imageReader != null ) {
            imageReader.close();
            imageReader = null;
        }
        if( imageReaderRaw != null ) {
            imageReaderRaw.close();
            imageReaderRaw = null;
            onRawImageAvailableListener = null;
        }
    }

    private List<String> convertFocusModesToValues(int [] supported_focus_modes_arr, float minimum_focus_distance) {
        if( Debug.LOG )
            Log.d(TAG, "convertFocusModesToValues()");
        if( supported_focus_modes_arr.length == 0 )
            return null;
        List<Integer> supported_focus_modes = new ArrayList<Integer>();
        for(int i=0;i<supported_focus_modes_arr.length;i++)
            supported_focus_modes.add(supported_focus_modes_arr[i]);
        List<String> output_modes = new ArrayList<String>();
        // also resort as well as converting
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
            output_modes.add("focus_mode_auto");
            if( Debug.LOG ) {
                Log.d(TAG, " supports focus_mode_auto");
            }
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_MACRO) ) {
            output_modes.add("focus_mode_macro");
            if( Debug.LOG )
                Log.d(TAG, " supports focus_mode_macro");
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) ) {
            output_modes.add("focus_mode_locked");
            if( Debug.LOG ) {
                Log.d(TAG, " supports focus_mode_locked");
            }
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_OFF) ) {
            output_modes.add("focus_mode_infinity");
            if( minimum_focus_distance > 0.0f ) {
                output_modes.add("focus_mode_manual2");
                if( Debug.LOG ) {
                    Log.d(TAG, " supports focus_mode_manual2");
                }
            }
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_EDOF) ) {
            output_modes.add("focus_mode_edof");
            if( Debug.LOG )
                Log.d(TAG, " supports focus_mode_edof");
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ) {
            output_modes.add("focus_mode_continuous_picture");
            if( Debug.LOG )
                Log.d(TAG, " supports focus_mode_continuous_picture");
        }
        if( supported_focus_modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ) {
            output_modes.add("focus_mode_continuous_video");
            if( Debug.LOG )
                Log.d(TAG, " supports focus_mode_continuous_video");
        }
        return output_modes;
    }

    public String getAPI() {
        return "Camera2 (Android L)";
    }

    @Override
    public CameraFeatures getCameraFeatures() {
        if( Debug.LOG )
            Log.d(TAG, "getCameraFeatures()");
        CameraFeatures camera_features = new CameraFeatures();
        if( Debug.LOG ) {
            int hardware_level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY )
                Log.d(TAG, "Hardware Level: LEGACY");
            else if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED )
                Log.d(TAG, "Hardware Level: LIMITED");
            else if( hardware_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL )
                Log.d(TAG, "Hardware Level: FULL");
            else
                Log.e(TAG, "Unknown Hardware Level!");
        }

        float max_zoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        camera_features.is_zoom_supported = max_zoom > 0.0f;
        if( Debug.LOG )
            Log.d(TAG, "max_zoom: " + max_zoom);
        if( camera_features.is_zoom_supported ) {
            // set 20 steps per 2x factor
            final int steps_per_2x_factor = 20;
            //final double scale_factor = Math.pow(2.0, 1.0/(double)steps_per_2x_factor);
            int n_steps =(int)( (steps_per_2x_factor * Math.log(max_zoom + 1.0e-11)) / Math.log(2.0));
            final double scale_factor = Math.pow(max_zoom, 1.0/(double)n_steps);
            if( Debug.LOG ) {
                Log.d(TAG, "n_steps: " + n_steps);
                Log.d(TAG, "scale_factor: " + scale_factor);
            }
            camera_features.zoom_ratios = new ArrayList<Integer>();
            camera_features.zoom_ratios.add(100);
            double zoom = 1.0;
            for(int i=0;i<n_steps-1;i++) {
                zoom *= scale_factor;
                camera_features.zoom_ratios.add((int)(zoom*100));
            }
            camera_features.zoom_ratios.add((int)(max_zoom*100));
            camera_features.max_zoom = camera_features.zoom_ratios.size()-1;
            this.zoom_ratios = camera_features.zoom_ratios;
        }
        else {
            this.zoom_ratios = null;
        }

        int [] face_modes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        camera_features.supports_face_detection = false;
        for(int i=0;i<face_modes.length;i++) {
            if( Debug.LOG )
                Log.d(TAG, "face detection mode: " + face_modes[i]);
            // Although we currently only make use of the "SIMPLE" features, some devices (e.g., Nexus 6) support FULL and not SIMPLE.
            // We don't support SIMPLE yet, as I don't have any devices to test this.
            if( face_modes[i] == CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_FULL ) {
                camera_features.supports_face_detection = true;
            }
        }
        if( camera_features.supports_face_detection ) {
            int face_count = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
            if( face_count <= 0 ) {
                camera_features.supports_face_detection = false;
            }
        }

        int [] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean capabilities_raw = false;
        for(int capability : capabilities) {
            if( capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW ) {
                capabilities_raw = true;
            }
        }
        if( Debug.LOG )
            Log.d(TAG, "capabilities_raw?: " + capabilities_raw);

        StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        android.util.Size [] camera_picture_sizes = configs.getOutputSizes(ImageFormat.JPEG);
        camera_features.picture_sizes = new ArrayList<Size>();
        for(android.util.Size camera_size : camera_picture_sizes) {
            if( Debug.LOG )
                Log.d(TAG, "picture size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
            camera_features.picture_sizes.add(new Controller.Size(camera_size.getWidth(), camera_size.getHeight()));
        }

        raw_size = null;
        if( capabilities_raw ) {
            android.util.Size [] raw_camera_picture_sizes = configs.getOutputSizes(ImageFormat.RAW_SENSOR);
            if( raw_camera_picture_sizes == null ) {
                if( Debug.LOG )
                    Log.d(TAG, "RAW not supported, failed to get RAW_SENSOR sizes");
                want_raw = false; // just in case it got set to true somehow
            }
            else {
                for(int i=0;i<raw_camera_picture_sizes.length;i++) {
                    android.util.Size size = raw_camera_picture_sizes[i];
                    if( raw_size == null || size.getWidth()*size.getHeight() > raw_size.getWidth()*raw_size.getHeight() ) {
                        raw_size = size;
                    }
                }
                if( raw_size == null ) {
                    if( Debug.LOG )
                        Log.d(TAG, "RAW not supported, failed to find a raw size");
                    want_raw = false; // just in case it got set to true somehow
                }
                else {
                    if( Debug.LOG )
                        Log.d(TAG, "raw supported, raw size: " + raw_size.getWidth() + " x " + raw_size.getHeight());
                    camera_features.supports_raw = true;
                }
            }
        }
        else {
            if( Debug.LOG )
                Log.d(TAG, "RAW capability not supported");
            want_raw = false; // just in case it got set to true somehow
        }

        android.util.Size [] camera_video_sizes = configs.getOutputSizes(MediaRecorder.class);
        camera_features.video_sizes = new ArrayList<Size>();
        for(android.util.Size camera_size : camera_video_sizes) {
            if( Debug.LOG )
                Log.d(TAG, "video size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
            if( camera_size.getWidth() > 4096 || camera_size.getHeight() > 2160 )
                continue; // Nexus 6 returns these, even though not supported?!
            camera_features.video_sizes.add(new Controller.Size(camera_size.getWidth(), camera_size.getHeight()));
        }

        android.util.Size [] camera_preview_sizes = configs.getOutputSizes(SurfaceTexture.class);
        camera_features.preview_sizes = new ArrayList<Size>();
        Point display_size = new Point();
        Activity activity = (Activity)context;
        {
            Display display = activity.getWindowManager().getDefaultDisplay();
            display.getRealSize(display_size);
            if( Debug.LOG )
                Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
        }
        for(android.util.Size camera_size : camera_preview_sizes) {
            if( Debug.LOG )
                Log.d(TAG, "preview size: " + camera_size.getWidth() + " x " + camera_size.getHeight());
            if( camera_size.getWidth() > display_size.x || camera_size.getHeight() > display_size.y ) {
                // Nexus 6 returns these, even though not supported?! (get green corruption lines if we allow these)
                // Google Camera filters anything larger than height 1080, with a todo saying to use device's measurements
                continue;
            }
            camera_features.preview_sizes.add(new Controller.Size(camera_size.getWidth(), camera_size.getHeight()));
        }

        if( characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
            camera_features.supported_flash_values = new ArrayList<String>();
            camera_features.supported_flash_values.add("flash_off");
            camera_features.supported_flash_values.add("flash_auto");
            camera_features.supported_flash_values.add("flash_on");
            camera_features.supported_flash_values.add("flash_torch");
            if( !use_fake_precapture ) {
                camera_features.supported_flash_values.add("flash_red_eye");
            }
        }
        else if( isFrontFacing() ) {
            camera_features.supported_flash_values = new ArrayList<String>();
            camera_features.supported_flash_values.add("flash_off");
            camera_features.supported_flash_values.add("flash_frontscreen_auto");
            camera_features.supported_flash_values.add("flash_frontscreen_on");
        }

        camera_features.minimum_focus_distance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if( Debug.LOG )
            Log.d(TAG, "minimum_focus_distance: " + camera_features.minimum_focus_distance);
        int [] supported_focus_modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES); // Android format
        camera_features.supported_focus_values = convertFocusModesToValues(supported_focus_modes, camera_features.minimum_focus_distance); // convert to our format (also resorts)
        camera_features.max_num_focus_areas = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

        camera_features.is_exposure_lock_supported = true;

        camera_features.is_video_stabilization_supported = true;

        Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if( iso_range != null ) {
            camera_features.supports_iso_range = true;
            camera_features.min_iso = iso_range.getLower();
            camera_features.max_iso = iso_range.getUpper();
            // we only expose exposure_time if iso_range is supported
            Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if( exposure_time_range != null ) {
                camera_features.supports_exposure_time = true;
                camera_features.supports_expo_bracketing = true;
                camera_features.min_exposure_time = exposure_time_range.getLower();
                camera_features.max_exposure_time = exposure_time_range.getUpper();
            }
        }

        Range<Integer> exposure_range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        camera_features.min_exposure = exposure_range.getLower();
        camera_features.max_exposure = exposure_range.getUpper();
        camera_features.exposure_step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();

        camera_features.can_disable_shutter_sound = true;

        return camera_features;
    }

    private String convertSceneMode(int value2) {
        String value = null;
        switch( value2 ) {
            case CameraMetadata.CONTROL_SCENE_MODE_ACTION:
                value = "action";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_BARCODE:
                value = "barcode";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_BEACH:
                value = "beach";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT:
                value = "candlelight";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_DISABLED:
                value = "auto";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS:
                value = "fireworks";
                break;
            // "hdr" no longer available in Camera2
		/*case CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO:
			// new for Camera2
			value = "high-speed-video";
			break;*/
            case CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE:
                value = "landscape";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_NIGHT:
                value = "night";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT:
                value = "night-portrait";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_PARTY:
                value = "party";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT:
                value = "portrait";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_SNOW:
                value = "snow";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_SPORTS:
                value = "sports";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO:
                value = "steadyphoto";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_SUNSET:
                value = "sunset";
                break;
            case CameraMetadata.CONTROL_SCENE_MODE_THEATRE:
                value = "theatre";
                break;
            default:
                if( Debug.LOG )
                    Log.d(TAG, "unknown scene mode: " + value2);
                value = null;
                break;
        }
        return value;
    }

    @Override
    public SupportedValues setSceneMode(String value) {
        if( Debug.LOG )
            Log.d(TAG, "setSceneMode: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        String default_value = getDefaultSceneMode();
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        boolean has_disabled = false;
        List<String> values = new ArrayList<String>();
        for(int i=0;i<values2.length;i++) {
            if( values2[i] == CameraMetadata.CONTROL_SCENE_MODE_DISABLED )
                has_disabled = true;
            String this_value = convertSceneMode(values2[i]);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        if( !has_disabled ) {
            values.add(0, "auto");
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if( supported_values != null ) {
            int selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
            if( supported_values.selected_value.equals("action") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_ACTION;
            }
            else if( supported_values.selected_value.equals("barcode") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_BARCODE;
            }
            else if( supported_values.selected_value.equals("beach") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_BEACH;
            }
            else if( supported_values.selected_value.equals("candlelight") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT;
            }
            else if( supported_values.selected_value.equals("auto") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_DISABLED;
            }
            else if( supported_values.selected_value.equals("fireworks") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS;
            }
            // "hdr" no longer available in Camera2
            else if( supported_values.selected_value.equals("landscape") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE;
            }
            else if( supported_values.selected_value.equals("night") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_NIGHT;
            }
            else if( supported_values.selected_value.equals("night-portrait") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT;
            }
            else if( supported_values.selected_value.equals("party") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_PARTY;
            }
            else if( supported_values.selected_value.equals("portrait") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT;
            }
            else if( supported_values.selected_value.equals("snow") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_SNOW;
            }
            else if( supported_values.selected_value.equals("sports") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_SPORTS;
            }
            else if( supported_values.selected_value.equals("steadyphoto") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO;
            }
            else if( supported_values.selected_value.equals("sunset") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_SUNSET;
            }
            else if( supported_values.selected_value.equals("theatre") ) {
                selected_value2 = CameraMetadata.CONTROL_SCENE_MODE_THEATRE;
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
            }

            camera_settings.scene_mode = selected_value2;
            if( camera_settings.setSceneMode(previewBuilder) ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( Debug.LOG ) {
                        Log.e(TAG, "failed to set scene mode");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        }
        return supported_values;
    }

    @Override
    public String getSceneMode() {
        if( previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE);
        String value = convertSceneMode(value2);
        return value;
    }

    private String convertColorEffect(int value2) {
        String value = null;
        switch( value2 ) {
            case CameraMetadata.CONTROL_EFFECT_MODE_AQUA:
                value = "aqua";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD:
                value = "blackboard";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_MONO:
                value = "mono";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE:
                value = "negative";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_OFF:
                value = "none";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE:
                value = "posterize";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_SEPIA:
                value = "sepia";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE:
                value = "solarize";
                break;
            case CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD:
                value = "whiteboard";
                break;
            default:
                if( Debug.LOG )
                    Log.d(TAG, "unknown effect mode: " + value2);
                value = null;
                break;
        }
        return value;
    }

    @Override
    public SupportedValues setColorEffect(String value) {
        if( Debug.LOG )
            Log.d(TAG, "setColorEffect: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        String default_value = getDefaultColorEffect();
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
        List<String> values = new ArrayList<String>();
        for(int i=0;i<values2.length;i++) {
            String this_value = convertColorEffect(values2[i]);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if( supported_values != null ) {
            int selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
            if( supported_values.selected_value.equals("aqua") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_AQUA;
            }
            else if( supported_values.selected_value.equals("blackboard") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD;
            }
            else if( supported_values.selected_value.equals("mono") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_MONO;
            }
            else if( supported_values.selected_value.equals("negative") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE;
            }
            else if( supported_values.selected_value.equals("none") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
            }
            else if( supported_values.selected_value.equals("posterize") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE;
            }
            else if( supported_values.selected_value.equals("sepia") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_SEPIA;
            }
            else if( supported_values.selected_value.equals("solarize") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE;
            }
            else if( supported_values.selected_value.equals("whiteboard") ) {
                selected_value2 = CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD;
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
            }

            camera_settings.color_effect = selected_value2;
            if( camera_settings.setColorEffect(previewBuilder) ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( Debug.LOG ) {
                        Log.e(TAG, "failed to set color effect");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        }
        return supported_values;
    }

    @Override
    public String getColorEffect() {
        if( previewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE);
        String value = convertColorEffect(value2);
        return value;
    }

    private String convertWhiteBalance(int value2) {
        String value = null;
        switch( value2 ) {
            case CameraMetadata.CONTROL_AWB_MODE_AUTO:
                value = "auto";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT:
                value = "cloudy-daylight";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT:
                value = "daylight";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT:
                value = "fluorescent";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT:
                value = "incandescent";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_SHADE:
                value = "shade";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_TWILIGHT:
                value = "twilight";
                break;
            case CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT:
                value = "warm-fluorescent";
                break;
            default:
                if( Debug.LOG )
                    Log.d(TAG, "unknown white balance: " + value2);
                value = null;
                break;
        }
        return value;
    }

    @Override
    public SupportedValues setWhiteBalance(String value) {
        if( Debug.LOG )
            Log.d(TAG, "setWhiteBalance: " + value);
        // we convert to/from strings to be compatible with original Android Camera API
        String default_value = getDefaultWhiteBalance();
        int [] values2 = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        List<String> values = new ArrayList<String>();
        for(int i=0;i<values2.length;i++) {
            String this_value = convertWhiteBalance(values2[i]);
            if( this_value != null ) {
                values.add(this_value);
            }
        }
        SupportedValues supported_values = checkModeIsSupported(values, value, default_value);
        if( supported_values != null ) {
            int selected_value2 = CameraMetadata.CONTROL_AWB_MODE_AUTO;
            if( supported_values.selected_value.equals("auto") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_AUTO;
            }
            else if( supported_values.selected_value.equals("cloudy-daylight") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
            }
            else if( supported_values.selected_value.equals("daylight") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT;
            }
            else if( supported_values.selected_value.equals("fluorescent") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT;
            }
            else if( supported_values.selected_value.equals("incandescent") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT;
            }
            else if( supported_values.selected_value.equals("shade") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_SHADE;
            }
            else if( supported_values.selected_value.equals("twilight") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_TWILIGHT;
            }
            else if( supported_values.selected_value.equals("warm-fluorescent") ) {
                selected_value2 = CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT;
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "unknown selected_value: " + supported_values.selected_value);
            }

            camera_settings.white_balance = selected_value2;
            if( camera_settings.setWhiteBalance(previewBuilder) ) {
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( Debug.LOG ) {
                        Log.e(TAG, "failed to set white balance");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }
        }
        return supported_values;
    }

    @Override
    public String getWhiteBalance() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AWB_MODE) == null )
            return null;
        int value2 = previewBuilder.get(CaptureRequest.CONTROL_AWB_MODE);
        String value = convertWhiteBalance(value2);
        return value;
    }

    @Override
    public SupportedValues setISO(String value) {
        if( Debug.LOG )
            Log.d(TAG, "setISO: " + value);
        String default_value = getDefaultISO();
        Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if( iso_range == null ) {
            if( Debug.LOG )
                Log.d(TAG, "iso not supported");
            return null;
        }
        if( Debug.LOG )
            Log.d(TAG, "iso range from " + iso_range.getLower() + " to " + iso_range.getUpper());
        List<String> values = new ArrayList<String>();
        values.add(default_value);
        int [] iso_values = {50, 100, 200, 400, 800, 1600, 3200, 6400};
        values.add("" + iso_range.getLower());
        for(int i=0;i<iso_values.length;i++) {
            if( iso_values[i] > iso_range.getLower() && iso_values[i] < iso_range.getUpper() ) {
                values.add("" + iso_values[i]);
            }
        }
        values.add("" + iso_range.getUpper());

        // n.b., we don't use checkModeIsSupported as ISO is a special case with Controller2: we return a set of ISO values to use in the popup menu, but any ISO within the iso_range is valid
        SupportedValues supported_values = null;
        try {
            if( value.equals(default_value) ) {
                if( Debug.LOG )
                    Log.d(TAG, "setting auto iso");
                supported_values = new SupportedValues(values, value);
                camera_settings.has_iso = false;
                camera_settings.iso = 0;
                if( camera_settings.setAEMode(previewBuilder, false) ) {
                    setRepeatingRequest();
                }
            }
            else {
                try {
                    if( Debug.LOG )
                        Log.d(TAG, "setting manual iso");
                    int selected_value2 = Integer.parseInt(value);
                    if( selected_value2 < iso_range.getLower() )
                        selected_value2 = iso_range.getLower();
                    if( selected_value2 > iso_range.getUpper() )
                        selected_value2 = iso_range.getUpper();
                    if( Debug.LOG )
                        Log.d(TAG, "iso: " + selected_value2);
                    supported_values = new SupportedValues(values, "" + selected_value2);
                    camera_settings.has_iso = true;
                    camera_settings.iso = selected_value2;
                    if( camera_settings.setAEMode(previewBuilder, false) ) {
                        setRepeatingRequest();
                    }
                }
                catch(NumberFormatException exception) {
                    if( Debug.LOG )
                        Log.d(TAG, "iso invalid format, can't parse to int");
                    supported_values = new SupportedValues(values, default_value);
                    camera_settings.has_iso = false;
                    camera_settings.iso = 0;
                    if( camera_settings.setAEMode(previewBuilder, false) ) {
                        setRepeatingRequest();
                    }
                }
            }
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set ISO");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }

        return supported_values;
    }

    @Override
    public String getISOKey() {
        return "";
    }

    @Override
    public int getISO() {
        return camera_settings.iso;
    }

    @Override
    // Returns whether ISO was modified
    // N.B., use setISO(String) to switch between auto and manual mode
    public boolean setISO(int iso) {
        if( Debug.LOG )
            Log.d(TAG, "setISO: " + iso);
        if( camera_settings.iso == iso ) {
            if( Debug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        try {
            camera_settings.iso = iso;
            if( camera_settings.setAEMode(previewBuilder, false) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set ISO");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public long getExposureTime() {
        return camera_settings.exposure_time;
    }

    @Override
    // Returns whether exposure time was modified
    // N.B., use setISO(String) to switch between auto and manual mode
    public boolean setExposureTime(long exposure_time) {
        if( Debug.LOG ) {
            Log.d(TAG, "setExposureTime: " + exposure_time);
            Log.d(TAG, "current exposure time: " + camera_settings.exposure_time);
        }
        if( camera_settings.exposure_time == exposure_time ) {
            if( Debug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        try {
            camera_settings.exposure_time = exposure_time;
            if( camera_settings.setAEMode(previewBuilder, false) ) {
                setRepeatingRequest();
            }
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set exposure time");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public Size getPictureSize() {
        Size size = new Size(picture_width, picture_height);
        return size;
    }

    @Override
    public void setPictureSize(int width, int height) {
        if( Debug.LOG )
            Log.d(TAG, "setPictureSize: " + width + " x " + height);
        if( camera == null ) {
            if( Debug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( captureSession != null ) {
            // can only call this when captureSession not created - as the surface of the imageReader we create has to match the surface we pass to the captureSession
            if( Debug.LOG )
                Log.e(TAG, "can't set picture size when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.picture_width = width;
        this.picture_height = height;
    }

    @Override
    public void setRaw(boolean want_raw) {
        if( Debug.LOG )
            Log.d(TAG, "setRaw: " + want_raw);
        if( camera == null ) {
            if( Debug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.want_raw == want_raw ) {
            return;
        }
        if( want_raw && this.raw_size == null ) {
            if( Debug.LOG )
                Log.e(TAG, "can't set raw when raw not supported");
            return;
        }
        if( captureSession != null ) {
            // can only call this when captureSession not created - as it affects how we create the imageReader
            if( Debug.LOG )
                Log.e(TAG, "can't set raw when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.want_raw = want_raw;
    }

    @Override
    public void setExpoBracketing(boolean want_expo_bracketing) {
        if( Debug.LOG )
            Log.d(TAG, "setExpoBracketing: " + want_expo_bracketing);
        if( camera == null ) {
            if( Debug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.want_expo_bracketing == want_expo_bracketing ) {
            return;
        }
        if( captureSession != null ) {
            // can only call this when captureSession not created - as it affects how we create the imageReader
            if( Debug.LOG )
                Log.e(TAG, "can't set hdr when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.want_expo_bracketing = want_expo_bracketing;
        updateUseFakePrecaptureMode(camera_settings.flash_value);
        camera_settings.setAEMode(previewBuilder, false); // need to set the ae mode, as flash is disabled for HDR mode
    }

    @Override
    public void setExpoBracketingNImages(int n_images) {
        if( Debug.LOG )
            Log.d(TAG, "setExpoBracketingNImages: " + n_images);
        if( n_images <= 1 || (n_images % 2) == 0 ) {
            if( Debug.LOG )
                Log.e(TAG, "n_images should be an odd number greater than 1");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.expo_bracketing_n_images = n_images;
    }

    @Override
    public void setExpoBracketingStops(double stops) {
        if( Debug.LOG )
            Log.d(TAG, "setExpoBracketingStops: " + stops);
        if( stops <= 0.0 ) {
            if( Debug.LOG )
                Log.e(TAG, "stops should be positive");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.expo_bracketing_stops = stops;
    }

    @Override
    public void setUseCamera2FakeFlash(boolean use_fake_precapture) {
        if( Debug.LOG )
            Log.d(TAG, "setUseCamera2FakeFlash: " + use_fake_precapture);
        if( camera == null ) {
            if( Debug.LOG )
                Log.e(TAG, "no camera");
            return;
        }
        if( this.use_fake_precapture == use_fake_precapture ) {
            return;
        }
        this.use_fake_precapture = use_fake_precapture;
        this.use_fake_precapture_mode = use_fake_precapture;
        // no need to call updateUseFakePrecaptureMode(), as this method should only be called after first creating camera controller
    }

    @Override
    public boolean getUseCamera2FakeFlash() {
        return this.use_fake_precapture;
    }

    private void createPictureImageReader() {
        if( Debug.LOG )
            Log.d(TAG, "createPictureImageReader");
        if( captureSession != null ) {
            // can only call this when captureSession not created - as the surface of the imageReader we create has to match the surface we pass to the captureSession
            if( Debug.LOG )
                Log.e(TAG, "can't create picture image reader when captureSession running!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        closePictureImageReader();
        if( picture_width == 0 || picture_height == 0 ) {
            if( Debug.LOG )
                Log.e(TAG, "application needs to call setPictureSize()");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        imageReader = ImageReader.newInstance(picture_width, picture_height, ImageFormat.JPEG, 2);
        if( Debug.LOG ) {
            Log.d(TAG, "created new imageReader: " + imageReader.toString());
            Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
        }
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if( Debug.LOG )
                    Log.d(TAG, "new still image available");
                if( jpeg_cb == null ) {
                    if( Debug.LOG )
                        Log.d(TAG, "no picture callback available");
                    return;
                }
                synchronized( image_reader_lock ) {
                    /* Whilst in theory the two setOnImageAvailableListener methods (for JPEG and RAW) seem to be called separately, I don't know if this is always true;
                     * also, we may process the RAW image when the capture result is available (see
                     * OnRawImageAvailableListener.setCaptureResult()), which may be in a separate thread.
                     */
                    Image image = reader.acquireNextImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte [] bytes = new byte[buffer.remaining()];
                    if( Debug.LOG )
                        Log.d(TAG, "read " + bytes.length + " bytes");
                    buffer.get(bytes);
                    image.close();
                    if( want_expo_bracketing && n_burst > 1 ) {
                        pending_burst_images.add(bytes);
                        if( pending_burst_images.size() == n_burst ) {
                            if( Debug.LOG )
                                Log.d(TAG, "all burst images available");
                            // need to set jpeg_cb etc to null before calling onCompleted, as that may reenter Controller to take another photo (if in burst mode) - see testTakePhotoBurst()
                            PictureCallback cb = jpeg_cb;
                            jpeg_cb = null;
                            // take a copy, so that we can clear pending_burst_images
                            List<byte []> images = new ArrayList<byte []>(pending_burst_images);
                            cb.onBurstPictureTaken(images);
                            pending_burst_images.clear();
                            cb.onCompleted();
                        }
                        else {
                            if( Debug.LOG )
                                Log.d(TAG, "number of burst images is now: " + pending_burst_images.size());
                        }
                    }
                    else {
                        // need to set jpeg_cb etc to null before calling onCompleted, as that may reenter Controller to take another photo (if in burst mode) - see testTakePhotoBurst()
                        PictureCallback cb = jpeg_cb;
                        jpeg_cb = null;
                        cb.onPictureTaken(bytes);
                        if( raw_cb == null ) {
                            if( Debug.LOG )
                                Log.d(TAG, "all image callbacks now completed");
                            cb.onCompleted();
                        }
                        else if( pending_dngCreator != null ) {
                            if( Debug.LOG )
                                Log.d(TAG, "can now call pending raw callback");
                            takePendingRaw();
                            if( Debug.LOG )
                                Log.d(TAG, "all image callbacks now completed");
                            cb.onCompleted();
                        }
                    }
                }
                if( Debug.LOG )
                    Log.d(TAG, "done onImageAvailable");
            }
        }, null);
        if( want_raw && raw_size != null ) {
            imageReaderRaw = ImageReader.newInstance(raw_size.getWidth(), raw_size.getHeight(), ImageFormat.RAW_SENSOR, 2);
            if( Debug.LOG ) {
                Log.d(TAG, "created new imageReaderRaw: " + imageReaderRaw.toString());
                Log.d(TAG, "imageReaderRaw surface: " + imageReaderRaw.getSurface().toString());
            }
            imageReaderRaw.setOnImageAvailableListener(onRawImageAvailableListener = new OnRawImageAvailableListener(), null);
        }
    }

    private void clearPending() {
        if( Debug.LOG )
            Log.d(TAG, "clearPending");
        pending_burst_images.clear();
        pending_dngCreator = null;
        pending_image = null;
        if( onRawImageAvailableListener != null ) {
            onRawImageAvailableListener.clear();
        }
        n_burst = 0;
    }

    private void takePendingRaw() {
        if( Debug.LOG )
            Log.d(TAG, "takePendingRaw");
        if( pending_dngCreator != null ) {
            PictureCallback cb = raw_cb;
            raw_cb = null;
            cb.onRawPictureTaken(pending_dngCreator, pending_image);
            // image and dngCreator should be closed by the application (we don't do it here, so that applications can keep hold of the data, e.g., in a queue for background processing)
            pending_dngCreator = null;
            pending_image = null;
            if( onRawImageAvailableListener != null ) {
                onRawImageAvailableListener.clear();
            }
        }
    }

    @Override
    public Size getPreviewSize() {
        return new Size(preview_width, preview_height);
    }

    @Override
    public void setPreviewSize(int width, int height) {
        if( Debug.LOG )
            Log.d(TAG, "setPreviewSize: " + width + " , " + height);
		/*if( texture != null ) {
			if( Debug.LOG )
				Log.d(TAG, "set size of preview texture");
			texture.setDefaultBufferSize(width, height);
		}*/
        preview_width = width;
        preview_height = height;
		/*if( previewImageReader != null ) {
			previewImageReader.close();
		}
		previewImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);
		*/
    }

    @Override
    public void setVideoStabilization(boolean enabled) {
        camera_settings.video_stabilization = enabled;
        camera_settings.setVideoStabilization(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set video stabilization");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public boolean getVideoStabilization() {
        return camera_settings.video_stabilization;
    }

    @Override
    public int getJpegQuality() {
        return this.camera_settings.jpeg_quality;
    }

    @Override
    public void setJpegQuality(int quality) {
        if( quality < 0 || quality > 100 ) {
            if( Debug.LOG )
                Log.e(TAG, "invalid jpeg quality" + quality);
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.camera_settings.jpeg_quality = (byte)quality;
    }

    @Override
    public int getZoom() {
        return this.current_zoom_value;
    }

    @Override
    public void setZoom(int value) {
        if( zoom_ratios == null ) {
            if( Debug.LOG )
                Log.d(TAG, "zoom not supported");
            return;
        }
        if( value < 0 || value > zoom_ratios.size() ) {
            if( Debug.LOG )
                Log.e(TAG, "invalid zoom value" + value);
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        float zoom = zoom_ratios.get(value)/100.0f;
        Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int left = sensor_rect.width()/2;
        int right = left;
        int top = sensor_rect.height()/2;
        int bottom = top;
        int hwidth = (int)(sensor_rect.width() / (2.0*zoom));
        int hheight = (int)(sensor_rect.height() / (2.0*zoom));
        left -= hwidth;
        right += hwidth;
        top -= hheight;
        bottom += hheight;
        if( Debug.LOG ) {
            Log.d(TAG, "zoom: " + zoom);
            Log.d(TAG, "hwidth: " + hwidth);
            Log.d(TAG, "hheight: " + hheight);
            Log.d(TAG, "sensor_rect left: " + sensor_rect.left);
            Log.d(TAG, "sensor_rect top: " + sensor_rect.top);
            Log.d(TAG, "sensor_rect right: " + sensor_rect.right);
            Log.d(TAG, "sensor_rect bottom: " + sensor_rect.bottom);
            Log.d(TAG, "left: " + left);
            Log.d(TAG, "top: " + top);
            Log.d(TAG, "right: " + right);
            Log.d(TAG, "bottom: " + bottom);
			/*Rect current_rect = previewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
			Log.d(TAG, "current_rect left: " + current_rect.left);
			Log.d(TAG, "current_rect top: " + current_rect.top);
			Log.d(TAG, "current_rect right: " + current_rect.right);
			Log.d(TAG, "current_rect bottom: " + current_rect.bottom);*/
        }
        camera_settings.scalar_crop_region = new Rect(left, top, right, bottom);
        camera_settings.setCropRegion(previewBuilder);
        this.current_zoom_value = value;
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set zoom");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public int getExposureCompensation() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) == null )
            return 0;
        return previewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
    }

    @Override
    // Returns whether exposure was modified
    public boolean setExposureCompensation(int new_exposure) {
        camera_settings.has_ae_exposure_compensation = true;
        camera_settings.ae_exposure_compensation = new_exposure;
        if( camera_settings.setExposureCompensation(previewBuilder) ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( Debug.LOG ) {
                    Log.e(TAG, "failed to set exposure compensation");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    @Override
    public void setPreviewFpsRange(int min, int max) {
        // TODO Auto-generated method stub

    }

    @Override
    public List<int[]> getSupportedPreviewFpsRange() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    // note, responsibility of callers to check that this is within the valid min/max range
    public long getDefaultExposureTime() {
        return 1000000000l/30;
    }

    @Override
    public void setFocusValue(String focus_value) {
        if( Debug.LOG )
            Log.d(TAG, "setFocusValue: " + focus_value);
        int focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        if( focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_locked") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_AUTO;
        }
        else if( focus_value.equals("focus_mode_infinity") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_OFF;
            camera_settings.focus_distance = 0.0f;
        }
        else if( focus_value.equals("focus_mode_manual2") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_OFF;
            camera_settings.focus_distance = camera_settings.focus_distance_manual;
        }
        else if( focus_value.equals("focus_mode_macro") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_MACRO;
        }
        else if( focus_value.equals("focus_mode_edof") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_EDOF;
        }
        else if( focus_value.equals("focus_mode_continuous_picture") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        }
        else if( focus_value.equals("focus_mode_continuous_video") ) {
            focus_mode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
        }
        else {
            if( Debug.LOG )
                Log.d(TAG, "setFocusValue() received unknown focus value " + focus_value);
            return;
        }
        camera_settings.has_af_mode = true;
        camera_settings.af_mode = focus_mode;
        camera_settings.setFocusMode(previewBuilder);
        camera_settings.setFocusDistance(previewBuilder); // also need to set distance, in case changed between infinity, manual or other modes
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set focus mode");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private String convertFocusModeToValue(int focus_mode) {
        if( Debug.LOG )
            Log.d(TAG, "convertFocusModeToValue: " + focus_mode);
        String focus_value = "";
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO ) {
            focus_value = "focus_mode_auto";
        }
        else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO ) {
            focus_value = "focus_mode_macro";
        }
        else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_EDOF ) {
            focus_value = "focus_mode_edof";
        }
        else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
            focus_value = "focus_mode_continuous_picture";
        }
        else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ) {
            focus_value = "focus_mode_continuous_video";
        }
        else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_OFF ) {
            focus_value = "focus_mode_manual2"; // n.b., could be infinity
        }
        return focus_value;
    }

    @Override
    public String getFocusValue() {
        int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) != null ?
                previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) : CaptureRequest.CONTROL_AF_MODE_AUTO;
        return convertFocusModeToValue(focus_mode);
    }

    @Override
    public float getFocusDistance() {
        return camera_settings.focus_distance;
    }

    @Override
    public boolean setFocusDistance(float focus_distance) {
        if( Debug.LOG )
            Log.d(TAG, "setFocusDistance: " + focus_distance);
        if( camera_settings.focus_distance == focus_distance ) {
            if( Debug.LOG )
                Log.d(TAG, "already set");
            return false;
        }
        camera_settings.focus_distance = focus_distance;
        camera_settings.focus_distance_manual = focus_distance;
        camera_settings.setFocusDistance(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set focus distance");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        return true;
    }

    /** Decides whether we should be using fake precapture mode.
     */
    private void updateUseFakePrecaptureMode(String flash_value) {
        if( Debug.LOG )
            Log.d(TAG, "useFakePrecaptureMode: " + flash_value);
        boolean frontscreen_flash = flash_value.equals("flash_frontscreen_auto") || flash_value.equals("flash_frontscreen_on");
        if( frontscreen_flash ) {
            use_fake_precapture_mode = true;
        }
        else if( this.want_expo_bracketing )
            use_fake_precapture_mode = true;
        else {
            use_fake_precapture_mode = use_fake_precapture;
        }
        if( Debug.LOG )
            Log.d(TAG, "use_fake_precapture_mode set to: " + use_fake_precapture_mode);
    }

    @Override
    public void setFlashValue(String flash_value) {
        if( Debug.LOG )
            Log.d(TAG, "setFlashValue: " + flash_value);
        if( camera_settings.flash_value.equals(flash_value) ) {
            if( Debug.LOG )
                Log.d(TAG, "flash value already set");
            return;
        }

        try {
            updateUseFakePrecaptureMode(flash_value);

            if( camera_settings.flash_value.equals("flash_torch") && !flash_value.equals("flash_off") ) {
                // hack - if switching to something other than flash_off, we first need to turn torch off, otherwise torch remains on (at least on Nexus 6)
                camera_settings.flash_value = "flash_off";
                camera_settings.setAEMode(previewBuilder, false);
                CaptureRequest request = previewBuilder.build();

                // need to wait until torch actually turned off
                camera_settings.flash_value = flash_value;
                camera_settings.setAEMode(previewBuilder, false);
                push_repeating_request_when_torch_off = true;
                push_repeating_request_when_torch_off_id = request;

                setRepeatingRequest(request);
            }
            else {
                camera_settings.flash_value = flash_value;
                if( camera_settings.setAEMode(previewBuilder, false) ) {
                    setRepeatingRequest();
                }
            }
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set flash mode");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public String getFlashValue() {
        // returns "" if flash isn't supported
        if( !characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ) {
            return "";
        }
        return camera_settings.flash_value;
    }

    @Override
    public void setRecordingHint(boolean hint) {
        // not relevant for Controller2
    }

    @Override
    public void setAutoExposureLock(boolean enabled) {
        camera_settings.ae_lock = enabled;
        camera_settings.setAutoExposureLock(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set auto exposure lock");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public boolean getAutoExposureLock() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK) == null )
            return false;
        return previewBuilder.get(CaptureRequest.CONTROL_AE_LOCK);
    }

    @Override
    public void setRotation(int rotation) {
        this.camera_settings.rotation = rotation;
    }

    @Override
    public void setLocationInfo(Location location) {
        if( Debug.LOG )
            Log.d(TAG, "setLocationInfo: " + location.getLongitude() + " , " + location.getLatitude());
        this.camera_settings.location = location;
    }

    @Override
    public void removeLocationInfo() {
        this.camera_settings.location = null;
    }

    @Override
    public void enableShutterSound(boolean enabled) {
        this.sounds_enabled = enabled;
    }

    /** Returns the viewable rect - this is crop region if available.
     *  We need this as callers will pass in (or expect returned) Controller.Area values that
     *  are relative to the current view (i.e., taking zoom into account) (the old Camera API in
     *  Controller1 always works in terms of the current view, whilst Camera2 works in terms
     *  of the full view always). Similarly for the rect field in Controller.Face.
     */
    private Rect getViewableRect() {
        if( previewBuilder != null ) {
            Rect crop_rect = previewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            if( crop_rect != null ) {
                return crop_rect;
            }
        }
        Rect sensor_rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        sensor_rect.right -= sensor_rect.left;
        sensor_rect.left = 0;
        sensor_rect.bottom -= sensor_rect.top;
        sensor_rect.top = 0;
        return sensor_rect;
    }

    private Rect convertRectToCamera2(Rect crop_rect, Rect rect) {
        // Controller.Area is always [-1000, -1000] to [1000, 1000] for the viewable region
        // but for Controller2, we must convert to be relative to the crop region
        double left_f = (rect.left+1000)/2000.0;
        double top_f = (rect.top+1000)/2000.0;
        double right_f = (rect.right+1000)/2000.0;
        double bottom_f = (rect.bottom+1000)/2000.0;
        int left = (int)(crop_rect.left + left_f * (crop_rect.width()-1));
        int right = (int)(crop_rect.left + right_f * (crop_rect.width()-1));
        int top = (int)(crop_rect.top + top_f * (crop_rect.height()-1));
        int bottom = (int)(crop_rect.top + bottom_f * (crop_rect.height()-1));
        left = Math.max(left, crop_rect.left);
        right = Math.max(right, crop_rect.left);
        top = Math.max(top, crop_rect.top);
        bottom = Math.max(bottom, crop_rect.top);
        left = Math.min(left, crop_rect.right);
        right = Math.min(right, crop_rect.right);
        top = Math.min(top, crop_rect.bottom);
        bottom = Math.min(bottom, crop_rect.bottom);

        Rect camera2_rect = new Rect(left, top, right, bottom);
        return camera2_rect;
    }

    private MeteringRectangle convertAreaToMeteringRectangle(Rect sensor_rect, Area area) {
        Rect camera2_rect = convertRectToCamera2(sensor_rect, area.rect);
        MeteringRectangle metering_rectangle = new MeteringRectangle(camera2_rect, area.weight);
        return metering_rectangle;
    }

    private Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
        // inverse of convertRectToCamera2()
        double left_f = (camera2_rect.left-crop_rect.left)/(double)(crop_rect.width()-1);
        double top_f = (camera2_rect.top-crop_rect.top)/(double)(crop_rect.height()-1);
        double right_f = (camera2_rect.right-crop_rect.left)/(double)(crop_rect.width()-1);
        double bottom_f = (camera2_rect.bottom-crop_rect.top)/(double)(crop_rect.height()-1);
        int left = (int)(left_f * 2000) - 1000;
        int right = (int)(right_f * 2000) - 1000;
        int top = (int)(top_f * 2000) - 1000;
        int bottom = (int)(bottom_f * 2000) - 1000;

        left = Math.max(left, -1000);
        right = Math.max(right, -1000);
        top = Math.max(top, -1000);
        bottom = Math.max(bottom, -1000);
        left = Math.min(left, 1000);
        right = Math.min(right, 1000);
        top = Math.min(top, 1000);
        bottom = Math.min(bottom, 1000);

        Rect rect = new Rect(left, top, right, bottom);
        return rect;
    }

    private Area convertMeteringRectangleToArea(Rect sensor_rect, MeteringRectangle metering_rectangle) {
        Rect area_rect = convertRectFromCamera2(sensor_rect, metering_rectangle.getRect());
        Area area = new Area(area_rect, metering_rectangle.getMeteringWeight());
        return area;
    }

    private Controller.Face convertFromCameraFace(Rect sensor_rect, android.hardware.camera2.params.Face camera2_face) {
        Rect area_rect = convertRectFromCamera2(sensor_rect, camera2_face.getBounds());
        Controller.Face face = new Controller.Face(camera2_face.getScore(), area_rect);
        return face;
    }

    @Override
    public boolean setFocusAndMeteringArea(List<Area> areas) {
        Rect sensor_rect = getViewableRect();
        if( Debug.LOG )
            Log.d(TAG, "sensor_rect: " + sensor_rect.left + " , " + sensor_rect.top + " x " + sensor_rect.right + " , " + sensor_rect.bottom);
        boolean has_focus = false;
        boolean has_metering = false;
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
            has_focus = true;
            camera_settings.af_regions = new MeteringRectangle[areas.size()];
            int i = 0;
            for(Controller.Area area : areas) {
                camera_settings.af_regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
            }
            camera_settings.setAFRegions(previewBuilder);
        }
        else
            camera_settings.af_regions = null;
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
            has_metering = true;
            camera_settings.ae_regions = new MeteringRectangle[areas.size()];
            int i = 0;
            for(Controller.Area area : areas) {
                camera_settings.ae_regions[i++] = convertAreaToMeteringRectangle(sensor_rect, area);
            }
            camera_settings.setAERegions(previewBuilder);
        }
        else
            camera_settings.ae_regions = null;
        if( has_focus || has_metering ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( Debug.LOG ) {
                    Log.e(TAG, "failed to set focus and/or metering regions");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
        }
        return has_focus;
    }

    @Override
    public void clearFocusAndMetering() {
        Rect sensor_rect = getViewableRect();
        boolean has_focus = false;
        boolean has_metering = false;
        if( sensor_rect.width() <= 0 || sensor_rect.height() <= 0 ) {
            // had a crash on Google Play due to creating a MeteringRectangle with -ve width/height ?!
            camera_settings.af_regions = null;
            camera_settings.ae_regions = null;
        }
        else {
            if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0 ) {
                has_focus = true;
                camera_settings.af_regions = new MeteringRectangle[1];
                camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
                camera_settings.setAFRegions(previewBuilder);
            }
            else
                camera_settings.af_regions = null;
            if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0 ) {
                has_metering = true;
                camera_settings.ae_regions = new MeteringRectangle[1];
                camera_settings.ae_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
                camera_settings.setAERegions(previewBuilder);
            }
            else
                camera_settings.ae_regions = null;
        }
        if( has_focus || has_metering ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( Debug.LOG ) {
                    Log.e(TAG, "failed to clear focus and metering regions");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<Area> getFocusAreas() {
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) == 0 )
            return null;
        MeteringRectangle [] metering_rectangles = previewBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
        if( metering_rectangles == null )
            return null;
        Rect sensor_rect = getViewableRect();
        camera_settings.af_regions[0] = new MeteringRectangle(0, 0, sensor_rect.width()-1, sensor_rect.height()-1, 0);
        if( metering_rectangles.length == 1 && metering_rectangles[0].getRect().left == 0 && metering_rectangles[0].getRect().top == 0 && metering_rectangles[0].getRect().right == sensor_rect.width()-1 && metering_rectangles[0].getRect().bottom == sensor_rect.height()-1 ) {
            // for compatibility with Controller1
            return null;
        }
        List<Area> areas = new ArrayList<Area>();
        for(int i=0;i<metering_rectangles.length;i++) {
            areas.add(convertMeteringRectangleToArea(sensor_rect, metering_rectangles[i]));
        }
        return areas;
    }

    @Override
    public List<Area> getMeteringAreas() {
        if( characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) == 0 )
            return null;
        MeteringRectangle [] metering_rectangles = previewBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
        if( metering_rectangles == null )
            return null;
        Rect sensor_rect = getViewableRect();
        if( metering_rectangles.length == 1 && metering_rectangles[0].getRect().left == 0 && metering_rectangles[0].getRect().top == 0 && metering_rectangles[0].getRect().right == sensor_rect.width()-1 && metering_rectangles[0].getRect().bottom == sensor_rect.height()-1 ) {
            // for compatibility with Controller1
            return null;
        }
        List<Area> areas = new ArrayList<Area>();
        for(int i=0;i<metering_rectangles.length;i++) {
            areas.add(convertMeteringRectangleToArea(sensor_rect, metering_rectangles[i]));
        }
        return areas;
    }

    @Override
    public boolean supportsAutoFocus() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null )
            return true;
        int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_AUTO || focus_mode == CaptureRequest.CONTROL_AF_MODE_MACRO )
            return true;
        return false;
    }

    @Override
    public boolean focusIsContinuous() {
        if( previewBuilder == null || previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null )
            return false;
        int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE || focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO )
            return true;
        return false;
    }

    @Override
    public boolean focusIsVideo() {
        if( previewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == null )
            return false;
        int focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO ) {
            return true;
        }
        return false;
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder holder) throws ControllerException {
        if( Debug.LOG ) {
            Log.d(TAG, "setPreviewDisplay");
            Log.e(TAG, "SurfaceHolder not supported for Controller2!");
            Log.e(TAG, "Should use setPreviewTexture() instead");
        }
        throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
    }

    @Override
    public void setPreviewTexture(SurfaceTexture texture) throws ControllerException {
        if( Debug.LOG )
            Log.d(TAG, "setPreviewTexture");
        if( this.texture != null ) {
            if( Debug.LOG )
                Log.d(TAG, "preview texture already set");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        this.texture = texture;
    }

    private void setRepeatingRequest() throws CameraAccessException {
        setRepeatingRequest(previewBuilder.build());
    }

    private void setRepeatingRequest(CaptureRequest request) throws CameraAccessException {
        if( Debug.LOG )
            Log.d(TAG, "setRepeatingRequest");
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            return;
        }
        captureSession.setRepeatingRequest(request, previewCaptureCallback, handler);
        if( Debug.LOG )
            Log.d(TAG, "setRepeatingRequest done");
    }

    private void capture() throws CameraAccessException {
        capture(previewBuilder.build());
    }

    private void capture(CaptureRequest request) throws CameraAccessException {
        if( Debug.LOG )
            Log.d(TAG, "capture");
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            return;
        }
        captureSession.capture(request, previewCaptureCallback, handler);
    }

    private void createPreviewRequest() {
        if( Debug.LOG )
            Log.d(TAG, "createPreviewRequest");
        if( camera == null  ) {
            if( Debug.LOG )
                Log.d(TAG, "camera not available!");
            return;
        }
        if( Debug.LOG )
            Log.d(TAG, "camera: " + camera);
        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
            camera_settings.setupBuilder(previewBuilder, false);
            if( Debug.LOG )
                Log.d(TAG, "successfully created preview request");
        }
        catch(CameraAccessException e) {
            //captureSession = null;
            if( Debug.LOG ) {
                Log.e(TAG, "failed to create capture request");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private Surface getPreviewSurface() {
        return surface_texture;
    }

    private void createCaptureSession(final MediaRecorder video_recorder) throws ControllerException {
        if( Debug.LOG )
            Log.d(TAG, "create capture session");

        if( previewBuilder == null ) {
            if( Debug.LOG )
                Log.d(TAG, "previewBuilder not present!");
            throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
        }
        if( camera == null ) {
            if( Debug.LOG )
                Log.e(TAG, "no camera");
            return;
        }

        if( captureSession != null ) {
            if( Debug.LOG )
                Log.d(TAG, "close old capture session");
            captureSession.close();
            captureSession = null;
        }

        try {
            captureSession = null;

            if( video_recorder != null ) {
                closePictureImageReader();
            }
            else {
                // in some cases need to recreate picture imageReader and the texture default buffer size (e.g., see test testTakePhotoPreviewPaused())
                createPictureImageReader();
            }
            if( texture != null ) {
                // need to set the texture size
                if( Debug.LOG )
                    Log.d(TAG, "set size of preview texture");
                if( preview_width == 0 || preview_height == 0 ) {
                    if( Debug.LOG )
                        Log.e(TAG, "application needs to call setPreviewSize()");
                    throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
                }
                texture.setDefaultBufferSize(preview_width, preview_height);
                // also need to create a new surface for the texture, in case the size has changed - but make sure we remove the old one first!
                if( surface_texture != null ) {
                    if( Debug.LOG )
                        Log.d(TAG, "remove old target: " + surface_texture);
                    previewBuilder.removeTarget(surface_texture);
                }
                this.surface_texture = new Surface(texture);
                if( Debug.LOG )
                    Log.d(TAG, "created new target: " + surface_texture);
            }
            if( video_recorder != null ) {
                if( Debug.LOG )
                    Log.d(TAG, "creating capture session for video recording");
            }
            else {
                if( Debug.LOG )
                    Log.d(TAG, "picture size: " + imageReader.getWidth() + " x " + imageReader.getHeight());
            }
			/*if( Debug.LOG )
			Log.d(TAG, "preview size: " + previewImageReader.getWidth() + " x " + previewImageReader.getHeight());*/
            if( Debug.LOG )
                Log.d(TAG, "preview size: " + this.preview_width + " x " + this.preview_height);

            class MyStateCallback extends CameraCaptureSession.StateCallback {
                boolean callback_done = false; // must sychronize on this and notifyAll when setting to true
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if( Debug.LOG ) {
                        Log.d(TAG, "onConfigured: " + session);
                        Log.d(TAG, "captureSession was: " + captureSession);
                    }
                    if( camera == null ) {
                        if( Debug.LOG ) {
                            Log.d(TAG, "camera is closed");
                        }
                        synchronized( this ) {
                            callback_done = true;
                            this.notifyAll();
                        }
                        return;
                    }
                    captureSession = session;
                    Surface surface = getPreviewSurface();
                    previewBuilder.addTarget(surface);
                    if( video_recorder != null )
                        previewBuilder.addTarget(video_recorder.getSurface());
                    try {
                        setRepeatingRequest();
                    }
                    catch(CameraAccessException e) {
                        if( Debug.LOG ) {
                            Log.e(TAG, "failed to start preview");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                        preview_error_cb.onError();
                    }
                    synchronized( this ) {
                        callback_done = true;
                        this.notifyAll();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    if( Debug.LOG ) {
                        Log.d(TAG, "onConfigureFailed: " + session);
                        Log.d(TAG, "captureSession was: " + captureSession);
                    }
                    synchronized( this ) {
                        callback_done = true;
                        this.notifyAll();
                    }
                    // don't throw ControllerException here, as won't be caught - instead we throw ControllerException below
                }
            }
            MyStateCallback myStateCallback = new MyStateCallback();

            Surface preview_surface = getPreviewSurface();
            List<Surface> surfaces = null;
            if( video_recorder != null ) {
                surfaces = Arrays.asList(preview_surface, video_recorder.getSurface());
            }
            else if( imageReaderRaw != null ) {
                surfaces = Arrays.asList(preview_surface, imageReader.getSurface(), imageReaderRaw.getSurface());
            }
            else {
                surfaces = Arrays.asList(preview_surface, imageReader.getSurface());
            }
            if( Debug.LOG ) {
                Log.d(TAG, "texture: " + texture);
                Log.d(TAG, "preview_surface: " + preview_surface);
                if( video_recorder == null ) {
                    if( imageReaderRaw != null ) {
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw);
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.getWidth());
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.getHeight());
                        Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.getImageFormat());
                    }
                    else {
                        Log.d(TAG, "imageReader: " + imageReader);
                        Log.d(TAG, "imageReader: " + imageReader.getWidth());
                        Log.d(TAG, "imageReader: " + imageReader.getHeight());
                        Log.d(TAG, "imageReader: " + imageReader.getImageFormat());
                    }
                }
            }
            camera.createCaptureSession(surfaces,
                    myStateCallback,
                    handler);
            if( Debug.LOG )
                Log.d(TAG, "wait until session created...");
            synchronized( myStateCallback ) {
                while( !myStateCallback.callback_done ) {
                    try {
                        // release the myStateCallback lock, and wait until myStateCallback calls notifyAll()
                        myStateCallback.wait();
                    }
                    catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if( Debug.LOG ) {
                Log.d(TAG, "created captureSession: " + captureSession);
            }
            if( captureSession == null ) {
                if( Debug.LOG )
                    Log.e(TAG, "failed to create capture session");
                throw new ControllerException();
            }
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "CameraAccessException trying to create capture session");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new ControllerException();
        }
    }

    @Override
    public void startPreview() throws ControllerException {
        if( Debug.LOG )
            Log.d(TAG, "startPreview");
        if( captureSession != null ) {
            try {
                setRepeatingRequest();
            }
            catch(CameraAccessException e) {
                if( Debug.LOG ) {
                    Log.e(TAG, "failed to start preview");
                    Log.e(TAG, "reason: " + e.getReason());
                    Log.e(TAG, "message: " + e.getMessage());
                }
                e.printStackTrace();
                // do via ControllerException instead of preview_error_cb, so caller immediately knows preview has failed
                throw new ControllerException();
            }
            return;
        }
        createCaptureSession(null);
    }

    @Override
    public void stopPreview() {
        if( Debug.LOG )
            Log.d(TAG, "stopPreview");
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            return;
        }
        try {
            captureSession.stopRepeating();
            // although stopRepeating() alone will pause the preview, seems better to close captureSession altogether - this allows the app to make changes such as changing the picture size
            if( Debug.LOG )
                Log.d(TAG, "close capture session");
            captureSession.close();
            captureSession = null;
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to stop repeating");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        // simulate Controller1 behaviour where face detection is stopped when we stop preview
        if( camera_settings.has_face_detect_mode ) {
            if( Debug.LOG )
                Log.d(TAG, "cancel face detection");
            camera_settings.has_face_detect_mode = false;
            camera_settings.setFaceDetectMode(previewBuilder);
            // no need to call setRepeatingRequest(), we're just setting the camera_settings for when we restart the preview
        }
    }

    @Override
    public boolean startFaceDetection() {
        if( previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) != null && previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL ) {
            return false;
        }
        camera_settings.has_face_detect_mode = true;
        camera_settings.face_detect_mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        camera_settings.setFaceDetectMode(previewBuilder);
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to start face detection");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void setFaceDetectionListener(final FaceDetectionListener listener) {
        this.face_detection_listener = listener;
    }

    @Override
    public void autoFocus(final AutoFocusCallback cb) {
        if( Debug.LOG )
            Log.d(TAG, "autoFocus");
        fake_precapture_torch_focus_performed = false;
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            // should call the callback, so the application isn't left waiting (e.g., when we autofocus before trying to take a photo)
            cb.onAutoFocus(false);
            return;
        }
        Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        if( focus_mode == null ) {
            // we preserve the old Camera API where calling autoFocus() on a device without autofocus immediately calls the callback
            // (unclear if Open Camera needs this, but just to be safe and consistent between camera APIs)
            cb.onAutoFocus(true);
            return;
        }
        else if( focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
            /* In the old Camera API, doing an autofocus in FOCUS_MODE_CONTINUOUS_PICTURE mode would call the callback when the camera isn't focusing,
             * and return whether focus was successful or not. So we replicate the behaviour here too (see previewCaptureCallback.process()).
             * This is essential to have correct behaviour for flash mode in continuous picture focus mode. Otherwise:
             *  - Taking photo with flash auto when flash is used, or flash on, takes longer (excessive amount of flash firing due to an additional unnecessary focus before taking photo).
             *  - Taking photo with flash auto when flash is needed sometime results in flash firing for the (unnecessary) autofocus, then not firing for final picture, resulting in too dark pictures.
             *    This seems to happen with scenes that have both light and dark regions.
             *  (All tested on Nexus 6, Android 6.)
             */
            this.autofocus_cb = cb;
            return;
        }
		/*if( state == STATE_WAITING_AUTOFOCUS ) {
			if( Debug.LOG )
				Log.d(TAG, "already waiting for an autofocus");
			// need to update the callback!
			this.autofocus_cb = cb;
			return;
		}*/
        CaptureRequest.Builder afBuilder = previewBuilder;
        if( Debug.LOG ) {
            {
                MeteringRectangle [] areas = afBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
                for(int i=0;areas != null && i<areas.length;i++) {
                    Log.d(TAG, i + " focus area: " + areas[i].getX() + " , " + areas[i].getY() + " : " + areas[i].getWidth() + " x " + areas[i].getHeight() + " weight " + areas[i].getMeteringWeight());
                }
            }
            {
                MeteringRectangle [] areas = afBuilder.get(CaptureRequest.CONTROL_AE_REGIONS);
                for(int i=0;areas != null && i<areas.length;i++) {
                    Log.d(TAG, i + " metering area: " + areas[i].getX() + " , " + areas[i].getY() + " : " + areas[i].getWidth() + " x " + areas[i].getHeight() + " weight " + areas[i].getMeteringWeight());
                }
            }
        }
        state = STATE_WAITING_AUTOFOCUS;
        precapture_state_change_time_ms = -1;
        this.autofocus_cb = cb;
        // Camera2Basic sets a trigger with capture
        // Google Camera sets to idle with a repeating request, then sets af trigger to start with a capture
        try {
            if( use_fake_precapture_mode && !camera_settings.has_iso ) {
                boolean want_flash = false;
                if( camera_settings.flash_value.equals("flash_auto") ) {
                    // calling fireAutoFlash() also caches the decision on whether to flash - otherwise if the flash fires now, we'll then think the scene is bright enough to not need the flash!
                    if( fireAutoFlash() )
                        want_flash = true;
                }
                else if( camera_settings.flash_value.equals("flash_on") ) {
                    want_flash = true;
                }
                if( want_flash ) {
                    if( Debug.LOG )
                        Log.d(TAG, "turn on torch for fake flash");
                    //afBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    afBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    fake_precapture_torch_focus_performed = true;
                }
                // CONTROL_AE_MODE is set back to flash auto after the capture is completed
            }
            afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            setRepeatingRequest(afBuilder.build());
            afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            capture(afBuilder.build());
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to autofocus");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            state = STATE_NORMAL;
            precapture_state_change_time_ms = -1;
            autofocus_cb.onAutoFocus(false);
            autofocus_cb = null;
        }
        afBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE); // ensure set back to idle
    }

    @Override
    public void cancelAutoFocus() {
        if( Debug.LOG )
            Log.d(TAG, "cancelAutoFocus");
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            return;
        }
        previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        // Camera2Basic does a capture then sets a repeating request - do the same here just to be safe
        try {
            capture();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to cancel autofocus [capture]");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
        previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        this.autofocus_cb = null;
        state = STATE_NORMAL;
        precapture_state_change_time_ms = -1;
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to set repeating request after cancelling autofocus");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    @Override
    public void setContinuousFocusMoveCallback(ContinuousFocusMoveCallback cb) {
        if( Debug.LOG )
            Log.d(TAG, "setContinuousFocusMoveCallback");
        this.continuous_focus_move_callback = cb;
    }

    private void takePictureAfterPrecapture() {
        if( Debug.LOG )
            Log.d(TAG, "takePictureAfterPrecapture");
        if( want_expo_bracketing ) {
            takePictureBurstExpoBracketing();
            return;
        }
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            return;
        }
        try {
            if( Debug.LOG ) {
                if( imageReaderRaw != null ) {
                    Log.d(TAG, "imageReaderRaw: " + imageReaderRaw.toString());
                    Log.d(TAG, "imageReaderRaw surface: " + imageReaderRaw.getSurface().toString());
                }
                else {
                    Log.d(TAG, "imageReader: " + imageReader.toString());
                    Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
                }
            }
            CaptureRequest.Builder stillBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            stillBuilder.setTag(RequestTag.CAPTURE);
            camera_settings.setupBuilder(stillBuilder, true);
            if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            }
            //stillBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            clearPending();
            Surface surface = getPreviewSurface();
            stillBuilder.addTarget(surface); // Google Camera adds the preview surface as well as capture surface, for still capture
            stillBuilder.addTarget(imageReader.getSurface());
            if( imageReaderRaw != null )
                stillBuilder.addTarget(imageReaderRaw.getSurface());

            captureSession.stopRepeating(); // need to stop preview before capture (as done in Camera2Basic; otherwise we get bugs such as flash remaining on after taking a photo with flash)
            captureSession.capture(stillBuilder.build(), previewCaptureCallback, handler);
            if( sounds_enabled ) // play shutter sound asap, otherwise user has the illusion of being slow to take photos
                media_action_sound.play(MediaActionSound.SHUTTER_CLICK);
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to take picture");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            jpeg_cb = null;
            if( take_picture_error_cb != null ) {
                take_picture_error_cb.onError();
                take_picture_error_cb = null;
                return;
            }
        }
    }

    private void takePictureBurstExpoBracketing() {
        if( Debug.LOG )
            Log.d(TAG, "takePictureBurstExpBracketing");
        if( Debug.LOG && !want_expo_bracketing ) {
            Log.e(TAG, "takePictureBurstExpoBracketing called but want_expo_bracketing is false");
        }
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            return;
        }
        try {
            if( Debug.LOG ) {
                Log.d(TAG, "imageReader: " + imageReader.toString());
                Log.d(TAG, "imageReader surface: " + imageReader.getSurface().toString());
            }

            CaptureRequest.Builder stillBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            stillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            stillBuilder.setTag(RequestTag.CAPTURE);
            camera_settings.setupBuilder(stillBuilder, true);
            clearPending();
            Surface surface = getPreviewSurface();
            stillBuilder.addTarget(surface); // Google Camera adds the preview surface as well as capture surface, for still capture
            stillBuilder.addTarget(imageReader.getSurface());

            List<CaptureRequest> requests = new ArrayList<CaptureRequest>();

			/*stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
			stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);

			stillBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -6);
			requests.add( stillBuilder.build() );
			stillBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
			requests.add( stillBuilder.build() );
			stillBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 6);
			requests.add( stillBuilder.build() );*/

            stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                stillBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            }
            // else don't turn torch off, as user may be in torch on mode
            if( capture_result_has_iso )
                stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, capture_result_iso );
            else
                stillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 800);
            if( capture_result_has_frame_duration  )
                stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, capture_result_frame_duration);
            else
                stillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000l/30);

            long base_exposure_time = 1000000000l/30;
            if( capture_result_has_exposure_time )
                base_exposure_time = capture_result_exposure_time;

            int n_half_images = expo_bracketing_n_images/2;
            long min_exposure_time = base_exposure_time;
            long max_exposure_time = base_exposure_time;
            final double scale = Math.pow(2.0, expo_bracketing_stops);
            Range<Long> exposure_time_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if( exposure_time_range != null ) {
                min_exposure_time = exposure_time_range.getLower();
                max_exposure_time = exposure_time_range.getUpper();
            }

            if( Debug.LOG ) {
                Log.d(TAG, "taking expo bracketing with n_images: " + expo_bracketing_n_images);
                Log.d(TAG, "ISO: " + stillBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
                Log.d(TAG, "Frame duration: " + stillBuilder.get(CaptureRequest.SENSOR_FRAME_DURATION));
                Log.d(TAG, "Base exposure time: " + base_exposure_time);
                Log.d(TAG, "Min exposure time: " + min_exposure_time);
                Log.d(TAG, "Max exposure time: " + max_exposure_time);
            }

            // darker images
            for(int i=0;i<n_half_images;i++) {
                long exposure_time = base_exposure_time;
                if( exposure_time_range != null ) {
                    double this_scale = scale;
                    for(int j=i;j<n_half_images-1;j++)
                        this_scale *= scale;
                    exposure_time /= this_scale;
                    if( exposure_time < min_exposure_time )
                        exposure_time = min_exposure_time;
                    if( Debug.LOG ) {
                        Log.d(TAG, "add burst request for " + i + "th dark image:");
                        Log.d(TAG, "    this_scale: " + this_scale);
                        Log.d(TAG, "    exposure_time: " + exposure_time);
                    }
                    stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
                    requests.add( stillBuilder.build() );
                }
            }

            // base image
            if( Debug.LOG )
                Log.d(TAG, "add burst request for base image");
            stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, base_exposure_time);
            requests.add( stillBuilder.build() );

            // lighter images
            for(int i=0;i<n_half_images;i++) {
                long exposure_time = base_exposure_time;
                if( exposure_time_range != null ) {
                    double this_scale = scale;
                    for(int j=0;j<i;j++)
                        this_scale *= scale;
                    exposure_time *= this_scale;
                    if( exposure_time > max_exposure_time )
                        exposure_time = max_exposure_time;
                    if( Debug.LOG ) {
                        Log.d(TAG, "add burst request for " + i + "th light image:");
                        Log.d(TAG, "    this_scale: " + this_scale);
                        Log.d(TAG, "    exposure_time: " + exposure_time);
                    }
                    stillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
                    requests.add( stillBuilder.build() );
                }
            }

            n_burst = requests.size();
            if( Debug.LOG )
                Log.d(TAG, "n_burst: " + n_burst);

            captureSession.stopRepeating(); // see note under takePictureAfterPrecapture()
            captureSession.captureBurst(requests, previewCaptureCallback, handler);
            if( sounds_enabled ) // play shutter sound asap, otherwise user has the illusion of being slow to take photos
                media_action_sound.play(MediaActionSound.SHUTTER_CLICK);
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to take picture burst");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            jpeg_cb = null;
            if( take_picture_error_cb != null ) {
                take_picture_error_cb.onError();
                take_picture_error_cb = null;
                return;
            }
        }
    }

    private void runPrecapture() {
        if( Debug.LOG )
            Log.d(TAG, "runPrecapture");
        // first run precapture sequence
        if( Debug.LOG ) {
            if( use_fake_precapture_mode )
                Log.e(TAG, "shouldn't be doing standard precapture when use_fake_precapture_mode is true!");
            else if( want_expo_bracketing )
                Log.e(TAG, "shouldn't be doing precapture for want_expo_bracketing - should be using fake precapture!");
        }
        try {
            // use a separate builder for precapture - otherwise have problem that if we take photo with flash auto/on of dark scene, then point to a bright scene, the autoexposure isn't running until we autofocus again
            final CaptureRequest.Builder precaptureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            precaptureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);

            camera_settings.setupBuilder(precaptureBuilder, false);
            precaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            precaptureBuilder.addTarget(getPreviewSurface());

            state = STATE_WAITING_PRECAPTURE_START;
            precapture_state_change_time_ms = System.currentTimeMillis();

            // first set precapture to idle - this is needed, otherwise we hang in state STATE_WAITING_PRECAPTURE_START, because precapture already occurred whilst autofocusing, and it doesn't occur again unless we first set the precapture trigger to idle
            captureSession.capture(precaptureBuilder.build(), previewCaptureCallback, handler);
            captureSession.setRepeatingRequest(precaptureBuilder.build(), previewCaptureCallback, handler);

            // now set precapture
            precaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            captureSession.capture(precaptureBuilder.build(), previewCaptureCallback, handler);
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to precapture");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            jpeg_cb = null;
            if( take_picture_error_cb != null ) {
                take_picture_error_cb.onError();
                take_picture_error_cb = null;
                return;
            }
        }
    }

    private void runFakePrecapture() {
        if( Debug.LOG )
            Log.d(TAG, "runFakePrecapture");
        if( camera_settings.flash_value.equals("flash_auto") || camera_settings.flash_value.equals("flash_on") ) {
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            previewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            fake_precapture_torch_performed = true;
        }
        else if( camera_settings.flash_value.equals("flash_frontscreen_auto") || camera_settings.flash_value.equals("flash_frontscreen_on") ) {
            if( jpeg_cb != null ) {
                if( Debug.LOG )
                    Log.d(TAG, "request screen turn on for frontscreen flash");
                jpeg_cb.onFrontScreenTurnOn();
            }
            else {
                if( Debug.LOG )
                    Log.e(TAG, "can't request screen turn on for frontscreen flash, as no jpeg_cb");
            }
        }
        else {
            if( Debug.LOG )
                Log.e(TAG, "runFakePrecapture called with unexpected flash value: " + camera_settings.flash_value);

        }
        state = STATE_WAITING_FAKE_PRECAPTURE_START;
        precapture_state_change_time_ms = System.currentTimeMillis();
        try {
            setRepeatingRequest();
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to start fake precapture");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            jpeg_cb = null;
            if( take_picture_error_cb != null ) {
                take_picture_error_cb.onError();
                take_picture_error_cb = null;
                return;
            }
        }
    }

    /** Used in use_fake_precapture mode when flash is auto, this returns whether we fire the flash.
     *  If the decision was recently calculated, we return that same decision - used to fix problem that if
     *  we fire flash during autofocus (for autofocus mode), we don't then want to decide the scene is too
     *  bright to not need flash for taking photo!
     */
    private boolean fireAutoFlash() {
        if( Debug.LOG )
            Log.d(TAG, "fireAutoFlash");
        long time_now = System.currentTimeMillis();
        final long cache_time_ms = 3000; // needs to be at least the time of a typical autoflash, see comment for this function above
        if( fake_precapture_use_flash_time_ms != -1 && time_now - fake_precapture_use_flash_time_ms < cache_time_ms ) {
            if( Debug.LOG )
                Log.d(TAG, "use recent decision: " + fake_precapture_use_flash);
            return fake_precapture_use_flash;
        }
        if( Debug.LOG && fake_precapture_use_flash_time_ms != -1 )
            Log.d(TAG, "time since last flash auto decision: " + (time_now - fake_precapture_use_flash_time_ms));
        fake_precapture_use_flash_time_ms = time_now;
        /** iso_threshold fine-tuned for Nexus 6 - front camera ISO never goes above 805, but a threshold of 700 is too low
         */
        int iso_threshold = camera_settings.flash_value.equals("flash_frontscreen_auto") ? 750 : 1000;
        fake_precapture_use_flash = capture_result_has_iso && capture_result_iso >= iso_threshold;
        if( Debug.LOG ) {
            Log.d(TAG, "fake_precapture_use_flash: " + fake_precapture_use_flash);
            Log.d(TAG, "    ISO was: " + capture_result_iso);
        }
        return fake_precapture_use_flash;
    }

    @Override
    public void takePicture(final PictureCallback picture, final ErrorCallback error) {
        if( Debug.LOG )
            Log.d(TAG, "takePicture");
        if( camera == null || captureSession == null ) {
            if( Debug.LOG )
                Log.d(TAG, "no camera or capture session");
            error.onError();
            return;
        }
        // we store as two identical callbacks, so we can independently set each to null as the two callbacks occur
        this.jpeg_cb = picture;
        if( imageReaderRaw != null )
            this.raw_cb = picture;
        else
            this.raw_cb = null;
        this.take_picture_error_cb = error;
        this.fake_precapture_torch_performed = false; // just in case still on?
        if( !ready_for_capture ) {
            if( Debug.LOG )
                Log.e(TAG, "takePicture: not ready for capture!");
            //throw new RuntimeException(); // debugging
        }

        {
            if( Debug.LOG ) {
                Log.d(TAG, "current flash value: " + camera_settings.flash_value);
                Log.d(TAG, "use_fake_precapture_mode: " + use_fake_precapture_mode);
            }
            // Don't need precapture if flash off or torch
            // And currently has_iso manual mode doesn't support flash - but just in case that's changed later, we still probably don't want to be doing a precapture...
            if( camera_settings.has_iso || camera_settings.flash_value.equals("flash_off") || camera_settings.flash_value.equals("flash_torch") ) {
                takePictureAfterPrecapture();
            }
            else if( use_fake_precapture_mode ) {
                // fake precapture works by turning on torch (or using a "front screen flash"), so we can't use the camera's own decision for flash auto
                // instead we check the current ISO value
                boolean auto_flash = camera_settings.flash_value.equals("flash_auto") || camera_settings.flash_value.equals("flash_frontscreen_auto");
                if( auto_flash && !fireAutoFlash() ) {
                    if( Debug.LOG )
                        Log.d(TAG, "fake precapture flash auto: seems bright enough to not need flash");
                    takePictureAfterPrecapture();
                }
                else {
                    runFakePrecapture();
                }
            }
            else {
                runPrecapture();
            }
        }

		/*camera_settings.setupBuilder(previewBuilder, false);
    	previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
		state = STATE_WAITING_AUTOFOCUS;
		precapture_started = -1;
    	//capture();
    	setRepeatingRequest();*/
    }

    @Override
    public void setDisplayOrientation(int degrees) {
        // for Controller2, the preview display orientation is handled via the TextureView's transform
        if( Debug.LOG )
            Log.d(TAG, "setDisplayOrientation not supported by this API");
        throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
    }

    @Override
    public int getDisplayOrientation() {
        if( Debug.LOG )
            Log.d(TAG, "getDisplayOrientation not supported by this API");
        throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
    }

    @Override
    public int getCameraOrientation() {
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    @Override
    public boolean isFrontFacing() {
        return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
    }

    @Override
    public void unlock() {
        // do nothing at this stage
    }

    @Override
    public void initVideoRecorderPrePrepare(MediaRecorder video_recorder) {
        // if we change where we play the START_VIDEO_RECORDING sound, make sure it can't be heard in resultant video
        if( sounds_enabled )
            media_action_sound.play(MediaActionSound.START_VIDEO_RECORDING);
    }

    @Override
    public void initVideoRecorderPostPrepare(MediaRecorder video_recorder) throws ControllerException {
        if( Debug.LOG )
            Log.d(TAG, "initVideoRecorderPostPrepare");
        try {
            if( Debug.LOG )
                Log.d(TAG, "obtain video_recorder surface");
            if( Debug.LOG )
                Log.d(TAG, "done");
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
            camera_settings.setupBuilder(previewBuilder, false);
            createCaptureSession(video_recorder);
        }
        catch(CameraAccessException e) {
            if( Debug.LOG ) {
                Log.e(TAG, "failed to create capture request for video");
                Log.e(TAG, "reason: " + e.getReason());
                Log.e(TAG, "message: " + e.getMessage());
            }
            e.printStackTrace();
            throw new ControllerException();
        }
    }

    @Override
    public void reconnect() throws ControllerException {
        if( Debug.LOG )
            Log.d(TAG, "reconnect");
        // if we change where we play the STOP_VIDEO_RECORDING sound, make sure it can't be heard in resultant video
        if( sounds_enabled )
            media_action_sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
        createPreviewRequest();
        createCaptureSession(null);
		/*if( Debug.LOG )
			Log.d(TAG, "add preview surface to previewBuilder");
    	Surface surface = getPreviewSurface();
		previewBuilder.addTarget(surface);*/
        //setRepeatingRequest();
    }

    @Override
    public String getParametersString() {
        return null;
    }

    @Override
    public boolean captureResultIsAEScanning() {
        return capture_result_is_ae_scanning;
    }

    @Override
    public boolean captureResultHasIso() {
        return capture_result_has_iso;
    }

    @Override
    public int captureResultIso() {
        return capture_result_iso;
    }

    @Override
    public boolean captureResultHasExposureTime() {
        return capture_result_has_exposure_time;
    }

    @Override
    public long captureResultExposureTime() {
        return capture_result_exposure_time;
    }

    @Override
    public boolean captureResultHasFrameDuration() {
        return capture_result_has_frame_duration;
    }

    @Override
    public long captureResultFrameDuration() {
        return capture_result_frame_duration;
    }

    @Override
    public boolean captureResultHasFocusDistance() {
        return capture_result_has_focus_distance;
    }

    @Override
    public float captureResultFocusDistanceMin() {
        return capture_result_focus_distance_min;
    }

    @Override
    public float captureResultFocusDistanceMax() {
        return capture_result_focus_distance_max;
    }

    private CameraCaptureSession.CaptureCallback previewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private long last_process_frame_number = 0;
        private int last_af_state = -1;

        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            if( request.getTag() == RequestTag.CAPTURE ) {
                if( Debug.LOG )
                    Log.d(TAG, "onCaptureStarted: capture");
                // n.b., we don't play the shutter sound here, as it typically sounds "too late"
            }
        }

        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
			/*if( Debug.LOG )
				Log.d(TAG, "onCaptureProgressed");*/
            process(request, partialResult);
            super.onCaptureProgressed(session, request, partialResult); // API docs say this does nothing, but call it just to be safe (as with Google Camera)
        }

        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			/*if( Debug.LOG )
				Log.d(TAG, "onCaptureCompleted");*/
            process(request, result);
            processCompleted(request, result);
            super.onCaptureCompleted(session, request, result); // API docs say this does nothing, but call it just to be safe (as with Google Camera)
        }

        /** Processes either a partial or total result.
         */
        private void process(CaptureRequest request, CaptureResult result) {
			/*if( Debug.LOG )
			Log.d(TAG, "process, state: " + state);*/
            if( result.getFrameNumber() < last_process_frame_number ) {
				/*if( Debug.LOG )
					Log.d(TAG, "processAF discarded outdated frame " + result.getFrameNumber() + " vs " + last_process_frame_number);*/
                return;
            }
            last_process_frame_number = result.getFrameNumber();

            // use Integer instead of int, so can compare to null: Google Play crashes confirmed that this can happen; Google Camera also ignores cases with null af state
            Integer af_state = result.get(CaptureResult.CONTROL_AF_STATE);
            if( af_state != null && af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ) {
				/*if( Debug.LOG )
					Log.d(TAG, "not ready for capture: " + af_state);*/
                ready_for_capture = false;
            }
            else {
				/*if( Debug.LOG )
					Log.d(TAG, "ready for capture: " + af_state);*/
                ready_for_capture = true;
                if( autofocus_cb != null && focusIsContinuous() ) {
                    Integer focus_mode = previewBuilder.get(CaptureRequest.CONTROL_AF_MODE);
                    if( focus_mode != null && focus_mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ) {
                        if( Debug.LOG )
                            Log.d(TAG, "call autofocus callback, as continuous mode and not focusing: " + af_state);
                        // need to check af_state != null, I received Google Play crash in 1.33 where it was null
                        boolean focus_success = af_state != null && ( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED );
                        if( Debug.LOG ) {
                            if( focus_success )
                                Log.d(TAG, "autofocus success");
                            else
                                Log.d(TAG, "autofocus failed");
                            if( af_state == null )
                                Log.e(TAG, "continuous focus mode but af_state is null");
                            else
                                Log.d(TAG, "af_state: " + af_state);
                        }
                        autofocus_cb.onAutoFocus(focus_success);
                        autofocus_cb = null;
                    }
                }
            }

			/*if( Debug.LOG ) {
				if( autofocus_cb == null ) {
					if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED )
						Log.d(TAG, "processAF: autofocus success but no callback set");
					else if( af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED )
						Log.d(TAG, "processAF: autofocus failed but no callback set");
				}
			}*/
            // CONTROL_AE_STATE can be null on some devices, so as with af_state, use Integer
            Integer ae_state = result.get(CaptureResult.CONTROL_AE_STATE);
			/*if( Debug.LOG ) {
				if( ae_state == null )
					Log.d(TAG, "CONTROL_AE_STATE is null");
				else if( ae_state == CaptureResult.CONTROL_AE_STATE_INACTIVE )
					Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_INACTIVE");
				else if( ae_state == CaptureResult.CONTROL_AE_STATE_SEARCHING )
					Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_SEARCHING");
				else if( ae_state == CaptureResult.CONTROL_AE_STATE_CONVERGED )
					Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_CONVERGED");
				else if( ae_state == CaptureResult.CONTROL_AE_STATE_LOCKED )
					Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_LOCKED");
				else if( ae_state == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED )
					Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_FLASH_REQUIRED");
				else if( ae_state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE )
					Log.d(TAG, "CONTROL_AE_STATE = CONTROL_AE_STATE_PRECAPTURE");
				else
					Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
			}*/
            if( ae_state != null && ae_state == CaptureResult.CONTROL_AE_STATE_SEARCHING ) {
				/*if( Debug.LOG && !capture_result_is_ae_scanning )
					Log.d(TAG, "ae_state now searching");*/
                capture_result_is_ae_scanning = true;
            }
            else {
				/*if( Debug.LOG && capture_result_is_ae_scanning )
					Log.d(TAG, "ae_state stopped searching");*/
                capture_result_is_ae_scanning = false;
            }

            if( state == STATE_NORMAL ) {
                // do nothing
            }
            else if( state == STATE_WAITING_AUTOFOCUS ) {
                if( af_state == null ) {
                    // autofocus shouldn't really be requested if af not available, but still allow this rather than getting stuck waiting for autofocus to complete
                    if( Debug.LOG )
                        Log.e(TAG, "waiting for autofocus but af_state is null");
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    if( autofocus_cb != null ) {
                        autofocus_cb.onAutoFocus(false);
                        autofocus_cb = null;
                    }
                }
                else if( af_state != last_af_state ) {
                    // check for autofocus completing
                    // need to check that af_state != last_af_state, except for continuous focus mode where if we're already focused, should return immediately
                    if( af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                            af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED
                            ) {
                        boolean focus_success = af_state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
                        if( Debug.LOG ) {
                            if( focus_success )
                                Log.d(TAG, "onCaptureCompleted: autofocus success");
                            else
                                Log.d(TAG, "onCaptureCompleted: autofocus failed");
                            Log.d(TAG, "af_state: " + af_state);
                        }
                        state = STATE_NORMAL;
                        precapture_state_change_time_ms = -1;
                        if( use_fake_precapture_mode && fake_precapture_torch_focus_performed ) {
                            if( Debug.LOG )
                                Log.d(TAG, "turn off torch after focus (fake precapture code)");
                            fake_precapture_torch_focus_performed = false;
                            camera_settings.setAEMode(previewBuilder, false);
                            try {
                                setRepeatingRequest();
                            }
                            catch(CameraAccessException e) {
                                if( Debug.LOG ) {
                                    Log.e(TAG, "failed to set repeating request to turn off torch after autofocus");
                                    Log.e(TAG, "reason: " + e.getReason());
                                    Log.e(TAG, "message: " + e.getMessage());
                                }
                                e.printStackTrace();
                            }
                        }
                        if( autofocus_cb != null ) {
                            autofocus_cb.onAutoFocus(focus_success);
                            autofocus_cb = null;
                        }
                    }
                }
            }
            else if( state == STATE_WAITING_PRECAPTURE_START ) {
                if( Debug.LOG )
                    Log.d(TAG, "waiting for precapture start...");
                if( Debug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                }
                if( ae_state == null || ae_state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE /*|| ae_state == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED*/ ) {
                    // we have to wait for CONTROL_AE_STATE_PRECAPTURE; if we allow CONTROL_AE_STATE_FLASH_REQUIRED, then on Nexus 6 at least we get poor quality results with flash:
                    // varying levels of brightness, sometimes too bright or too dark, sometimes with blue tinge, sometimes even with green corruption
                    if( Debug.LOG ) {
                        Log.d(TAG, "precapture started after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_WAITING_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_start_timeout_c ) {
                    // hack - give up waiting - sometimes we never get a CONTROL_AE_STATE_PRECAPTURE so would end up stuck
                    if( Debug.LOG ) {
                        Log.e(TAG, "precapture start timeout");
                    }
                    count_precapture_timeout++;
                    state = STATE_WAITING_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
            }
            else if( state == STATE_WAITING_PRECAPTURE_DONE ) {
                if( Debug.LOG )
                    Log.d(TAG, "waiting for precapture done...");
                if( Debug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                }
                if( ae_state == null || ae_state != CaptureResult.CONTROL_AE_STATE_PRECAPTURE ) {
                    if( Debug.LOG ) {
                        Log.d(TAG, "precapture completed after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_done_timeout_c ) {
                    // just in case
                    if( Debug.LOG ) {
                        Log.e(TAG, "precapture done timeout");
                    }
                    count_precapture_timeout++;
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
            }
            else if( state == STATE_WAITING_FAKE_PRECAPTURE_START ) {
                if( Debug.LOG )
                    Log.d(TAG, "waiting for fake precapture start...");
                if( Debug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                }
                if( ae_state == null || ae_state == CaptureResult.CONTROL_AE_STATE_SEARCHING ) {
                    if( Debug.LOG ) {
                        Log.d(TAG, "fake precapture started after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_WAITING_FAKE_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_start_timeout_c ) {
                    // just in case
                    if( Debug.LOG ) {
                        Log.e(TAG, "fake precapture start timeout");
                    }
                    count_precapture_timeout++;
                    state = STATE_WAITING_FAKE_PRECAPTURE_DONE;
                    precapture_state_change_time_ms = System.currentTimeMillis();
                }
            }
            else if( state == STATE_WAITING_FAKE_PRECAPTURE_DONE ) {
                if( Debug.LOG )
                    Log.d(TAG, "waiting for fake precapture done...");
                if( Debug.LOG ) {
                    if( ae_state != null )
                        Log.d(TAG, "CONTROL_AE_STATE = " + ae_state);
                    else
                        Log.d(TAG, "CONTROL_AE_STATE is null");
                    Log.d(TAG, "ready_for_capture? " + ready_for_capture);
                }
                // wait for af and ae scanning to end (need to check af too, as in continuous focus mode, a focus may start again after switching torch on for the fake precapture)
                if( ready_for_capture && ( ae_state == null || ae_state != CaptureResult.CONTROL_AE_STATE_SEARCHING)  ) {
                    if( Debug.LOG ) {
                        Log.d(TAG, "fake precapture completed after: " + (System.currentTimeMillis() - precapture_state_change_time_ms));
                    }
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
                else if( precapture_state_change_time_ms != -1 && System.currentTimeMillis() - precapture_state_change_time_ms > precapture_done_timeout_c ) {
                    // sometimes camera can take a while to stop ae/af scanning, better to just go ahead and take photo
                    if( Debug.LOG ) {
                        Log.e(TAG, "fake precapture done timeout");
                    }
                    count_precapture_timeout++;
                    state = STATE_NORMAL;
                    precapture_state_change_time_ms = -1;
                    takePictureAfterPrecapture();
                }
            }

            if( af_state != null && af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af_state != last_af_state ) {
                if( Debug.LOG )
                    Log.d(TAG, "continuous focusing started");
                if( continuous_focus_move_callback != null ) {
                    continuous_focus_move_callback.onContinuousFocusMove(true);
                }
            }
            else if( af_state != null && last_af_state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN && af_state != last_af_state ) {
                if( Debug.LOG )
                    Log.d(TAG, "continuous focusing stopped");
                if( continuous_focus_move_callback != null ) {
                    continuous_focus_move_callback.onContinuousFocusMove(false);
                }
            }

            if( af_state != null && af_state != last_af_state ) {
                if( Debug.LOG )
                    Log.d(TAG, "CONTROL_AF_STATE changed from " + last_af_state + " to " + af_state);
                last_af_state = af_state;
            }
        }

        /** Processes a total result.
         */
        private void processCompleted(CaptureRequest request, CaptureResult result) {
			/*if( Debug.LOG )
				Log.d(TAG, "processCompleted");*/

            if( result.get(CaptureResult.SENSOR_SENSITIVITY) != null ) {
                capture_result_has_iso = true;
                capture_result_iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
				/*if( Debug.LOG )
					Log.d(TAG, "capture_result_iso: " + capture_result_iso);*/
                if( camera_settings.has_iso && camera_settings.iso != capture_result_iso ) {
                    // ugly hack: problem that when we start recording video (video_recorder.start() call), this often causes the ISO setting to reset to the wrong value!
                    // seems to happen more often with shorter exposure time
                    // seems to happen on other camera apps with Camera2 API too
                    // this workaround still means a brief flash with incorrect ISO, but is best we can do for now!
                    if( Debug.LOG ) {
                        Log.d(TAG, "ISO " + capture_result_iso + " different to requested ISO " + camera_settings.iso);
                        Log.d(TAG, "    requested ISO was: " + request.get(CaptureRequest.SENSOR_SENSITIVITY));
                        Log.d(TAG, "    requested AE mode was: " + request.get(CaptureRequest.CONTROL_AE_MODE));
                    }
                    try {
                        setRepeatingRequest();
                    }
                    catch(CameraAccessException e) {
                        if( Debug.LOG ) {
                            Log.e(TAG, "failed to set repeating request after ISO hack");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                    }
                }
            }
            else {
                capture_result_has_iso = false;
            }
            if( result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ) {
                capture_result_has_exposure_time = true;
                capture_result_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            }
            else {
                capture_result_has_exposure_time = false;
            }
            if( result.get(CaptureResult.SENSOR_FRAME_DURATION) != null ) {
                capture_result_has_frame_duration = true;
                capture_result_frame_duration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
            }
            else {
                capture_result_has_frame_duration = false;
            }
			/*if( Debug.LOG ) {
				if( result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null ) {
					long capture_result_exposure_time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
					Log.d(TAG, "capture_result_exposure_time: " + capture_result_exposure_time);
				}
				if( result.get(CaptureResult.SENSOR_FRAME_DURATION) != null ) {
					long capture_result_frame_duration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
					Log.d(TAG, "capture_result_frame_duration: " + capture_result_frame_duration);
				}
			}*/
            if( result.get(CaptureResult.LENS_FOCUS_RANGE) != null ) {
                Pair<Float, Float> focus_range = result.get(CaptureResult.LENS_FOCUS_RANGE);
				/*if( Debug.LOG ) {
					Log.d(TAG, "capture result focus range: " + focus_range.first + " to " + focus_range.second);
				}*/
                capture_result_has_focus_distance = true;
                capture_result_focus_distance_min = focus_range.first;
                capture_result_focus_distance_max = focus_range.second;
            }
            else {
                capture_result_has_focus_distance = false;
            }

            if( face_detection_listener != null && previewBuilder != null && previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) != null && previewBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE) == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL ) {
                Rect sensor_rect = getViewableRect();
                android.hardware.camera2.params.Face [] camera_faces = result.get(CaptureResult.STATISTICS_FACES);
                if( camera_faces != null ) {
                    Controller.Face [] faces = new Controller.Face[camera_faces.length];
                    for(int i=0;i<camera_faces.length;i++) {
                        faces[i] = convertFromCameraFace(sensor_rect, camera_faces[i]);
                    }
                    face_detection_listener.onFaceDetection(faces);
                }
            }

            if( push_repeating_request_when_torch_off && push_repeating_request_when_torch_off_id == request ) {
                if( Debug.LOG )
                    Log.d(TAG, "received push_repeating_request_when_torch_off");
                Integer flash_state = result.get(CaptureResult.FLASH_STATE);
                if( Debug.LOG ) {
                    if( flash_state != null )
                        Log.d(TAG, "flash_state: " + flash_state);
                    else
                        Log.d(TAG, "flash_state is null");
                }
                if( flash_state != null && flash_state == CaptureResult.FLASH_STATE_READY ) {
                    push_repeating_request_when_torch_off = false;
                    push_repeating_request_when_torch_off_id = null;
                    try {
                        setRepeatingRequest();
                    }
                    catch(CameraAccessException e) {
                        if( Debug.LOG ) {
                            Log.e(TAG, "failed to set flash [from torch/flash off hack]");
                            Log.e(TAG, "reason: " + e.getReason());
                            Log.e(TAG, "message: " + e.getMessage());
                        }
                        e.printStackTrace();
                    }
                }
            }
			/*if( push_set_ae_lock && push_set_ae_lock_id == request ) {
				if( Debug.LOG )
					Log.d(TAG, "received push_set_ae_lock");
				// hack - needed to fix bug on Nexus 6 where auto-exposure sometimes locks when taking a photo of bright scene with flash on!
            	// this doesn't completely resolve the issue, but seems to make it far less common; also when it does happen, taking another photo usually fixes it
				push_set_ae_lock = false;
				push_set_ae_lock_id = null;
				camera_settings.setAutoExposureLock(previewBuilder);
				try {
					setRepeatingRequest();
				}
				catch(CameraAccessException e) {
					if( Debug.LOG ) {
						Log.e(TAG, "failed to set ae lock [from ae lock hack]");
						Log.e(TAG, "reason: " + e.getReason());
						Log.e(TAG, "message: " + e.getMessage());
					}
					e.printStackTrace();
				}
			}*/

            if( request.getTag() == RequestTag.CAPTURE ) {
                if( Debug.LOG )
                    Log.d(TAG, "capture request completed");
                if( onRawImageAvailableListener != null ) {
                    if( test_wait_capture_result ) {
                        // for RAW capture, we require the capture result before creating DngCreator
                        // but for testing purposes, we need to test the possibility where onImageAvailable() for
                        // the RAW image is called before we receive the capture result here
                        try {
                            if( Debug.LOG )
                                Log.d(TAG, "test_wait_capture_result: waiting...");
                            Thread.sleep(500); // 200ms is enough to test the problem on Nexus 6, but use 500ms to be sure
                        }
                        catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    onRawImageAvailableListener.setCaptureResult(result);
                }
                // actual parsing of image data is done in the imageReader's OnImageAvailableListener()
                // need to cancel the autofocus, and restart the preview after taking the photo
                // Camera2Basic does a capture then sets a repeating request - do the same here just to be safe
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                if( Debug.LOG )
                    Log.e(TAG, "### reset ae mode");
                String saved_flash_value = camera_settings.flash_value;
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    // same hack as in setFlashValue() - for fake precapture we need to turn off the torch mode that was set, but
                    // at least on Nexus 6, we need to turn to flash_off to turn off the torch!
                    camera_settings.flash_value = "flash_off";
                }
                // if not using fake precapture, not sure if we need to set the ae mode, but the AE mode is set again in Camera2Basic
                camera_settings.setAEMode(previewBuilder, false);
                // n.b., if capture/setRepeatingRequest throw exception, we don't call the take_picture_error_cb.onError() callback, as the photo should have been taken by this point
                try {
                    capture();
                }
                catch(CameraAccessException e) {
                    if( Debug.LOG ) {
                        Log.e(TAG, "failed to cancel autofocus after taking photo");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                }
                if( use_fake_precapture_mode && fake_precapture_torch_performed ) {
                    // now set up the request to switch to the correct flash value
                    camera_settings.flash_value = saved_flash_value;
                    camera_settings.setAEMode(previewBuilder, false);
                }
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE); // ensure set back to idle
                try {
                    setRepeatingRequest();
                }
                catch(CameraAccessException e) {
                    if( Debug.LOG ) {
                        Log.e(TAG, "failed to start preview after taking photo");
                        Log.e(TAG, "reason: " + e.getReason());
                        Log.e(TAG, "message: " + e.getMessage());
                    }
                    e.printStackTrace();
                    preview_error_cb.onError();
                }
                fake_precapture_torch_performed = false;
            }
        }
    };
}
