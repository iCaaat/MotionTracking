package com.example.myapplication;

import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.MarkerOptions;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 方向传感器相关
    private SensorManager sensorManager;
    private Sensor rotationSensor, gravitySensor, magneticFieldSensor;

    // 用于存储重力和地磁数据
    private float[] gravity = null;
    private float[] geomagnetic = null;
    private float currentAzimuth = 0.0f;

    private static final String LOCATION_MARKER_FLAG = "mylocation";  // 位置标记的标识符

    private Marker mLocMarker;  // 定位图标

    private MapView mapView;
    private AMap aMap;
    //声明AMapLocationClient类对象
    private AMapLocationClient locationClient;

    private ArrayList<LatLng> trackPoints = new ArrayList<>();
    private boolean isTracking = false;
    private Button btnLocate;
    // AMapLocationClientOption对象用来设置发起定位的模式和相关参数。
    private AMapLocationClientOption locationOption = new AMapLocationClientOption();
    private float currentZoomLevel = 15.0f;// 用于保存用户的缩放级别
    private AMap.OnMyLocationChangeListener mListener;
    //初始化定位蓝点样式类
    MyLocationStyle myLocationStyle;
    private double totalDistance = 0; // 轨迹总距离
    private double currentTotalDistance = 0; // 本次运动距离
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 隐私合规接口与权限申请
        privacyAbout();



        // 初始化地图
        initMap(savedInstanceState);

        // 注册传感器
        sensorRegister();

        // 初始化定位
        initLocation();

        // 加载轨迹数据并绘制
        List<LatLng> loadedTrack = loadTrackFromFile();
        if (loadedTrack != null) {
            restoreTrack(loadedTrack);
        }

        // 定位按钮点击事件
        btnLocate.setOnClickListener(v -> locateCurrentPosition());

        // ======
        Toast.makeText(MainActivity.this, "128初始化成功", Toast.LENGTH_SHORT).show();
    }

    // 隐私合规与权限申请
    private void privacyAbout() {
        //隐私合规接口
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        AMapLocationClient.updatePrivacyAgree(this, true);
        AMapLocationClient.updatePrivacyShow(this, true, true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }

//        // Android 13及以上需要请求后台定位权限
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 101);
//        }
    }

    private void sensorRegister() {
        // 获取传感器服务
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // ==========
        if (sensorManager == null) {
            Toast.makeText(MainActivity.this, "119传感器为空", Toast.LENGTH_SHORT).show();
        }
        // 获取传感器实例
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // 注册传感器监听器
        sensorManager.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        // 注册重力传感器和地磁传感器
        sensorManager.registerListener(sensorEventListener, gravitySensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorEventListener, magneticFieldSensor, SensorManager.SENSOR_DELAY_UI);

        Toast.makeText(MainActivity.this, "159传感器初始化成功", Toast.LENGTH_SHORT).show();
    }

    // 传感器监听
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
                    lastUpdateTime = currentTime;
                    // 获取方向传感器数据（rotation vector）
                    float[] rotationMatrix = new float[9];
                    float[] orientation = new float[3];

                    // 使用 getRotationMatrix 获取旋转矩阵
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        // 获取设备的朝向（方位角）
                        SensorManager.getOrientation(rotationMatrix, orientation);

                        // 将方位角转换为度数
                        currentAzimuth = (float) Math.toDegrees(orientation[0]); // 获取设备朝向的角度
