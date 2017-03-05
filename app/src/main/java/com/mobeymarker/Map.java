package com.mobeymarker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.util.Log;
import android.view.animation.Interpolator;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Map extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    public final int        // Handler codes.
            MAP_CHECK = 0,
            MAP_INIT = 1,
            MAP_READY = 2,
            CUR_INIT = 3,
            CUR = 4,
            IN_PROGRESS = 9;

    private GoogleMap mMap;
    private GoogleApiClient g;

    private LocationRequest lr;

    // User's               <<< LAST LOCATION >>>
    private Location loc;   // Get user's last location.

    private LatLng cur;         // <<< current Location >>>   (used for driver GPS ticks)

    private int i;          // Global counter for getDriverLocation()

    private Marker marker;

    Handler h0 = null;       // h0 - main Thread

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        goHandler();
        h0.sendEmptyMessage(1);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Kaan
        // Create an instance of GoogleAPIClient.
        if (g == null) {
            g = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

//        mMap.animateCamera(CameraUpdateFactory.zoomTo(17.0f));
//        kaan();1
    }

    private void kaan() {
        Log.i("XXX", "<<<<<<<<<<<<<<<<<<<<<< before ASYNC");
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(5000);
                mMap.moveCamera(CameraUpdateFactory.zoomIn());
                SystemClock.sleep(5000);
                mMap.animateCamera(CameraUpdateFactory.zoomTo(3.0f));
            }
        });
        Log.i("XXX", ">>>>>>>  after ASYNC");
    }

    @Override
    protected void onStart() {
        g.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (g.isConnected())
            g.disconnect();
        super.onStop();
    }


    /**
     * =======================     The   UI    - H A N D L E R    ==================================
     */
    Handler h1 = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            int counter = 0;
            LatLng l;
            switch (msg.what) {
                case CUR_INIT:
                    l = (LatLng) msg.getData().getParcelable("cur");
                    Log.i("XXX", String.format("<~~~~ CUR_INIT  [ %d ] ", counter));
                    Log.i("XXX", String.format("h0 <---- LatLng(%s, %s)", l.latitude, l.longitude));
                    locs.put(counter, l);
                    counter++;
                    break;
                case CUR:
                    l = (LatLng) msg.getData().getParcelable("cur");
                    Log.i("XXX", String.format("<~~~~~~~~ CUR  [ %d ] ", counter));
                    Log.i("XXX", String.format("h0 <---- LatLng(%s, %s)", l.latitude, l.longitude));
                    locs.put(counter, l);

                    AsyncTask a = new AsyncTask() {
                        @Override
                        protected Object doInBackground(Object[] params) {
                            letsgo(0, 1);
                            return null;
                        }
                    }.execute();

                    counter++;
                    break;
                case IN_PROGRESS:
                    StartTimer();
                    Log.i("XXX", String.format("[h0][start] IN_PROGRESS --- %d ---> (  ) --> \tTime: %5d", counter - 1, updatedTime));
                    break;
                default:
                    Log.i("XXX", "Handler  default:   ???");
                    break;
            }
            return false;
        }
    });
    /**
     * =======================     The   MAIN  -  H A N D L E R    ==================================
     */
    private long startTime = 0L;
    long timeInMilliseconds = 0L;
    long timeSwapBuff = 0L;
    long updatedTime = 0L;
    public HashMap<Integer, LatLng> locs = new HashMap<>();

    private void goHandler() {
        HandlerThread hx = new HandlerThread("hThread", Thread.MIN_PRIORITY);
        hx.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        hx.start();

        // TODO: Handler heartbeat function. (Got "CUR" in last  2 secs --> letsgo(newPoint)
        // TODO:    letsgo(newPoint, velocity)       ( store the last point --> cur )
        h0 = new Handler(hx.getLooper()) {
            Boolean ret = false;
            int counter = 0;
            LatLng l;

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MAP_INIT:
                        Log.i("XXX", "<~~~~~~~~~~~~~ Map initialized ~~~~~~~~~~~~~~ >");
                        StartTimer();
                        ret = true;
                        break;
                    case MAP_READY:
                        Log.i("XXX", "<~~~~~~~~~~~~~ Map RDY ~~~~~~~~~~~~~~ >");
                        StopTimer();
                        Log.i("XXX", String.format("Duration: %5d", updatedTime));
                        break;

                    case CUR_INIT:
                        l = (LatLng) msg.getData().getParcelable("cur");
                        Log.i("XXX", String.format("<~~~~ CUR_INIT  [ %d ] ", counter));
                        Log.i("XXX", String.format("h0 <---- LatLng(%s, %s)", l.latitude, l.longitude));
                        locs.put(counter, l);
                        counter++;
                        break;
                    case CUR:
                        l = (LatLng) msg.getData().getParcelable("cur");
                        Log.i("XXX", String.format("<~~~~~~~~ CUR  [ %d ] ", counter));
                        Log.i("XXX", String.format("h0 <---- LatLng(%s, %s)", l.latitude, l.longitude));
                        locs.put(counter, l);

                        AsyncTask a = new AsyncTask() {
                            @Override
                            protected Object doInBackground(Object[] params) {
                                letsgo(0, 1);
                                return null;
                            }
                        }.execute();

                        counter++;
                        break;
                    case IN_PROGRESS:
                        StartTimer();
                        Log.i("XXX", String.format("[h0][start] IN_PROGRESS --- %d ---> (  ) --> \tTime: %5d", counter - 1, updatedTime));
                        break;
                    default:
                        Log.i("XXX", "Handler  default:   ???");
                        break;
                }
                Log.i("XXX", "h0 <---- msg.toString() <--- " + msg.toString());
            }
        };

    }

    /**
     * ====================================================================================
     */

    private void StartTimer() {
        startTime = SystemClock.uptimeMillis();
        h0.postDelayed(updateTimerThread, 0);
    }

    private void StopTimer() {
        timeSwapBuff += timeInMilliseconds;
        h0.removeCallbacks(updateTimerThread);
    }

    private void ResetTimer() {
        timeSwapBuff = 0;
    }

    private Runnable updateTimerThread = new Runnable() {
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            int secs = (int) (timeInMilliseconds / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            int hours = mins / 60;
            mins = mins % 60;
            int milliseconds = (int) (updatedTime % 1000);
            String timer = "> TiME: \t" + String.format("%02d:%02d", mins, secs);

            h0.postDelayed(this, 1000);
        }
    };

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        h0.sendEmptyMessage(2); // TODO: Why this gets sent after Thread below ???

        loc = getLast();        // Go to user's last location
        String[] latlng = {null};
        String tmp0 = PreferenceManager.getDefaultSharedPreferences(this).getString("loc", null);
        if (tmp0 != null) {
            latlng = tmp0.split(", ");
            Log.i("XXX", "Shared Preference latlng: " + String.format("%s", latlng.toString()));
        }

