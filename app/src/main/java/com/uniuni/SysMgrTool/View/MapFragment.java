package com.uniuni.SysMgrTool.View;

// MapFragment.java

import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.common.MyClusterRenderer;
import com.uniuni.SysMgrTool.common.ResponseCallBack;
import com.uniuni.SysMgrTool.common.Result;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.List;

import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

public class MapFragment extends Fragment implements OnMapReadyCallback, LocationListener {

    private  MapView mapView;
    private GoogleMap googleMap;

    private LocationManager locationManager;

    private ClusterManager<DeliveryInfo> clusterManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        try {
            MapsInitializer.initialize(requireActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        mapView.onResume();
        // 检查位置权限
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 1);
        } else {
            getLocation();
        }

        return view;
    }

    private void getLocation() {
        try {
            locationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, this);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<DeliveryInfo> loadData()
    {
        //Get package list from cache, if empty, try to load from db, if still empty, try to load from server
        ArrayList<DeliveryInfo> lst = MySingleton.getInstance().getdDeliveryinfoMgr().getListDeliveryInfo();
        if (lst.isEmpty())
        {
            //try to load data from db
            MySingleton.getInstance().getdDeliveryinfoMgr().loadDeliveryInfo(new ResponseCallBack<List<DeliveryInfo>>(){
                @Override
                public void onComplete(Result<List<DeliveryInfo>> result) {
                    Result.Success success = (Result.Success) result;
                    if (success.data != null) {
                        List<DeliveryInfo> lst = (List<DeliveryInfo>) success.data;
                        if (lst.isEmpty())
                        {
                            //try to load data from server
                            MySingleton.getInstance().getdDeliveryinfoMgr().getDeliveryInfo(MySingleton.getInstance().getLoginInfo().loginId);
                        }
                    }
                }

                @Override
                public void onFail(Result<List<DeliveryInfo>> result) {}
            });
        }

        return lst;
    }

    @Override
    public void onLocationChanged(Location location) {
        // 当位置改变时，这里可以获取到最新的位置
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // 使用经纬度数据
        LatLng markerLatLng = new LatLng(latitude, longitude);
        //googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng, 12));
    }

    @Override
    public void onProviderDisabled(String provider) {
        // 当GPS定位提供者被用户关闭时，会调用这个方法
    }

    @Override
    public void onProviderEnabled(String provider) {
        // 当GPS定位提供者被用户开启时，会调用这个方法
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // 定位提供者状态改变时，会调用这个方法
    }

    void test()
    {
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

            clusterManager.addItem(offsetItem);
        }

        clusterManager.cluster();
    }


    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if (ActivityCompat.checkSelfPermission(MySingleton.getInstance().getCtx(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MySingleton.getInstance().getCtx(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String permission = Manifest.permission.ACCESS_FINE_LOCATION;
            String[] permission_list = new String[1];
            permission_list[0] = permission;
            ActivityCompat.requestPermissions(this.getActivity(), permission_list, 1);


            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        googleMap.setMyLocationEnabled(true);

        // Initialize ClusterManager
        clusterManager = new ClusterManager<DeliveryInfo>(this.getContext(), googleMap);
        // 设置自定义的 ClusterRenderer
        clusterManager.setRenderer(new MyClusterRenderer<>(getContext(), googleMap, clusterManager));

        // 设置地图拖动和缩放事件监听器，以便更新 ClusterManager
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnCameraMoveListener(()->{clusterManager.cluster();});

        ArrayList<DeliveryInfo> lst = loadData();

        LatLng firstMarker = null;
        for (DeliveryInfo info :lst)
        {
            if (MySingleton.getInstance().getmDeliveredPackagesMgr().exit(info.getOrderSn()))
                continue; //only display packages that are not delivered

            //addCustomMarker(info);
            clusterManager.addItem(info);

            if (firstMarker == null) {
                firstMarker = new LatLng(info.getLatitude(), info.getLongitude());
            }

        }

        clusterManager.cluster();

        test();


        // 添加标记点，这里是示例，你需要根据你的数据添加标记
        if (firstMarker != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstMarker, 12));
        }

        clusterManager.setOnClusterClickListener(cluster->{
            Toast.makeText(this.getActivity(), "Cluster clicked with " + cluster.getSize() + " items", Toast.LENGTH_SHORT).show();
            return false;
        });

        clusterManager.setOnClusterItemClickListener(item->{
            Toast.makeText(this.getActivity(), "Package number: " + item.getRouteNumber(), Toast.LENGTH_SHORT).show();
            //show the package delivery ui
            Intent intent = new Intent(getActivity(), CameraActivity.class);
            intent.putExtra("order_id", item.getOrderId());
            startActivity(intent);

            return false;
        });

    }


    private void addCustomMarker(DeliveryInfo pkg) {
        // Create a custom marker bitmap with the package number
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(
                getMarkerBitmapFromView(pkg.getRouteNumber()));

        // Add the marker to the map
        Marker m = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(pkg.getLatitude(), pkg.getLongitude()))
                .icon(icon).title(pkg.getRouteNumber()));

        googleMap.setOnMapLoadedCallback(()->{
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
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
