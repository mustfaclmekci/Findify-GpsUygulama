package com.example.gpsuygulama2;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "FirebaseGPSControl";
    private GoogleMap gMap;
    private Double latitude = 0.0;
    private Double longitude = 0.0;

    private boolean isLedOn = false;
    private boolean isBuzzerOn = false;

    private DatabaseReference databaseReference;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase'i başlat
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        // Toolbar (Hamburger Menü)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.app_name);

        // DrawerLayout ve NavigationView ayarları
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Hamburger menü simgesi
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Harita fragmentini başlat
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.id_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        TextView tvCoordinates = findViewById(R.id.tvCoordinates);
        Button btnFetchData = findViewById(R.id.btnFetchData);
        Button btnToggleLed = findViewById(R.id.btnToggleLed);
        Button btnToggleSound = findViewById(R.id.btnToggleSound);

        // Firebase Realtime Database referansı
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Firebase verilerini dinle
        listenToFirebaseData(tvCoordinates);

        // LED kontrol butonu
        btnToggleLed.setOnClickListener(v -> {
            isLedOn = !isLedOn;
            btnToggleLed.setText(isLedOn ? "LED Kapat" : "LED Yak");
            updateFirebaseControl("LED", isLedOn);
        });

        // Buzzer kontrol butonu
        btnToggleSound.setOnClickListener(v -> {
            isBuzzerOn = !isBuzzerOn;
            btnToggleSound.setText(isBuzzerOn ? "Ses Kapat" : "Ses Çıkar");
            updateFirebaseControl("Buzzer", isBuzzerOn);
        });

        // GPS verisi çekme butonu
        btnFetchData.setOnClickListener(v -> fetchGPSDataFromFirebase(tvCoordinates));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        updateMap();
    }

    private void updateMap() {
        if (gMap == null || latitude == 0.0 || longitude == 0.0) {
            return;
        }
        LatLng location = new LatLng(latitude, longitude);
        gMap.clear();
        gMap.addMarker(new MarkerOptions().position(location).title("Konum"));
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 12));
    }

    private void fetchGPSDataFromFirebase(TextView tvCoordinates) {
        databaseReference.child("GPS").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    latitude = snapshot.child("Latitude").getValue(Double.class);
                    longitude = snapshot.child("Longitude").getValue(Double.class);

                    if (latitude != null && longitude != null) {
                        tvCoordinates.setText("Latitude: " + latitude + "\nLongitude: " + longitude);
                        updateMap();
                    } else {
                        tvCoordinates.setText("GPS verisi eksik.");
                    }
                } else {
                    tvCoordinates.setText("GPS verisi mevcut değil.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase'den veri alınamadı: " + error.getMessage());
            }
        });
    }

    private void listenToFirebaseData(TextView tvCoordinates) {
        databaseReference.child("Controls").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    isLedOn = snapshot.child("LED").getValue(Integer.class) == 1;
                    isBuzzerOn = snapshot.child("Buzzer").getValue(Integer.class) == 1;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase'den veri dinlenemedi: " + error.getMessage());
            }
        });

        databaseReference.child("GPS").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    latitude = snapshot.child("Latitude").getValue(Double.class);
                    longitude = snapshot.child("Longitude").getValue(Double.class);

                    if (latitude != null && longitude != null) {
                        tvCoordinates.setText("Latitude: " + latitude + "\nLongitude: " + longitude);
                        updateMap();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase'den GPS verisi alınamadı: " + error.getMessage());
            }
        });
    }

    private void updateFirebaseControl(String controlName, boolean isOn) {
        databaseReference.child("Controls").child(controlName).setValue(isOn ? 1 : 0)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, controlName + " durumu Firebase'e gönderildi.");
                    } else {
                        Log.e(TAG, controlName + " Firebase'e gönderilemedi: " + task.getException().getMessage());
                    }
                });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.item1) {
            Toast.makeText(this, "Cihazları Bul seçildi", Toast.LENGTH_SHORT).show();
        } else if (item.getItemId() == R.id.item2) {
            Toast.makeText(this, "Haritayı Yenile seçildi", Toast.LENGTH_SHORT).show();
        } else if (item.getItemId() == R.id.menu3) {
            Toast.makeText(this, "Ayarlar seçildi", Toast.LENGTH_SHORT).show();
        }
        drawerLayout.closeDrawers();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

}
