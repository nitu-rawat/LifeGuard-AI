package com.nitu.project;

import android.Manifest;
import android.app.Service;
import android.content.*;
import android.hardware.*;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.SmsManager;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.*;

import java.util.*;

public class SOSService extends Service implements SensorEventListener {

    SensorManager sm;
    Sensor acc;

    float lastX, lastY, lastZ;
    boolean first = true;
    long lastTime = 0;

    FusedLocationProviderClient fusedLocationClient;
    ArrayList<String> numbers = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        loadContacts();
    }

    private void loadContacts() {
        SharedPreferences prefs = getSharedPreferences("SOS", MODE_PRIVATE);
        String data = prefs.getString("nums", "");

        if (!data.isEmpty()) {
            numbers = new ArrayList<>(Arrays.asList(data.split(",")));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {

        float x = e.values[0], y = e.values[1], z = e.values[2];

        if (first) {
            lastX = x; lastY = y; lastZ = z;
            first = false;
            return;
        }

        float shake = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);

        if (shake > 20) {
            long now = System.currentTimeMillis();

            if (now - lastTime > 8000) {
                lastTime = now;
                triggerSOS();
            }
        }

        lastX = x; lastY = y; lastZ = z;
    }

    private void triggerSOS() {

        sendSMS();
        makeCall();
        startRecording();
    }

    private void sendSMS() {

        if (numbers.isEmpty()) return;

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != 0) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {

            String msg = "🚨 EMERGENCY!\nI need help!";

            if (loc != null) {
                msg += "\nhttps://maps.google.com/?q=" +
                        loc.getLatitude() + "," + loc.getLongitude();
            }

            SmsManager sms = SmsManager.getDefault();

            for (String num : numbers) {
                sms.sendTextMessage(num, null, msg, null, null);
            }
        });
    }

    private void makeCall() {
        try {
            Intent call = new Intent(Intent.ACTION_CALL);
            call.setData(Uri.parse("tel:112"));
            call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(call);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        try {
            MediaRecorder r = new MediaRecorder();

            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            r.setOutputFile(getExternalFilesDir(null) + "/rec.3gp");

            r.prepare();
            r.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
    @Override public IBinder onBind(Intent i) { return null; }
}