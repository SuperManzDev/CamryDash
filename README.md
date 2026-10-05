# CamryDash

Custom automotive telemetry dashboard and Raspberry Pi Zero W bridge for a 2012 Toyota Camry (2.5L I-4 2AR-FE engine with U760E 6-speed automatic transmission).

The setup keeps tablet Wi-Fi dedicated to wireless Apple CarPlay (MNZCARPLAY) while receiving live CAN bus telemetry from a generic Wi-Fi OBD-II dongle over Bluetooth RFCOMM.

## Architecture

Generic Wi-Fi OBD-II adapters broadcast their own ad-hoc or access point network (typically 192.168.0.10:35000). Joining that network directly from the tablet cuts off the tablet's Wi-Fi hotspot required for wireless CarPlay.

The Raspberry Pi Zero W solves this by acting as an intermediary bridge:

1. Pi Zero W Wi-Fi connects to the Wi-Fi OBD-II dongle.
2. The Pi polling daemon (`obd_bridge.py`) queries engine PIDs over TCP at 20 Hz.
3. The Pi broadcasts parsed telemetry frames over Bluetooth Serial Port Profile (RFCOMM channel 1).
4. The tablet connects to the Pi over Bluetooth, keeping its Wi-Fi hotspot active for CarPlay.

```
[Vehicle OBD-II Port]
       │
       ▼
[Wi-Fi OBD Dongle] (192.168.0.10:35000)
       │  (Wi-Fi AP)
       ▼
[Raspberry Pi Zero W]
       │  (Bluetooth RFCOMM SPP)
       ▼
[Android Tablet: CamryDash App] (Runs fullscreen or split 50/50 with CarPlay)
```

## Features

- Dynamic Layout Reflow: Renders a dual-gauge layout in fullscreen landscape (1024x600) and automatically reflows into a stacked gauge grid in split-screen (512x600) when run alongside navigation or CarPlay.
- Hardware Gear Estimation: Uses actual 2012 Camry U760E gear ratios (1st: 3.300, 2nd: 1.900, 3rd: 1.420, 4th: 1.000, 5th: 0.713, 6th: 0.608, final drive: 3.815) and tire circumference to compute transmission gear in real time.
- Offline Simulation Mode: Built-in driving simulator cycles through acceleration, gear shifts 1 through 6, cruising at 70 mph, braking, and engine warmup for testing outside the car.
- Live / Simulation Toggle: Tap the mode button in the header bar to switch between the simulator and the live Bluetooth stream.
- Zero External Dependencies: Drawn directly to Canvas for high refresh rates and minimal battery draw on Android 7.0+ devices.

## Metrics Displayed

- Digital Speedometer (MPH)
- Tachometer Arc (0 to 7000 RPM with 6200+ RPM redline)
- Transmission Gear Badge (P, R, N, D, 1, 2, 3, 4, 5, 6)
- Engine Coolant Temperature (°F with blue/green/amber/red color stages)
- Intake Air Temperature (°F)
- Battery Voltage (V with charging/discharging warnings)
- Calculated Engine Load (%)
- Throttle Position (%)
- Mass Air Flow (g/s)

## Project Structure

```
CamryDash/
├── AndroidManifest.xml
├── build.ps1
├── CamryDash.apk
├── pi_bridge/
│   ├── obd_bridge.py
│   ├── camrydash-bridge.service
│   └── setup_pi.sh
├── res/
│   ├── drawable/
│   │   └── ic_launcher.xml
│   └── values/
│       └── strings.xml
└── src/
    └── com/
        └── car/
            └── camrydash/
                ├── BtManager.java
                ├── CamryDashView.java
                ├── CamryData.java
                ├── MainActivity.java
                └── MockDataGenerator.java
```

## Building the APK

Run the PowerShell build script:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

The script compiles the Java sources, dexes with D8, packages resources with AAPT, aligns, and signs `CamryDash.apk`.

## Raspberry Pi Zero W Setup

1. Copy the `pi_bridge` directory to your Raspberry Pi:
   ```bash
   scp -r pi_bridge pi@<pi-ip>:~/
   ```

2. Run the automated setup script on the Pi:
   ```bash
   cd ~/pi_bridge
   sudo bash setup_pi.sh
   ```

3. Connect the Pi to your Wi-Fi OBD dongle's network (e.g. `WiFi_OBDII`):
   ```bash
   sudo nmcli dev wifi connect "WiFi_OBDII"
   ```

4. Pair your Android tablet with the Pi (named `CamryDash-Bridge`) in Android Bluetooth Settings.

5. Open CamryDash on the tablet. The app connects automatically and displays live vehicle telemetry.
