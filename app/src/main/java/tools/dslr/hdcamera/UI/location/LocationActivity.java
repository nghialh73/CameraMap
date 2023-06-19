package tools.dslr.hdcamera.UI.location;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.List;

import tools.dslr.hdcamera.R;

public class LocationActivity extends Activity implements OnMapReadyCallback {
    private ImageView ivBack;
    private TextView tvAddress;
    private TextView tvLat;
    private TextView tvLon;

    private RelativeLayout rlCopyAddress;
    private RelativeLayout rlCopyLocation;

    private LocationServiceManager mCheckLocation;
    private GoogleMap mMap;

    private Geocoder geocoder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        mCheckLocation = new LocationServiceManager(this);
        geocoder = new Geocoder(this);
        initView();
        initData();
        initControl();
    }

    private void initView() {
        ivBack = findViewById(R.id.iv_address_back);
        tvAddress = findViewById(R.id.txt_address);
        tvLat = findViewById(R.id.txt_address_lat);
        tvLon = findViewById(R.id.txt_address_long);
        rlCopyAddress = findViewById(R.id.rl_copy_address);
        MapFragment mapfragment = (MapFragment) getFragmentManager().findFragmentById(R.id.fr_map);
        mapfragment.getMapAsync(this);

    }

    private void initData() {
        Location location = mCheckLocation.getLocation();
        if (location != null) {
            fillData(location);
        }
    }

    private void initControl() {
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        rlCopyAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!"".equals(tvAddress.getText().toString())) {
                    copyText(tvAddress.getText().toString());
                    Toast.makeText(LocationActivity.this, "Has copied the address to the clipboard", Toast.LENGTH_LONG).show();
                }
            }
        });

        rlCopyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!"".equals(tvLat.getText().toString()) && !"".equals(tvLon.getText().toString())) {
                    String save = "Latitude: " + tvLat.getText().toString() + "; Longitude: " + tvLon.getText().toString();
                    copyText(save);
                    Toast.makeText(LocationActivity.this, "Has copied the location to the clipboard", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void copyText(String txt) {
        ClipboardManager clipboard = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text", txt);
        clipboard.setPrimaryClip(clip);
    }

    public void fillData(Location location) {
        if (location == null) {
            return;
        }
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        String result[] = ConvertLocationToString.getInDegree(latLng.latitude, latLng.longitude);
        tvLat.setText(result[0]);
        tvLon.setText(result[1]);
        //tvAddress.setText(getLocationNameCompass(latLng));
    }

    public String getLocationNameCompass(LatLng latLng) {
        String name = "Waiting";
        try {
            List<Address> addresses = this.geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses.size() > 0) {
                return ((Address) addresses.get(0)).getAddressLine(0);
            }
            return name;
        } catch (IOException e) {
            e.printStackTrace();
            return name;
        }
    }

    private void createLocationRequest() {
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(2 * 1000);
        mLocationRequest.setFastestInterval(2 * 1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        createLocationRequest();
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if ((ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) && (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            if ((ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) && (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION))) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        100);
            }
        } else {
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            mMap.setMyLocationEnabled(true);
            FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        LatLng myLocation = new LatLng(location.getLatitude(), location.getLongitude());
//                        mMap.addMarker(new MarkerOptions().position(myLocation).title(location.getT).snippet("Hà Nội"));
                        CameraPosition cameraPosition = new CameraPosition(myLocation, 12f, 0, 0);
                        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                        Log.d("mLocation", location.getLatitude() + "" + location.getLongitude());
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 100: {
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults.length > 0 && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        onMapReady(mMap);
                    } else {
                        Toast.makeText(this, "The app was not allowed to access your location,please grant its ", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.anim_trai_phai, R.anim.anim_phai_trai);
    }
}
