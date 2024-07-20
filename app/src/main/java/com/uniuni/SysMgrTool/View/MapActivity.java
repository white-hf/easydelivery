package com.uniuni.SysMgrTool.View;

// MapFragment.java

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.uniuni.SysMgrTool.Event.Event;
import com.uniuni.SysMgrTool.Event.EventConstant;
import com.uniuni.SysMgrTool.Event.Subscriber;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.common.MyClusterRenderer;
import com.uniuni.SysMgrTool.common.ResponseCallBack;
import com.uniuni.SysMgrTool.common.Result;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.maps.android.clustering.ClusterManager;
import com.uniuni.SysMgrTool.dao.PackageEntity;
import com.uniuni.SysMgrTool.manager.DeliveryinfoMgr;

public class MapActivity extends AppCompatActivity implements Subscriber, OnMapReadyCallback, LocationListener {

    private String STRING_DELIVERY_SUCCESS = null;
    private MapView mapView;
    private TextView txtViewDeliverySummary;
    private GoogleMap googleMap;

    private LocationManager locationManager;

    private ClusterManager<DeliveryInfo> clusterManager;

    private double mLatitude;
    private double mLongitude;

    private Location mLastLocation = new Location("");

    private MyClusterRenderer<DeliveryInfo> myClusterRenderer;
    private LatLng savedPosition;

    private CameraFragment mCameraFragment;
    private ListViewFragment mListFragment = new ListViewFragment();
    private final Bundle args = new Bundle();
    public static final String TAG = "MapFragment";
    private static final int MAX_ITEMS_PER_CLUSTER = 20;
    private static final int DEFAULT_ZOOM_LEVEL = 12;

    @Nullable
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        View content = findViewById(android.R.id.content);
        content.setBackgroundColor(Color.WHITE);

