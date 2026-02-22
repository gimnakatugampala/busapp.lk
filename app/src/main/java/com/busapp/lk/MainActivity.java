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
import android.os.Looper;
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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
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
import com.google.android.gms.maps.model.MapStyleOptions;
import android.content.res.Resources;
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

    // Location tracking
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private boolean initialCameraSet = false;

    // Multiple buses
    private List<Bus> buses;
    private Map<Marker, Bus> markerBusMap;
    private Polyline currentRouteLine;
    private Marker startMarker, endMarker, userMarker;
    private LatLng userLocation;

    // UI Elements
    private CardView cardBusInfo;
    private ImageView ivClose;
    private TextView tvBusId, tvBusNumber, tvSpeed, tvLocation, tvStatus;
    private TextView tvRouteDistance, tvStartPoint, tvEndPoint, tvProgress;
    private TextView tvDistanceToUser, tvETA;
    private TextView tvRouteType, tvOperatorType;
    private View colorSwatch;
    private ImageView btnLocateMe;
    private Bus selectedBus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBuses();
        setupLocationRequest();

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

    // Sri Lanka geographic bounding box
    private static final double SL_LAT_MIN = 5.9;
    private static final double SL_LAT_MAX = 9.9;
    private static final double SL_LNG_MIN = 79.6;
    private static final double SL_LNG_MAX = 82.0;

    // Default fallback: Colombo Fort
    private static final LatLng COLOMBO_DEFAULT = new LatLng(6.9271, 79.8612);

    private boolean isInSriLanka(double lat, double lng) {
        return lat >= SL_LAT_MIN && lat <= SL_LAT_MAX
                && lng >= SL_LNG_MIN && lng <= SL_LNG_MAX;
    }

    private void applyLocation(Location location) {
        if (location == null) return;

        double lat = location.getLatitude();
        double lng = location.getLongitude();

        if (isInSriLanka(lat, lng)) {
            // Real Sri Lanka location — use it
            userLocation = new LatLng(lat, lng);
        } else {
            // Emulator / wrong country — fall back to Colombo
            userLocation = COLOMBO_DEFAULT;
        }

        if (map != null) {
            addUserMarker();
            if (!initialCameraSet) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 13));
                initialCameraSet = true;
            }
        }

        if (selectedBus != null && cardBusInfo.getVisibility() == View.VISIBLE) {
            updateBusInfoUI(selectedBus);
        }
    }

    private void setupLocationRequest() {
        // Build a high-accuracy location request that updates every 5 seconds
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                applyLocation(locationResult.getLastLocation());
            }
        };
    }

    private void initViews() {
        cardBusInfo = findViewById(R.id.cardBusInfo);
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
        tvRouteType = findViewById(R.id.tvRouteType);
        tvOperatorType = findViewById(R.id.tvOperatorType);
        colorSwatch = findViewById(R.id.colorSwatch);
        btnLocateMe = findViewById(R.id.btnLocateMe);

        btnLocateMe.setOnClickListener(v -> {
            if (userLocation != null && map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
            }
        });

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
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                if (map != null) {
                    try {
                        map.setMyLocationEnabled(true);
                    } catch (SecurityException ignored) {}
                }
            } else {
                Toast.makeText(this, "Location permission required for better experience",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // First, try getLastLocation for an instant result
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, this::applyLocation);

        // Then start continuous updates for accuracy
        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void addUserMarker() {
        if (userLocation == null || map == null) return;

        if (userMarker != null) {
            userMarker.remove();
        }

        userMarker = map.addMarker(new MarkerOptions()
                .position(userLocation)
                .title("Your Location")
                .icon(createUserLocationIcon())
                .anchor(0.5f, 0.5f));
    }

    private BitmapDescriptor createUserLocationIcon() {
        int size = 56;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = size / 2f;
        float cy = size / 2f;
        float outerRadius = size / 2f - 2;
        float innerRadius = outerRadius * 0.55f;

        // Outer semi-transparent pulse ring
        paint.setColor(Color.parseColor("#4D2196F3"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, outerRadius, paint);

        // White border ring
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, innerRadius + 3.5f, paint);

        // Main blue ball
        paint.setColor(Color.parseColor("#2196F3"));
        canvas.drawCircle(cx, cy, innerRadius, paint);

        // Glossy shine highlight (top-left)
        paint.setColor(Color.parseColor("#80FFFFFF"));
        canvas.drawCircle(cx - innerRadius * 0.28f, cy - innerRadius * 0.30f, innerRadius * 0.38f, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void initBuses() {
        buses = new ArrayList<>();

        // ── Bus 138: Pettah → Mount Lavinia via Galle Road (A2) ── RED
        List<LatLng> route138 = new ArrayList<>();
        route138.add(new LatLng(6.9347, 79.8428));
        route138.add(new LatLng(6.9298, 79.8479));
        route138.add(new LatLng(6.9228, 79.8497));
        route138.add(new LatLng(6.9133, 79.8497));
        route138.add(new LatLng(6.9010, 79.8516));
        route138.add(new LatLng(6.8866, 79.8565));
        route138.add(new LatLng(6.8681, 79.8620));
        route138.add(new LatLng(6.8406, 79.8636));
        buses.add(new Bus("001", "138", route138,
                "Pettah", "Mount Lavinia", 15.2,
                "#F44336", "#B71C1C", "#EF9A9A", "Red",
                "Colombo and Suburbs", "Public"));

        // ── Bus 176: Fort → Nugegoda ── BLUE
        List<LatLng> route176 = new ArrayList<>();
        route176.add(new LatLng(6.9338, 79.8430));
        route176.add(new LatLng(6.9217, 79.8613));
        route176.add(new LatLng(6.9140, 79.8726));
        route176.add(new LatLng(6.9040, 79.8790));
        route176.add(new LatLng(6.8908, 79.8850));
        route176.add(new LatLng(6.8750, 79.8921));
        route176.add(new LatLng(6.8649, 79.8997));
        buses.add(new Bus("002", "176", route176,
                "Fort Railway Station", "Nugegoda", 12.8,
                "#2196F3", "#0D47A1", "#90CAF9", "Blue",
                "Express", "Private"));

        // ── Bus 120: Colombo Fort → Kaduwela ── WHITE
        List<LatLng> route120 = new ArrayList<>();
        route120.add(new LatLng(6.9338, 79.8430));
        route120.add(new LatLng(6.9217, 79.8613));
        route120.add(new LatLng(6.9270, 79.8740));
        route120.add(new LatLng(6.9310, 79.8900));
        route120.add(new LatLng(6.9330, 79.9100));
        route120.add(new LatLng(6.9335, 79.9370));
        route120.add(new LatLng(6.9330, 79.9840));
        buses.add(new Bus("003", "120", route120,
                "Colombo Fort", "Kaduwela", 18.5,
                "#FFFFFF", "#616161", "#E0E0E0", "White",
                "Intercity", "Public"));

        // ── Bus 155: Borella → Dehiwala ── YELLOW
        List<LatLng> route155 = new ArrayList<>();
        route155.add(new LatLng(6.9140, 79.8726));
        route155.add(new LatLng(6.9060, 79.8680));
        route155.add(new LatLng(6.8970, 79.8630));
        route155.add(new LatLng(6.8866, 79.8565));
        route155.add(new LatLng(6.8520, 79.8650));
        buses.add(new Bus("004", "155", route155,
                "Borella Junction", "Dehiwala Zoo", 9.3,
                "#FFC107", "#F57F17", "#FFE082", "Yellow",
                "Colombo and Suburbs", "Private"));

        // ── Bus 400: Pettah → Moratuwa ── GREEN
        List<LatLng> route400 = new ArrayList<>();
        route400.add(new LatLng(6.9347, 79.8428));
        route400.add(new LatLng(6.9010, 79.8516));
        route400.add(new LatLng(6.8681, 79.8620));
        route400.add(new LatLng(6.8200, 79.8700));
        route400.add(new LatLng(6.7730, 79.8810));
        buses.add(new Bus("005", "400", route400,
                "Pettah", "Moratuwa", 20.1,
                "#4CAF50", "#1B5E20", "#A5D6A7", "Green",
                "Intercity", "Private"));

        // ── Bus 212: Maradana → Kelaniya ── ORANGE
        List<LatLng> route212 = new ArrayList<>();
        route212.add(new LatLng(6.9217, 79.8613));
        route212.add(new LatLng(6.9320, 79.8700));
        route212.add(new LatLng(6.9420, 79.8780));
        route212.add(new LatLng(6.9540, 79.8880));
        route212.add(new LatLng(6.9540, 79.9200));
        buses.add(new Bus("006", "212", route212,
                "Maradana", "Kelaniya", 11.3,
                "#FF5722", "#BF360C", "#FFAB91", "Orange",
                "Colombo and Suburbs", "Public"));

        // ── Bus 315: Fort → Malabe ── BLACK
        List<LatLng> route315 = new ArrayList<>();
        route315.add(new LatLng(6.9338, 79.8430));
        route315.add(new LatLng(6.9217, 79.8613));
        route315.add(new LatLng(6.9140, 79.8726));
        route315.add(new LatLng(6.9200, 79.9000));
        route315.add(new LatLng(6.9050, 79.9700));
        route315.add(new LatLng(6.9100, 79.9980));
        buses.add(new Bus("007", "315", route315,
                "Fort", "Malabe", 16.7,
                "#212121", "#000000", "#616161", "Black",
                "International Airport", "Private"));

        // ── Bus 187: Nugegoda → Kottawa ── PURPLE
        List<LatLng> route187 = new ArrayList<>();
        route187.add(new LatLng(6.8649, 79.8997));
        route187.add(new LatLng(6.8530, 79.9100));
        route187.add(new LatLng(6.8400, 79.9200));
        route187.add(new LatLng(6.8250, 79.9400));
        route187.add(new LatLng(6.8100, 79.9630));
        buses.add(new Bus("008", "187", route187,
                "Nugegoda", "Kottawa", 13.5,
                "#9C27B0", "#4A148C", "#CE93D8", "Purple",
                "Domestic Airport", "Public"));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.setPadding(0, 0, 0, 200); // pad bottom for bottom sheet

        // Apply Uber-style custom map theme
        try {
            MapStyleOptions style = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style);
            map.setMapStyle(style);
        } catch (Resources.NotFoundException e) {
            // style file missing — continue with default
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        }

        // Add user marker if location already available
        if (userLocation != null) {
            addUserMarker();
            if (!initialCameraSet) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 13));
                initialCameraSet = true;
            }
        } else {
            // Default to Colombo while waiting for real location
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 12));
        }

        for (Bus bus : buses) {
            LatLng position = new LatLng(bus.currentLat, bus.currentLng);
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .icon(createBusIcon(bus.busNumber, bus.bodyColor, bus.darkColor, bus.accentColor, bus.colorName))
                    .anchor(0.5f, 0.5f)
                    .flat(true));

            markerBusMap.put(marker, bus);
            bus.marker = marker;
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

    private int toAlphaHex(String hex, int alpha) {
        int color = Color.parseColor(hex);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private BitmapDescriptor createBusIcon(String busNumber, String bodyColor, String darkColor, String accentColor, String colorName) {
        // Draw at full resolution then scale down for crisp result
        int origW = 160;
        int origH = 200;
        int w = 96;
        int h = 120;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale((float) w / origW, (float) h / origH);
        w = origW;
        h = origH;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ---------- DROP SHADOW ----------
        paint.setColor(Color.parseColor("#33000000"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(new RectF(24, h - 32, w - 24, h - 10), paint);

        // ---------- 3D BOTTOM FACE (underside depth) ----------
        paint.setColor(Color.parseColor(darkColor));
        RectF bottomFace = new RectF(18, 118, w - 18, 155);
        canvas.drawRoundRect(bottomFace, 10, 10, paint);

        // ---------- BUS MAIN BODY ----------
        paint.setColor(Color.parseColor(bodyColor));
        RectF body = new RectF(18, 42, w - 18, 130);
        canvas.drawRoundRect(body, 14, 14, paint);

        // ---------- BODY RIGHT-SIDE SHADING (3D depth) ----------
        paint.setColor(toAlphaHex(bodyColor, 26));
        RectF rightShade = new RectF(w - 40, 42, w - 18, 130);
        canvas.drawRoundRect(rightShade, 14, 14, paint);

        // ---------- BODY TOP HIGHLIGHT ----------
        paint.setColor(toAlphaHex(accentColor, 51));
        RectF topHighlight = new RectF(22, 44, w - 22, 70);
        canvas.drawRoundRect(topHighlight, 12, 12, paint);

        // ---------- ROOF ----------
        paint.setColor(Color.parseColor(bodyColor));
        RectF roof = new RectF(22, 34, w - 22, 60);
        canvas.drawRoundRect(roof, 12, 12, paint);

        // Roof gloss
        paint.setColor(Color.parseColor("#2AFFFFFF"));
        canvas.drawRoundRect(new RectF(28, 36, w - 28, 48), 8, 8, paint);

        // ---------- WINDSHIELD (front top) ----------
        paint.setColor(Color.parseColor("#B3BBDEFB"));
        RectF windshield = new RectF(30, 56, w - 30, 82);
        canvas.drawRoundRect(windshield, 6, 6, paint);

        // Windshield reflection streak
        paint.setColor(Color.parseColor("#60FFFFFF"));
        canvas.drawRoundRect(new RectF(34, 58, 56, 64), 3, 3, paint);

        // Windshield divider bar
        paint.setColor(Color.parseColor(bodyColor));
        paint.setStrokeWidth(2.5f);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(w / 2f, 56, w / 2f, 82, paint);
        paint.setStyle(Paint.Style.FILL);

        // ---------- SIDE WINDOWS ----------
        // Window row
        int[] winX = {24, 58, 92};
        for (int wx : winX) {
            // Window frame
            paint.setColor(Color.parseColor(darkColor));
            canvas.drawRoundRect(new RectF(wx, 86, wx + 28, 112), 5, 5, paint);
            // Window glass
            paint.setColor(Color.parseColor("#99BBDEFB"));
            canvas.drawRoundRect(new RectF(wx + 3, 89, wx + 25, 109), 4, 4, paint);
            // Window shine
            paint.setColor(Color.parseColor("#55FFFFFF"));
            canvas.drawRoundRect(new RectF(wx + 5, 91, wx + 14, 97), 2, 2, paint);
        }

        // ---------- DOOR OUTLINE ----------
        paint.setColor(Color.parseColor(darkColor));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(new RectF(w - 44, 86, w - 20, 128), 5, 5, paint);
        canvas.drawLine(w - 32, 86, w - 32, 128, paint);
        paint.setStyle(Paint.Style.FILL);

        // ---------- HEADLIGHTS ----------
        paint.setColor(Color.parseColor("#FFFDE7"));
        canvas.drawRoundRect(new RectF(22, 44, 36, 54), 4, 4, paint);
        canvas.drawRoundRect(new RectF(w - 36, 44, w - 22, 54), 4, 4, paint);
        // Headlight glow
        paint.setColor(Color.parseColor("#40FFEE58"));
        canvas.drawRoundRect(new RectF(20, 42, 38, 56), 5, 5, paint);
        canvas.drawRoundRect(new RectF(w - 38, 42, w - 20, 56), 5, 5, paint);

        // ---------- WHEELS (3D pill shape) ----------
        // Wheel shadow
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawOval(new RectF(20, 140, 52, 162), paint);
        canvas.drawOval(new RectF(w - 52, 140, w - 20, 162), paint);
        // Tyre
        paint.setColor(Color.parseColor("#212121"));
        canvas.drawOval(new RectF(22, 136, 50, 160), paint);
        canvas.drawOval(new RectF(w - 50, 136, w - 22, 160), paint);
        // Rim
        paint.setColor(Color.parseColor("#BDBDBD"));
        canvas.drawOval(new RectF(28, 140, 44, 156), paint);
        canvas.drawOval(new RectF(w - 44, 140, w - 28, 156), paint);
        // Hub
        paint.setColor(Color.WHITE);
        canvas.drawCircle(36, 148, 4, paint);
        canvas.drawCircle(w - 36, 148, 4, paint);

        // ---------- BUS NUMBER BADGE ----------
        // Determine text color: dark text on light buses (white/yellow), white on dark
        boolean isLightBus = colorName.equals("White") || colorName.equals("Yellow");
        int badgeTextColor = isLightBus
                ? Color.parseColor(darkColor) : Color.WHITE;
        int badgeBgColor = isLightBus
                ? Color.parseColor(darkColor) : Color.WHITE;
        int badgeNumColor = isLightBus
                ? Color.WHITE : Color.parseColor(darkColor);

        // Badge background (contrast colour)
        paint.setColor(badgeBgColor);
        RectF badge = new RectF(22, 112, w - 22, 135);
        canvas.drawRoundRect(badge, 8, 8, paint);

        // Bus number
        paint.setColor(badgeNumColor);
        paint.setTextSize(14f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText("#" + busNumber, w / 2f, 127, paint);

        // ---------- COLOR NAME LABEL (below badge) ----------
        // Label pill background
        paint.setColor(Color.parseColor(bodyColor));
        // Stroke border for white bus so label is visible
        if (colorName.equals("White")) {
            paint.setColor(Color.parseColor(darkColor));
        }
        RectF labelBg = new RectF(28, 138, w - 28, 156);
        canvas.drawRoundRect(labelBg, 8, 8, paint);

        paint.setColor(isLightBus ? Color.WHITE : Color.WHITE);
        paint.setTextSize(11f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(colorName.toUpperCase(), w / 2f, 151, paint);

        // ---------- DIRECTION ARROW (top pointer) ----------
        paint.setColor(Color.parseColor(accentColor));
        Path arrow = new Path();
        arrow.moveTo(w / 2f, 8);
        arrow.lineTo(w / 2f - 14, 30);
        arrow.lineTo(w / 2f - 5, 26);
        arrow.lineTo(w / 2f - 5, 36);
        arrow.lineTo(w / 2f + 5, 36);
        arrow.lineTo(w / 2f + 5, 26);
        arrow.lineTo(w / 2f + 14, 30);
        arrow.close();
        canvas.drawPath(arrow, paint);
        // Arrow highlight
        paint.setColor(Color.parseColor("#80FFFFFF"));
        Path arrowShine = new Path();
        arrowShine.moveTo(w / 2f, 10);
        arrowShine.lineTo(w / 2f - 7, 24);
        arrowShine.lineTo(w / 2f, 20);
        arrowShine.close();
        canvas.drawPath(arrowShine, paint);

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
        if (bus.waypoints == null || bus.waypoints.isEmpty()) return;

        // Target is the next waypoint in the list
        LatLng target = bus.waypoints.get(bus.waypointIndex);
        double dirLat = target.latitude - bus.currentLat;
        double dirLng = target.longitude - bus.currentLng;
        double distance = Math.sqrt(dirLat * dirLat + dirLng * dirLng);

        if (distance < 0.0005) {
            // Arrived at this waypoint — advance to the next
            bus.waypointIndex++;
            if (bus.waypointIndex >= bus.waypoints.size()) {
                // Reached final stop — loop back to start
                bus.waypointIndex = 0;
                bus.currentLat = bus.waypoints.get(0).latitude;
                bus.currentLng = bus.waypoints.get(0).longitude;
                bus.distanceTraveled = 0;
            }
        } else {
            // Move smoothly along the road segment (no random jitter)
            double step = 0.0003;
            bus.currentLat += (dirLat / distance) * step;
            bus.currentLng += (dirLng / distance) * step;
            bus.distanceTraveled += step * 111;
        }

        LatLng oldPosition = bus.marker.getPosition();
        LatLng newPosition = new LatLng(bus.currentLat, bus.currentLng);
        bus.speed = 20 + random.nextDouble() * 25;

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

        // Draw polyline through every waypoint so it follows real roads
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.parseColor(bus.accentColor))
                .width(10)
                .geodesic(true);
        for (LatLng point : bus.waypoints) {
            polylineOptions.add(point);
        }
        currentRouteLine = map.addPolyline(polylineOptions);

        startMarker = map.addMarker(new MarkerOptions()
                .position(bus.startPoint)
                .title("Start: " + bus.startPointName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        endMarker = map.addMarker(new MarkerOptions()
                .position(bus.endPoint)
                .title("End: " + bus.endPointName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : bus.waypoints) {
            builder.include(point);
        }
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

    private String getRouteTypeEmoji(String routeType) {
        switch (routeType) {
            case "Express":              return "⚡";
            case "Intercity":            return "🚌";
            case "International Airport": return "✈️";
            case "Domestic Airport":     return "🛩️";
            default:                     return "🏙️"; // Colombo and Suburbs
        }
    }

    private int getRouteTypeColor(String routeType) {
        switch (routeType) {
            case "Express":              return Color.parseColor("#D32F2F"); // deep red
            case "Intercity":            return Color.parseColor("#1565C0"); // deep blue
            case "International Airport": return Color.parseColor("#00838F"); // teal
            case "Domestic Airport":     return Color.parseColor("#2E7D32"); // green
            default:                     return Color.parseColor("#E65100"); // orange — Colombo & Suburbs
        }
    }

    private void updateBusInfoUI(Bus bus) {
        tvBusNumber.setText("Bus #" + bus.busNumber);
        tvBusNumber.setTextColor(Color.parseColor("#1A1A1A"));
        tvBusId.setText("ID: " + bus.id + "  ·  " + bus.colorName);
        boolean isLight = bus.colorName.equals("White") || bus.colorName.equals("Yellow");
        // Color swatch bar beside the bus number
        if (colorSwatch != null) {
            colorSwatch.setBackgroundColor(Color.parseColor(bus.bodyColor));
        }
        tvSpeed.setText(String.format("%.0f km/h", bus.speed));

        // Route type badge
        tvRouteType.setText(getRouteTypeEmoji(bus.routeType) + " " + bus.routeType);
        tvRouteType.setBackgroundColor(getRouteTypeColor(bus.routeType));
        tvRouteType.setTextColor(Color.WHITE);

        // Operator type badge
        boolean isPrivate = bus.operatorType.equals("Private");
        tvOperatorType.setText(isPrivate ? "🏢 Private" : "🏛️ Public");
        tvOperatorType.setBackgroundColor(isPrivate ? Color.parseColor("#6A1B9A") : Color.parseColor("#1565C0"));
        tvOperatorType.setTextColor(Color.WHITE);

        tvRouteDistance.setText(String.format("%.1f km", bus.totalDistance));
        tvStartPoint.setText(bus.startPointName);
        tvEndPoint.setText(bus.endPointName);

        double progress = (bus.distanceTraveled / bus.totalDistance) * 100;
        if (progress > 100) progress = 100;
        tvProgress.setText(String.format("%.0f%% Complete", progress));

        if (userLocation != null) {
            double distanceToUser = calculateDistance(
                    userLocation.latitude, userLocation.longitude,
                    bus.currentLat, bus.currentLng);

            tvDistanceToUser.setText(String.format("%.1f km away from you", distanceToUser));

            double avgSpeed = bus.speed > 0 ? bus.speed : 30;
            double etaMinutes = (distanceToUser / avgSpeed) * 60;

            if (etaMinutes < 1) {
                tvETA.setText("⚡ Arriving in less than 1 min");
                tvETA.setTextColor(Color.parseColor("#D32F2F"));
            } else if (etaMinutes <= 5) {
                tvETA.setText(String.format("⏱️ Near you in %d mins", (int) Math.ceil(etaMinutes)));
                tvETA.setTextColor(Color.parseColor("#388E3C"));
            } else if (etaMinutes <= 15) {
                tvETA.setText(String.format("🚌 Arriving in %d mins", (int) Math.ceil(etaMinutes)));
                tvETA.setTextColor(Color.parseColor("#F57C00"));
            } else {
                tvETA.setText(String.format("⏰ Arriving in %d mins", (int) Math.ceil(etaMinutes)));
                tvETA.setTextColor(Color.parseColor("#757575"));
            }
        } else {
            tvDistanceToUser.setText("📍 Acquiring your location...");
            tvETA.setText("Waiting for GPS fix");
            tvETA.setTextColor(Color.parseColor("#757575"));
        }

        if (bus.speed < 5) {
            tvStatus.setText("● STOPPED");
            tvStatus.setTextColor(Color.WHITE);
            tvStatus.setBackgroundColor(Color.parseColor("#D32F2F"));
        } else if (bus.speed < 20) {
            tvStatus.setText("● SLOW");
            tvStatus.setTextColor(Color.WHITE);
            tvStatus.setBackgroundColor(Color.parseColor("#F57C00"));
        } else {
            tvStatus.setText("● MOVING");
            tvStatus.setTextColor(Color.WHITE);
            tvStatus.setBackgroundColor(Color.parseColor("#43A047"));
        }

        getAddressFromLocation(bus.currentLat, bus.currentLng);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
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
    protected void onResume() {
        super.onResume();
        // Restart location updates when app comes to foreground
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates to save battery when app is backgrounded
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
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
        List<LatLng> waypoints;
        int waypointIndex = 0;

        String bodyColor;
        String darkColor;
        String accentColor;
        String colorName;
        String routeType;    // Express | Intercity | Colombo and Suburbs | International Airport | Domestic Airport
        String operatorType; // Private | Public

        Bus(String id, String busNumber, List<LatLng> waypoints,
            String startName, String endName, double distance,
            String bodyColor, String darkColor, String accentColor, String colorName,
            String routeType, String operatorType) {
            this.id = id;
            this.busNumber = busNumber;
            this.waypoints = waypoints;
            this.startPoint = waypoints.get(0);
            this.endPoint = waypoints.get(waypoints.size() - 1);
            this.startPointName = startName;
            this.endPointName = endName;
            this.totalDistance = distance;
            this.currentLat = startPoint.latitude;
            this.currentLng = startPoint.longitude;
            this.speed = 30;
            this.distanceTraveled = 0;
            this.bodyColor = bodyColor;
            this.darkColor = darkColor;
            this.accentColor = accentColor;
            this.colorName = colorName;
            this.routeType = routeType;
            this.operatorType = operatorType;
        }
    }
}