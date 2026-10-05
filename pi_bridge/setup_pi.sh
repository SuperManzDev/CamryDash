#!/usr/bin/env bash
# CamryDash Raspberry Pi Zero W Setup Script
# Run with sudo: sudo bash setup_pi.sh

set -euo pipefail

echo "=== CamryDash Pi Zero W Setup ==="

# 1. Install prerequisites
echo "[1/5] Installing Bluetooth and Python dependencies..."
apt-get update -qq
apt-get install -y -qq python3 python3-pip bluez bluetooth

# 2. Enable SPP (Compatibility mode for BlueZ)
echo "[2/5] Enabling BlueZ compatibility mode (SPP)..."
sed -i 's|^ExecStart=/usr/lib/bluetooth/bluetoothd.*|ExecStart=/usr/lib/bluetooth/bluetoothd -C|' /lib/systemd/system/bluetooth.service || true
systemctl daemon-reload
systemctl restart bluetooth

# Configure friendly Bluetooth name
echo "[3/5] Setting Bluetooth device name..."
hciconfig hci0 name "CamryDash-Bridge"
hciconfig hci0 piscan

# 3. Install bridge files
echo "[4/5] Deploying bridge script..."
mkdir -p /opt/camrydash
cp obd_bridge.py /opt/camrydash/
chmod +x /opt/camrydash/obd_bridge.py
cp camrydash-bridge.service /etc/systemd/system/

# 4. Enable and start service
echo "[5/5] Enabling systemd service..."
systemctl daemon-reload
systemctl enable camrydash-bridge.service
systemctl restart camrydash-bridge.service

echo ""
echo "Setup complete. Status:"
systemctl status camrydash-bridge.service --no-pager || true
echo ""
echo "Pair your Android tablet with 'CamryDash-Bridge' in Bluetooth Settings."