//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(Double.parseDouble(latlng[0]), Double.parseDouble(latlng[1])), 16.0f));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-27.932849, 153.382598), 15.0f));


        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                try {
                    getLocs();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }.execute();
    }

    public Bitmap resizeMapIcons(String iconName, int width, int height) {
        Bitmap imageBitmap = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(iconName, "drawable", getPackageName()));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
        return resizedBitmap;
    }

    // FROM:    -27.932849, 153.382598
    // TO:      -27.931746, 153.376654
    public void getLocs() throws InterruptedException {
        synchronized (this) {
            // Get initial starting point.
            getLoc(1, 1);
            getLoc(2, 0);
            wait(400);
            /** ******************** API LatLng Request *********************************** */
//            for (i = 2; i < 6; i++) {          // Simulate 'i' location ticks.
//                getLoc(i, 0);
//                wait(400);                 // Wait 1.1s between ticks... Simulate real-time.
//            }
        } /** ************************************************************************* */
    }

    private void getLoc(final int i, final int init) {
        client.getC().getDriverLocation("123", i).enqueue(new Callback<retLoc>() {
            @Override
            public void onResponse(Call<retLoc> call, Response<retLoc> response) {
                cur = new LatLng(Double.parseDouble(response.body().latitude), Double.parseDouble(response.body().longitude));
                Message m = h1.obtainMessage();
                Bundle b = new Bundle();
                b.putParcelable("cur", cur);
                m.setData(b);
                m.what = (init > 0) ? CUR_INIT : CUR;
                h1.sendMessage(m);
                Log.i("XXX", String.format("<----- cur = getDriverLocation | i: %d", i));
//                for (int i = 0; i < 20; i++) {
//                    Log.i("XXX", String.format("Loc %d: \t%s", i, locs.get(i)));
//                }
            }

            @Override
            public void onFailure(Call<retLoc> call, Throwable t) {
                Log.i("XXX", "onFailure()");
            }
        });
    }

    private void letsgo(int x, int y) {
        Log.i("XXX", "\n\n>>>>>>>>>>>>>>  letsgo() >>>>>\n");
        final long start = time0();
        final long duration = 5000;
//        final Interpolator interpolator = new LinearInterpolator();
        final Interpolator interpolator = new FastOutLinearInInterpolator();


        for (i = 0; i < 5; i++) {
            Log.i("XXX", String.format("Loc %d: \t%s", i, locs.get(i)));
        }

        final MarkerOptions mo = new MarkerOptions()
                .position(locs.get(0))
                .icon(BitmapDescriptorFactory.fromBitmap(resizeMapIcons("car", 100, 100)));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                marker = mMap.addMarker(mo);
            }
        });

