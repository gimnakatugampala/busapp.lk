// MainActivity.java
package com.busapp.lk;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private Marker busMarker;
    private Handler handler;
    private Runnable updateRunnable;

    // Sample bus data
    private String busId = "001";
    private String busNumber = "138";
    private double currentLat = 6.9271; // Colombo, Sri Lanka
    private double currentLng = 79.8612;
    private double currentSpeed = 0;

    // UI Elements
    private TextView tvBusId, tvBusNumber, tvSpeed, tvLat, tvLng, tvStatus;
    private CardView cardBusInfo;
    private Random random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        initViews();

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        random = new Random();
        handler = new Handler();

        // Start simulating real-time data updates
        startDataSimulation();
    }

    private void initViews() {
        tvBusId = findViewById(R.id.tvBusId);
        tvBusNumber = findViewById(R.id.tvBusNumber);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvLat = findViewById(R.id.tvLat);
        tvLng = findViewById(R.id.tvLng);
        tvStatus = findViewById(R.id.tvStatus);
        cardBusInfo = findViewById(R.id.cardBusInfo);

        updateUI();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Add initial marker
        LatLng busLocation = new LatLng(currentLat, currentLng);
        busMarker = map.addMarker(new MarkerOptions()
                .position(busLocation)
                .title("Bus " + busNumber)
                .snippet("Bus ID: " + busId)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Move camera to bus location
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(busLocation, 14));
    }

    private void startDataSimulation() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Simulate data from Arduino IoT device
                simulateArduinoData();
                updateUI();
                updateMapMarker();

                // Update every 2 seconds (simulating real-time data)
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void simulateArduinoData() {
        // Simulate GPS movement (small random changes)
        currentLat += (random.nextDouble() - 0.5) * 0.001;
        currentLng += (random.nextDouble() - 0.5) * 0.001;

        // Simulate speed changes (0-60 km/h)
        currentSpeed = 20 + random.nextDouble() * 40;
    }

    private void updateUI() {
        tvBusId.setText("Bus ID: " + busId);
        tvBusNumber.setText("Bus #" + busNumber);
        tvSpeed.setText(String.format("%.1f km/h", currentSpeed));
        tvLat.setText(String.format("%.6f", currentLat));
        tvLng.setText(String.format("%.6f", currentLng));

        // Update status based on speed
        if (currentSpeed < 5) {
            tvStatus.setText("STOPPED");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if (currentSpeed < 20) {
            tvStatus.setText("SLOW");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            tvStatus.setText("MOVING");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    private void updateMapMarker() {
        if (busMarker != null) {
            LatLng newPosition = new LatLng(currentLat, currentLng);
            busMarker.setPosition(newPosition);

            // Optionally animate camera to follow bus
            // map.animateCamera(CameraUpdateFactory.newLatLng(newPosition));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
}