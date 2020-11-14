package com.example.mytestapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
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
        SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
        Executors.newSingleThreadExecutor().submit(usbIoManager);
    }


    @Override
    public void onNewData(final byte[] data) {
        Log.d(TAG, new String(data, StandardCharsets.US_ASCII));
        //final TextView textView = this.findViewById(R.id.textID****);



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

    @Override
    public void onRunError(Exception e) {
    }
}
