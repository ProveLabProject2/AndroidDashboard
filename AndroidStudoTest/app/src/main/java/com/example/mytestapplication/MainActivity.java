package com.example.mytestapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import android.os.Environment;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
    private boolean writeToFile = false;
    public String jsonStr = "";
    public String dataFinal = "";
    public OutputStreamWriter out = null;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    UsbSerialDriver driver;

    UsbManager manager;

    public final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

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
                                connected = true;
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
//add button insert here
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(10755, 67, CdcAcmSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(UsbSerialProber.getDefaultProbeTable());

        List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            prober = new UsbSerialProber(customTable);
            availableDrivers = prober.findAllDrivers(manager);

            if (availableDrivers.isEmpty()) {
                return;
            }
        }


        // Open a connection to the first available driver.
        driver = availableDrivers.get(0);
        manager.requestPermission(driver.getDevice(), permissionIntent);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                0);
        //If button is clicked, write 'X' to the Arduino
        //Whatever X means will be interpreted by the arduino and the appropriate action will happen
        //i.e. turn signals, headlights, etc.
        //UsbSerialPort port = driver.getPorts().get(0);
        final Button writeButton = findViewById(R.id.buttonLS);
        writeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                UsbSerialPort port = driver.getPorts().get(0);
                try {
                    port.write("button was clicked here".getBytes(), 100);
                    Log.d(TAG, "Data was sent");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "failed to send");
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                writeToFile = true;
            }
            else {
                // User refused to grant permission.
            }
        }
    }

    @Override
    public void onNewData(final byte[] data) {
        String tempData = new String(data, StandardCharsets.US_ASCII);
        Log.d(TAG, tempData);
        jsonStr += tempData;

        if (jsonStr.indexOf('}') != -1) {
            dataFinal = jsonStr;

            jsonStr = jsonStr.substring(jsonStr.indexOf('}') + 1);

            try {
                JSONObject tempJsonObject = new JSONObject(dataFinal);
                dataFinal = tempJsonObject.toString();
            } catch (JSONException e) {
                return;
            }

            try {
                jsonParse();
                Log.d(TAG, dataFinal);
                if (writeToFile)
                    fileWrite();

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
        try {
            disconnect();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    //method to parse through JSON input
    public void jsonParse() throws JSONException {
        JSONObject json;
        try {
            json = new JSONObject(dataFinal);
        } catch(Exception e){
            return;
        }
        for(int i = 0;  i < json.names().length(); i++){
            String key = json.names().getString(i);
            Object value = json.get(json.names().getString(i));
            if (key.equals("Brakes")){
                Log.d(TAG, "Brakes: " + value);
                TextView brakesText = (TextView) findViewById(R.id.brakeData);
                brakesText.setText("Brakes: " + value);
                //assign value to a TextView on the UI thread
            }
            if (key.equals("Alert Code")){
                Log.d(TAG,"Alert Code: " + value);
            }
            if (key.equals("Accelerator")) {
                Log.d(TAG, "Accelerator: " + value);
                TextView accText = (TextView) findViewById(R.id.accData);
                accText.setText("Accelerator: " + value);
            }
            if (key.equals("BMS")) {
                Log.d(TAG, "BMS: " + value);
                TextView bmsText = (TextView) findViewById(R.id.bmsData);
                bmsText.setText("BMS: " + value);
            }
            if (key.equals("MC")) {
                Log.d(TAG, "MC: " + value);
                TextView mcText = (TextView) findViewById(R.id.mcData);
                mcText.setText("MC: " + value);
            }
            //Continue adding until all required values are assigned to a TextView
        }
    }

    public void fileWrite(){
        try {
            if (out == null){
                File proveTextFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"prove.txt");
                if (!proveTextFile.exists()) {
                    proveTextFile.createNewFile();
                }

                FileOutputStream fos = new FileOutputStream(proveTextFile);
                out = new OutputStreamWriter(fos);
                Log.d(TAG, "File: " + proveTextFile.getAbsolutePath());
            }
            Log.d(TAG, "Finished writing to file: " );
            out.write(dataFinal);
            out.write('\n');
            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    //creates disconnect method to end connection and close data file
    private void disconnect() throws IOException {
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
        out.close();
    }
}