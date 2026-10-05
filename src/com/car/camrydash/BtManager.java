package com.car.camrydash;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.UUID;

public class BtManager {
    private static final String TAG = "CamryBtManager";

    // Standard SerialPort (SPP) UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface OnTelemetryListener {
        void onData(CamryData data);
        void onStatusChange(String status, boolean isConnected);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter btAdapter;
    private final CamryData liveData = new CamryData();
    private OnTelemetryListener listener;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private boolean isRunning = false;
    private String targetDeviceName = null;

    private final RetryRunnable retryRunnable;
    private final DataRunnable dataRunnable;

    public BtManager(OnTelemetryListener listener) {
        this.listener = listener;
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
        this.liveData.isSimulation = false;
        this.retryRunnable = new RetryRunnable(this);
        this.dataRunnable = new DataRunnable(this);
    }

    public CamryData getLiveData() {
        return liveData;
    }

    public boolean isBluetoothAvailable() {
        return btAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return btAdapter != null && btAdapter.isEnabled();
    }

    public void start(String deviceNameHint) {
        this.targetDeviceName = deviceNameHint;
        this.isRunning = true;
        reconnect();
    }

    public void stop() {
        this.isRunning = false;
        mainHandler.removeCallbacks(retryRunnable);
        disconnect();
    }

    private synchronized void reconnect() {
        if (!isRunning) return;
        disconnect();

        if (btAdapter == null || !btAdapter.isEnabled()) {
            notifyStatus("BLUETOOTH DISABLED", false);
            return;
        }

        BluetoothDevice targetDevice = null;
        try {
            targetDevice = findTargetDevice();
        } catch (SecurityException e) {
            notifyStatus("BT PERMISSION REQUIRED", false);
            return;
        }

        if (targetDevice == null) {
            notifyStatus("NO PAIRED PI BRIDGE FOUND", false);
            mainHandler.postDelayed(retryRunnable, 4000);
            return;
        }

        String devName = "BRIDGE";
        try {
            devName = targetDevice.getName();
        } catch (SecurityException ignored) {}

        notifyStatus("CONNECTING TO " + devName, false);
        connectThread = new ConnectThread(this, targetDevice);
        connectThread.start();
    }

