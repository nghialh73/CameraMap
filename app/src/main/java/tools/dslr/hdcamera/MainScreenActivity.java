package tools.dslr.hdcamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import tools.dslr.hdcamera.UI.location.LocationServiceManager;

public class MainScreenActivity extends AppCompatActivity implements LocationAddressListener {

    Context context;
    boolean doubleBackToExitPressedOnce = false;
    private LocationServiceManager locationServiceManager = null;
    private Location location;

    private ProgressBar mProgressBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_main);
        context = this;

        mProgressBar = findViewById(R.id.loading_indicator);
        mProgressBar.setVisibility(View.VISIBLE);
//        final ImageButton swipeButton = findViewById(R.id.swipe_start);
//        swipeButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                MainScreenActivity.this.startActivity(new Intent(MainScreenActivity.this, HomeActivity.class));
//            }
//        });
        boolean has_coarse_location_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean has_fine_location_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (has_coarse_location_permission && has_fine_location_permission) {
            initLocation();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions( new String[] { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            }
        }
    }

    private void initLocation() {
        locationServiceManager = new LocationServiceManager(this, this);
        location = locationServiceManager.getLocation();
        if (location != null) {
            locationServiceManager.getAddressFromLocation(location.getLatitude(), location.getLongitude());
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @Override
    public void getLocationAddress(double lat, double lon, String address) {
        if (address != null && !address.isEmpty()) {
            mProgressBar.setVisibility(View.GONE);
            startActivity(new Intent(MainScreenActivity.this, HomeActivity.class));
        }
    }

    @Override
    public void getLocation(double lat, double lon) {
        locationServiceManager.getAddressFromLocation(lat, lon);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initLocation();
            }
        }
    }
}