        STRING_DELIVERY_SUCCESS = getResources().getString(R.string.save_successfully);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        FloatingActionButton floatButton = findViewById(R.id.floatingActionButton);
        floatButton.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, mListFragment)
                    .addToBackStack(null)
                    .commit();
        });

        txtViewDeliverySummary = findViewById(R.id.topTextView);
        txtViewDeliverySummary.setText(String.format(getResources().getString(R.string.delivering_d_pending_d),
                MySingleton.getInstance().getDeliveryinfoMgr().size(),
                MySingleton.getInstance().getPendingPackagesMgr().size()));

        EditText seachEditText = findViewById(R.id.searchEditText);
        seachEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                performSearch(textView.getText().toString().trim());
                return true;
            }
            return false;
        });

        try {
            MapsInitializer.initialize(this.getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        mapView.onResume();
        // 检查位置权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 1);
        } else {
            getLocation();
        }

        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_UPLOAD_FAILURE, this);
        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS, this);
        MySingleton.getInstance().getPublisher().subscribe(EventConstant.EVENT_TO_LOGIN, this);
    }

    private void performSearch(String routeNumber) {
        DeliveryInfo info = clusterManager.getAlgorithm().getItems().stream().filter(item -> item.getRouteNumber().equals(routeNumber)).findFirst().orElse(null);
        if (info != null) {
            savedPosition = null;
            savedPosition = new LatLng(info.getLatitude(), info.getLongitude());
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, cameraPosition.zoom));
            showCameraFragment(info);
        }
    }

    private void getLocation() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            String provider = locationManager.getBestProvider(criteria, true);
            locationManager.requestLocationUpdates((provider != null) ? provider : LocationManager.GPS_PROVIDER, 1000, 5, this);
        } catch (SecurityException e) {
            Log.e(TAG, "getLocation: " + e.getMessage());
        }
    }

    private ArrayList<DeliveryInfo> loadData() {
        //Get package list from cache, if empty, try to load from db, if still empty, try to load from server
        ArrayList<DeliveryInfo> lst = MySingleton.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
        if (lst.isEmpty()) {
            //try to load data from db
            MySingleton.getInstance().getDeliveryinfoMgr().loadDeliveryInfo(new ResponseCallBack<List<DeliveryInfo>>() {
                @Override
                public void onComplete(Result<List<DeliveryInfo>> result) {
                    Result.Success success = (Result.Success) result;
                    if (success.data != null) {
                        List<DeliveryInfo> lst = (List<DeliveryInfo>) success.data;
                        if (lst.isEmpty()) {
                            //try to load data from server
                            MySingleton.getInstance().getDeliveryinfoMgr().getDeliveryInfo(MySingleton.getInstance().getLoginInfo().loginId);
                        }
                    }
                }

                @Override
                public void onFail(Result<List<DeliveryInfo>> result) {
                }
            });
        }

        return lst;
    }

    @Override
    public void onLocationChanged(Location location) {

        mLatitude = location.getLatitude();
        mLongitude = location.getLongitude();

//        LatLng markerLatLng = new LatLng(mLatitude, mLongitude);
//        float distance = location.distanceTo(mLastLocation);
//
//        if (distance > 2) {
//            CameraPosition cameraPosition = googleMap.getCameraPosition();
//            float zoomLevel = cameraPosition.zoom;
//            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng, zoomLevel));
//
//        }

        mLastLocation = location;
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    void test() {
        MySingleton.getInstance().getDeliveryinfoMgr().getListDeliveryInfo().clear();
        LatLng centerLocation = new LatLng(37.7749, -122.4194); // 旧金山的经纬度
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerLocation, 10));
        // Set some lat/lng coordinates to start with.

        // Add ten cluster items in close proximity, for purposes of this example.
        for (int i = 0; i < 10; i++) {
            double lat = 37.7749 + i * 0.01;
            double lng = -122.4194 + i * 0.01;
            DeliveryInfo offsetItem = new DeliveryInfo();
            offsetItem.setLatitude(lat);
            offsetItem.setLongitude(lng);
            offsetItem.setRouteNumber("Title " + i);
            offsetItem.setName("Snippet " + i);
            offsetItem.setOrderId((long) (100 + i));
            offsetItem.setOrderSn("Tracking " + i);
            offsetItem.setAddress("Address " + i);

            clusterManager.addItem(offsetItem);

            MySingleton.getInstance().getDeliveryinfoMgr().getListDeliveryInfo().add(offsetItem);
        }

        clusterManager.cluster();
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private String extractApartmentNumber(String address) {

        if (address == null || address.isEmpty()) {
            return "";
        }

        Matcher matcher = NUMBER_PATTERN.matcher(address);
        String[] numbers = new String[2];
        int count = 0;

        while (matcher.find() && count < 2) {
            numbers[count] = matcher.group(1).trim();
            count++;
        }

        if (count < 2) {
            numbers[count] = "";
        }

        return numbers[1];
    }

    private void showClusterItemListDialog(Cluster<DeliveryInfo> cluster) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cluster Items");

        List<String> itemTitles = new ArrayList<>();
        final List<DeliveryInfo> clusterItems = new ArrayList<>(cluster.getItems());
        clusterItems.sort((item1, item2) -> {
            Integer unitNumber1 = item1.getCivilNumber();
            Integer unitNumber2 = item2.getCivilNumber();
            return unitNumber1.compareTo(unitNumber2);
        });

        for (DeliveryInfo item : clusterItems) {
            String unitNumber = extractApartmentNumber(item.getAddress());
            if (!unitNumber.isEmpty())
                itemTitles.add(item.getTitle() + " (" + unitNumber + ")");
            else
                itemTitles.add(item.getTitle());
        }

        builder.setItems(itemTitles.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DeliveryInfo selectedItem = clusterItems.get(which);
                showCameraFragment(selectedItem);
            }
        });

        builder.show();
    }

    private void showCameraFragment(DeliveryInfo item) {
        //show the package delivery ui
        args.clear();
        args.putLong("order_id", item.getOrderId());
        args.putDouble("latitude", mLatitude);
        args.putDouble("longitude", mLongitude);
        mCameraFragment = new CameraFragment();

        mCameraFragment.setArguments(args);

        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, mCameraFragment)
                .addToBackStack(TAG)
                .commit();

    }

    private void initClusterManager() {
        clusterManager = null;
        myClusterRenderer = null;

        clusterManager = new ClusterManager<DeliveryInfo>(getApplicationContext(), googleMap);
        myClusterRenderer = new MyClusterRenderer<>(getApplicationContext(), googleMap, clusterManager);
        clusterManager.setRenderer(myClusterRenderer);

        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnCameraMoveListener(() -> {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            MyClusterRenderer<DeliveryInfo> myClusterRenderer = (MyClusterRenderer) clusterManager.getRenderer();
            myClusterRenderer.setZoomLevel(cameraPosition.zoom);
            clusterManager.cluster();
        });


        ArrayList<DeliveryInfo> lst = loadData();

        LatLng firstMarker = null;
        for (DeliveryInfo info : lst) {
            if (MySingleton.getInstance().getPendingPackagesMgr().exit(info.getOrderSn()))
                continue; //only display packages that are not delivered and not in pending list

            //addCustomMarker(info);
            clusterManager.addItem(info);

            if (firstMarker == null) {
                firstMarker = new LatLng(info.getLatitude(), info.getLongitude());
            }
        }

        clusterManager.cluster();

        //if (lst.isEmpty())
        //   test();

        if (firstMarker != null) {
            mLastLocation.setLongitude(firstMarker.longitude);
            mLastLocation.setLatitude(firstMarker.latitude);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstMarker, DEFAULT_ZOOM_LEVEL));
        }

        clusterManager.setOnClusterClickListener(cluster -> {
            if (cluster.getSize() < MAX_ITEMS_PER_CLUSTER) {
                showClusterItemListDialog(cluster);
                return true;
            }

            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (ClusterItem item : cluster.getItems()) {
                builder.include(item.getPosition());
            }
            LatLngBounds bounds = builder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            return true;
        });

        clusterManager.setOnClusterItemClickListener(item -> {
            //show the package delivery ui
            showCameraFragment(item);
            return false;
        });
    }

    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if (ActivityCompat.checkSelfPermission(MySingleton.getInstance().getCtx(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MySingleton.getInstance().getCtx(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String permission = Manifest.permission.ACCESS_FINE_LOCATION;
            String[] permission_list = new String[1];
            permission_list[0] = permission;
            ActivityCompat.requestPermissions(this, permission_list, 1);


            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        googleMap.setMyLocationEnabled(true);

        initClusterManager();

        Log.d(TAG, "clusterManager is not null, item count: " + clusterManager.getAlgorithm().getItems().size());
        if (googleMap != null && savedPosition != null) {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, cameraPosition.zoom));
        }

    }

    public void removeCustomMarker(DeliveryInfo pkg) {
        if (clusterManager != null && pkg != null) {
            clusterManager.removeItem(pkg);
            clusterManager.cluster();
        }
    }

    private void addCustomMarker(DeliveryInfo pkg) {
        // Create a custom marker bitmap with the package number
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(
                getMarkerBitmapFromView(pkg.getRouteNumber()));

        // Add the marker to the map
        Marker m = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(pkg.getLatitude(), pkg.getLongitude()))
                .icon(icon).title(pkg.getRouteNumber()));

        googleMap.setOnMapLoadedCallback(() -> {
            if (m != null)
                m.showInfoWindow();
        });
    }

    private Bitmap getMarkerBitmapFromView(String number) {
        // Load the bitmap from drawable resource
        Bitmap backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.package_marker);

        // Create a mutable bitmap with same dimensions as backgroundBitmap
        Bitmap bitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Draw the package number on the bitmap
        Canvas canvas = new Canvas(bitmap);

        // Initialize the paint object
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(64); // Adjust text size as needed
        paint.setTextAlign(Paint.Align.CENTER);

        // Calculate text position
        float xPos = canvas.getWidth() / 2;
        float yPos = (canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2);

        // Draw the text
        canvas.drawText(number, xPos, yPos, paint);

        return bitmap;
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (googleMap != null && savedPosition != null) {
            CameraPosition cameraPosition = googleMap.getCameraPosition();
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPosition, cameraPosition.zoom));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();

        if (googleMap != null) {
            savedPosition = googleMap.getCameraPosition().target; // 保存当前地图中心点
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up ClusterManager

        if (myClusterRenderer != null) {
            myClusterRenderer.onRemove(); // Clean up custom renderer
            myClusterRenderer = null; // Release reference
        }

        if (clusterManager != null) {
            ArrayList<DeliveryInfo> deliveryinfoLst = MySingleton.getInstance().getDeliveryinfoMgr().getListDeliveryInfo();
            for (DeliveryInfo info : deliveryinfoLst) {
                boolean b = clusterManager.removeItem(info);
                Log.d(TAG, "remove item:" + info.getTitle() + " " + b);
            }

            clusterManager.clearItems(); // Clear added items
            clusterManager = null; // Release reference
        }

        MySingleton.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_UPLOAD_FAILURE, this);
        MySingleton.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_UPLOAD_SUCCESS, this);
        MySingleton.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_SAVE_DELIVERY_SUCCESS, this);
        MySingleton.getInstance().getPublisher().unsubscribe(EventConstant.EVENT_TO_LOGIN, this);


        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void receive(Event event) {
        DeliveryinfoMgr deliveryinfoMgr = MySingleton.getInstance().getDeliveryinfoMgr();
        switch (event.getEventType()) {
            case EventConstant.EVENT_UPLOAD_FAILURE:
                Toast.makeText(this, "Uploading the data of delivered packages failed", Toast.LENGTH_SHORT).show();

                break;
            case EventConstant.EVENT_TO_LOGIN:
                //We have to couple the ui code here
                AlertDialog alertDialog = LoginDialog.init(this);
                alertDialog.show();
                break;
            case EventConstant.EVENT_SAVE_DELIVERY_SUCCESS: {
                PackageEntity packageEntity = (PackageEntity) event.getMessage();

                //We has to remove the package from local cache, because the package is delivered.
                //the data in the local cache is not updated.
                DeliveryInfo info = deliveryinfoMgr.get(packageEntity.orderId);
                if (info != null) {
                    this.removeCustomMarker(info);
                    deliveryinfoMgr.getListDeliveryInfo().remove(info);

                    String msg = String.format(getResources().getString(R.string.save_successfully), info.getRouteNumber());
                    updateDeliveryInfo(msg);

                }
                else
                {
                    FileLog.getInstance().writeLog("When handling save_delivery_success event,failed to get packageEntity.orderId:" + packageEntity.orderId);
                }
                break;
            }
            case EventConstant.EVENT_UPLOAD_SUCCESS: {
                PackageEntity packageEntity = (PackageEntity) event.getMessage();
                String msg = String.format(STRING_DELIVERY_SUCCESS, packageEntity.trackingId);
                updateDeliveryInfo(msg);
                break;
            }
        }
    }

    private void updateDeliveryInfo(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        txtViewDeliverySummary.setText(String.format(getResources().getString(R.string.delivering_d_pending_d),
                MySingleton.getInstance().getDeliveryinfoMgr().size(),
                MySingleton.getInstance().getPendingPackagesMgr().size()));
    }

    public void removeThumbnail(int index) {
        mCameraFragment.removeThumbnail(index);
    }
}
