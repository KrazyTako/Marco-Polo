package com.polo.marco.marcopoloapp.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.solver.widgets.Snapshot;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.Toast;

import com.facebook.login.LoginManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.polo.marco.marcopoloapp.api.notifications.CustomListAdapter;
import com.polo.marco.marcopoloapp.api.notifications.Notifications;
import com.polo.marco.marcopoloapp.R;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationListener;
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
import com.polo.marco.marcopoloapp.firebase.models.Marco;
import com.polo.marco.marcopoloapp.firebase.models.Polo;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        NavigationView.OnNavigationItemSelectedListener,
        GoogleMap.OnMarkerClickListener {

    //Google maps stuff
    private static GoogleMap mMap;
    private GoogleApiClient client;
    private LocationRequest locationRequest;
    private Location lastLocation = null;
    private Marker currentLocationMarker = null;
    final private int PERMISSIONS_REQUEST_CODE = 124;

    //Hamburger menu stuff
    private NavigationView mDrawer;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private DatabaseReference databaseUsers = FirebaseDatabase.getInstance().getReference("users");
    private DatabaseReference databaseMarcos = FirebaseDatabase.getInstance().getReference("marcos");
    private DatabaseReference databasePolos = FirebaseDatabase.getInstance().getReference("polos");

    //
    public static boolean mIsInForegroundMode = false;

    // Active Polo Session Variables
    public boolean hasActivePolo = false;
    public boolean preventReloop = false;
    public String activePoloerUserId = "000000000000000000000000";
    public static ArrayList<Marker> mapMarkerArray = new ArrayList<>();

    //test
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        mIsInForegroundMode = true;

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawer = (NavigationView) findViewById(R.id.main_drawer);
        mDrawer.setNavigationItemSelectedListener(this);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.open, R.string.close);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        final SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        databaseMarcos.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                        Marco marco = child.getValue(Marco.class);
                        if (marco.getStatus() == true)
                            addMarcoMarker(marco.getLatitude(), marco.getLongitude(), marco.getMessage(), marco.getName(), marco.getUserId(), false);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        databasePolos.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (final DataSnapshot child : dataSnapshot.getChildren()) {
                        for (final DataSnapshot childs : child.getChildren()) {
                            Polo polo = childs.getValue(Polo.class);
                            if (child.getKey().equalsIgnoreCase(LoginActivity.currentUser.getUserId())) {
                                if (polo.getMessage().equalsIgnoreCase("delete") || preventReloop) {
                                    preventReloop = false;
                                    return;
                                }

                                if (!hasActivePolo && distance(LoginActivity.currentUser.getLatitude(), LoginActivity.currentUser.getLongitude(), polo.getLatitude(), polo.getLongitude()) <= 0.01) {
                                    Toast.makeText(getBaseContext(), "You are already in the same location!", Toast.LENGTH_LONG).show();
                                    removeMarcoMarker(getMarker(childs.getKey()));
                                    removeMarcoMarker(getMarker(activePoloerUserId));

                                    databasePolos.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot snap) {
                                            if (snap.exists()) {
                                                for (DataSnapshot c : snap.getChildren()) {
                                                    if (c.exists()) {
                                                        if (c.getKey().equalsIgnoreCase(LoginActivity.currentUser.getUserId())) {
                                                            for (DataSnapshot d : c.getChildren()) {
                                                                if (d.exists()) {
                                                                    databasePolos.child(LoginActivity.currentUser.getUserId()).child(d.getKey()).child("message").setValue("DELETE");
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });

                                    preventReloop = true;
                                    return;
                                }

                                Marker m = getMarker(childs.getKey());
                                if (!hasActivePolo && m == null) {
                                    activePoloerUserId = childs.getKey();
                                    addMarcoMarker(polo.getLatitude(), polo.getLongitude(), polo.getMessage(), polo.getSenderName(), childs.getKey(), true);

                                    databasePolos.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot snap) {
                                            if (snap.exists()) {
                                                if (snap.child(childs.getKey()).exists()) {
                                                    Log.d("MapsActivity", "Now has active polo");
                                                    hasActivePolo = true;
                                                }
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                } else if (m != null) {
                                    m.setPosition(new LatLng(polo.getLatitude(), polo.getLongitude()));
                                    if (polo.getResponded() == true) {
                                        hasActivePolo = true;
                                    }
                                }

                                if (distance(LoginActivity.currentUser.getLatitude(), LoginActivity.currentUser.getLongitude(), polo.getLatitude(), polo.getLongitude()) <= 0.01) {
                                    Toast.makeText(getBaseContext(), "You have found eachother, congrats!", Toast.LENGTH_LONG).show();
                                    hasActivePolo = false;

                                    if (m != null)
                                        removeMarcoMarker(m);


                                    databasePolos.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot snapshot) {
                                            if (snapshot.hasChild(LoginActivity.currentUser.getUserId())) {
                                                databasePolos.child(LoginActivity.currentUser.getUserId()).child(activePoloerUserId).child("message").setValue("DELETE");
                                            }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });

                                    activePoloerUserId = "000000000000000000000000";
                                }
                            }
                        }
                    }

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsInForegroundMode = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsInForegroundMode = true;
    }

    //Function that's called when the marco button is clicked
    public void onClickBtnMarco(View view) {
        if (hasActivePolo) {
            Toast.makeText(this, getResources().getString(R.string.has_active_polo), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, MarcoActivity.class);
        startActivity(intent);
    }

    //Handle action bar items only
    //The regular menu items are handled in OnNavigationItemClickListener()
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        if (item.getItemId() == R.id.nav_notifications) {
            startActivity(new Intent(MapsActivity.this, Notifications.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //Inflate the menu so that action buttons are visible while
    //using a navigation drawer
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.navigation_menu, menu);
        return true;
    }

    //Sets all menu options other than notifications to invisible so that only notifications
    //icon is visible in the action bar.  MUST be updated if other menu items are added later.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.nav_account);
        item.setVisible(false);
        item = menu.findItem(R.id.nav_privacy_policy);
        item.setVisible(false);
        item = menu.findItem(R.id.nav_friends);
        item.setVisible(false);
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    //Ensures drawer toggle behavior if the state of the app changes
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permission is granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (client == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        //SettingsActivity settingsActivity = new SettingsActivity();
                        //settingsActivity.showSyncDialog();
                    }
                }
                //permission is denied
                else {
                    Toast.makeText(this, "Permission was Denied!", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

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
        mMap.setOnMarkerClickListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }
    }

    protected synchronized void buildGoogleApiClient() {
        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        client.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        getCurrentLocation();
        if (lastLocation != null) {
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }

        locationRequest = new LocationRequest();

        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, this);
        }
        Intent intent = this.getIntent();
        Bundle extras = intent.getExtras();
        if (!extras.isEmpty()) {
            boolean hasMarkerLocations = extras.containsKey("latitude");
            if (hasMarkerLocations) {
                LatLng extraLatlng = new LatLng(extras.getDouble("latitude"), extras.getDouble("longitude"));
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(extraLatlng);
                mMap.addMarker(markerOptions);
            }
        }
    }

    //Getting current location
    private void getCurrentLocation() {
        //Creating a location object
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(client);
        }
    }

    public void checkLocationPermission() {
        List<String> permissionsNeeded = new ArrayList<String>();
        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("android.permission.ACCESS_FINE_LOCATION");
        if (!addPermission(permissionsList, Manifest.permission.READ_CONTACTS))
            permissionsNeeded.add("android.permission.READ_CONTACTS");

        if (permissionsNeeded.size() > 0)
            ActivityCompat.requestPermissions(this,
                    permissionsList.toArray(new String[permissionsList.size()]),
                    PERMISSIONS_REQUEST_CODE);
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
                return false;
        }
        return true;
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        dist = dist * 1.609344;

        return (dist);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::	This function converts decimal degrees to radians						 :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::	This function converts radians to decimal degrees						 :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private static double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;

        if (currentLocationMarker != null) {
            currentLocationMarker.remove();
        }

        LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latlng);
        markerOptions.title("Current Location");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));

        currentLocationMarker = mMap.addMarker(markerOptions);

