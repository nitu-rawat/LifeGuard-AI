package com.example.projectexpo;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    Button selectBtn, sosBtn;
    TextView statusText;

    private static final int PICK_CONTACT = 1;
    private ArrayList<String> selectedContacts = new ArrayList<>();

    private SensorManager sensorManager;
    private float lastX, lastY, lastZ;
    private long lastUpdate;
    private static final float SHAKE_THRESHOLD = 12.0f;

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectBtn = findViewById(R.id.selectBtn);
        sosBtn = findViewById(R.id.sosBtn);
        statusText = findViewById(R.id.statusText);

        // Permissions
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_CONTACTS
                }, 1);

        // Load saved contacts
        loadContacts();

        // Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Contact select button
        selectBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
            startActivityForResult(intent, PICK_CONTACT);
        });

        // Sensor
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            sensorManager.registerListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == PICK_CONTACT && resultCode == RESULT_OK) {

            Uri uri = data.getData();
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {

                String number = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));

                if (!selectedContacts.contains(number)) {
                    selectedContacts.add(number);
                    saveContacts();
                    Toast.makeText(this, "Added: " + number, Toast.LENGTH_SHORT).show();
                }

                cursor.close();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void saveContacts() {
        SharedPreferences prefs = getSharedPreferences("SOS_PREF", MODE_PRIVATE);
        prefs.edit().putString("data", TextUtils.join(",", selectedContacts)).apply();
    }

    private void loadContacts() {
        SharedPreferences prefs = getSharedPreferences("SOS_PREF", MODE_PRIVATE);
        String data = prefs.getString("data", "");

        if (!data.isEmpty()) {
            selectedContacts = new ArrayList<>(Arrays.asList(data.split(",")));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        long time = System.currentTimeMillis();

        if ((time - lastUpdate) > 200) {

            float speed = Math.abs(x + y + z - lastX - lastY - lastZ);

            if (speed > SHAKE_THRESHOLD && !selectedContacts.isEmpty()) {
                sendSOS();
            }

            lastX = x;
            lastY = y;
            lastZ = z;
            lastUpdate = time;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void sendSOS() {

        statusText.setText("📍 Getting Location...");

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {

            String message;

            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                String link = "https://maps.google.com/?q=" + lat + "," + lon;

                message = "🚨 Emergency! I am in danger! - Nitu Rawat\nLocation: " + link;
            } else {
                message = "🚨 Emergency! I am in danger! - Nitu Rawat";
            }

            SmsManager sms = SmsManager.getDefault();

            for (String number : selectedContacts) {
                sms.sendTextMessage(number, null, message, null, null);
            }

            // WhatsApp (first contact)
            try {
                String url = "https://wa.me/91" + selectedContacts.get(0) + "?text=" + Uri.encode(message);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show();
            }

            statusText.setText("🚨 SOS Sent!");
            Toast.makeText(this, "Alert Sent!", Toast.LENGTH_LONG).show();
        });
    }
}