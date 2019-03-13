/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.geldmaatjegat;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.RequiresPermission;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static com.example.geldmaatjegat.Constant.ALARM;
import static com.example.geldmaatjegat.Constant.ALARM_TIME_LENGTH;
import static com.example.geldmaatjegat.Constant.ALLOWENCE;
import static com.example.geldmaatjegat.Constant.ALLOWENCE_TIME_LENGTH;
import static com.example.geldmaatjegat.Constant.BALANCE;
import static com.example.geldmaatjegat.Constant.BALANCE_LENGTH;
import static com.example.geldmaatjegat.Constant.BED_LIGHT;
import static com.example.geldmaatjegat.Constant.BED_LIGHT_DURATION_LENGTH;
import static com.example.geldmaatjegat.Constant.BED_LIGHT_TIME_LENGTH;
import static com.example.geldmaatjegat.Constant.FIRMWARE_TRANSFER;
import static com.example.geldmaatjegat.Constant.FIRMWARE_TRANSFER_LENGTH;
import static com.example.geldmaatjegat.Constant.HIPPO;
import static com.example.geldmaatjegat.Constant.IDENTITY;
import static com.example.geldmaatjegat.Constant.OFF;
import static com.example.geldmaatjegat.Constant.ON;
import static com.example.geldmaatjegat.Constant.PICTURE_TRANSFER;
import static com.example.geldmaatjegat.Constant.PICTURE_TRANSFER_LENGTH;
import static com.example.geldmaatjegat.Constant.SOUND;
import static com.example.geldmaatjegat.Constant.SQUIRREL;
import static com.example.geldmaatjegat.Constant.TIME;
import static com.example.geldmaatjegat.Constant.TIME_LENGTH;
import static com.example.geldmaatjegat.Constant.TIME_ZONE;
import static java.util.Arrays.copyOfRange;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements View.OnClickListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    BluetoothGattCharacteristic characteristicGM;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private static final int READ_REQUEST_CODE = 42;
    private static final int READ_REQUEST_CODE2 = 43;
    private static final int READ_REQUEST_FIRMWARE = 45;

    byte[] combined;
    byte[] hash;

    byte[] resultWritten = null;

    public HashMap<String, Byte> pictureMap = new HashMap();

    TextView textView = null;

    String nameUpload = "";
    TextView nameUploadTextview = null;



    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.

            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