//        Projection proj = mMap.getProjection();         // not used.


        // FROM:    -27.932849, 153.382598
        // TO:      -27.931746, 153.376654
//        final LatLng from = locs.get(x);
        final LatLng from = new LatLng(-27.932849, 153.382598);
//        final LatLng to = locs.get(y);
        final LatLng to = new LatLng(-27.931746, 153.376654);
        Log.i("XXX", String.format("DEBUG ---> to ---> %s", to.toString()));

        final Runnable move = new Runnable() {
            @Override
            public void run() {
                long elapsed = time0() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);


                Log.i("XXX", String.format("Elapsed:\t%d\nt:\t%f", elapsed, t));
                double lat = t * to.latitude + (1 - t)
                        * from.latitude;
                double lng = t * to.longitude + (1 - t)
                        * from.longitude;
                marker.setPosition(new LatLng(lat, lng));
                Log.i("XXX", String.format("New t:\t%f", t));

                if (t < 1.0) { // Post again 16ms later.
                    h1.postDelayed(this, 16);
                }
//                h0.removeMessages(IN_PROGRESS);           TODO
//                StopTimer();
//                Log.i("XXX", String.format("[end]--> ( Runnable ) --> \tTime: %5d", updatedTime));
            }
        };

        Runnable moveQ = new Runnable() {
            @Override
            public void run() {
                while (h0.hasMessages(IN_PROGRESS)) {
                    Log.i("XXX", String.format("[moveQ] - Still in progress... \t[ %5d ]", updatedTime));
                    try {
                        wait(900);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                h0.sendEmptyMessage(IN_PROGRESS);
                h0.post(move);// ? But is it going to exit to 'finally' after the move() recursive threads ?
                // ? or after first started thread exits ?
//                    h0.postDelayed(this, updatedTime);
            }
        };
        h0.post(moveQ);
    }

    private long time0() {
        return SystemClock.uptimeMillis();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
//        loc = getLast();        // Go to user's last location
//        mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(loc.getLatitude(), loc.getLongitude())));
//
////        mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(-27.932849, 153.382598);));
//        mMap.animateCamera(CameraUpdateFactory.zoomTo(16.0f));

        // Save the user's last location to local Shared Preferences.
        Location l = getLast();

        String tmp0 = PreferenceManager.getDefaultSharedPreferences(this).getString("loc", null);
        String[] latlng = {null};
        if (tmp0 != null) {
            latlng = PreferenceManager.getDefaultSharedPreferences(this).getString("loc", null).split(", ");
            Log.i("XXX", "-->\tUser's Last `fusedlocation` latlng:\t");
            Log.i("XXX", "--->\tLatLng: " + String.format("%s", (latlng.length > 0) ? latlng[0] + ", " + latlng[1] : "null"));
            Log.i("XXX", String.format("-->\tUser's New `fusedlocation` latlng:\t\n--->\tLatLng: %f, %f",
                    l.getLatitude(), l.getLongitude()));
        }

        // Save user's last known LatLng from FusedLocation API.
        // (TODO: Include bearing, velocity..)
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .remove("loc")          // Restore user's last known location
                .putString("loc", l.getLatitude() + ", " + l.getLongitude())
                .apply();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    private Location getLast() {
        if (ActivityCompat
                .checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    0);
            Log.i("XXX", "REQUESTED: Permission ACCESS_COARSE_LOCATION + ACCESS_FINE_LOCATION");
        }
        loc = LocationServices.FusedLocationApi.getLastLocation(g);
//        LocationServices.FusedLocationApi.requestLocationUpdates(g, new LocationRequest())
        if (loc != null)
            Log.i("XXX", String.format("--> getLast(): ( %f - %f )\n%s",
                    loc.getLatitude(), loc.getLongitude(), loc.toString()));
        return loc;
    }
}


