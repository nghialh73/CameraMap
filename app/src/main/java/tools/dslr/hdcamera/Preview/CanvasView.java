package tools.dslr.hdcamera.Preview;

import tools.dslr.hdcamera.Debug;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

/** Overlay for the Preview - this just redirects to Preview.onDraw to do the
 *  work. Only used if using a MyTextureView (if using MySurfaceView, then that
 *  class can handle the onDraw()). TextureViews can't be used for both a
 *  camera preview, and used for drawing on.
 */
public class CanvasView extends View {
    private static final String TAG = "CanvasView";

    private Preview preview = null;
    private int [] measure_spec = new int[2];

    CanvasView(Context context, Bundle savedInstanceState, Preview preview) {
        super(context);
        this.preview = preview;
        if( Debug.LOG ) {
            Log.d(TAG, "new CanvasView");
        }

        // deprecated setting, but required on Android versions prior to 3.0
        //getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated

        final Handler handler = new Handler();
        Runnable tick = new Runnable() {
            public void run() {
				/*if( Debug.LOG )
					Log.d(TAG, "invalidate()");*/
                invalidate();
                handler.postDelayed(this, 100);
            }
        };
        tick.run();
    }

    @Override
    public void onDraw(Canvas canvas) {
		/*if( Debug.LOG )
			Log.d(TAG, "onDraw()");*/
        preview.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
        super.onMeasure(measure_spec[0], measure_spec[1]);
    }
}
