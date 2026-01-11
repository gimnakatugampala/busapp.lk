package com.busapp.lk;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private Handler handler;
    private Runnable updateRunnable;
    private Random random;
    private Geocoder geocoder;

    // Multiple buses
    private List<Bus> buses;
    private Map<Marker, Bus> markerBusMap;

    // UI Elements
    private CardView cardBusInfo;
    private LinearLayout busInfoContent;
    private ImageView ivClose;
    private TextView tvBusId, tvBusNumber, tvSpeed, tvLocation, tvStatus;
    private Bus selectedBus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBuses();

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        geocoder = new Geocoder(this, Locale.getDefault());
        random = new Random();
        handler = new Handler();
        markerBusMap = new HashMap<>();
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

        // Initially hide the info card
        cardBusInfo.setVisibility(View.GONE);

        // Close button
        ivClose.setOnClickListener(v -> {
            cardBusInfo.setVisibility(View.GONE);
            selectedBus = null;
        });
    }

    private void initBuses() {
        buses = new ArrayList<>();

        // Initialize multiple buses with different routes around Colombo
        buses.add(new Bus("001", "138", 6.9271, 79.8612, 35));
        buses.add(new Bus("002", "176", 6.9350, 79.8500, 28));
        buses.add(new Bus("003", "120", 6.9180, 79.8700, 42));
        buses.add(new Bus("004", "155", 6.9400, 79.8550, 31));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Style like Uber - minimal UI
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);

        // Add markers for all buses
        for (Bus bus : buses) {
            LatLng position = new LatLng(bus.lat, bus.lng);
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .icon(createBusIcon(bus.busNumber))
                    .anchor(0.5f, 0.5f)
                    .flat(true));

            markerBusMap.put(marker, bus);
            bus.marker = marker;
        }

        // Center on Colombo
        LatLng colombo = new LatLng(6.9271, 79.8612);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(colombo, 13));

        // Marker click listener
        map.setOnMarkerClickListener(marker -> {
            Bus bus = markerBusMap.get(marker);
            if (bus != null) {
                showBusInfo(bus);
            }
            return true;
        });

        // Map click listener to hide info
        map.setOnMapClickListener(latLng -> {
            if (cardBusInfo.getVisibility() == View.VISIBLE) {
                cardBusInfo.setVisibility(View.GONE);
                selectedBus = null;
            }
        });

        // Start real-time updates
        startRealTimeTracking();
    }

    private BitmapDescriptor createBusIcon(String busNumber) {
        int width = 120;
        int height = 120;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Draw bus shape
        paint.setColor(Color.parseColor("#2E7D32"));
        paint.setStyle(Paint.Style.FILL);

        // Bus body
        RectF busBody = new RectF(20, 30, 100, 90);
        canvas.drawRoundRect(busBody, 8, 8, paint);

        // Bus windows
        paint.setColor(Color.parseColor("#C8E6C9"));
        canvas.drawRect(30, 40, 50, 55, paint);
        canvas.drawRect(55, 40, 75, 55, paint);
        canvas.drawRect(80, 40, 95, 55, paint);

        // Bus number background
        paint.setColor(Color.WHITE);
        RectF numberBg = new RectF(35, 60, 85, 82);
        canvas.drawRoundRect(numberBg, 4, 4, paint);

        // Bus number text
        paint.setColor(Color.parseColor("#1B5E20"));
        paint.setTextSize(18);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText(busNumber, 60, 76, paint);

        // Direction indicator (small triangle at front)
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

                // Update selected bus info if visible
                if (selectedBus != null && cardBusInfo.getVisibility() == View.VISIBLE) {
                    updateBusInfoUI(selectedBus);
                }

                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void updateBusPosition(Bus bus) {
        // Simulate GPS movement
        double latChange = (random.nextDouble() - 0.5) * 0.0008;
        double lngChange = (random.nextDouble() - 0.5) * 0.0008;

        LatLng oldPosition = new LatLng(bus.lat, bus.lng);
        bus.lat += latChange;
        bus.lng += lngChange;
        LatLng newPosition = new LatLng(bus.lat, bus.lng);

        // Simulate speed changes
        bus.speed = 15 + random.nextDouble() * 45;

        // Smooth animation of marker
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

    private void showBusInfo(Bus bus) {
        selectedBus = bus;
        updateBusInfoUI(bus);
        cardBusInfo.setVisibility(View.VISIBLE);

        // Animate card sliding up
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

        // Get location name
        getAddressFromLocation(bus.lat, bus.lng);
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

    // Bus data class
    static class Bus {
        String id;
        String busNumber;
        double lat;
        double lng;
        double speed;
        Marker marker;

        Bus(String id, String busNumber, double lat, double lng, double speed) {
            this.id = id;
            this.busNumber = busNumber;
            this.lat = lat;
            this.lng = lng;
            this.speed = speed;
        }
    }
}