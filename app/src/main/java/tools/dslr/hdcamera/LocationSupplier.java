package tools.dslr.hdcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

interface LocationAddressListener {
    void getLocationAddress(double lat, double lon, String address);
    void getLocation(double lat, double lon);
}

/**
 * Handles listening for GPS location (both coarse and fine).
 */
public class LocationSupplier {
    private static final String TAG = "LocationSupplier";

    private Context con = null;
    private LocationManager locationManager = null;
    private MyLocationListener[] locationListeners = null;

    LocationSupplier(Context con) {
        this.con = con;
        locationManager = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
    }

    public static String locationToDMS(double coord) {
        String sign = (coord < 0.0) ? "-" : "";
        boolean is_zero = true;
        coord = Math.abs(coord);
        int intPart = (int) coord;
        is_zero = is_zero && (intPart == 0);
        String degrees = String.valueOf(intPart);
        double mod = coord - intPart;

        coord = mod * 60;
        intPart = (int) coord;
        is_zero = is_zero && (intPart == 0);
        mod = coord - intPart;
        String minutes = String.valueOf(intPart);

        coord = mod * 60;
        intPart = (int) coord;
        is_zero = is_zero && (intPart == 0);
        String seconds = String.valueOf(intPart);

        if (is_zero) {
            // so we don't show -ve for coord that is -ve but smaller than 1"
            sign = "";
        }

        // use unicode rather than degrees symbol, due to Android Studio warning - see https://sourceforge.net/p/opencamera/tickets/107/
        return sign + degrees + "\u00b0" + minutes + "'" + seconds + "\"";
    }

    @SuppressLint("MissingPermission")
    public Location getLocation() {
        // returns null if not available
        if (locationListeners == null)
            return null;
        // location listeners should be stored in order best to worst
        for (int i = 0; i < locationListeners.length; i++) {
            Location location = locationListeners[i].getLocation();
            if (location != null) return location;
            if (location == null)
                location = locationManager.getLastKnownLocation(i == 0 ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER);
            return location;
        }
        return null;
    }