//          --- Archive ---
//            runOnUiThread(new Runnable() {  // Blocking UI thread ?
//                @Override
//                public void run() {
//                    synchronized (this) {
//                        try {
//                            wait(1100);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            });


//                  ARCHIVE
//package com.mobeymarker;
//
//        import android.Manifest;
//        import android.content.pm.PackageManager;
//        import android.graphics.Bitmap;
//        import android.graphics.BitmapFactory;
//        import android.location.Location;
//        import android.os.AsyncTask;
//        import android.os.Bundle;
//        import android.os.Handler;
//        import android.os.HandlerThread;
//        import android.os.Message;
//        import android.os.Process;
//        import android.os.SystemClock;
//        import android.preference.PreferenceManager;
//        import android.support.annotation.NonNull;
//        import android.support.annotation.Nullable;
//        import android.support.v4.app.ActivityCompat;
//        import android.support.v4.app.FragmentActivity;
//        import android.util.Log;
//        import android.view.animation.Interpolator;
//        import android.view.animation.LinearInterpolator;
//
//        import com.google.android.gms.common.ConnectionResult;
//        import com.google.android.gms.common.api.GoogleApiClient;
//        import com.google.android.gms.location.LocationRequest;
//        import com.google.android.gms.location.LocationServices;
//        import com.google.android.gms.maps.CameraUpdateFactory;
//        import com.google.android.gms.maps.GoogleMap;
//        import com.google.android.gms.maps.OnMapReadyCallback;
//        import com.google.android.gms.maps.Projection;
//        import com.google.android.gms.maps.SupportMapFragment;
//        import com.google.android.gms.maps.model.BitmapDescriptorFactory;
//        import com.google.android.gms.maps.model.LatLng;
//        import com.google.android.gms.maps.model.Marker;
//        import com.google.android.gms.maps.model.MarkerOptions;
//
//        import java.util.HashMap;
//
//        import retrofit2.Call;
//        import retrofit2.Callback;
//        import retrofit2.Response;
//
//public class Map extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
//    public final int        // Handler codes.
//            MAP_CHECK = 0,
//            MAP_INIT = 1,
//            MAP_READY = 2,
//            CUR_INIT = 3,
//            CUR = 4,
//            IN_PROGRESS = 9;
//
//    private GoogleMap mMap;
//    private GoogleApiClient g;
//
//    private LocationRequest lr;
//
//    // User's               <<< LAST LOCATION >>>
//    private Location loc;   // Get user's last location.
//
//    private LatLng cur;         // <<< current Location >>>   (used for driver GPS ticks)
//    private LatLng from;        // <<< from Location >>>
//    private LatLng to;          // <<< to   Location >>>
//
//    private int i;          // Global counter for getDriverLocation()
//
//    private Marker marker;
//
//    HashMap<Integer, LatLng> locs = new HashMap<>();
//
//    private Handler h0 = null;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        goHandler();
//        h0.sendEmptyMessage(1);
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.map);
//        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
//        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
//                .findFragmentById(R.id.map);
//        mapFragment.getMapAsync(this);
//
//        // Kaan
//        // Create an instance of GoogleAPIClient.
//        if (g == null) {
//            g = new GoogleApiClient.Builder(this)
//                    .addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this)
//                    .addApi(LocationServices.API)
//                    .build();
//        }
//
////        mMap.animateCamera(CameraUpdateFactory.zoomTo(17.0f));
////        kaan();1
//    }
//    private void kaan() {
//        Log.i("XXX", "<<<<<<<<<<<<<<<<<<<<<< before ASYNC");
//        AsyncTask.execute(new Runnable() {
//            @Override
//            public void run() {
//                SystemClock.sleep(5000);
//                mMap.moveCamera(CameraUpdateFactory.zoomIn());
//                SystemClock.sleep(5000);
//                mMap.animateCamera(CameraUpdateFactory.zoomTo(3.0f));
//            }
//        });
//        Log.i("XXX", ">>>>>>>  after ASYNC");
//    }
//    @Override
//    protected void onStart() {
//        g.connect();
//        super.onStart();
//    }
//    @Override
//    protected void onStop() {
//        if (g.isConnected())
//            g.disconnect();
//        super.onStop();
//    }
//
//    /**
//     * =======================     The    H A N D L E R    ==================================
//     */
//    private long startTime = 0L;
//    long timeInMilliseconds = 0L;
//    long timeSwapBuff = 0L;
//    long updatedTime = 0L;
//
//    private void goHandler(){
//        HandlerThread hx = new HandlerThread("hThread", Thread.MIN_PRIORITY);
//        hx.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
//        hx.start();
//
//        // TODO: Handler heartbeat function. (Got "CUR" in last  2 secs --> letsgo(newPoint)
//        // TODO:    letsgo(newPoint, velocity)       ( store the last point --> cur )
//        h0 = new Handler(hx.getLooper()) {
//            Boolean ret = false;
//            int counter = 0;
//            LatLng l,
//                    from,
//                    to;
//
//            @Override
//            public void handleMessage(Message msg) {
//                switch (msg.what) {
//                    case MAP_INIT:
//                        Log.i("XXX", "<~~~~~~~~~~~~~ Map initialized ~~~~~~~~~~~~~~ >");
//                        StartTimer();
//                        ret = true;
//                        break;
//                    case MAP_READY:
//                        Log.i("XXX", "<~~~~~~~~~~~~~ Map RDY ~~~~~~~~~~~~~~ >");
//                        StopTimer();
//                        Log.i("XXX", String.format("Duration: %5d", updatedTime));
//                        break;
//
//                    case CUR_INIT:
//                        l = (LatLng) msg.getData().getParcelable("cur");
//                        Log.i("XXX", String.format("<~~~~ CUR_INIT  [ %d ] ", counter));
//                        Log.i("XXX", String.format("h0 <---- LatLng(%s, %s)", l.latitude, l.longitude));
//                        locs.put(counter, l);
//                        counter++;
//                        break;
//                    case CUR:
//                        l = (LatLng) msg.getData().getParcelable("cur");
//                        Log.i("XXX", String.format("<~~~~~~~~ CUR  [ %d ] ", counter));
//                        Log.i("XXX", String.format("h0 <---- LatLng(%s, %s)", l.latitude, l.longitude));
//                        locs.put(counter, l);
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                letsgo(counter - 1, counter);
//                            }
//                        });
//                        counter++;
//                        break;
//                    case IN_PROGRESS:
//                        StartTimer();
//                        Log.i("XXX", String.format("--> ( h0 ) - New move <-- \tTime: %5d", updatedTime));
//                        break;
//                    default:
//                        Log.i("XXX", "Handler  default:   ???");
//                        break;
//                }
//                Log.i("XXX", "h0 <---- msg.toString() <--- " + msg.toString());
//            }
//        };
//
//    }
//    /**
//     * ====================================================================================
//     */
//
//    private void StartTimer() {
//        startTime = SystemClock.uptimeMillis();
//        h0.postDelayed(updateTimerThread, 0);
//    }
//
//    private void StopTimer() {
//        timeSwapBuff += timeInMilliseconds;
//        h0.removeCallbacks(updateTimerThread);
//    }
//
//    private void ResetTimer() {
//        timeSwapBuff = 0;
//    }
//
//    private Runnable updateTimerThread = new Runnable() {
//        public void run() {
//            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
//            updatedTime = timeSwapBuff + timeInMilliseconds;
//            int secs = (int) (timeInMilliseconds / 1000);
//            int mins = secs / 60;
//            secs = secs % 60;
//            int hours = mins / 60;
//            mins = mins % 60;
//            int milliseconds = (int) (updatedTime % 1000);
//            String timer = "> TiME: \t" + String.format("%02d:%02d", mins, secs);
//
//            h0.postDelayed(this, 1000);
//        }
//    };
//
//    /**
//     * Manipulates the map once available.
//     * This callback is triggered when the map is ready to be used.
//     * This is where we can add markers or lines, add listeners or move the camera. In this case,
//     * we just add a marker near Sydney, Australia.
//     * If Google Play services is not installed on the device, the user will be prompted to install
//     * it inside the SupportMapFragment. This method will only be triggered once the user has
//     * installed Google Play services and returned to the app.
//     */
//    @Override
//    public void onMapReady(GoogleMap googleMap) {
//        mMap = googleMap;
//
//        h0.sendEmptyMessage(2); // TODO: Why this gets sent after Thread below ???
//
//        loc = getLast();        // Go to user's last location
//
//        String[] latlng = PreferenceManager.getDefaultSharedPreferences(this).getString("loc", null).split(", ");
//        Log.i("XXX", "Shared Preference latlng: " + String.format("%s", latlng.toString()));
//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(Double.parseDouble(latlng[0]), Double.parseDouble(latlng[1])), 16.0f));
//
//
//        try {
//            getLocs();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public Bitmap resizeMapIcons(String iconName, int width, int height) {
//        Bitmap imageBitmap = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(iconName, "drawable", getPackageName()));
//        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
//        return resizedBitmap;
//    }
//
//    // FROM:    -27.932849, 153.382598
//    // TO:      -27.931746, 153.376654
//    public void getLocs() throws InterruptedException {
//        synchronized (this) {
//            // Get initial starting point.
//            getLoc(1, 1);
//            wait(400);
//            /** ******************** API LatLng Request *********************************** */
//            for (i = 2; i < 6; i++) {          // Simulate 'i' location ticks.
//                getLoc(i, 0);
//                wait(400);                 // Wait 1.1s between ticks... Simulate real-time.
//            }
//        } /** ************************************************************************* */
//    }
//
//    private void getLoc(final int i, final int init) {
//        client.getC().getDriverLocation("123", i).enqueue(new Callback<retLoc>() {
//            @Override
//            public void onResponse(Call<retLoc> call, Response<retLoc> response) {
//                cur = new LatLng(Double.parseDouble(response.body().latitude), Double.parseDouble(response.body().longitude));
//                Message m = h0.obtainMessage();
//                Bundle b = new Bundle();
//                b.putParcelable("cur", cur);
//                m.setData(b);
//                m.what = (init > 0) ? CUR_INIT : CUR;
//                h0.sendMessage(m);
//                Log.i("XXX", String.format("<----- cur = getDriverLocation | i: %d", i));
////                for (int i = 0; i < 20; i++) {
////                    Log.i("XXX", String.format("Loc %d: \t%s", i, locs.get(i)));
////                }
//            }
//
//            @Override
//            public void onFailure(Call<retLoc> call, Throwable t) {
//                Log.i("XXX", "onFailure()");
//            }
//        });
//    }
//
//
//    private void letsgo(int x, int y) {
//        Log.i("XXX", "\n\n>>>>>>>>>>>>>>  letsgo() >>>>>\n");
//        final long start = time0();
//        final long duration = 6000;
//        final Interpolator interpolator = new LinearInterpolator();
//
//        synchronized (this) {
//            try {
//                wait(5000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        for (i = 0; i < 20; i++) {
//            Log.i("XXX", String.format("Loc %d: \t%s", i, locs.get(i)));
//        }
//
//        MarkerOptions mo = new MarkerOptions()
//                .position(locs.get(0))
//                .icon(BitmapDescriptorFactory.fromBitmap(resizeMapIcons("car", 100, 100)));
//        marker = mMap.addMarker(mo);
////        marker.setPosition(from);                 // or   cur
//
//
//        Projection proj = mMap.getProjection();         // not used.
//
//        from = locs.get(x);
//        to = locs.get(y);
//
//        final Runnable move = new Runnable() {
//            @Override
//            public void run() {
//                long elapsed = time0() - start;
//                h0.sendEmptyMessage(IN_PROGRESS);
//                float t = interpolator.getInterpolation((float) elapsed
//                        / duration);
//                double lat = t * to.latitude + (1 - t)
//                        * from.latitude;
//                double lng = t * to.longitude + (1 - t)
//                        * from.longitude;
//                marker.setPosition(new LatLng(lat, lng));
//
//                if (t < 1.0) {
//                    // Post again 16ms later.
//                    h0.postDelayed(this, 16);
//                }
//            }
//        };
//
//        final Runnable moveQ = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    StartTimer();
//                    while (h0.hasMessages(IN_PROGRESS)) ;
//                    Log.i("XXX", String.format("[start] --> ( Runnable ) --> \tTime: %5d", updatedTime));
//                    move.run();     // ? But is it going to exit to 'finally' after the move() recursive threads ?
//                } finally {         // ? or after first started thread exits ?
//                    StopTimer();
//                    h0.removeMessages(IN_PROGRESS);
//                    Log.i("XXX", String.format("[end]--> ( Runnable ) --> \tTime: %5d", updatedTime));
//                    h0.postDelayed(this, updatedTime);
//                }
//
//            }
//        };
//        h0.post(moveQ);
//    }
//
//    private long time0() {
//        return SystemClock.uptimeMillis();
//    }
//
//    @Override
//    public void onConnected(@Nullable Bundle bundle) {
////        loc = getLast();        // Go to user's last location
////        mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(loc.getLatitude(), loc.getLongitude())));
////
//////        mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(-27.932849, 153.382598);));
////        mMap.animateCamera(CameraUpdateFactory.zoomTo(16.0f));
//
//        // Save the user's last location to local Shared Preferences.
//        Location l = getLast();
//
//        String[] latlng = PreferenceManager.getDefaultSharedPreferences(this)
//                .getString("loc", null).split(", ");
//        Log.i("XXX", "-->\tUser's Last `fusedlocation` latlng:\t");
//        Log.i("XXX", "--->\tLatLng: " + String.format("%s", (latlng.length > 0) ? latlng[0] + ", " + latlng[1] : "null"));
//        Log.i("XXX", String.format("-->\tUser's New `fusedlocation` latlng:\t\n--->\tLatLng: %f, %f",
//                l.getLatitude(), l.getLongitude()));
//
//        // Save user's last known LatLng from FusedLocation API.
//        // (TODO: Include bearing, velocity..)
//        PreferenceManager.getDefaultSharedPreferences(this)
//                .edit()
//                .remove("loc")          // Restore user's last known location
//                .putString("loc", l.getLatitude() + ", " + l.getLongitude())
//                .apply();
//    }
//
//    @Override
//    public void onConnectionSuspended(int i) {
//    }
//
//    @Override
//    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
//    }
//
//    private Location getLast() {
//        if (ActivityCompat
//                .checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
//                    0);
//            Log.i("XXX", "REQUESTED: Permission ACCESS_COARSE_LOCATION + ACCESS_FINE_LOCATION");
//        }
//        loc = LocationServices.FusedLocationApi.getLastLocation(g);
////        LocationServices.FusedLocationApi.requestLocationUpdates(g, new LocationRequest())
//        if (loc != null)
//            Log.i("XXX", String.format("--> getLast(): ( %f - %f )\n%s",
//                    loc.getLatitude(), loc.getLongitude(), loc.toString()));
//        return loc;
//    }
//}
//
//
////          --- Archive ---
////            runOnUiThread(new Runnable() {  // Blocking UI thread ?
////                @Override
////                public void run() {
////                    synchronized (this) {
////                        try {
////                            wait(1100);
////                        } catch (InterruptedException e) {
////                            e.printStackTrace();
////                        }
////                    }
////                }
////            });