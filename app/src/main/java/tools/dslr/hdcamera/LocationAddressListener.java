package tools.dslr.hdcamera;

public interface LocationAddressListener {
    void getLocationAddress(double lat, double lon, String address);

    void getLocation(double lat, double lon);
}
