package tools.dslr.hdcamera.Preview.CameraSurface;

import tools.dslr.hdcamera.CameraController.Controller;

import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.view.View;

/** Provides support for the surface used for the preview - this can either be
 *  a SurfaceView or a TextureView.
 */
public interface CameraSurface {
    abstract View getView();
    abstract void setPreviewDisplay(Controller camera_controller); // n.b., uses double-dispatch similar to Visitor pattern - behaviour depends on type of CameraSurface and Controller
    abstract void setVideoRecorder(MediaRecorder video_recorder);
    abstract void setTransform(Matrix matrix);
}
