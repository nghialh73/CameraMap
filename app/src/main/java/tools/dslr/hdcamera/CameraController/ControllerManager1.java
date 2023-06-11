package tools.dslr.hdcamera.CameraController;

import tools.dslr.hdcamera.Debug;

import android.hardware.Camera;
import android.util.Log;

/** Provides support using Android's original camera API
 *  android.hardware.Camera.
 */
@SuppressWarnings("deprecation")
public class ControllerManager1 extends ControllerManager {
    private static final String TAG = "ControllerManager1";
    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    public boolean isFrontFacing(int cameraId) {
        try {
            Camera.CameraInfo camera_info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, camera_info);
            return (camera_info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        }
        catch(RuntimeException e) {
            // Had a report of this crashing on Galaxy Nexus - may be device specific issue, see http://stackoverflow.com/questions/22383708/java-lang-runtimeexception-fail-to-get-camera-info
            // but good to catch it anyway
            if( Debug.LOG )
                Log.d(TAG, "failed to set parameters");
            e.printStackTrace();
            return false;
        }
    }
}