//                        updateMapOrientation(); // 更新地图方向和标记
                        updateLocationMarkerOrientation(currentAzimuth); // 更新定位图标朝向
                    }
                }


            }
            // 如果有重力或地磁传感器的数据，则更新它们
            else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                gravity = event.values;  // 更新重力传感器数据
            }
            else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = event.values;  // 更新地磁传感器数据
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // 不需要做任何操作，默认空实现
        }
    };



    // 更新定位图标的方向
    private void updateLocationMarkerOrientation(float azimuth) {
        if (mLocMarker != null) {
            // 设置定位图标的旋转角度
            mLocMarker.setRotateAngle(-azimuth); // 旋转角度取反，使图标朝向正确
        }
    }

    // 添加定位图标
    private void addMarker(LatLng latlng) {
        if (mLocMarker != null) {
            return;
        }
        MarkerOptions options = new MarkerOptions();
        // 从 mipmap 目录加载定位图标资源
        options.icon(BitmapDescriptorFactory.fromResource(R.mipmap.navi_map_gps_locked)); // navi_map_gps_locked 是一个示例图标，可以根据需要替换
        options.anchor(0.5f, 0.5f); // 锚点设置为图标的中心
        options.position(latlng); // 设置定位图标的位置
        mLocMarker = aMap.addMarker(options); // 将图标添加到地图
        mLocMarker.setTitle(LOCATION_MARKER_FLAG); // 设置标记的标题
    }

    // 更新地图朝向
    private void updateMapOrientation() {
        // 使用当前的 azimuth（设备的朝向）更新地图的方向
        CameraUpdate cameraUpdate = CameraUpdateFactory.changeBearing(currentAzimuth);
        aMap.moveCamera(cameraUpdate);

        // 更新标记的朝向，确保它跟随设备旋转
        if (mLocMarker != null) {
            mLocMarker.setRotateAngle(currentAzimuth);  // 使用设备朝向更新定位标记的朝向
        }
    }

    // 权限处理
    // 当权限请求结果返回时，处理权限的结果
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == 100) {  // 你请求定位权限时的请求码
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // 权限被授予，继续初始化定位
//                initLocation();
//            } else {
//                // 权限被拒绝，提示用户无法使用定位功能
//                Toast.makeText(this, "没有定位权限，无法使用定位功能", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

    // 初始化地图
    private void initMap(Bundle savedInstanceState) {
        // 读取 SharedPreferences 中存储的总距离
        SharedPreferences sharedPreferences = getSharedPreferences("DistancePrefs", MODE_PRIVATE);
        totalDistance = sharedPreferences.getFloat("totalDistance", 0);  // 默认值为 0
        // 显示总距离
        TextView totalDistanceTextView = findViewById(R.id.totalDistanceTextView);
        totalDistanceTextView.setText(String.format("总距离: %.2f m", totalDistance));


        // 创建地图视图
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        if (aMap == null) {
            aMap = mapView.getMap();
        }

        // 设置初始缩放级别，避免每次定位更新时都调整缩放
        aMap.moveCamera(CameraUpdateFactory.zoomTo(15));

        // 获取当前的缩放级别
        currentZoomLevel = aMap.getCameraPosition().zoom;

        // 初始化定位按钮
        btnLocate = findViewById(R.id.btn_locate);

        Button startButton = findViewById(R.id.start_button);
        Button stopButton = findViewById(R.id.stop_button);

        aMap.getUiSettings().setZoomControlsEnabled(false);

        // 设置按钮点击事件
        startButton.setOnClickListener(v -> startTracking());
        stopButton.setOnClickListener(v -> stopTracking());

        // 初始化定位样式
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationIcon(BitmapDescriptorFactory.fromResource(R.mipmap.navi_map_gps_locked)); // 自定义定位图标
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_MAP_ROTATE);  // 设置为地图旋转模式
        myLocationStyle.interval(2000);  // 定位更新间隔
        myLocationStyle.showMyLocation(false);  // 显示定位小蓝点
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setMyLocationEnabled(true);  // 启用定位蓝点

        // 一开始不显示定位图标，后续定位成功后才添加
        aMap.setOnMyLocationChangeListener(location -> {
            if (mLocMarker == null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                addMarker(latLng);  // 添加定位标记
            }
        });
    }


    // 初始化定位
    private void initLocation() {
        try {
            // 初始化定位客户端
            locationClient = new AMapLocationClient(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "定位客户端初始化失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setInterval(2000); // 定位间隔，单位：毫秒
        locationClient.setLocationOption(option);


        locationClient.setLocationListener(new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation aMapLocation) {
                if (aMapLocation != null ) {
                    if (aMapLocation.getErrorCode() == 0) {
                        LatLng latLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                        if (isTracking) {
                            trackPoints.add(latLng);
                            updateTrack();
                        }
                        // 如果定位标记未添加，则添加标记
                        if (mLocMarker == null) {
                            addMarker(latLng);
                        } else {
                            // 更新标记位置
                            mLocMarker.setPosition(latLng);
                        }
                        // 获取当前的缩放级别
                        float currentZoomLevel = aMap.getCameraPosition().zoom;
                        // 移动相机到当前位置，但不改变缩放级别
                        aMap.moveCamera(CameraUpdateFactory.newLatLng(latLng)); // 移动并缩放到当前位置
//                        locationClient.startLocation();
//                        aMap.setMyLocationEnabled(true);  // 启用定位蓝点
//                        Toast.makeText(MainActivity.this, "定位初始化成功", Toast.LENGTH_SHORT).show();
                    }

                    // 确保只设置一次样式
                    if (!isTracking) {
                        myLocationStyle.showMyLocation(true);  // 设置是否显示定位小蓝点
                        aMap.setMyLocationStyle(myLocationStyle); // 设置样式
                        aMap.setMyLocationEnabled(true); // 启用定位图层
                    }
                } else {
                    Toast.makeText(MainActivity.this, "定位失败", Toast.LENGTH_SHORT).show();
                }

            }
        });
        // 启动定位
        locationClient.startLocation();
        // 启用地图的定位显示
        aMap.setMyLocationEnabled(true);  // 启用定位

        Toast.makeText(MainActivity.this, "336定位成功", Toast.LENGTH_SHORT).show();
    }

    // 开始追踪轨迹
    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }
        isTracking = true;
        trackPoints.clear();

        locationClient.startLocation();
        Toast.makeText(this, "开始记录轨迹", Toast.LENGTH_SHORT).show();

    }

    // 停止记录轨迹
    private void stopTracking() {
        isTracking = false;
//        locationClient.stopLocation();


        Toast.makeText(this, "停止记录轨迹\n本次运动距离: " + String.format("%.2f", currentTotalDistance) + " m", Toast.LENGTH_SHORT).show();
        totalDistance += currentTotalDistance;
        currentTotalDistance = 0;

        // 存储总距离到 SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("DistancePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("totalDistance", (float) totalDistance);  // 存储总距离
        editor.apply();  // 提交存储的更改


    }

    // 更新轨迹
    private void updateTrack() {
//        aMap.clear();
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(trackPoints)
                .width(10)
                .color(Color.RED);
        aMap.addPolyline(polylineOptions);

        // 计算距离
        if (trackPoints.size() > 1) {
            LatLng lastPoint = trackPoints.get(trackPoints.size() - 2);  // 上一个点
            LatLng currentPoint = trackPoints.get(trackPoints.size() - 1);  // 当前点

            // 计算距离
            float[] results = new float[1];
            Location.distanceBetween(lastPoint.latitude, lastPoint.longitude,
                    currentPoint.latitude, currentPoint.longitude,
                    results);
            currentTotalDistance += results[0];  // 累加距离

            // 在更新运动轨迹时更新总距离和当前运动距离
            TextView totalDistanceTextView = findViewById(R.id.totalDistanceTextView);
            String totalDistanceText = String.format("总距离: %.2f m", totalDistance + currentTotalDistance);
            totalDistanceTextView.setText(totalDistanceText);

            TextView distanceTextView = findViewById(R.id.distanceTextView);
            String distanceText = String.format("当前运动距离: %.2f m", currentTotalDistance);
            distanceTextView.setText(distanceText);

            // 保存轨迹数据到文件
            saveTrackToFile(trackPoints);
        }
    }

    // 轨迹存储
    private void saveTrackToFile(List<LatLng> trackPoints) {
        // 使用 Gson 将轨迹数据转化为 JSON 格式
        Gson gson = new Gson();
        String json = gson.toJson(trackPoints); // 将轨迹列表转为 JSON 字符串

        try {
            // 打开文件输出流
            FileOutputStream fos = openFileOutput("track_data.json", MODE_PRIVATE);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // 轨迹读取
    private List<LatLng> loadTrackFromFile() {
        List<LatLng> trackPoints = null;

        try {
            // 打开文件输入流
            FileInputStream fis = openFileInput("track_data.json");
            InputStreamReader reader = new InputStreamReader(fis);

            // 使用 Gson 将 JSON 转换为 List<LatLng>
            Gson gson = new Gson();
            Type listType = new TypeToken<List<LatLng>>(){}.getType();
            trackPoints = gson.fromJson(reader, listType);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return trackPoints;
    }
    // 轨迹恢复
    private void restoreTrack(List<LatLng> trackPoints) {
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(trackPoints)
                .width(10)
                .color(Color.RED);
        aMap.addPolyline(polylineOptions);
    }


/*    private double calculateTotalDistance(ArrayList<LatLng> points) {
        double totalDistance = 0.0;
        for (int i = 1; i < points.size(); i++) {
            LatLng start = points.get(i - 1);
            LatLng end = points.get(i);
            totalDistance += AMapUtils.calculateLineDistance(start, end);
        }
        return totalDistance;
    }*/


    private void locateCurrentPosition() {
        // 检查定位权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        // 启动定位
        locationClient.startLocation();


        Toast.makeText(MainActivity.this, "474定位成功", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationClient != null) {
            locationClient.onDestroy();
        }
        mapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();

        // 注销传感器监听器
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.unregisterListener(sensorEventListener);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}