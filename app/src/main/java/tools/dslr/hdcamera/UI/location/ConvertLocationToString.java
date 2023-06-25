package tools.dslr.hdcamera.UI.location;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ConvertLocationToString {
    public static String[] getInDegree(double latitude, double longitude) {
        String[] result = new String[2];
        try {
            int latSeconds = (int) Math.round(latitude * 3600);
            int latDegrees = latSeconds / 3600;
            latSeconds = Math.abs(latSeconds % 3600);
            int latMinutes = latSeconds / 60;
            latSeconds %= 60;

            int longSeconds = (int) Math.round(longitude * 3600);
            int longDegrees = longSeconds / 3600;
            longSeconds = Math.abs(longSeconds % 3600);
            int longMinutes = longSeconds / 60;
            longSeconds %= 60;
            String latDegree = latDegrees >= 0 ? "N" : "S";
            String lonDegrees = longDegrees >= 0 ? "E" : "W";
            result[0] = Math.abs(latDegrees) + "\u00B0" + latMinutes + "'" + latSeconds + " " + latDegree;
            result[1] = Math.abs(longDegrees) + "\u00B0" + longMinutes + "'" + longSeconds + " " + lonDegrees;
            return result;

        } catch (Exception e) {
            return new String[]{"" + String.format("%8.5f", latitude) + "  "
                    + String.format("%8.5f", longitude)};
        }
    }

    public static String[] getInDegree1(double latitude, double longitude) {
        String[] result = new String[2];
        try {
            int latSeconds = (int) Math.round(latitude * 3600);
            int latDegrees = latSeconds / 3600;
            latSeconds = Math.abs(latSeconds % 3600);
            float latMinutes = (float) latSeconds / 60;

            int longSeconds = (int) Math.round(longitude * 3600);
            int longDegrees = longSeconds / 3600;
            longSeconds = Math.abs(longSeconds % 3600);
            float longMinutes = (float) longSeconds / 60;

            result[0] = Math.abs(latDegrees) + "°" + String.format("%.3f", latMinutes);
            result[1] = Math.abs(longDegrees) + "°" + String.format("%.3f", longMinutes);
            return result;

        } catch (Exception e) {
            return new String[]{"" + String.format("%8.5f", latitude) + "  "
                    + String.format("%8.5f", longitude)};
        }
    }
}
