package com.example.temperature;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;

public class background_service extends Service {
    FirebaseDatabase database = FirebaseDatabase.getInstance("https://first-2f743-default-rtdb.firebaseio.com/");
    DatabaseReference myRef;
    String bat_temp , local_temp;

    private final Handler handler = new Handler();
    FusedLocationProviderClient mFusedLocationClient;
    private Object lock = new Object();
    double a[] = new double[2];

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(sendUpdatesToUI);
        handler.postDelayed(sendUpdatesToUI, 0);
    }
    private Runnable sendUpdatesToUI = new Runnable() {
        public void run() {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            String formattedDate = formatter.format(date);
            formatter = new SimpleDateFormat("HH:mm");
            String formattedTime = formatter.format(date);
            Toast.makeText(background_service.this, formattedTime.substring(4,5), Toast.LENGTH_SHORT).show();
//            if(formattedTime.substring(4,5).trim().equals("0") || formattedTime.substring(4,5).trim().equals("5")){
                myRef = database.getReference().child(formattedDate).child(formattedTime);
                combine_task();
//            }
            handler.postDelayed(this, 360000); //  mins
        }
    };
    @Override
    public void onStart(Intent intent, int startid) {
        handler.removeCallbacks(sendUpdatesToUI);
        handler.postDelayed(sendUpdatesToUI, 0);
    }

    public void combine_task(){
        load_battery_temp();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    get_location();
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    load_local_temp();
                }
            }
        }).start();
    }

    public void get_location(){
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                mFusedLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        Location location = task.getResult();
                        LocationCallback mLocationCallback = new LocationCallback() {

                            @Override
                            public void onLocationResult(LocationResult locationResult) {
                                Location mLastLocation = locationResult.getLastLocation();
                                a[0] = mLastLocation.getLatitude();
                                a[1] =mLastLocation.getLongitude();
                            }
                        };

                        if(location == null){
                            LocationRequest mLocationRequest = new LocationRequest();
                            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                            mLocationRequest.setInterval(5);
                            mLocationRequest.setFastestInterval(0);
                            mLocationRequest.setNumUpdates(1);
                            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
                            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        }
                        else{
                            a[0] = location.getLatitude();
                            a[1] = location.getLongitude();                        }
                    }
                });
            }
            else{
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        }
    }
    public void load_battery_temp(){
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        bat_temp = String.valueOf(temperature/10.0);
    }
    public void load_local_temp(){
        RequestQueue queue = Volley.newRequestQueue(this);
        String latitude = String.valueOf(a[0]);
        String longitude = String.valueOf(a[1]);
        String url = "https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m";
        JsonObjectRequest js = new JsonObjectRequest(Request.Method.GET, url,null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject r) {
                        try {
                            String s = r.getJSONObject("current").getString("temperature_2m");
                            local_temp = s;
                            myRef.setValue(bat_temp+","+local_temp);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        });
        queue.add(js);
    }

}
