package com.example.hmsmartlightapi27;

import static java.lang.String.*;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AlertDialog.Builder dialogBuilder;
    private AlertDialog dialog;
    private Button polaczoneWifibutton;
    private Button parujUrzadzenie;
    private TextView licznik;
    private ListView list;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> arrayList;
    private EditText nazwaZarowki;
    private String nazwaSieci;
    private EditText hasloSieci;
    WifiManager mainWifiObj;
    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;
    private NsdServiceInfo mServiceInfo;
    public String ESP8266Address;
    private static final String SERVICE_TYPE = "_http._tcp.";
    private List<Integer> IdArray = Arrays.asList(new Integer[200]);
    private String deviceIDrmv;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    Gson gson = new Gson();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_menu_glowne, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.info_button) {
            wyswietlFAQ();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);
        }

        FloatingActionButton fab = findViewById(R.id.dodajActionButton);
        list = (ListView) findViewById(R.id.listaZarowek);
        arrayList = new ArrayList<String>();

        SharedPreferences sharedPrefs = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.apply();
        String json = sharedPrefs.getString("arrayList", "");
        String json2 = sharedPrefs.getString("IdArray", "");
        Type type = new TypeToken<ArrayList<String>>() {
        }.getType();
        Type type2 = new TypeToken<List<Integer>>() {
        }.getType();

        if (json != "") {
            arrayList = gson.fromJson(json, type);
            IdArray = gson.fromJson(json2, type2);
        }

        adapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.listview_font, arrayList);
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i("User clicked " + id + " " + IdArray.get((int) id), arrayList.get(position));
                Intent myIntent = new Intent(getBaseContext(), Sterowanie.class);
                myIntent.putExtra("Adres", IdArray.get((int) id).toString());
                myIntent.putExtra("DeviceName", arrayList.get(position));
                startActivity(myIntent);
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i("User LONG clicked " + id + " " + IdArray.get((int) id), arrayList.get(position));
                usuwanie(arrayList.get(position), (int) id, IdArray.get((int) id));
                return true;
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dodajWifi();
                new CountDownTimer(5000, 1000) {
                    int i = 5;

                    @Override
                    public void onTick(long millisUntilFinished) {
                        licznik.setText("(" + i + ")");
                        i--;
                    }

                    @Override
                    public void onFinish() {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        licznik.setText(null);
                    }
                }.start();
            }
        });
    }

    public void dodajWifi() {
        dialogBuilder = new AlertDialog.Builder(this);
        final View wifiPopupMsg = getLayoutInflater().inflate(R.layout.wifipopup, null);
        dialogBuilder.setView(wifiPopupMsg);
        polaczoneWifibutton = (Button) wifiPopupMsg.findViewById(R.id.OKbutton);
        dialog = dialogBuilder.create();
        dialog.show();
        licznik = (TextView) wifiPopupMsg.findViewById(R.id.textViewlicznik);
        polaczoneWifibutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                parowanie();
            }
        });
    }

    public void parowanie() {
        dialogBuilder = new AlertDialog.Builder(this);
        final View wifiPopupMsg = getLayoutInflater().inflate(R.layout.popupdanewifi, null);
        dialogBuilder.setView(wifiPopupMsg);
        parujUrzadzenie = (Button) wifiPopupMsg.findViewById(R.id.buttonAkceptacjaDanych);
        dialog = dialogBuilder.create();
        dialog.show();

        String wifis[] = new String[0];
        WifiManager wmgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // Get List of Available Wifi Networks
        List<ScanResult> availNetworks = wmgr.getScanResults();
        if (availNetworks.size() > 0) {
            wifis = new String[availNetworks.size()];
            // Get Each network detail
            for (int i = 0; i < availNetworks.size(); i++) {
                if(availNetworks.get(i).frequency<2800)
                    wifis[i] = availNetworks.get(i).SSID;
            }
            Log.v("ALLWIFIS", Arrays.toString(wifis));
        }
        List<String> SuppWifis = new ArrayList<String>(Arrays.asList(wifis));
        SuppWifis.removeAll(Collections.singleton(null));
        wifis = new String[SuppWifis.size()];
        for (int i = 0; i < SuppWifis.size(); i++) {
                wifis[i] = SuppWifis.get(i);
        }
        Log.v("SUPPWIFIS", Arrays.toString(wifis));

        Spinner spinnerwifi = (Spinner) wifiPopupMsg.findViewById(R.id.spinner_wifi);
        ArrayAdapter<String> aa = new ArrayAdapter<String>(this, R.layout.spinner_font, wifis);
        aa.setDropDownViewResource(R.layout.spinner_font);
        spinnerwifi.setAdapter(aa);
        parujUrzadzenie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                int randomID = (int) ((Math.random() * (999999 - 100000)) + 100000);
                nazwaZarowki = (EditText) wifiPopupMsg.findViewById(R.id.editTextNazwaUrzadzenia);
                nazwaSieci = spinnerwifi.getSelectedItem().toString();
                hasloSieci = (EditText) wifiPopupMsg.findViewById(R.id.editTextHasloSieci);
                new HTTPReqTask().execute("P-" + randomID + "-" + nazwaSieci.replaceAll(" ", "") + "-" + hasloSieci.getText().toString() + "-");
                arrayList.add(nazwaZarowki.getText().toString());
                IdArray.set(adapter.getPosition(nazwaZarowki.getText().toString()), randomID);

                String json = gson.toJson(arrayList);
                String json2 = gson.toJson(IdArray);
                SharedPreferences sharedPrefs = getSharedPreferences("MySharedPref", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString("arrayList", json);
                editor.putString("IdArray", json2);
                editor.commit();

                Log.i("ID number:", valueOf(IdArray.get(adapter.getPosition(nazwaZarowki.getText().toString()))));
                nazwaZarowki = null;
                adapter.notifyDataSetChanged();
                list.invalidateViews();
                list.refreshDrawableState();

            }
        });
    }

    public void usuwanie(String nazwa, int id, int deviceId) {
        dialogBuilder = new AlertDialog.Builder(this);
        final View deletepopup = getLayoutInflater().inflate(R.layout.deletepopup, null);
        dialogBuilder.setView(deletepopup);
        TextView nazwaUrzadzenia;
        nazwaUrzadzenia = (TextView) deletepopup.findViewById(R.id.textView3);
        nazwaUrzadzenia.setText(nazwa);
        dialog = dialogBuilder.create();
        dialog.show();
        Button Usun;
        Usun = (Button) deletepopup.findViewById(R.id.OKbutton);
        Usun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                deviceIDrmv = valueOf(deviceId);

                ESP8266Address = "";
                mNsdManager = (NsdManager)(getApplicationContext().getSystemService(Context.NSD_SERVICE));
                initializeResolveListener();
                initializeDiscoveryListener();
                mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

                Log.i("ID number:", valueOf(deviceId));

                IdArray.remove(id);
                arrayList.remove(id);

                String json = gson.toJson(arrayList);
                String json2 = gson.toJson(IdArray);
                SharedPreferences sharedPrefs = getSharedPreferences("MySharedPref", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.remove("arrayList");
                editor.remove("IdArray");
                editor.apply();
                editor.putString("arrayList", json);
                editor.putString("IdArray", json2);
                editor.commit();

                adapter.notifyDataSetChanged();
                list.invalidateViews();
                list.refreshDrawableState();
            }
        });
    }


    private class HTTPReqTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... dane) {
            URL url;
            HttpURLConnection connection = null;

            try {
                url = new URL("http://192.168.4.1/" + Arrays.toString(dane));
                connection = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }
    }

    private class HTTPReqTaskDelete extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... dane) {
            URL url;
            HttpURLConnection connection = null;

            try {
                url = new URL("http://" + ESP8266Address + "/" + Arrays.toString(dane));
                connection = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
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
                String name = service.getServiceName();
                String type = service.getServiceType();
                Log.d("NSD", "Service Name=" + name);
                Log.d("NSD", "Service Type=" + type);
                if (type.equals(SERVICE_TYPE) && name.contains(deviceIDrmv)) {
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
                Log.e("NSD", "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                mServiceInfo = serviceInfo;
                InetAddress host = mServiceInfo.getHost();
                String address = host.getHostAddress();
                Log.d("NSD", "Resolved address = " + address);
                ESP8266Address = address;
                new HTTPReqTaskDelete().execute("D");
            }
        };
    }

    public void wyswietlFAQ()
    {
        dialogBuilder = new AlertDialog.Builder(this);
        final View wyswietlFAQview = getLayoutInflater().inflate(R.layout.faqxml, null);
        dialogBuilder.setView(wyswietlFAQview);
        Button okbutton = (Button) wyswietlFAQview.findViewById(R.id.OKbutton);
        dialog = dialogBuilder.create();
        dialog.show();
        okbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
    }

}