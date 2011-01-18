package com.camptocamp.android.gis;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.nutiteq.components.WgsPoint;
import com.nutiteq.location.LocationListener;
import com.nutiteq.location.LocationMarker;
import com.nutiteq.location.LocationSource;

public class C2CGpsProvider implements LocationSource, android.location.LocationListener {

    private static final String TAG = "C2CGpsProvider";
    private static final int DT_GPS = 1000 * 60 * 2; // 2mn
    private static final int DT_NETWORK = 1000 * 60 * 5; // 5mn
    private static final long UP_IDLE = 5000L;
    private static final long UP_ACTIVE = 1000L;
    private static final float UP_DIST = 5F;

    private LocationMarker marker;
    private LocationManager manager;
    private Location location = null;
    private WeakReference<Map> mMap;
    private int status = STATUS_CONNECTING;

    public List<C2CLine> trace = new ArrayList<C2CLine>();
    public boolean record = false;

    public C2CGpsProvider(Map a) {
        mMap = new WeakReference<Map>(a);
        manager = (LocationManager) a.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Herited from android
     */
    @Override
    public void onLocationChanged(Location loc) {
        Log.v(TAG, "loc=" + loc.getLatitude() + ", " + loc.getLongitude() + ", "
                + loc.getAccuracy() + ", " + loc.getProvider());
        if (loc != null && isBetterLocation(loc)) {
            // Update Marker
            marker.setLocation(new WgsPoint(loc.getLongitude(), loc.getLatitude()));
            // Update trace
            // TODO: Cut if signal lost ?
            if (record) {
                Map a = mMap.get();
                if (a != null && location != null) {
                    C2CLine line = new C2CLine(new WgsPoint[] {
                            new WgsPoint(location.getLongitude(), location.getLatitude()),
                            new WgsPoint(loc.getLongitude(), loc.getLatitude()) });
                    a.mapComponent.addLine(line);
                    trace.add(line);
                }
            }
            // Network location no more needed as primary source
            if (LocationManager.GPS_PROVIDER.equals(loc.getProvider()) && location != null) {
                manager.removeUpdates(this);
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UP_ACTIVE, UP_DIST,
                        this);
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UP_IDLE, UP_DIST,
                        this);
            }
            location = loc;
        }
        status = STATUS_CONNECTED;
    }

    @Override
    public void onProviderEnabled(String provider) {
        status = STATUS_CONNECTING;
    }

    @Override
    public void onProviderDisabled(final String provider) {
        final Map a = mMap.get();
        // GPS provider is disabled
        if (a != null && LocationManager.GPS_PROVIDER.equals(provider)) {
            // TODO: allow location from cellid/wifi only
            a.isTrackingPosition = false;
            a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            Toast.makeText(a, R.string.toast_gps_disabled, Toast.LENGTH_SHORT).show();
        }
        status = STATUS_CONNECTION_LOST;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (status == LocationProvider.AVAILABLE) {
            this.status = STATUS_CONNECTED;
        } else {
            this.status = STATUS_CONNECTION_LOST;
        }
    }

    /**
     * Herited from nutiteq
     */
    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public WgsPoint getLocation() {
        return new WgsPoint(location.getLongitude(), location.getLatitude());
    }

    @Override
    public void setLocationMarker(LocationMarker marker) {
        this.marker = marker;
        marker.setLocationSource(this);
    }

    @Override
    public LocationMarker getLocationMarker() {
        return marker;
    }

    @Override
    public void start() {
        // get last known location
        Location loc = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc != null) {
            loc.setTime(System.currentTimeMillis());
            onLocationChanged(loc);
        }
        // Register for location updates
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UP_IDLE, UP_DIST, this);
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UP_ACTIVE, UP_DIST, this);
    }

    @Override
    public void quit() {
        manager.removeUpdates(this);
        // if (marker != null) {
        // marker.quit();
        // }
    }

    @Override
    public void addLocationListener(LocationListener listener) {
        // Unused
    }

    /**
     * Custom
     */

    // http://developer.android.com/guide/topics/location/obtaining-user-location.html#BestEstimate
    private boolean isBetterLocation(Location loc) {
        // return true; // FIXME: just for dev
        if (location == null) {
            return true;
        }

        // Check if the location is new or old
        long timeDelta = loc.getTime() - location.getTime();
        boolean isSignificantlyNewer;
        boolean isSignificantlyOlder;
        if (LocationManager.GPS_PROVIDER.equals(loc.getProvider())) {
            isSignificantlyNewer = timeDelta > DT_GPS;
            isSignificantlyOlder = timeDelta < -DT_GPS;
        } else {
            isSignificantlyNewer = timeDelta > DT_NETWORK;
            isSignificantlyOlder = timeDelta < -DT_NETWORK;
        }
        boolean isNewer = timeDelta > 0;

        // Difference is significante
        if (isSignificantlyNewer) {
            Log.v(TAG, "isSignificantlyNewer");
            return true;
        } else if (isSignificantlyOlder) {
            Log.v(TAG, "isSignificantlyOlder");
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (loc.getAccuracy() - location.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;
        boolean isFromSameProvider = isSameProvider(loc.getProvider(), location.getProvider());

        // Determine location quality
        if (isMoreAccurate) {
            Log.v(TAG, "isMoreAccurate");
            return true;
        } else if (isNewer && !isLessAccurate) {
            Log.v(TAG, "isNewer");
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            Log.v(TAG, "isNewer && isFromSameProvider");
            return true;
        }

        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    public void setRecord(boolean rec) {
        record = rec;
    }
}