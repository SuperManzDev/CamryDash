#!/usr/bin/env python3
"""
CamryDash Direct OBD-Pi Service.
Connects directly to the 2012 Toyota Camry CAN bus via MCP2515 CAN Hat
(OBD-II Pin 6: CAN-H, Pin 14: CAN-L) using Linux SocketCAN (can0 at 500 kbps).
Broadcasts parsed vehicle telemetry over Bluetooth RFCOMM to CamryDash.
"""

import sys
import time
import socket
import struct
import select
import json
import logging
import argparse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger("OBD-Pi")

RFCOMM_CHANNEL = 1
CAN_INTERFACE = "can0"

# Standard SAE J1979 11-bit CAN IDs
OBD_REQUEST_ID = 0x7DF
OBD_RESPONSE_ID_BASE = 0x7E8


class DirectCanClient:
    """Manages raw SocketCAN communication with MCP2515 hardware."""

    def __init__(self, interface=CAN_INTERFACE, timeout=0.1):
        self.interface = interface
        self.timeout = timeout
        self.sock = None

    def connect(self):
        logger.info("Opening raw SocketCAN interface on %s...", self.interface)
        try:
            self.sock = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
            self.sock.bind((self.interface,))
            self.sock.settimeout(self.timeout)
            logger.info("SocketCAN opened successfully on %s.", self.interface)
        except Exception as e:
            logger.error("Failed to bind SocketCAN on %s: %s", self.interface, e)
            logger.info("Ensure interface is up: 'sudo ip link set can0 up type can bitrate 500000'")
            self.sock = None
            raise

    def send_pid_query(self, pid):
        """Sends an 11-bit OBD-II PID query frame (Mode 01)."""
        if not self.sock:
            return
        # DLC 8: [02] [01] [PID] [00 00 00 00 00]
        payload = bytes([0x02, 0x01, pid, 0x00, 0x00, 0x00, 0x00, 0x00])
        can_frame = struct.pack("=IB3x8s", OBD_REQUEST_ID, len(payload), payload)
        self.sock.send(can_frame)

    def receive_response(self, target_pid, timeout=0.08):
        """Reads CAN frames until an OBD reply for target_pid is found."""
        if not self.sock:
            return None

        start = time.time()
        while time.time() - start < timeout:
            try:
                cf, _ = self.sock.recvfrom(16)
                can_id, can_dlc, data = struct.unpack("=IB3x8s", cf)
                can_id &= 0x7FF  # 11-bit mask

                # ECU reply is typically 0x7E8 (Engine ECU)
                if can_id in (0x7E8, 0x7E9):
                    # Data: [Length] [Mode + 0x40] [PID] [Bytes...]
                    mode = data[1]
                    pid = data[2]
                    if mode == 0x41 and pid == target_pid:
                        return data[3:3 + (data[0] - 2)]
            except socket.timeout:
                break
            except Exception as e:
                logger.debug("SocketCAN read error: %s", e)
                break
        return None

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass
            self.sock = None


