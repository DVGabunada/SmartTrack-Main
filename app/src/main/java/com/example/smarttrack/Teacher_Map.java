package com.example.smarttrack;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Address;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.Polygon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import org.osmdroid.views.overlay.TilesOverlay;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;


public class Teacher_Map extends AppCompatActivity {

    private MapView mapView;
    private Marker pinnedMarker;
    private Button confirmLocationButton;
    private SearchView locationSearchView;
    private ListView searchResults;
    private MyLocationNewOverlay myLocationOverlay;

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private Geocoder geocoder;
    private List<Address> addressList;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> searchLocations;

    public static final int LOCATION_REQUEST_CODE = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load OSM configurations
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));

        setContentView(R.layout.activity_teacher_map);

        // Initialize UI elements
        confirmLocationButton = findViewById(R.id.confirmLocationButton);
        locationSearchView = findViewById(R.id.locationSearchView);
        searchResults = findViewById(R.id.searchResults);
        mapView = findViewById(R.id.mapView);
        geocoder = new Geocoder(this, Locale.getDefault());

        // 🔥 Fix: Ensure list is initialized
        searchLocations = new ArrayList<>();
        addressList = new ArrayList<>();

        // Setup the map
        setupMap();

        // Request permissions
        requestPermissions();

        // Setup search
        setupSearch();

        // Initialize the "Pin Current Location" Button
        Button pinCurrentLocationButton = findViewById(R.id.pinCurrentLocationButton);
        pinCurrentLocationButton.setOnClickListener(v -> pinUserCurrentLocation());


        // Confirm button action
        confirmLocationButton.setOnClickListener(v -> {
            String selectedLocation = locationSearchView.getQuery().toString(); // Get address from search bar

            if (selectedLocation.isEmpty()) {
                Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get longitude & latitude from the pinned marker
            double selectedLongitude = pinnedMarker.getPosition().getLongitude();
            double selectedLatitude = pinnedMarker.getPosition().getLatitude();

            // Send the selected location to `activity_create_event`
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedLocation", selectedLocation);
            resultIntent.putExtra("longitude", selectedLongitude);
            resultIntent.putExtra("latitude", selectedLatitude);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

        private void requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // **🔥 Center the map on the Philippines**
        GeoPoint philippinesCenter = new GeoPoint(12.8797, 121.7740);
        mapView.getController().setZoom(6.5);
        mapView.getController().setCenter(philippinesCenter);

        // **🔥 Add the Custom Color Filter**
        ColorFilteredTilesOverlay customOverlay = new ColorFilteredTilesOverlay(mapView.getTileProvider());
        mapView.getOverlays().add(0, customOverlay);

        // **🔥 Enable User Location**
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Allow User to Tap and Pin a Location
        mapView.setOnTouchListener((v, event) -> {
            GeoPoint tappedPoint = (GeoPoint) mapView.getProjection().fromPixels((int) event.getX(), (int) event.getY());
            pinLocation(tappedPoint);
            updateSearchBar(tappedPoint);
            return false;
        });
    }


    private void pinLocation(GeoPoint geoPoint) {
        if (pinnedMarker != null) {
            pinnedMarker.remove(mapView);
        }
        pinnedMarker = new Marker(mapView);
        pinnedMarker.setPosition(geoPoint);
        pinnedMarker.setTitle("Selected Location");
        pinnedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(pinnedMarker);
        mapView.invalidate();
    }

    private void updateSearchBar(GeoPoint geoPoint) {
        try {
            List<Address> addresses = geocoder.getFromLocation(geoPoint.getLatitude(), geoPoint.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                locationSearchView.setQuery(address, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupSearch() {
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, searchLocations);
        searchResults.setAdapter(adapter);

        locationSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchLocation(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchLocation(newText);
                return false;
            }
        });

        searchResults.setOnItemClickListener((parent, view, position, id) -> {
            String selectedPlace = searchLocations.get(position);
            locationSearchView.setQuery(selectedPlace, false);
            searchResults.setVisibility(ListView.GONE);
            locateOnMap(selectedPlace);
        });
    }

    private void searchLocation(String location) {
        try {
            // **🔥 Limit Search to the Philippines**
            addressList = geocoder.getFromLocationName(location + ", Philippines", 5);
            searchLocations.clear();

            if (addressList != null) {
                for (Address address : addressList) {
                    searchLocations.add(address.getAddressLine(0));
                }
                adapter.notifyDataSetChanged();
                searchResults.setVisibility(ListView.VISIBLE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        return false;
    }


    private void locateOnMap(String location) {
        for (Address address : addressList) {
            if (address.getAddressLine(0).toLowerCase().contains(location.toLowerCase())) {
                GeoPoint geoPoint = new GeoPoint(address.getLatitude(), address.getLongitude());
                pinLocation(geoPoint);
                mapView.getController().animateTo(geoPoint);
                mapView.getController().setZoom(15);
                return;
            }
        }
        Toast.makeText(this, "No matching place found", Toast.LENGTH_SHORT).show();
    }
    private void pinUserCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS_REQUEST_CODE);
            return;
        }

        if (myLocationOverlay != null) {
            GeoPoint userLocation = myLocationOverlay.getMyLocation();
            if (userLocation != null) {
                pinLocation(userLocation);
                mapView.getController().animateTo(userLocation);
                mapView.getController().setZoom(15);
                updateSearchBar(userLocation);
            } else {
                Toast.makeText(this, "Getting location... Please wait.", Toast.LENGTH_SHORT).show();
                myLocationOverlay.enableMyLocation(); // Try re-enabling location
            }
        } else {
            Toast.makeText(this, "Location service unavailable. Try restarting the app.", Toast.LENGTH_LONG).show();
        }
    }



}



