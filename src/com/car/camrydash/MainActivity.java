package com.car.camrydash;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements
        MockDataGenerator.OnTelemetryUpdateListener,
        BtManager.OnTelemetryListener,
        CamryDashView.OnModeToggleListener,
        CamryDashView.OnOpenSettingsListener,
        TranslationBoxDialog.OnSettingsAppliedListener {

    private CamryDashView dashView;
    private MockDataGenerator mockGenerator;
    private BtManager btManager;
    private TranslationBoxDialog settingsDialog;
    private boolean isSimulation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while driving
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setImmersiveMode();

        dashView = new CamryDashView(this);
        setContentView(dashView);

        // Initialize Mock Simulation
        mockGenerator = new MockDataGenerator(this);

        // Initialize Bluetooth Manager
        btManager = new BtManager(this);

        // Initialize TranslationBox Settings Dialog
        settingsDialog = new TranslationBoxDialog(this, this);

        // Mode and Settings listeners
        dashView.setOnModeToggleListener(this);
        dashView.setOnOpenSettingsListener(this);

        // Request runtime permissions on Android 12+ if needed
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                }, 101);
            }
        }

        // Restore saved preference
        SharedPreferences prefs = getSharedPreferences("camrydash_prefs", MODE_PRIVATE);
        String savedMode = prefs.getString("source_mode", "simulator");
        String targetDev = prefs.getString("target_device", null);
        setSimulationMode("simulator".equals(savedMode), targetDev);
    }

    @Override
    public void onUpdate(CamryData data) {
        if (isSimulation && dashView != null) {
            dashView.updateData(data);
        }
    }

    @Override
    public void onData(CamryData data) {
        if (!isSimulation && dashView != null) {
            dashView.updateData(data);
        }
    }

    @Override
    public void onStatusChange(String status, boolean isConnected) {
        if (!isSimulation && dashView != null) {
            dashView.updateData(btManager.getLiveData());
        }
    }

    @Override
    public void onToggleMode(boolean simulationActive) {
        SharedPreferences prefs = getSharedPreferences("camrydash_prefs", MODE_PRIVATE);
        String targetDev = prefs.getString("target_device", null);
        prefs.edit().putString("source_mode", simulationActive ? "simulator" : "translationbox").apply();
        setSimulationMode(simulationActive, targetDev);
    }

    @Override
    public void onOpenSettings() {
        if (settingsDialog != null) {
            settingsDialog.show();
        }
    }

    @Override
    public void onApplied(String sourceMode, String targetDeviceName, String ip, int port) {
        boolean enableSim = "simulator".equals(sourceMode);
        setSimulationMode(enableSim, targetDeviceName);
    }

    private void setSimulationMode(boolean enableSim, String targetDevice) {
        this.isSimulation = enableSim;
        if (enableSim) {
            btManager.stop();
            mockGenerator.start();
        } else {
            mockGenerator.stop();
            btManager.start(targetDevice);
        }
    }

    private void setImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Force view re-layout when resizing into/out of split-screen mode
        if (dashView != null) {
            dashView.requestLayout();
            dashView.invalidate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("camrydash_prefs", MODE_PRIVATE);
        String savedMode = prefs.getString("source_mode", "simulator");
        String targetDev = prefs.getString("target_device", null);
        setSimulationMode("simulator".equals(savedMode), targetDev);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mockGenerator.stop();
        btManager.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mockGenerator.stop();
        btManager.stop();
    }
}