    private BluetoothDevice findTargetDevice() {
        if (btAdapter == null) return null;
        Set<BluetoothDevice> paired = null;
        try {
            paired = btAdapter.getBondedDevices();
        } catch (SecurityException e) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e);
            return null;
        }
        if (paired == null || paired.isEmpty()) return null;

        if (targetDeviceName != null && !targetDeviceName.isEmpty()) {
            for (BluetoothDevice dev : paired) {
                try {
                    if (dev.getName() != null && dev.getName().equalsIgnoreCase(targetDeviceName)) {
                        return dev;
                    }
                } catch (SecurityException ignored) {}
            }
        }

        for (BluetoothDevice dev : paired) {
            try {
                String name = dev.getName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("camry") || lower.contains("obd") || lower.contains("raspberry") || lower.contains("bridge")) {
                        return dev;
                    }
                }
            } catch (SecurityException ignored) {}
        }

        if (paired.size() == 1) {
            return paired.iterator().next();
        }

        return null;
    }

    private synchronized void disconnect() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    private void notifyStatus(final String msg, final boolean connected) {
        liveData.connected = connected;
        liveData.statusText = msg;
        mainHandler.post(new StatusRunnable(this, msg, connected));
    }

    private void notifyData() {
        mainHandler.post(dataRunnable);
    }

    private static class RetryRunnable implements Runnable {
        private final BtManager manager;

        RetryRunnable(BtManager mgr) {
            this.manager = mgr;
        }

        @Override
        public void run() {
            if (manager.isRunning && (manager.connectedThread == null || !manager.connectedThread.isAlive())) {
                manager.reconnect();
            }
        }
    }

    private static class StatusRunnable implements Runnable {
        private final BtManager manager;
        private final String message;
        private final boolean isConnected;

        StatusRunnable(BtManager mgr, String msg, boolean conn) {
            this.manager = mgr;
            this.message = msg;
            this.isConnected = conn;
        }

        @Override
        public void run() {
            if (manager.listener != null) {
                manager.listener.onStatusChange(message, isConnected);
            }
        }
    }

    private static class DataRunnable implements Runnable {
        private final BtManager manager;

        DataRunnable(BtManager mgr) {
            this.manager = mgr;
        }

        @Override
        public void run() {
            if (manager.listener != null) {
                manager.listener.onData(manager.liveData);
            }
        }
    }

    private static class ConnectThread extends Thread {
        private final BtManager manager;
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        ConnectThread(BtManager mgr, BluetoothDevice dev) {
            this.manager = mgr;
            this.device = dev;
            BluetoothSocket tmp = null;
            try {
                tmp = dev.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Socket create failed", e);
                try {
                    tmp = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", int.class).invoke(dev, 1);
                } catch (Exception e2) {
                    Log.e(TAG, "Reflection socket create failed", e2);
                }
            }
            this.socket = tmp;
        }

        @Override
        public void run() {
            try {
                if (manager.btAdapter != null && manager.btAdapter.isDiscovering()) {
                    manager.btAdapter.cancelDiscovery();
                }
            } catch (SecurityException ignored) {}

            if (socket == null) {
                manager.notifyStatus("SOCKET ERROR", false);
                manager.scheduleRetry();
                return;
            }

            try {
                socket.connect();
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                try {
                    socket.close();
                } catch (Exception ignored) {}
                manager.notifyStatus("CONNECTION FAILED - RETRYING", false);
                manager.scheduleRetry();
                return;
            }

            String dName = "BRIDGE";
            try {
                dName = device.getName();
            } catch (SecurityException ignored) {}

            synchronized (manager) {
                manager.connectThread = null;
                manager.connectedThread = new ConnectedThread(manager, socket, dName);
                manager.connectedThread.start();
            }
        }

        public void cancel() {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
        }
    }

    private static class ConnectedThread extends Thread {
        private final BtManager manager;
        private final BluetoothSocket socket;
        private final String devName;
        private boolean active = true;

        ConnectedThread(BtManager mgr, BluetoothSocket s, String name) {
            this.manager = mgr;
            this.socket = s;
            this.devName = name;
        }

        @Override
        public void run() {
            manager.notifyStatus("LIVE BT: " + devName, true);
            manager.liveData.isSimulation = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while (active && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    manager.parsePayload(line);
                    manager.notifyData();
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream disconnected", e);
            }

            manager.notifyStatus("DISCONNECTED - RECONNECTING", false);
            manager.scheduleRetry();
        }

        public void cancel() {
            active = false;
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void scheduleRetry() {
        if (!isRunning) return;
        mainHandler.postDelayed(retryRunnable, 3000);
    }

    private void parsePayload(String raw) {
        try {
            if (raw.startsWith("{") && raw.endsWith("}")) {
                JSONObject json = new JSONObject(raw);
                float rpm = (float) json.optDouble("rpm", liveData.rpm);
                float spd = (float) json.optDouble("spd", liveData.speedMph);
                float temp = (float) json.optDouble("temp", liveData.coolantF);
                float intake = (float) json.optDouble("intake", liveData.intakeTempF);
                float load = (float) json.optDouble("load", liveData.engineLoad);
                float tps = (float) json.optDouble("tps", liveData.throttlePos);
                float volt = (float) json.optDouble("volt", liveData.voltage);
                float maf = (float) json.optDouble("maf", liveData.maf);
                String gear = json.optString("gear", null);

                liveData.updateDirectF(rpm, spd, temp, intake, load, tps, volt, maf, gear);
            } else if (raw.contains(",")) {
                String[] parts = raw.split(",");
                if (parts.length >= 2) {
                    float rpm = Float.parseFloat(parts[0].trim());
                    float spd = Float.parseFloat(parts[1].trim());
                    float temp = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : liveData.coolantF;
                    float intake = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : liveData.intakeTempF;
                    float load = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : liveData.engineLoad;
                    float tps = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : liveData.throttlePos;
                    float volt = parts.length > 6 ? Float.parseFloat(parts[6].trim()) : liveData.voltage;
                    float maf = parts.length > 7 ? Float.parseFloat(parts[7].trim()) : liveData.maf;

                    liveData.updateDirectF(rpm, spd, temp, intake, load, tps, volt, maf, null);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse error on: " + raw, e);
        }
    }
}
