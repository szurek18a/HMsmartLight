package com.example.hmsmartlightapi27;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TimePicker;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.sql.Time;
import java.util.Arrays;
import java.util.Calendar;


public class Sterowanie extends AppCompatActivity {
    String kolor = null;
    String adres = null;
    static final String TAG = "Server";
    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;
    private NsdServiceInfo mServiceInfo;
    public String ESP8266Address;
    private static final String SERVICE_TYPE = "_http._tcp.";
    private AlertDialog.Builder dialogBuilder;
    private AlertDialog dialog;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.timer_button) {
            setTimer();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sterowanie);

        Intent myIntent = getIntent();
        adres = myIntent.getStringExtra("Adres");
        String DeviceName = myIntent.getStringExtra("DeviceName");
        Log.i("ID:", adres);
        getSupportActionBar().setTitle(DeviceName);

        ESP8266Address = "";
        mNsdManager = (NsdManager)(getApplicationContext().getSystemService(Context.NSD_SERVICE));
        initializeResolveListener();
        initializeDiscoveryListener();
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

        ColorPicker picker = (ColorPicker) findViewById(R.id.picker);
        SVBar svBar = (SVBar) findViewById(R.id.svbar);
        picker.addSVBar(svBar);
        picker.setShowOldCenterColor(false);

        SeekBar seekBar = findViewById(R.id.seekBar);

        Button buttonSteady = findViewById(R.id.buttonSteady);
        Button buttonRainbow = findViewById(R.id.buttonRainbow);
        Button buttonStrobe = findViewById(R.id.buttonStrobe);

        picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
            @Override
            public void onColorChanged(int color) {
                String red = Integer.toHexString(Color.red(color));
                String green = Integer.toHexString(Color.green(color));
                String blue = Integer.toHexString(Color.blue(color));
                if (red.length() == 1)
                    red = "0" + red;
                if (green.length() == 1)
                    green = "0" + green;
                if (blue.length() == 1)
                    blue = "0" + blue;
                kolor = red + green + blue;
            }
        });

        picker.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new HTTPReqTask().execute("C" + kolor);
                }
                return false;
            }
        });

        svBar.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new HTTPReqTask().execute("C" + kolor);
                }
                return false;
            }
        });

        seekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new HTTPReqTask().execute("B" + seekBar.getProgress());
                }
                return false;
            }
        });

        buttonSteady.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new HTTPReqTask().execute("S" + kolor);
                    picker.setEnabled(true);
                    svBar.setEnabled(true);
                }
                return false;
            }
        });

        buttonRainbow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new HTTPReqTask().execute("R");
                    picker.setEnabled(false);
                    svBar.setEnabled(false);
                }
                return false;
            }
        });

        buttonStrobe.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new HTTPReqTask().execute("X");
                    picker.setEnabled(false);
                    svBar.setEnabled(false);
                }
                return false;
            }
        });
    }

    private class HTTPReqTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... dane) {
            URL url;
            HttpURLConnection connection = null;

            try {
                url = new URL("http://" + ESP8266Address + "/" + Arrays.toString(dane));
                Log.i("URL:", String.valueOf(url));
                connection = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
                if(e instanceof java.net.UnknownHostException || e instanceof java.net.NoRouteToHostException) {
                    runOnUiThread(new Runnable(){
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Device Offline!",Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }
    }

    private void initializeDiscoveryListener() {

        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                String name = service.getServiceName();
                String type = service.getServiceType();
                Log.d("NSD", "Service Name=" + name);
                Log.d("NSD", "Service Type=" + type);
                if (type.equals(SERVICE_TYPE) && name.contains(adres)) {
                    Log.d("NSD", "Service Found @ '" + name + "'");
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {

            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    private void initializeResolveListener() {
        mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e("NSD", "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                mServiceInfo = serviceInfo;

                InetAddress host = mServiceInfo.getHost();
                String address = host.getHostAddress();
                Log.d("NSD", "Resolved address = " + address);
                ESP8266Address = address;
            }
        };
    }

    public void setTimer()
    {
        dialogBuilder = new AlertDialog.Builder(this);
        final View ustawzegar = getLayoutInflater().inflate(R.layout.ustawzegar, null);
        dialogBuilder.setView(ustawzegar);
        Button ustawionyZegar = (Button) ustawzegar.findViewById(R.id.YesbuttonZegar);
        Button anulujHarmonogram = (Button) ustawzegar.findViewById(R.id.buttonNoZegar);
        TimePicker PickerOn = (TimePicker) ustawzegar.findViewById(R.id.timePickerOn);
        TimePicker PickerOff = (TimePicker) ustawzegar.findViewById(R.id.timePickerOff);
        PickerOn.setIs24HourView(true);
        PickerOff.setIs24HourView(true);

        dialog = dialogBuilder.create();
        dialog.show();


        ustawionyZegar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new HTTPReqTask().execute("T" + String.format("%02d",  PickerOn.getHour()) + String.format("%02d",  PickerOn.getMinute()) + String.format("%02d",  PickerOff.getHour()) + String.format("%02d",  PickerOff.getMinute()));
                dialog.dismiss();
            }
        });

        anulujHarmonogram.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new HTTPReqTask().execute("Y");
                dialog.dismiss();
            }
        });
    }


}