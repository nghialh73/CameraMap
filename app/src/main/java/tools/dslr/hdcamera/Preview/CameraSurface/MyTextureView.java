package tools.dslr.hdcamera.Preview.CameraSurface;

import tools.dslr.hdcamera.CameraController.ControllerException;
import tools.dslr.hdcamera.Debug;
import tools.dslr.hdcamera.CameraController.Controller;
import tools.dslr.hdcamera.Preview.Preview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

/** Provides support for the surface used for the preview, using a TextureView.
 */
public class MyTextureView extends TextureView implements CameraSurface {
    private static final String TAG = "MyTextureView";

    private Preview preview = null;
    private int [] measure_spec = new int[2];

    public MyTextureView(Context context, Bundle savedInstanceState, Preview preview) {
        super(context);
        this.preview = preview;
        if( Debug.LOG ) {
            Log.d(TAG, "new MyTextureView");
        }

        // Install a TextureView.SurfaceTextureListener so we get notified when the
        // underlying surface is created and destroyed.
        this.setSurfaceTextureListener(preview);
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
            camera_controller.setPreviewTexture(this.getSurfaceTexture());
        }
        catch(ControllerException e) {
            if( Debug.LOG )
                Log.e(TAG, "Failed to set preview display: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void setVideoRecorder(MediaRecorder video_recorder) {
        // should be no need to do anything (see documentation for MediaRecorder.setPreviewDisplay())
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return preview.touchEvent(event);
    }

	/*@Override
	public void onDraw(Canvas canvas) {
		preview.draw(canvas);
	}*/

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
        super.onMeasure(measure_spec[0], measure_spec[1]);
    }

    @Override
    public void setTransform(Matrix matrix) {
        super.setTransform(matrix);
    }
}