/*    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        //((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = null; //(ExpandableListView) findViewById(R.id.gatt_services_list);
        //mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        Button timeButton = findViewById(R.id.time);
        Button balanceButton = findViewById(R.id.balance);
        Button alarmSetButton = findViewById(R.id.alarmOn);
        Button alarmOffButton = findViewById(R.id.alarmOff);
        Button bedlightOnButton = findViewById(R.id.blOn);
        Button bedlightOffButton = findViewById(R.id.blOff);
        Button allawence1w = findViewById(R.id.oneWeek);
        Button allawnce2w = findViewById(R.id.twoWeeks);
        Button allawence1m = findViewById(R.id.month);
        Button allawenceOff = findViewById(R.id.allowenceOff);

        Button soundOnButton = findViewById(R.id.soundOn);
        Button soundOffButton = findViewById(R.id.soundOff);

        Button identityHippo = findViewById(R.id.squirrel);
        Button identitySquirel = findViewById(R.id.hippo);

        Button uploadImage = findViewById(R.id.uploadImage);
        Button uploadImageInit = findViewById(R.id.uploadImageInit);

        Button uploadFirmWare = findViewById(R.id.uploadFirmware);
        Button upleadFirmWareInit = findViewById(R.id.uploadImageInit);


        balanceButton.setOnClickListener(this);
        timeButton.setOnClickListener(this);
        alarmSetButton.setOnClickListener(this);
        alarmOffButton.setOnClickListener(this);
        bedlightOnButton.setOnClickListener(this);
        bedlightOffButton.setOnClickListener(this);
        allawence1w.setOnClickListener(this);
        allawnce2w.setOnClickListener(this);
        allawence1m.setOnClickListener(this);
        allawenceOff.setOnClickListener(this);
        soundOnButton.setOnClickListener(this);
        soundOffButton.setOnClickListener(this);
        identityHippo.setOnClickListener(this);
        identitySquirel.setOnClickListener(this);
        uploadImage.setOnClickListener(this);
        uploadImageInit.setOnClickListener(this);
        uploadFirmWare.setOnClickListener(this);

        pictureMap.put("SNS clock", (byte) 0x0b);
        pictureMap.put("SNS alarm", (byte) 0x0c);
        pictureMap.put("SNS account", (byte) 0x0d);
        pictureMap.put("SNS Allowance day", (byte) 0x0e);
        pictureMap.put("SNS Increase", (byte) 0x0f);
        pictureMap.put("SNS decrease", (byte) 0x10);
        pictureMap.put("SNS Night Light", (byte) 0x11);

        pictureMap.put("ASN clock", (byte) 0x15);
        pictureMap.put("ASN alarm", (byte) 0x16);
        pictureMap.put("ASN account", (byte) 0x17);
        pictureMap.put("ASN Allowance day", (byte) 0x18);
        pictureMap.put("ASN Increase", (byte) 0x19);
        pictureMap.put("ASN decrease", (byte) 0x1a);
        pictureMap.put("ASN Night Light", (byte) 0xab);
        //get the spinner from the xml.
        Spinner dropdown = findViewById(R.id.spinner1);
        //create a list of items for the spinner.
        String[] items = new String[]{"SNS clock", "SNS alarm", "SNS account", "SNS Allowance day", "SNS Increase", "SNS decrease", "SNS Night Light",
                "ASN clock", "ASN alarm", "ASN account", "ASN Allowance day", "ASN Increase", "ASN decrease", "ASN Night Light"};
        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        //There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        dropdown.setAdapter(adapter);

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);

        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);

                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        nameUploadTextview = findViewById(R.id.nameUpload);

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                try {
                    readTextFromUri(uri,1);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        if (requestCode == READ_REQUEST_CODE2 && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                try {
                    readTextFromUri(uri, 1);
                    nameUploadTextview.setText("PICTURE: \n" + uri.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        if (requestCode == READ_REQUEST_FIRMWARE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                try {
                    readTextFromUri(uri, 2);
                    nameUploadTextview.setText("FIRMWARE: \n " + uri.toString());

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {


                mConnectionState.setText(resourceId);

            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data.replaceAll("[^a-zA-Z0-9 ]", "" ));
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        //mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public String byteToHex(byte[] numArray) {
        String result = "";
        for(byte num: numArray){
            char[] hexDigits = new char[2];
            hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
            hexDigits[1] = Character.forDigit((num & 0xF), 16);
            result += "0x";
            result += new String(hexDigits);
            result += " ";
        }

        return result;
    }

    public byte[] longToByteArray(long input, int size, byte command){
        byte[] inputArray = ByteBuffer.allocate(Long.BYTES).putLong(input).array();


        byte[] result = new byte[size+1];

        for(int i = 0; i<size; i++){
            result[result.length-1-i] = inputArray[inputArray.length-1-i];
        }
        result[0] = command;

        return result;

    }

    public byte[] longToByteArray(long input1,long input2,  int size1, int size2, byte command){
        byte[] inputArray1 = ByteBuffer.allocate(Long.BYTES).putLong(input1).array();
        byte[] inputArray2 = ByteBuffer.allocate(Long.BYTES).putLong(input2).array();



        byte[] result = new byte[size1 + size2 +1];

        for(int i = 0; i<size2; i++){
            result[result.length-1-i] = inputArray2[inputArray2.length-1-i];
        }
        for(int i = 0; i<size1; i++){
            result[result.length - size2-1-i] = inputArray1[inputArray1.length-1-i];
        }
        result[0] = command;

        return result;

    }

    @Override
    public void onClick(View v) {
        connectGeldMaatje();

        mDataField.setText("NULL");

        textView = (TextView) findViewById(R.id.textView2);


        final TextView balanceInput = findViewById(R.id.balance_input);

        final TextView yearInput = findViewById(R.id.year_text_input);
        final TextView monthInput = findViewById(R.id.month_input);
        final TextView dayInput = findViewById(R.id.day_input);
        final TextView hourInput = findViewById(R.id.hour_input);
        final TextView minuteInput = findViewById(R.id.minut_input);
        final TextView secondInput = findViewById(R.id.second_text_input);

        final TextView alYearInput = findViewById(R.id.year_text_input_all);
        final TextView alMonthInput = findViewById(R.id.month_input_all);
        final TextView alDayInput = findViewById(R.id.day_input_all);
        final TextView alHourInput = findViewById(R.id.hour_input_all);
        final TextView alMinuteInput = findViewById(R.id.minut_input_all);
        final TextView alSecondInput = findViewById(R.id.second_text_input_all);

        final TextView blHourInput = findViewById(R.id.hour_input_bl);
        final TextView blMinuteInput = findViewById(R.id.minut_input_bl);
        final TextView blSecondInput = findViewById(R.id.second_text_input_bl);
        final TextView blDuration = findViewById(R.id.duraction);

        final TextView alarmHourInput = findViewById(R.id.hour_input_alarm);
        final TextView alarmMinuteInput = findViewById(R.id.minut_input_alarm);
        final TextView alarmSecondInput = findViewById(R.id.second_text_input_alarm);

        final CheckBox checkBoxMa = findViewById(R.id.checkBoxMa);
        final CheckBox checkBoxDi = findViewById(R.id.checkBoxDi);
        final CheckBox checkBoxWoe = findViewById(R.id.checkBoxWo);
        final CheckBox checkBoxDo = findViewById(R.id.checkBoxDo);
        final CheckBox checkBoxVrij = findViewById(R.id.checkBoxVrij);
        final CheckBox checkBoxZa = findViewById(R.id.checkBoxZa);
        final CheckBox checkBoxZo = findViewById(R.id.checkBoxZo);




        switch (v.getId()){
            case R.id.balance:
                resultWritten = setBalance(getLong(balanceInput));
                break;
            case R.id.time:
                resultWritten = setTime(getTime(yearInput, monthInput, dayInput, hourInput, minuteInput, secondInput));
                break;
            case R.id.alarmOn:
                resultWritten = setAlarm(true, getTime(alarmHourInput, alarmMinuteInput, alarmSecondInput),
                        checkBoxMa.isChecked(), checkBoxDi.isChecked(), checkBoxWoe.isChecked(),checkBoxDo.isChecked(),
                        checkBoxVrij.isChecked(), checkBoxZa.isChecked(), checkBoxZo.isChecked());
                break;
            case R.id.alarmOff:
                resultWritten = setAlarm(false, getTime(alarmHourInput, alarmMinuteInput, alarmSecondInput),
                        checkBoxMa.isChecked(), checkBoxDi.isChecked(), checkBoxWoe.isChecked(),checkBoxDo.isChecked(),
                        checkBoxVrij.isChecked(), checkBoxZa.isChecked(), checkBoxZo.isChecked());
                break;
            case R.id.blOn:
                resultWritten = bedLight(true, getTime(blHourInput, blMinuteInput, blSecondInput), getLong(blDuration));
                break;
            case R.id.blOff:
                resultWritten = bedLight(false, getTime(blHourInput, blMinuteInput, blSecondInput), getLong(blDuration));
                break;
            case R.id.oneWeek:
                resultWritten = allowence(true, getTime(alYearInput, alMonthInput, alDayInput, alHourInput, alMinuteInput, alSecondInput), 0);
                break;
            case R.id.twoWeeks:
                resultWritten = allowence(true, getTime(alYearInput, alMonthInput, alDayInput, alHourInput, alMinuteInput, alSecondInput), 1);
                break;
            case R.id.month:
                resultWritten = allowence(true, getTime(alYearInput, alMonthInput, alDayInput, alHourInput, alMinuteInput, alSecondInput), 3);
                break;
            case R.id.allowenceOff:
                resultWritten = allowence(false, getTime(alYearInput, alMonthInput, alDayInput, alHourInput, alMinuteInput, alSecondInput), 3);
                break;
            case R.id.soundOn:
                resultWritten = sound(true);
                break;
            case R.id.soundOff:
                resultWritten = sound(false);
                break;
            case R.id.hippo:
                resultWritten = identity(true);
                break;
            case R.id.squirrel:
                resultWritten = identity(false);
                break;
            case R.id.uploadImage:
                resultWritten = uploadImage();
                break;
            case R.id.uploadImageInit:
                uploadImageInit();
                break;
            case R.id.uploadFirmware:
                resultWritten = uploadFirmware();
                break;
        }

        if(resultWritten == null){
            textView.setText("NULL");
        }else {
            textView.setText(byteToHex(resultWritten));
        }

    }

    private byte[] uploadImageInit(){

        for(int i = 0; i< (int) Math.ceil(combined.length/20.0)-1; i++){
            byte[] write = Arrays.copyOfRange(combined, i*20, i*20 + 20);

            mBluetoothLeService.writeValue(write,characteristicGM);
            //Log.d(TAG, "readTextFromUri:  " + byteToHex(write));
        }

        byte[] write = Arrays.copyOfRange(combined, combined.length - (combined.length%20), combined.length);
        mBluetoothLeService.writeValue(write,characteristicGM);
        //Log.d(TAG, "readTextFromUri:  " + byteToHex(write));


        mBluetoothLeService.writeValue(hash,characteristicGM);

        nameUploadTextview = findViewById(R.id.nameUpload);
        nameUploadTextview.setText("");




        //mBluetoothLeService.writeValue(new byte[]{(byte) 0x44, (byte) 0x0b,(byte) 0x03, (byte) 0xf0},characteristicGM);
        return new byte[]{(byte) 0x44, (byte) 0x0b,(byte) 0x03, (byte) 0xf0};
    }

    private byte[] uploadImage(){
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_CODE2);

        return new byte[]{(byte) 0x00};
    }

    private byte[] uploadFirmware(){
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_FIRMWARE);

        return new byte[]{(byte) 0x00};
    }


    private void readTextFromUri(Uri uri, int function) throws IOException {

        final Spinner spinner = findViewById(R.id.spinner1);
        System.out.println(spinner.getSelectedItem().toString());

        connectGeldMaatje();

        ContentResolver res = getContentResolver();
        InputStream in = res.openInputStream(uri);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        byte[] bytes = buffer.toByteArray();


        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        hash = md.digest(bytes);

        combined = Arrays.copyOf(bytes, bytes.length);

        byte[] pictureArray;

        if(function == 1){
            pictureArray = longToByteArray(new Long((int) Math.ceil(combined.length/20.0)), PICTURE_TRANSFER_LENGTH, PICTURE_TRANSFER);
            pictureArray[1] = pictureMap.get(spinner.getSelectedItem());
        } else if (function == 2){
            pictureArray = longToByteArray(new Long((int) Math.ceil(combined.length/20.0)), FIRMWARE_TRANSFER_LENGTH, FIRMWARE_TRANSFER);
            System.out.println(byteToHex(pictureArray));
        } else {
            pictureArray = longToByteArray(new Long((int) Math.ceil(combined.length/20.0)), FIRMWARE_TRANSFER_LENGTH, FIRMWARE_TRANSFER);
        }

        resultWritten = pictureArray;
        textView.setText(byteToHex(resultWritten));

        mBluetoothLeService.writeCharacterisitc(pictureArray,characteristicGM);

    }

    private byte[] allowence(boolean on, long time, int period) {
        byte[] allowenceArray = longToByteArray(time, ALLOWENCE_TIME_LENGTH, ALLOWENCE);
        allowenceArray = Arrays.copyOf(allowenceArray, allowenceArray.length + 1); //create new array from old array and allocate one more element
        allowenceArray[allowenceArray.length - 1] = (byte) period;
        allowenceArray[1] = on? ON:OFF;
        mBluetoothLeService.writeCharacterisitc(allowenceArray, characteristicGM);
        return allowenceArray;

    }

    private byte[] setAlarm(boolean on, long time, boolean ma, boolean di, boolean woe, boolean don, boolean vrij, boolean za, boolean zo) {
        byte[] alarmArray = longToByteArray(time, ALARM_TIME_LENGTH, ALARM);

        boolean[] week = new boolean[]{ma,di,woe,don,vrij,za, zo};

        for(int i = 0; i<week.length; i++){
            boolean day = week[i];
            if(day){
                alarmArray = Arrays.copyOf(alarmArray, alarmArray.length + 1); //create new array from old array and allocate one more element
                alarmArray[alarmArray.length - 1] = (byte) i;
            }
        }
        alarmArray[1] = on? ON:OFF;
        mBluetoothLeService.writeCharacterisitc(alarmArray, characteristicGM);
        return alarmArray;
    }

    private byte[] bedLight(boolean on, long startTime, long duration) {
        byte[] bedLightArray = longToByteArray(startTime, duration, BED_LIGHT_TIME_LENGTH, BED_LIGHT_DURATION_LENGTH, BED_LIGHT);
        bedLightArray[1] = on ? ON : OFF;
        mBluetoothLeService.writeCharacterisitc(bedLightArray, characteristicGM);

        return bedLightArray;
    }

    private byte[] identity(boolean isHippo){
        byte[] identityArray = new byte[]{IDENTITY, isHippo ? HIPPO : SQUIRREL};
        mBluetoothLeService.writeCharacterisitc(identityArray, characteristicGM);

        return identityArray;

    }

    private byte[] sound(boolean on) {

        byte[] soundArray = new byte[]{SOUND, on ? ON : OFF};
        mBluetoothLeService.writeCharacterisitc(soundArray, characteristicGM);

        return soundArray;

    }

    private byte[] setBalance(long balance) {

        byte[] balanceArray = longToByteArray(Math.abs(balance), BALANCE_LENGTH, BALANCE);
        if(balance > 0){
            balanceArray[1] = ON;
        } else {
            balanceArray[1] = OFF;

        }
        mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic,true);
        mBluetoothLeService.writeCharacterisitc(balanceArray, characteristicGM);

        return balanceArray;

    }

    private long getLong(TextView input){
        String inputString = input.getText().toString();
        return Long.valueOf(inputString.replace(",", ""));

    }

    private int getInt(TextView input){
        String inputString = input.getText().toString();
        return Integer.valueOf(inputString.replace(",", ""));

    }

    private long getTime(TextView year, TextView month, TextView day,
                         TextView hour, TextView minute, TextView seconds){
        ZonedDateTime time = ZonedDateTime.of( getInt(year) , getInt(month) , getInt(day) , getInt(hour) , getInt(minute) , getInt(seconds) , 0 , ZoneId.of( TIME_ZONE ) );

        return time.toEpochSecond() - 946688400;
    }

    private long getTime(TextView hour, TextView minute, TextView seconds){
        ZonedDateTime time = ZonedDateTime.of( 2000 , 1 , 1, getInt(hour) , getInt(minute) , getInt(seconds) , 0 , ZoneId.of( TIME_ZONE ) );

        return time.toEpochSecond() - 946688400;
    }

    private byte[] setTime(long time){

        byte[] timeArray = longToByteArray(time, TIME_LENGTH,TIME );
        mBluetoothLeService.writeCharacterisitc(timeArray, characteristicGM);

        return timeArray;
    }

    private void connectGeldMaatje(){
        for (BluetoothGattService gattService : mBluetoothLeService.getSupportedGattServices()) {
            if (gattService.getUuid().compareTo(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")) == 0) {
                for (BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
                    if (characteristic.getUuid().compareTo(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")) == 0) {
                        characteristicGM = characteristic;
                    }
                    if(characteristic.getUuid().compareTo(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")) == 0) {
                        mNotifyCharacteristic = characteristic;
                    }
                }
            }
        }
    }

}