def run_obdpi(interface="can0", simulate=False):
    # Setup Bluetooth RFCOMM server
    try:
        bt_server = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        bt_server.bind(("", RFCOMM_CHANNEL))
        bt_server.listen(1)
        bt_server.setblocking(False)
        logger.info("Bluetooth RFCOMM server listening on channel %d.", RFCOMM_CHANNEL)
    except Exception as e:
        logger.error("Failed to start Bluetooth RFCOMM server: %s", e)
        return 1

    can_client = None
    if not simulate:
        can_client = DirectCanClient(interface=interface)

    client_sock = None
    last_poll = 0
    poll_interval = 0.05  # 20 Hz

    telemetry = {
        "rpm": 0.0,
        "spd": 0.0,
        "temp": 195.0,
        "intake": 74.0,
        "load": 0.0,
        "tps": 0.0,
        "volt": 14.1,
        "maf": 0.0
    }
    sim_tick = 0.0

    while True:
        # Check incoming Bluetooth client
        if client_sock is None:
            try:
                readable, _, _ = select.select([bt_server], [], [], 0.05)
                if readable:
                    client_sock, client_addr = bt_server.accept()
                    client_sock.setblocking(False)
                    logger.info("CamryDash connected from %s", client_addr)
            except Exception as e:
                logger.debug("BT accept error: %s", e)

        # Connect SocketCAN if needed
        if not simulate and can_client:
            if can_client.sock is None:
                try:
                    can_client.connect()
                except Exception:
                    time.sleep(2.0)
                    continue

        now = time.time()
        if now - last_poll >= poll_interval:
            last_poll = now

            if simulate:
                sim_tick += 0.05
                telemetry["rpm"] = 1750.0 + (sim_tick % 10.0) * 160.0
                telemetry["spd"] = 40.0 + (sim_tick % 10.0) * 2.8
                telemetry["load"] = 28.0 + (sim_tick % 5.0) * 2.5
                telemetry["tps"] = 18.0 + (sim_tick % 5.0) * 2.0
                telemetry["maf"] = 15.0 + (sim_tick % 5.0) * 1.5
                telemetry["volt"] = 14.18
                telemetry["temp"] = 195.0
            else:
                try:
                    # Query Engine RPM (PID 0x0C)
                    can_client.send_pid_query(0x0C)
                    r_rpm = can_client.receive_response(0x0C)
                    if r_rpm and len(r_rpm) >= 2:
                        telemetry["rpm"] = round(((r_rpm[0] * 256) + r_rpm[1]) / 4.0, 1)

                    # Query Speed (PID 0x0D)
                    can_client.send_pid_query(0x0D)
                    r_spd = can_client.receive_response(0x0D)
                    if r_spd and len(r_spd) >= 1:
                        telemetry["spd"] = round(r_spd[0] * 0.621371, 1)

                    # Query Engine Load (PID 0x04)
                    can_client.send_pid_query(0x04)
                    r_load = can_client.receive_response(0x04)
                    if r_load and len(r_load) >= 1:
                        telemetry["load"] = round((r_load[0] * 100.0) / 255.0, 1)

                    # Query Coolant Temp (PID 0x05)
                    can_client.send_pid_query(0x05)
                    r_temp = can_client.receive_response(0x05)
                    if r_temp and len(r_temp) >= 1:
                        temp_c = r_temp[0] - 40
                        telemetry["temp"] = round((temp_c * 9.0 / 5.0) + 32.0, 1)

                    # Query Throttle (PID 0x11)
                    can_client.send_pid_query(0x11)
                    r_tps = can_client.receive_response(0x11)
                    if r_tps and len(r_tps) >= 1:
                        telemetry["tps"] = round((r_tps[0] * 100.0) / 255.0, 1)

                    # Query MAF (PID 0x10)
                    can_client.send_pid_query(0x10)
                    r_maf = can_client.receive_response(0x10)
                    if r_maf and len(r_maf) >= 2:
                        telemetry["maf"] = round(((r_maf[0] * 256) + r_maf[1]) / 100.0, 1)

                except Exception as e:
                    logger.warning("CAN query error: %s", e)
                    if can_client:
                        can_client.close()
                    continue

            # Stream payload over Bluetooth
            if client_sock:
                payload = json.dumps(telemetry) + "\n"
                try:
                    client_sock.sendall(payload.encode("utf-8"))
                except Exception as e:
                    logger.info("CamryDash disconnected: %s", e)
                    try:
                        client_sock.close()
                    except Exception:
                        pass
                    client_sock = None

        time.sleep(0.01)


def main():
    parser = argparse.ArgumentParser(description="CamryDash Direct OBD-Pi MCP2515 CAN Service")
    parser.add_argument("--interface", default=CAN_INTERFACE, help="SocketCAN interface (default can0)")
    parser.add_argument("--simulate", action="store_true", help="Run with simulated telemetry without CAN Hat")
    args = parser.parse_args()

    logger.info("Starting OBD-Pi Direct CAN Bridge...")
    try:
        run_obdpi(args.interface, args.simulate)
    except KeyboardInterrupt:
        logger.info("Stopped by user.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
