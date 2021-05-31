package com.example.speedpost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static android.widget.Toast.makeText;
import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MainActivity extends Activity implements Runnable, LocationListener, View.OnClickListener {

    private final long UPDATE_INTERVAL =  10000;
    private final long FASTEST_INTERVAL = 2000;
    private final long TIME_BETWEEN_FRAMES = 200;
    private final long DEFAULT_POST_INTERVAL = 60000;
    private final String URL = "http://muchserver.ddns.net:8000/add_speed";
    final float SPEED_CONVERSION_CONSTANT = 2.23694f; // M/s to MPH

    Thread speedometerThread = null;
    volatile boolean speedometerRunning;
    long lastTimeFrame;
    private LocationRequest locationRequest;
    private double latitude, longitude;
    private float[] results;
    private float totalDistance;
    private DecimalFormat speedFormat;
    private String pattern = "##0.00";
    private float currentSpeed, maxSpeed;
    private int screenWidth, screenHeight;
    private SurfaceView drawSurface;
    private SurfaceHolder surfaceHolder;
    private SpeedometerService speedometerService;
    private boolean serviceRunning;
    private Button startTripButton;
    private RequestQueue requestQueue;
    private EditText intervalEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeVariables();
        bindUI();

        startLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        speedometerRunning = true;
        speedometerThread = new Thread(this);
        speedometerThread.start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        speedometerRunning = false;
        try {
            speedometerThread.join();
        }
        catch (InterruptedException e) {
            makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.startTripButton:
                startTrip();
                break;

            default:
                break;

        }

    }

    /**************************************************************************
     *
     * Speedometer service methods.
     *
     *************************************************************************/
    private void startTrip() {
        Intent intent;
        intent = new Intent(this, SpeedometerService.class);
        if(serviceRunning) {
            intent.setAction(SpeedometerService.STOP_SPEEDOMETER_SERVICE);
            startTripButton.setText(getResources().getString(R.string.trip_button_text_start));
            postSpeed();
            serviceRunning = false;
        }
        else {
            intent.setAction(SpeedometerService.START_SPEEDOMETER_SERVICE);
            startTripButton.setText(getResources().getString(R.string.trip_button_text_stop));
            postSpeed();

            setTimer();
            serviceRunning = true;
            zeroDistance();
        }
        startService(intent);
    }

    /**************************************************************************
     *
     * Volley methods.
     *
     *************************************************************************/
    private void postSpeed() {
        JSONObject jsonObject = new JSONObject();

        float postSpeed = Float.valueOf(String.format("%.2f", currentSpeed));
        Date date = Calendar.getInstance().getTime();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        String sent = simpleDateFormat.format(date);
        try {
            jsonObject.put("speed", postSpeed);
            jsonObject.put("sent", sent);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        makeText(getApplicationContext(), response.toString(), Toast.LENGTH_SHORT).show();
                    }
                },
                 new Response.ErrorListener() {

                     @Override
                     public void onErrorResponse(VolleyError error) {
                         makeText(getApplicationContext(), error.toString(), Toast.LENGTH_SHORT).show();
                     }
                 }) {
                    @Override
                    public Map<String, String> getHeaders() /*throws AuthFailureError*/ {
                        HashMap<String, String> headers = new HashMap<>();
                        headers.put("Content-Type", "application/json; charset=utf-8");
                        return headers;
                    }

                };
        requestQueue.add(jsonObjectRequest);
    }

    /**************************************************************************
     *
     * Runnable methods.
     *
     *************************************************************************/
    @Override
    public void run() {
        while(speedometerRunning) {
            drawUI();
            controlFrameRate();
        }
    }

    private void drawUI() {
        if(surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            Paint paint = new Paint();
            // Start drawing.

            canvas.drawColor(Color.rgb(0, 150, 255));
            paint.setColor(Color.WHITE);
            paint.setTextSize((float) screenHeight / 4);
            canvas.drawText(speedFormat.format(currentSpeed), screenWidth / 3, screenHeight / 2, paint);
            paint.setTextSize((float) screenHeight / 10);
            canvas.drawText("Distance: " + totalDistance + "m", screenWidth / 4, 2 * screenHeight / 3, paint);


            // End drawing.
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void controlFrameRate() {
        long thisTimeFrame = System.currentTimeMillis() - lastTimeFrame;
        long timeToSleep = TIME_BETWEEN_FRAMES - thisTimeFrame;

        if(timeToSleep > 0) {
            try {
                Thread.sleep(timeToSleep);
            }
            catch (InterruptedException e) {
                makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
        lastTimeFrame = System.currentTimeMillis();
    }

    private void setTimer() {
        String intervalEditTextString = intervalEditText.getText().toString();
        long postInterval = Long.parseLong(intervalEditTextString) * 1000;

        postInterval =  postInterval > 0 ? postInterval : DEFAULT_POST_INTERVAL;

        CountDownTimer countDownTimer = new CountDownTimer(postInterval, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                postSpeed();
                if(serviceRunning) {
                    setTimer();
                }
            }
        };
        countDownTimer.start();
    }

    /**************************************************************************
     *
     * Location methods.
     *
     *************************************************************************/
    protected void startLocationUpdates() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);

        LocationSettingsRequest locationSettingsRequest = builder.build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                onLocationChanged(locationResult.getLastLocation());
            }
        }, Looper.myLooper());
    }

    @Override
    public void onLocationChanged(Location location) {
        if(location != null) {
            if(serviceRunning && latitude != 0.0f && longitude != 0.0f) {
                Location.distanceBetween(latitude, longitude, location.getLatitude(), location.getLongitude(), results);
                totalDistance = totalDistance + results[0];

            }
            if(location.hasSpeed()) {
                currentSpeed = location.getSpeed() * SPEED_CONVERSION_CONSTANT;
                maxSpeed = currentSpeed > maxSpeed ? currentSpeed : maxSpeed;
            }

            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /**************************************************************************
     *
     * Helper methods.
     *
     *************************************************************************/
    private void initializeVariables() {
        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getRealSize(point);

        screenWidth = point.x;
        screenHeight = point.y;

        results = new float[3];
        zeroDistance();

        currentSpeed = 0.0f;
        maxSpeed = 0.0f;

        latitude = 0.0f;
        longitude = 0.0f;

        speedFormat = new DecimalFormat(pattern);

        speedometerService = new SpeedometerService();
        serviceRunning = false;

        requestQueue = Volley.newRequestQueue(this);
    }

    private void bindUI() {
        drawSurface = findViewById(R.id.drawSurface);
        surfaceHolder = drawSurface.getHolder();

        startTripButton = findViewById(R.id.startTripButton);
        startTripButton.setOnClickListener(this);

        intervalEditText = findViewById(R.id.intervalEditText);

    }

    private void zeroDistance() {
        totalDistance = 0.0f;
    }


}
