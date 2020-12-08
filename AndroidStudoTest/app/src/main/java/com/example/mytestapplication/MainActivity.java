package com.example.mytestapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.icu.lang.UScript;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {
    public boolean connected = false;
    private static final String TAG = "MainActivity";
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort port;
    public String jsonStr = "";
    public String dataFinal = "";
    public JsonObject infoGson;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    UsbSerialDriver driver;

    UsbManager manager;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            Log.d(TAG, "Success");
                            //call method to set up device communication
                            UsbDeviceConnection connection = manager.openDevice(device);
                            if (connection == null) {
                                // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
                                return;
                            }

                            Log.d(TAG, driver.getPorts().toString());
                            UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
                            try {
                                port.open(connection);
                            } catch (IOException e) {
                                Log.d(TAG, "onCreate0: ");
                                e.printStackTrace();
                            }
                            try {
                                port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                            } catch (IOException e) {
                                Log.d(TAG, "onCreate1: ");
                                e.printStackTrace();
                            }
                            SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, (SerialInputOutputManager.Listener) context);
                            Executors.newSingleThreadExecutor().submit(usbIoManager);
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(10755, 67, CdcAcmSerialDriver.class);
        UsbSerialProber prober = new UsbSerialProber(customTable);

        List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }


        // Open a connection to the first available driver.
        driver = availableDrivers.get(0);
        Log.d(TAG, "test");
        manager.requestPermission(driver.getDevice(), permissionIntent);
    }


    @Override
    public void onNewData(final byte[] data) {
        String tempData = new String(data, StandardCharsets.US_ASCII);
        jsonStr += tempData;
        if (jsonStr.indexOf('}') != -1) {
            dataFinal = jsonStr;
            jsonStr = "";
            try {
                jsonParse();
            } catch (JSONException e) {
                e.printStackTrace();
            }



        this.runOnUiThread(new Runnable() {
            public void run() {
                // This is the data received from the USB cable from arduino converted to a string
                String receivedData = new String(data, StandardCharsets.US_ASCII);
                //"{"BatteryTemp":50, "BatteryCharge": 100, }" <- this is an example of how the data
                //will look

                //Convert the data to json object

                //Pseudo Code for processing data:
                    // Iterate through all json keys
                    // For each iteration, check the key name.
                    // Have a large if statement for each key name, ex if keyname is "BatteryTemp"
                    // In the if statements for each key name, change the corresponding text view
                    // Example of changing text view:
                        // ((TextView) findViewById(R.id.batteryTemperatureTextView)).setText(jsonData[keyName])
            }
        });
        }
    }

    @Override
    public void onRunError(Exception e) {
        final Activity mainActivity = this;
        Log.d(TAG, "error_found");
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity,"Disconnected",Toast.LENGTH_LONG).show();
            }
        });
        disconnect();
    }

    //method to parse through JSON input
    public void jsonParse() throws JSONException {
        JSONObject test = new JSONObject(dataFinal);
        for(int i = 0;  i < test.names().length(); i++){
            String key = test.names().getString(i);
            Object value = test.get(test.names().getString(i));
            if (key.equals("Battery")){
                Log.d(TAG, "Battery Value: " + value);
                //assign value to a TextView on the UI thread
            }
            if (key.equals("Temperature")){
                Log.d(TAG,"Temperature Value: " + value);
            }
            //Continue adding until all required values are assigned to a TextView
        }
    }



    //creates disconnect method to end connection
    private void disconnect(){
        Log.d(TAG, "disconnect_called");
        final Activity mainActivity = this;
        connected = false;
        Log.d(TAG, String.valueOf(usbIoManager));
        if(usbIoManager != null) {
            usbIoManager.stop();
            Log.d(TAG,"IoManager_Stopped");
        }
        Log.d(TAG, String.valueOf(port));
        usbIoManager = null;
        try {
            port.close();
            Log.d(TAG, "port_closed");
        } catch (IOException ignored) {}
        port = null;
    }
}