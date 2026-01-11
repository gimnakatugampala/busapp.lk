package com.busapp.lk;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private GoogleMap map;
    private Handler handler;
    private Runnable updateRunnable;
    private Random random;
    private Geocoder geocoder;
    private FusedLocationProviderClient fusedLocationClient;

    // Multiple buses
    private List<Bus> buses;
    private Map<Marker, Bus> markerBusMap;
    private Polyline currentRouteLine;
    private Marker startMarker, endMarker, userMarker;
    private LatLng userLocation;

    // UI Elements
    private CardView cardBusInfo;
    private LinearLayout busInfoContent;
    private ImageView ivClose;
    private TextView tvBusId, tvBusNumber, tvSpeed, tvLocation, tvStatus;
    private TextView tvRouteDistance, tvStartPoint, tvEndPoint, tvProgress;
    private TextView tvDistanceToUser, tvETA;
    private Bus selectedBus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBuses();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        geocoder = new Geocoder(this, Locale.getDefault());
        random = new Random();
        handler = new Handler();
        markerBusMap = new HashMap<>();

        requestLocationPermission();
    }

    private void initViews() {
        cardBusInfo = findViewById(R.id.cardBusInfo);
        busInfoContent = findViewById(R.id.busInfoContent);
        ivClose = findViewById(R.id.ivClose);
        tvBusId = findViewById(R.id.tvBusId);
        tvBusNumber = findViewById(R.id.tvBusNumber);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvLocation = findViewById(R.id.tvLocation);
        tvStatus = findViewById(R.id.tvStatus);
        tvRouteDistance = findViewById(R.id.tvRouteDistance);
        tvStartPoint = findViewById(R.id.tvStartPoint);
        tvEndPoint = findViewById(R.id.tvEndPoint);
        tvProgress = findViewById(R.id.tvProgress);
        tvDistanceToUser = findViewById(R.id.tvDistanceToUser);
        tvETA = findViewById(R.id.tvETA);

        cardBusInfo.setVisibility(View.GONE);

        ivClose.setOnClickListener(v -> {
            cardBusInfo.setVisibility(View.GONE);
            selectedBus = null;
            clearRouteDisplay();
        });
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getUserLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            } else {
                Toast.makeText(this, "Location permission required for better experience",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        if (map != null) {
                            addUserMarker();
                        }
                    }
                });
    }

    private void addUserMarker() {
        if (userLocation == null || map == null) return;

        if (userMarker != null) {
            userMarker.remove();
        }

        userMarker = map.addMarker(new MarkerOptions()
                .position(userLocation)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        // Move camera to user location initially
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 13));
    }

    private void initBuses() {
        buses = new ArrayList<>();

        // Bus 138: Pettah to Mount Lavinia
        buses.add(new Bus("001", "138",
                new LatLng(6.9271, 79.8612), // Start: Pettah
                new LatLng(6.8406, 79.8636), // End: Mount Lavinia
                "Pettah", "Mount Lavinia", 15.2));

        // Bus 176: Fort to Nugegoda
        buses.add(new Bus("002", "176",
                new LatLng(6.9350, 79.8500), // Start: Fort
                new LatLng(6.8649, 79.8997), // End: Nugegoda
                "Fort Railway Station", "Nugegoda", 12.8));

        // Bus 120: Colombo to Kaduwela
        buses.add(new Bus("003", "120",
                new LatLng(6.9180, 79.8700), // Start: Colombo
                new LatLng(6.9330, 79.9840), // End: Kaduwela
                "Colombo Fort", "Kaduwela", 18.5));

        // Bus 155: Borella to Dehiwala
        buses.add(new Bus("004", "155",
                new LatLng(6.9140, 79.8800), // Start: Borella
                new LatLng(6.8520, 79.8650), // End: Dehiwala
                "Borella Junction", "Dehiwala Zoo", 9.3));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);

        // Enable user location button
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        }

        // Add user marker if location already available
        if (userLocation != null) {
            addUserMarker();
        }

        // Add markers for all buses
        for (Bus bus : buses) {
            LatLng position = new LatLng(bus.currentLat, bus.currentLng);
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .icon(createBusIcon(bus.busNumber))
                    .anchor(0.5f, 0.5f)
                    .flat(true));

            markerBusMap.put(marker, bus);
            bus.marker = marker;
        }

        LatLng colombo = new LatLng(6.9271, 79.8612);
        if (userLocation != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 13));
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(colombo, 12));
        }

        map.setOnMarkerClickListener(marker -> {
            Bus bus = markerBusMap.get(marker);
            if (bus != null) {
                showBusInfo(bus);
                showBusRoute(bus);
            }
            return true;
        });

        map.setOnMapClickListener(latLng -> {
            if (cardBusInfo.getVisibility() == View.VISIBLE) {
                cardBusInfo.setVisibility(View.GONE);
                selectedBus = null;
                clearRouteDisplay();
            }
        });

        startRealTimeTracking();
    }

    private BitmapDescriptor createBusIcon(String busNumber) {
        int width = 120;
        int height = 120;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.parseColor("#2E7D32"));
        paint.setStyle(Paint.Style.FILL);
        RectF busBody = new RectF(20, 30, 100, 90);
        canvas.drawRoundRect(busBody, 8, 8, paint);

        paint.setColor(Color.parseColor("#C8E6C9"));
        canvas.drawRect(30, 40, 50, 55, paint);
        canvas.drawRect(55, 40, 75, 55, paint);
        canvas.drawRect(80, 40, 95, 55, paint);

        paint.setColor(Color.WHITE);
        RectF numberBg = new RectF(35, 60, 85, 82);
        canvas.drawRoundRect(numberBg, 4, 4, paint);

        paint.setColor(Color.parseColor("#1B5E20"));
        paint.setTextSize(18);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText(busNumber, 60, 76, paint);

        paint.setColor(Color.parseColor("#66BB6A"));
        Path triangle = new Path();
        triangle.moveTo(60, 25);
        triangle.lineTo(50, 35);
        triangle.lineTo(70, 35);
        triangle.close();
        canvas.drawPath(triangle, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void startRealTimeTracking() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                for (Bus bus : buses) {
                    updateBusPosition(bus);
                }

                if (selectedBus != null && cardBusInfo.getVisibility() == View.VISIBLE) {
                    updateBusInfoUI(selectedBus);
                }

                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void updateBusPosition(Bus bus) {
        // Calculate direction towards end point
        double dirLat = bus.endPoint.latitude - bus.currentLat;
        double dirLng = bus.endPoint.longitude - bus.currentLng;
        double distance = Math.sqrt(dirLat * dirLat + dirLng * dirLng);

        if (distance < 0.001) {
            // Reached end, reset to start
            bus.currentLat = bus.startPoint.latitude;
            bus.currentLng = bus.startPoint.longitude;
            bus.distanceTraveled = 0;
        } else {
            // Move towards end point with some randomness
            double step = 0.0004;
            bus.currentLat += (dirLat / distance) * step + (random.nextDouble() - 0.5) * 0.0001;
            bus.currentLng += (dirLng / distance) * step + (random.nextDouble() - 0.5) * 0.0001;

            // Update distance traveled
            bus.distanceTraveled += step * 111; // Rough km conversion
        }

        LatLng oldPosition = bus.marker.getPosition();
        LatLng newPosition = new LatLng(bus.currentLat, bus.currentLng);
        bus.speed = 20 + random.nextDouble() * 35;

        animateMarker(bus.marker, oldPosition, newPosition);
    }

    private void animateMarker(Marker marker, LatLng from, LatLng to) {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(2000);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            double lat = from.latitude + (to.latitude - from.latitude) * fraction;
            double lng = from.longitude + (to.longitude - from.longitude) * fraction;
            marker.setPosition(new LatLng(lat, lng));
        });

        animator.start();
    }

    private void showBusRoute(Bus bus) {
        clearRouteDisplay();

        // Draw route line
        PolylineOptions polylineOptions = new PolylineOptions()
                .add(bus.startPoint, bus.endPoint)
                .color(Color.parseColor("#4CAF50"))
                .width(10)
                .geodesic(true);
        currentRouteLine = map.addPolyline(polylineOptions);

        // Add start marker
        startMarker = map.addMarker(new MarkerOptions()
                .position(bus.startPoint)
                .title("Start: " + bus.startPointName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Add end marker
        endMarker = map.addMarker(new MarkerOptions()
                .position(bus.endPoint)
                .title("End: " + bus.endPointName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Adjust camera to show full route
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(bus.startPoint);
        builder.include(bus.endPoint);
        builder.include(new LatLng(bus.currentLat, bus.currentLng));

        LatLngBounds bounds = builder.build();
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
    }

    private void clearRouteDisplay() {
        if (currentRouteLine != null) {
            currentRouteLine.remove();
            currentRouteLine = null;
        }
        if (startMarker != null) {
            startMarker.remove();
            startMarker = null;
        }
        if (endMarker != null) {
            endMarker.remove();
            endMarker = null;
        }
    }

    private void showBusInfo(Bus bus) {
        selectedBus = bus;
        updateBusInfoUI(bus);
        cardBusInfo.setVisibility(View.VISIBLE);

        cardBusInfo.setTranslationY(300);
        cardBusInfo.animate()
                .translationY(0)
                .setDuration(300)
                .start();
    }

    private void updateBusInfoUI(Bus bus) {
        tvBusId.setText("Bus ID: " + bus.id);
        tvBusNumber.setText("Bus #" + bus.busNumber);
        tvSpeed.setText(String.format("%.0f km/h", bus.speed));

        // Route information
        tvRouteDistance.setText(String.format("%.1f km", bus.totalDistance));
        tvStartPoint.setText(bus.startPointName);
        tvEndPoint.setText(bus.endPointName);

        // Calculate progress percentage
        double progress = (bus.distanceTraveled / bus.totalDistance) * 100;
        if (progress > 100) progress = 100;
        tvProgress.setText(String.format("%.0f%% Complete", progress));

        // Calculate distance to user and ETA
        if (userLocation != null) {
            double distanceToUser = calculateDistance(
                    userLocation.latitude, userLocation.longitude,
                    bus.currentLat, bus.currentLng);

            tvDistanceToUser.setText(String.format("%.1f km away from you", distanceToUser));

            // Calculate ETA (assuming average speed)
            double avgSpeed = bus.speed > 0 ? bus.speed : 30; // Use 30 km/h as fallback
            double etaHours = distanceToUser / avgSpeed;
            double etaMinutes = etaHours * 60;

            if (etaMinutes < 1) {
                tvETA.setText("⚡ Arriving in less than 1 min");
                tvETA.setTextColor(Color.parseColor("#D32F2F"));
            } else if (etaMinutes <= 5) {
                tvETA.setText(String.format("⏱️ Near you in %d mins", (int)Math.ceil(etaMinutes)));
                tvETA.setTextColor(Color.parseColor("#388E3C"));
            } else if (etaMinutes <= 15) {
                tvETA.setText(String.format("🚌 Arriving in %d mins", (int)Math.ceil(etaMinutes)));
                tvETA.setTextColor(Color.parseColor("#F57C00"));
            } else {
                tvETA.setText(String.format("⏰ Arriving in %d mins", (int)Math.ceil(etaMinutes)));
                tvETA.setTextColor(Color.parseColor("#757575"));
            }
        } else {
            tvDistanceToUser.setText("Enable location to see distance");
            tvETA.setText("Location required for ETA");
        }

        // Update status
        if (bus.speed < 5) {
            tvStatus.setText("● STOPPED");
            tvStatus.setTextColor(Color.parseColor("#D32F2F"));
        } else if (bus.speed < 20) {
            tvStatus.setText("● SLOW");
            tvStatus.setTextColor(Color.parseColor("#F57C00"));
        } else {
            tvStatus.setText("● MOVING");
            tvStatus.setTextColor(Color.parseColor("#388E3C"));
        }

        getAddressFromLocation(bus.currentLat, bus.currentLng);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula
        final int R = 6371; // Radius of the earth in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // Distance in km
    }

    private void getAddressFromLocation(double lat, double lng) {
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String location = "";

                    if (address.getThoroughfare() != null) {
                        location = address.getThoroughfare();
                    }
                    if (address.getLocality() != null) {
                        location += (location.isEmpty() ? "" : ", ") + address.getLocality();
                    }
                    if (location.isEmpty() && address.getAddressLine(0) != null) {
                        location = address.getAddressLine(0);
                    }

                    String finalLocation = location.isEmpty() ? "Colombo Area" : location;
                    runOnUiThread(() -> tvLocation.setText(finalLocation));
                }
            } catch (IOException e) {
                runOnUiThread(() -> tvLocation.setText("Colombo Area"));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    static class Bus {
        String id;
        String busNumber;
        LatLng startPoint;
        LatLng endPoint;
        String startPointName;
        String endPointName;
        double totalDistance;
        double currentLat;
        double currentLng;
        double speed;
        double distanceTraveled;
        Marker marker;

        Bus(String id, String busNumber, LatLng start, LatLng end,
            String startName, String endName, double distance) {
            this.id = id;
            this.busNumber = busNumber;
            this.startPoint = start;
            this.endPoint = end;
            this.startPointName = startName;
            this.endPointName = endName;
            this.totalDistance = distance;
            this.currentLat = start.latitude;
            this.currentLng = start.longitude;
            this.speed = 30;
            this.distanceTraveled = 0;
        }
    }
}