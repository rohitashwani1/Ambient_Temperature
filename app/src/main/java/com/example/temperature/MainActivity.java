package com.example.temperature;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.Utils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    FusedLocationProviderClient mFusedLocationClient;
    private final Object lock = new Object();
    TextView bat_temp , local_temp;
    Button date_choose;
    static TextView t;
    double a[] = new double[2];
    FirebaseDatabase database = FirebaseDatabase.getInstance("https://first-2f743-default-rtdb.firebaseio.com/");
    DatabaseReference myRef;

    public static LineChart lineChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bat_temp = findViewById(R.id.button4);
        local_temp = findViewById(R.id.button2);
        date_choose = findViewById(R.id.button5);
        lineChart = findViewById(R.id.chart1);
        t = findViewById(R.id.textView);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        combine_task();
        date_choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatePicker datePicker = new DatePicker();
                datePicker.show(getSupportFragmentManager() ,"DATE PICK");
                t.setVisibility(View.GONE);
                lineChart.setVisibility(View.GONE);
            }
        });
        Intent serviceIntent = new Intent(this, background_service.class);
        startService(serviceIntent);
    }



    public void combine_task(){
        if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            get_location_permission();
        }
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

    public void get_location_permission(){
        try {
            if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
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
                            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
                            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        }
                        else{
                            a[0] = location.getLatitude();
                            a[1] = location.getLongitude();                        }
                    }
                });
            }
            else{
                Toast.makeText(this, "Please turn on" + " your location...", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        }
    }
    public void load_battery_temp(){
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        bat_temp.setText(temperature / 10.0 + "°C");
    }
    public void load_local_temp(){
        local_temp.setText("Loading...");
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
                            local_temp.setText(s+"°C");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                local_temp.setText("That didn't work!");
            }
        });
        queue.add(js);
    }

    public static class DatePicker extends DialogFragment {
        String selectedDate;
        HashMap<String, String> h;
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://first-2f743-default-rtdb.firebaseio.com/");
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            Calendar mCalendar = Calendar.getInstance();
            int year = mCalendar.get(Calendar.YEAR);
            int month = mCalendar.get(Calendar.MONTH);
            int dayOfMonth = mCalendar.get(Calendar.DAY_OF_MONTH);
            return new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(android.widget.DatePicker view, int year, int month, int dayOfMonth) {
                    Calendar mCalendar = Calendar.getInstance();
                    mCalendar.set(Calendar.YEAR, year);
                    mCalendar.set(Calendar.MONTH, month);
                    mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    selectedDate = sdf.format(mCalendar.getTime());
                    database.getReference().child(selectedDate).get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DataSnapshot> task) {
                            if (task.isSuccessful()) {
                                h = (HashMap<String, String>) task.getResult().getValue();
                                if(h == null){
                                    t.setVisibility(View.VISIBLE);
                                }
                                else {
                                    lineChart.setVisibility(View.VISIBLE);
                                    ArrayList<Entry> entries = new ArrayList<>();
                                    ArrayList<Entry> entries1 = new ArrayList<>();
                                    ArrayList<Entry> entries2 = new ArrayList<>();
                                    ArrayList<String> arr = new ArrayList<>(h.keySet());
                                    Collections.sort(arr);
                                    for (String i : arr) {
                                        float hr = Float.parseFloat(i.substring(0, 2).trim());
                                        float min = Float.parseFloat(i.substring(3, 5).trim());
                                        String[] parts = h.get(i).split(",");
                                        float bat_temp = Float.parseFloat(parts[0].trim());
                                        float local_temp = Float.parseFloat(parts[1].trim());
                                        entries.add(new Entry(hr + min / 60.0f, bat_temp));
                                        entries1.add(new Entry(hr + min / 60.0f, local_temp));
                                        entries2.add(new Entry(hr + min / 60.0f, (local_temp+bat_temp)/2));
                                    }
                                    LineDataSet lineDataSet = new LineDataSet(entries, "Battery_Temp");
                                    LineDataSet lineDataSet1 = new LineDataSet(entries1, "Local_Temp");
                                    LineDataSet lineDataSet2 = new LineDataSet(entries2, "Ambient_Temp");
                                    lineDataSet.setColor(Color.RED);
                                    lineDataSet.setCircleColor(Color.RED);
                                    lineDataSet1.setColor(Color.BLACK);
                                    lineDataSet1.setCircleColor(Color.BLACK);
                                    lineDataSet2.setCircleColor(Color.YELLOW);
                                    lineDataSet2.setColor(Color.YELLOW);
                                    LineData lineData = new LineData(lineDataSet,lineDataSet1,lineDataSet2);
                                    YAxis yAxis = lineChart.getAxisLeft();
                                    lineChart.getAxisRight().setEnabled(false);
                                    yAxis.enableGridDashedLine(10f, 10f, 0f);
                                    yAxis.setAxisMaximum(50f);
                                    yAxis.setAxisMinimum(0f);
                                    XAxis xAxis = lineChart.getXAxis();
                                    xAxis.enableGridDashedLine(10f, 10f, 0f);
                                    xAxis.setAxisMaximum(24f);
                                    xAxis.setAxisMinimum(0f);
                                    lineChart.setDescription(new Description());
                                    lineChart.setData(lineData);
                                    lineChart.getDescription().setEnabled(false);
//                                    lineChart.setBackground(ResourcesCompat.getDrawable(getResources(),R.drawable.fade_red,null));
                                }
                            }
                            else{
                                t.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }, year, month, dayOfMonth);
        }
    }


}

