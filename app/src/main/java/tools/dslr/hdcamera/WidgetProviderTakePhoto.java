package tools.dslr.hdcamera;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

/** Handles the Open Camera "take photo" widget. This widget launches Open
 *  Camera, and immediately takes a photo.
 */
public class WidgetProviderTakePhoto extends AppWidgetProvider {
    private static final String TAG = "WidgetProviderTakePhoto";

    // from http://developer.android.com/guide/topics/appwidgets/index.html
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if( Debug.LOG )
            Log.d(TAG, "onUpdate");
        final int N = appWidgetIds.length;
        if( Debug.LOG )
            Log.d(TAG, "N = " + N);

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];
            if( Debug.LOG )
                Log.d(TAG, "appWidgetId: " + appWidgetId);

            Intent intent = new Intent(context, TakePhoto.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

            // Get the layout for the App Widget and attach an on-click listener
            // to the button
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_take_photo);
            views.setOnClickPendingIntent(R.id.widget_take_photo, pendingIntent);

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    /*@Override
	public void onReceive(Context con1, Intent intent) {
    	if( Debug.LOG ) {
    		Log.d(TAG, "onReceive " + intent);
    	}
	    if (intent.getAction().equals("net.sourceforge.gif.LAUNCH_OPEN_CAMERA")) {
	    	if( Debug.LOG )
	    		Log.d(TAG, "Launching HomeActivity");
	        final Intent activity = new Intent(con1, HomeActivity.class);
	        activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        con1.startActivity(activity);
	    	if( Debug.LOG )
	    		Log.d(TAG, "done");
	    }
	    super.onReceive(con1, intent);
	}*/
}
