package tools.dslr.hdcamera.Preview.CameraSurface;

import tools.dslr.hdcamera.CameraController.ControllerException;
import tools.dslr.hdcamera.CameraController.Controller;
import tools.dslr.hdcamera.Debug;
import tools.dslr.hdcamera.Preview.Preview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/** Provides support for the surface used for the preview, using a SurfaceView.
 */
public class MySurfaceView extends SurfaceView implements CameraSurface {
    private static final String TAG = "MySurfaceView";

    private Preview preview = null;
    private int [] measure_spec = new int[2];

    @SuppressWarnings("deprecation")
    public
    MySurfaceView(Context context, Bundle savedInstanceState, Preview preview) {
        super(context);
        this.preview = preview;
        if( Debug.LOG ) {
            Log.d(TAG, "new MySurfaceView");
        }

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        getHolder().addCallback(preview);
        // deprecated setting, but required on Android versions prior to 3.0
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void setPreviewDisplay(Controller camera_controller) {
        if( Debug.LOG )
            Log.d(TAG, "setPreviewDisplay");
        try {
            camera_controller.setPreviewDisplay(this.getHolder());
        }
        catch(ControllerException e) {
            if( Debug.LOG )
                Log.e(TAG, "Failed to set preview display: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void setVideoRecorder(MediaRecorder video_recorder) {
        video_recorder.setPreviewDisplay(this.getHolder().getSurface());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return preview.touchEvent(event);
    }

    @Override
    public void onDraw(Canvas canvas) {
        preview.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
        super.onMeasure(measure_spec[0], measure_spec[1]);
    }

    @Override
    public void setTransform(Matrix matrix) {
        if( Debug.LOG )
            Log.d(TAG, "setting transforms not supported for MySurfaceView");
        throw new RuntimeException();
    }
}
