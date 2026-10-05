package com.car.camrydash;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TranslationBoxDialog implements View.OnClickListener {

    public interface OnSettingsAppliedListener {
        void onApplied(String sourceMode, String targetDeviceName, String ip, int port);
    }

    private final Context context;
    private final OnSettingsAppliedListener listener;
    private final SharedPreferences prefs;

    private Dialog activeDialog;
    private RadioGroup rgSource;
    private RadioButton rbTranslationBox;
    private RadioButton rbObdPi;
    private RadioButton rbDirect;
    private RadioButton rbSimulator;
    private Spinner spnBt;
    private Button btnPairNew;
    private Button btnRefresh;
    private EditText etIp;
    private EditText etPort;
    private TextView tvStatus;
    private Button btnCancel;
    private Button btnConnect;

    public TranslationBoxDialog(Context context, OnSettingsAppliedListener listener) {
        this.context = context;
        this.listener = listener;
        this.prefs = context.getSharedPreferences("camrydash_prefs", Context.MODE_PRIVATE);
    }

    public void show() {
        activeDialog = new Dialog(context);
        activeDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activeDialog.setContentView(R.layout.dialog_translationbox);

        if (activeDialog.getWindow() != null) {
            activeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        rgSource = (RadioGroup) activeDialog.findViewById(R.id.rg_source);
        rbTranslationBox = (RadioButton) activeDialog.findViewById(R.id.rb_translationbox);
        rbObdPi = (RadioButton) activeDialog.findViewById(R.id.rb_obdpi);
        rbDirect = (RadioButton) activeDialog.findViewById(R.id.rb_direct);
        rbSimulator = (RadioButton) activeDialog.findViewById(R.id.rb_simulator);

        spnBt = (Spinner) activeDialog.findViewById(R.id.spn_bt_devices);
        btnPairNew = (Button) activeDialog.findViewById(R.id.btn_open_bt_settings);
        btnRefresh = (Button) activeDialog.findViewById(R.id.btn_refresh_bt);
        etIp = (EditText) activeDialog.findViewById(R.id.et_obd_ip);
        etPort = (EditText) activeDialog.findViewById(R.id.et_obd_port);
        tvStatus = (TextView) activeDialog.findViewById(R.id.tv_connection_status);
        btnCancel = (Button) activeDialog.findViewById(R.id.btn_cancel);
        btnConnect = (Button) activeDialog.findViewById(R.id.btn_connect);

        // Load saved preferences
        String currentMode = prefs.getString("source_mode", "simulator");
        if ("translationbox".equals(currentMode)) {
            rbTranslationBox.setChecked(true);
        } else if ("obdpi".equals(currentMode)) {
            rbObdPi.setChecked(true);
        } else if ("direct".equals(currentMode)) {
            rbDirect.setChecked(true);
        } else {
            rbSimulator.setChecked(true);
        }

        etIp.setText(prefs.getString("obd_ip", "192.168.0.10"));
        etPort.setText(String.valueOf(prefs.getInt("obd_port", 35000)));

        // Load Bluetooth devices
        populateBtDevices(spnBt);

        btnRefresh.setOnClickListener(this);
        btnPairNew.setOnClickListener(this);
        btnCancel.setOnClickListener(this);
        btnConnect.setOnClickListener(this);

        activeDialog.show();

        if (activeDialog.getWindow() != null) {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int dialogWidth = Math.min((int) (560 * dm.density), (int) (dm.widthPixels * 0.85f));
            int dialogHeight = Math.min((int) (580 * dm.density), (int) (dm.heightPixels * 0.90f));
            activeDialog.getWindow().setLayout(dialogWidth, dialogHeight);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == btnRefresh) {
            populateBtDevices(spnBt);
            Toast.makeText(context, "Device list refreshed", Toast.LENGTH_SHORT).show();
        } else if (v == btnPairNew) {
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "Cannot open Bluetooth settings", Toast.LENGTH_SHORT).show();
            }
        } else if (v == btnCancel) {
            if (activeDialog != null) {
                activeDialog.dismiss();
            }
        } else if (v == btnConnect) {
            String mode = "simulator";
            if (rbTranslationBox.isChecked()) mode = "translationbox";
            else if (rbObdPi.isChecked()) mode = "obdpi";
            else if (rbDirect.isChecked()) mode = "direct";

            String selectedDev = spnBt.getSelectedItem() != null ? spnBt.getSelectedItem().toString() : "";
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) ip = "192.168.0.10";

            int port = 35000;
            try {
                port = Integer.parseInt(etPort.getText().toString().trim());
            } catch (NumberFormatException ignored) {}

            // Save preferences
            prefs.edit()
                    .putString("source_mode", mode)
                    .putString("target_device", selectedDev)
                    .putString("obd_ip", ip)
                    .putInt("obd_port", port)
                    .apply();

            if (activeDialog != null) {
                activeDialog.dismiss();
            }

            if (listener != null) {
                listener.onApplied(mode, selectedDev, ip, port);
            }
        }
    }

    private void populateBtDevices(Spinner spinner) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        List<String> deviceList = new ArrayList<>();
        int selectedIndex = 0;
        String savedDev = prefs.getString("target_device", "");

        if (adapter != null) {
            try {
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                if (bonded != null) {
                    int idx = 0;
                    for (BluetoothDevice dev : bonded) {
                        String name = dev.getName();
                        if (name == null || name.isEmpty()) name = dev.getAddress();
                        deviceList.add(name);
                        if (name.equalsIgnoreCase(savedDev) || name.toLowerCase().contains("translation") || name.toLowerCase().contains("camry") || name.toLowerCase().contains("obd")) {
                            selectedIndex = idx;
                        }
                        idx++;
                    }
                }
            } catch (SecurityException ignored) {}
        }

        if (deviceList.isEmpty()) {
            deviceList.add("No Paired Devices Found");
        }

        ArrayAdapter<String> adapterList = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, deviceList);
        spinner.setAdapter(adapterList);
        if (selectedIndex < deviceList.size()) {
            spinner.setSelection(selectedIndex);
        }
    }
}