/*        mMap.moveCamera(CameraUpdateFactory.newLatLng(latlng));
        mMap.animateCamera(CameraUpdateFactory.zoomBy(10));*/

        /*if (client != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
        }*/

        if (LoginActivity.currentUser != null) {
            //update User in DB
            LoginActivity.currentUser.setLatitude(location.getLatitude());
            LoginActivity.currentUser.setLongitude(location.getLongitude());

            Log.d("MapsActivity", "hasActivePolo: " + hasActivePolo);
            if (hasActivePolo) {
                databasePolos.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.hasChild(activePoloerUserId)) {
                            databasePolos.child(activePoloerUserId).child(LoginActivity.currentUser.getUserId()).child("latitude").setValue(LoginActivity.currentUser.getLatitude());
                            databasePolos.child(activePoloerUserId).child(LoginActivity.currentUser.getUserId()).child("longitude").setValue(LoginActivity.currentUser.getLongitude());
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        }
    }

    //This is where we handle the clicks for the drawer menu items
    //Each option creates a new activity, see corresponding .java/.xml files
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        Intent intent = null;
        //if (!friendsRead) {
        //testRead();
        //friendsRead = true;
        //}
        if (menuItem.getItemId() == R.id.nav_account) {
            intent = new Intent(this, SettingsActivity.class);
            mDrawerLayout.closeDrawer(GravityCompat.START);
            startActivity(intent);
            return true;
        }
        if (menuItem.getItemId() == R.id.nav_notifications) {
            intent = new Intent(this, Notifications.class);
            mDrawerLayout.closeDrawer(GravityCompat.START);
            startActivity(intent);
            return true;
        }
        if (menuItem.getItemId() == R.id.nav_privacy_policy) {
            intent = new Intent(this, PrivacyPolicyActivity.class);
            mDrawerLayout.closeDrawer(GravityCompat.START);
            startActivity(intent);
            return true;
        }
        if (menuItem.getItemId() == R.id.nav_friends) {
            intent = new Intent(this, FriendsListActivity.class);
            mDrawerLayout.closeDrawer(GravityCompat.START);
            startActivity(intent);
            return true;
        }
        return false;
    }

    public static void addMarcoMarker(double lat, double lng, String message, String sender, String userId, boolean privat) {
        LatLng extraLatlng = new LatLng(lat, lng);
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(extraLatlng);

        if (privat) {
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        }

        markerOptions.snippet(privat + "|" + sender + "|" + message + "|" + userId).title(userId);
        Marker marker = mMap.addMarker(markerOptions);
        mapMarkerArray.add(marker);
    }

    public static Marker getMarker(String userId) {
        for (final Marker marker : mapMarkerArray) {
            if (marker.getTitle() != null && marker.getTitle().equalsIgnoreCase(userId)) {
                return marker;
            }
        }
        return null;
    }

    public static void removeMarcoMarker(Marker marker) {
        if (marker != null) {
            mapMarkerArray.remove(marker);
            marker.remove();
        }
    }

    public static Marker lastMarkerClicked = null;

    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.setInfoWindowAnchor(10000, 10000);

        if (hasActivePolo) {
            Toast.makeText(this, getResources().getString(R.string.has_active_polo), Toast.LENGTH_LONG).show();
            return false;
        }

        lastMarkerClicked = marker;
        Intent intent = new Intent(this, PoloActivity.class);

        if (marker.getSnippet() != null && marker.getSnippet().contains("|")) {
            String[] data = marker.getSnippet().split("\\|");
            intent.putExtra("private", data[0]);
            intent.putExtra("sender", data[1]);
            intent.putExtra("message", data[2]);
            intent.putExtra("userId", data[3]);
        }
        startActivity(intent);
        return false;
    }
}
