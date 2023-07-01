package tools.dslr.hdcamera.UI.location;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import tools.dslr.hdcamera.Debug;
import tools.dslr.hdcamera.LocationAddressListener;

@SuppressLint("Registered")
public class LocationServiceManager extends Service implements LocationListener {
    boolean canGetLocation = false;
    public boolean isGPSEnabled = false;
    boolean isNetworkEnabled = false;
    double latitude;
    Location location;
    protected LocationManager locationManager;
    double longitude;
    private final Context mContext;

    LocationAddressListener mListener;
    public LocationServiceManager(Context context, LocationAddressListener listener) {
        this.mContext = context;
        this.mListener = listener;
        getLocation();
    }

    @SuppressLint({"MissingPermission"})
    public Location getLocation() {
        try {
            this.locationManager = (LocationManager) this.mContext.getSystemService(Context.LOCATION_SERVICE);
            this.isGPSEnabled = this.locationManager.isProviderEnabled("gps");
            this.isNetworkEnabled = this.locationManager.isProviderEnabled("network");
            if (this.isGPSEnabled || this.isNetworkEnabled) {
                this.canGetLocation = true;
                if (this.isNetworkEnabled) {
                    this.locationManager.requestLocationUpdates("network", 60000, 10.0f, this);
                    if (this.locationManager != null) {
                        this.location = this.locationManager.getLastKnownLocation("network");
                        if (this.location != null) {
                            this.latitude = this.location.getLatitude();
                            this.longitude = this.location.getLongitude();
                        }
                    }
                }
                if (this.isGPSEnabled && this.location == null) {
                    this.locationManager.requestLocationUpdates("gps", 60000, 10.0f, this);
                    if (!(this.locationManager == null || ContextCompat.checkSelfPermission(this.mContext, "android.permission.ACCESS_FINE_LOCATION") == 0 || ContextCompat.checkSelfPermission(this.mContext, "android.permission.ACCESS_COARSE_LOCATION") == 0)) {
                        this.location = this.locationManager.getLastKnownLocation("gps");
                        if (this.location != null) {
                            this.latitude = this.location.getLatitude();
                            this.longitude = this.location.getLongitude();
                        }
                    }
                }
                return this.location;
            }
            return this.location;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public double getLatitude() {
        if (this.location != null) {
            this.latitude = this.location.getLatitude();
        }
        return this.latitude;
    }

    public double getLongitude() {
        if (this.location != null) {
            this.longitude = this.location.getLongitude();
        }
        return this.longitude;
    }

    public void getAddressFromLocation(double lat, double lng) {
        Geocoder geocoder = new Geocoder(mContext);
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

                addressString = address.getAddressLine(0);
                mListener.getLocationAddress(lat, lng, addressString);
            }
        } catch (IOException e) {
            if (Debug.LOG) {
                Log.e("Geocoder", "Unable connect to Geocoder");
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        mListener.getLocation(location.getLatitude(), location.getLongitude());
    }

    public void onProviderDisabled(String provider) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public IBinder onBind(Intent arg0) {
        return null;
    }
}