    public void getAddressFromLocation(double lat, double lng , LocationAddressListener listener) {
        Geocoder geocoder = new Geocoder(con, Locale.getDefault());
        String addressString = "";

        try {
            List<Address> addressList = geocoder.getFromLocation(lat, lng, 1);

            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                StringBuilder sb = new StringBuilder();

                if (address.getPremises() != null)
                    sb.append(address.getPremises()).append(", ");
                if (address.getFeatureName() != null)
                    sb.append(address.getFeatureName()).append(", ");
                if (address.getSubLocality() != null)
                    sb.append(address.getSubLocality()).append(", ");
                if (address.getLocality() != null)
                    sb.append(address.getLocality()).append(", ");
                if (address.getSubAdminArea() != null)
                    sb.append(address.getSubAdminArea()).append(", ");
                if (address.getAdminArea() != null)
                    sb.append(address.getAdminArea()).append(", ");
                if (address.getCountryName() != null)
                    sb.append(address.getCountryName());
                // StringBuilder sb is converted into a string
                // and this value is assigned to the
                // initially declared addressString string.
                addressString = sb.toString();
                listener.getLocationAddress(lat, lng, addressString);
            }
        } catch (IOException e) {
            if (Debug.LOG) {
                Log.e(TAG, "Unable connect to Geocoder");
            }
        }
    }

    // returns false if location permission not available for either coarse or fine
    boolean setupLocationListener(LocationAddressListener listenerAddress) {
        if (Debug.LOG)
            Log.d(TAG, "setupLocationListener");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(con);
        // Define a listener that responds to location updates
        // we only set it up if store_location is true, to avoid unnecessarily wasting battery
        boolean store_location = sharedPreferences.getBoolean(Keys.getLocationPreferenceKey(), true);
        if (store_location && locationListeners == null) {
            // Note, ContextCompat.checkSelfPermission is meant to handle being called on any Android version, i.e., pre
            // Android Marshmallow it should return true as permissions are set an installation, and can't be switched off by
            // the user. However on Galaxy Nexus Android 4.3 and Nexus 7 (2013) Android 5.1.1, ACCESS_COARSE_LOCATION returns
            // PERMISSION_DENIED! So we keep the checks to Android Marshmallow or later (where we need them), and avoid
            // checking behaviour for earlier devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Debug.LOG)
                    Log.d(TAG, "check for location permissions");
                boolean has_coarse_location_permission = ContextCompat.checkSelfPermission(con, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean has_fine_location_permission = ContextCompat.checkSelfPermission(con, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (Debug.LOG) {
                    Log.d(TAG, "has_coarse_location_permission? " + has_coarse_location_permission);
                    Log.d(TAG, "has_fine_location_permission? " + has_fine_location_permission);
                }
                if (!has_coarse_location_permission || !has_fine_location_permission) {
                    if (Debug.LOG)
                        Log.d(TAG, "location permission not available");
                    // return false, which tells caller to request permission - we'll call this function again if permission is granted
                    return false;
                }
            }

            locationListeners = new MyLocationListener[2];
            locationListeners[0] = new MyLocationListener(listenerAddress);
            locationListeners[1] = new MyLocationListener(listenerAddress);

            // location listeners should be stored in order best to worst
            // also see https://sourceforge.net/p/opencamera/tickets/1/ - need to check provider is available
            // now also need to check for permissions - need to support devices that might have one but not both of fine and coarse permissions supplied
            if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListeners[1]);
                if (Debug.LOG)
                    Log.d(TAG, "created coarse (network) location listener");
            } else {
                if (Debug.LOG)
                    Log.e(TAG, "don't have a NETWORK_PROVIDER");
            }
            if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListeners[0]);
                if (Debug.LOG)
                    Log.d(TAG, "created fine (gps) location listener");
            } else {
                if (Debug.LOG )
                    Log.e(TAG, "don't have a GPS_PROVIDER");
            }
        }
        else if( !store_location ) {
            freeLocationListeners();
        }
        return true;
    }

    void freeLocationListeners() {
        if( Debug.LOG )
            Log.d(TAG, "freeLocationListeners");
        if( locationListeners != null ) {
            for(int i=0;i<locationListeners.length;i++) {
                locationManager.removeUpdates(locationListeners[i]);
                locationListeners[i] = null;
            }
            locationListeners = null;
        }
    }

    // for testing:

    public boolean testHasReceivedLocation() {
        if( locationListeners == null )
            return false;
        for(int i=0;i<locationListeners.length;i++) {
            if( locationListeners[i].has_received_location)
                return true;
        }
        return false;
    }

    public boolean hasLocationListeners() {
        if( this.locationListeners == null)
            return false;
        if (this.locationListeners.length != 2)
            return false;
        for (int i = 0; i < this.locationListeners.length; i++) {
            if (this.locationListeners[i] == null)
                return false;
        }
        return true;
    }

    private static class MyLocationListener implements LocationListener {
        public boolean has_received_location = false;
        LocationAddressListener listenerAddress;
        private Location location = null;

        MyLocationListener(LocationAddressListener listenerAddress) {
            this.listenerAddress = listenerAddress;
        }

        Location getLocation() {
            return location;
        }

        @Override
        public void onLocationChanged(Location location) {
            if (Debug.LOG)
                Log.d(TAG, "onLocationChanged");
            this.has_received_location = true;
            // Android camera source claims we need to check lat/long != 0.0d
            if (location.getLatitude() != 0.0d || location.getLongitude() != 0.0d) {
                if (Debug.LOG) {
                    Log.d(TAG, "received location:");
                    Log.d(TAG, "lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy());
                }
                this.location = location;
                listenerAddress.getLocation(location.getLatitude(), location.getLongitude());
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status) {
                case LocationProvider.OUT_OF_SERVICE:
                case LocationProvider.TEMPORARILY_UNAVAILABLE: {
                    if (Debug.LOG) {
                        if (status == LocationProvider.OUT_OF_SERVICE)
                            Log.d(TAG, "location provider out of service");
                        else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
                            Log.d(TAG, "location provider temporarily unavailable");
                    }
                    this.location = null;
                    this.has_received_location = false;
                    break;
                }
                default:
                    break;
            }
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
            if (Debug.LOG)
                Log.d(TAG, "onProviderDisabled");
            this.location = null;
            this.has_received_location = false;
        }
    }
}
