package tools.dslr.hdcamera;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

/** Handles the Open Camera lock screen widget. Lock screen widgets are no
 *  longer supported in Android 5 onwards (instead Open Camera can be launched
 *  from the lock screen using the standard camera icon), but this is kept here
 *  for older Android versions.
 */
public class WidgetProvider extends AppWidgetProvider {
    private static final String TAG = "WidgetProvider";

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

            PendingIntent pendingIntent = null;
            // for now, always put up the keyguard if the device is PIN locked etc
			/*SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(con1);
			if( sharedPreferences.getBoolean(HomeActivity.getShowWhenLockedPreferenceKey(), true) ) {
		    	if( Debug.LOG )
		    		Log.d(TAG, "do show above lock screen");
	            Intent intent = new Intent(con1, WidgetProvider.class);
	            intent.setAction("net.sourceforge.gif.LAUNCH_OPEN_CAMERA");
	            pendingIntent = PendingIntent.getBroadcast(con1, 0, intent, 0);
			}
			else*/ {
		    	/*if( Debug.LOG )
		    		Log.d(TAG, "don't show above lock screen");*/
                Intent intent = new Intent(context, HomeActivity.class);
                pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            }

            // Get the layout for the App Widget and attach an on-click listener
            // to the button
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setOnClickPendingIntent(R.id.widget_launch_open_camera, pendingIntent);
			/*if( sharedPreferences.getBoolean(HomeActivity.getShowWhenLockedPreferenceKey(), true) ) {
				views.setTextViewText(R.id.launch_open_camera, "Open Camera (unlocked)");
			}
			else {
				views.setTextViewText(R.id.launch_open_camera, "Open Camera (locked)");
			}*/

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
