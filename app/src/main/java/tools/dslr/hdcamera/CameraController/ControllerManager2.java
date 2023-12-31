package tools.dslr.hdcamera.CameraController;

import tools.dslr.hdcamera.Debug;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Log;

/** Provides support using Android 5's Camera 2 API
 *  android.hardware.camera2.*.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ControllerManager2 extends ControllerManager {
    private static final String TAG = "ControllerManager2";

    private Context context = null;

    public ControllerManager2(Context context) {
        this.context = context;
    }

    @Override
    public int getNumberOfCameras() {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            return manager.getCameraIdList().length;
        }
        catch(CameraAccessException e) {
            if( Debug.LOG )
                Log.e(TAG, "exception trying to get camera ids");
            e.printStackTrace();
        }
        catch(AssertionError e) {
            // had reported java.lang.AssertionError on Google Play, "Expected to get non-empty characteristics" from CameraManager.getOrCreateDeviceIdListLocked(CameraManager.java:465)
            // yes, in theory we shouldn't catch AssertionError as it represents a programming error, however it's a programming error in the camera driver (a condition they thought couldn't happen)
            if( Debug.LOG )
                Log.e(TAG, "assertion error trying to get camera ids");
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean isFrontFacing(int cameraId) {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraIdS = manager.getCameraIdList()[cameraId];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
            return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
        }
        catch(CameraAccessException e) {
            if( Debug.LOG )
                Log.e(TAG, "exception trying to get camera characteristics");
            e.printStackTrace();
        }
        return false;
    }

    /* Returns true if the device supports the required hardware level, or better.
     * From http://msdx.github.io/androiddoc/docs//reference/android/hardware/camera2/CameraCharacteristics.html#INFO_SUPPORTED_HARDWARE_LEVEL
     * From Android N, higher levels than "FULL" are possible, that will have higher integer values.
     * Also see https://sourceforge.net/p/opencamera/tickets/141/ .
     */
    private boolean isHardwareLevelSupported(CameraCharacteristics c, int requiredLevel) {
        int deviceLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if( Debug.LOG ) {
            if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY )
                Log.d(TAG, "Camera has LEGACY Camera2 support");
            else if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED )
                Log.d(TAG, "Camera has LIMITED Camera2 support");
            else if( deviceLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL )
                Log.d(TAG, "Camera has FULL Camera2 support");
            else
                Log.d(TAG, "Camera has unknown Camera2 support: " + deviceLevel);
        }
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    /* Rather than allowing Camera2 API on all Android 5+ devices, we restrict it to cases where all cameras have at least LIMITED support.
     * (E.g., Nexus 6 has FULL support on back camera, LIMITED support on front camera.)
     * For now, devices with only LEGACY support should still with the old API.
     */
    public boolean allowCamera2Support(int cameraId) {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraIdS = manager.getCameraIdList()[cameraId];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIdS);
            boolean supported = isHardwareLevelSupported(characteristics, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED);
            return supported;
        }
        catch(CameraAccessException e) {
            if( Debug.LOG )
                Log.e(TAG, "exception trying to get camera characteristics");
            e.printStackTrace();
        }
        catch(NumberFormatException e) {
            if( Debug.LOG )
                Log.e(TAG, "exception trying to get camera characteristics");
            // I've seen Google Play NumberFormatException crashes from CameraManager.getCameraCharacteristics on Asus ZenFones with Android 5.0
            e.printStackTrace();
        }
        return false;
    }
}
