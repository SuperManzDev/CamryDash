#!/usr/bin/env python3
"""
CamryDash OBD-II to Bluetooth Bridge for Raspberry Pi Zero W.
Connects to generic Wi-Fi OBD-II adapter (ELM327 TCP socket)
and streams telemetry over Bluetooth RFCOMM to the CamryDash Android app.
"""

import sys
import time
import socket
import select
import json
import logging
import argparse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger("CamryBridge")

# Default Wi-Fi OBD dongle settings
DEFAULT_OBD_HOST = "192.168.0.10"
DEFAULT_OBD_PORT = 35000

# Bluetooth RFCOMM settings
RFCOMM_CHANNEL = 1
BT_SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB"


class ELM327Client:
    def __init__(self, host=DEFAULT_OBD_HOST, port=DEFAULT_OBD_PORT, timeout=3.0):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.sock = None

    def connect(self):
        logger.info("Connecting to Wi-Fi OBD adapter at %s:%d...", self.host, self.port)
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect((self.host, self.port))
        logger.info("Connected to Wi-Fi OBD adapter.")
        self._init_elm()

    def _init_elm(self):
        init_commands = [
            "AT Z\r",    # Reset
            "AT E0\r",   # Echo off
            "AT L0\r",   # Linefeeds off
            "AT S0\r",   # Spaces off
            "AT SP 6\r", # ISO 15765-4 CAN 11bit 500k (2012 Toyota Camry)
            "0100\r",    # Supported PIDs 01-20
        ]
        for cmd in init_commands:
            self._send_raw(cmd)
            resp = self._read_until_prompt()
            logger.debug("Init [%s] -> [%s]", cmd.strip(), resp)
            time.sleep(0.05)

    def _send_raw(self, cmd):
        if not self.sock:
            raise ConnectionError("OBD socket is not open")
        self.sock.sendall(cmd.encode("ascii"))

    def _read_until_prompt(self, timeout=1.5):
        self.sock.settimeout(timeout)
        buf = []
        start = time.time()
        while time.time() - start < timeout:
            chunk = self.sock.recv(64)
            if not chunk:
                break
            text = chunk.decode("ascii", errors="ignore")
            buf.append(text)
            if ">" in text:
                break
        raw = "".join(buf)
        return raw.replace(">", "").strip()

    def query(self, pid_cmd):
        self._send_raw(pid_cmd + "\r")
        return self._read_until_prompt()

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass
            self.sock = None


class TelemetryParser:
    """Parses standard SAE J1979 Mode 01 PID responses."""

    @staticmethod
    def clean_hex(resp):
        cleaned = resp.replace(" ", "").replace("\r", "").replace("\n", "").upper()
        if "NO DATA" in cleaned or "ERROR" in cleaned or "UNABLE" in cleaned:
            return None
        return cleaned

    @staticmethod
    def parse_rpm(resp):
        h = TelemetryParser.clean_hex(resp)
        if not h or "410C" not in h:
            return None
        idx = h.find("410C") + 4
        if len(h) >= idx + 4:
            a = int(h[idx:idx+2], 16)
            b = int(h[idx+2:idx+4], 16)
            return ((a * 256) + b) / 4.0
        return None

    @staticmethod
    def parse_speed(resp):
        h = TelemetryParser.clean_hex(resp)
        if not h or "410D" not in h:
            return None
        idx = h.find("410D") + 4
        if len(h) >= idx + 2:
            kmh = int(h[idx:idx+2], 16)
            return kmh * 0.621371  # Convert to MPH
        return None

    @staticmethod
    def parse_temp(resp):
        h = TelemetryParser.clean_hex(resp)
        if not h or ("4105" not in h and "410F" not in h):
            return None
        key = "4105" if "4105" in h else "410F"
        idx = h.find(key) + 4
        if len(h) >= idx + 2:
            temp_c = int(h[idx:idx+2], 16) - 40
            return (temp_c * 9.0 / 5.0) + 32.0  # Convert to Fahrenheit
        return None

    @staticmethod
    def parse_percentage(resp, pid_hex):
        h = TelemetryParser.clean_hex(resp)
        if not h or pid_hex not in h:
            return None
        idx = h.find(pid_hex) + 4
        if len(h) >= idx + 2:
            val = int(h[idx:idx+2], 16)
            return (val * 100.0) / 255.0
        return None

    @staticmethod
    def parse_maf(resp):
        h = TelemetryParser.clean_hex(resp)
        if not h or "4110" not in h:
            return None
        idx = h.find("4110") + 4
        if len(h) >= idx + 4:
            a = int(h[idx:idx+2], 16)
            b = int(h[idx+2:idx+4], 16)
            return ((a * 256) + b) / 100.0
        return None

    @staticmethod
    def parse_voltage(resp):
        h = TelemetryParser.clean_hex(resp)
        if not h:
            return None
        # Check if response is from ATRV (e.g. "14.2V")
        if "V" in resp:
            try:
                cleaned = resp.replace("V", "").strip()
                return float(cleaned)
            except ValueError:
                pass
        # Check PID 0142 (Control Module Voltage)
        if "4142" in h:
            idx = h.find("4142") + 4
            if len(h) >= idx + 4:
                a = int(h[idx:idx+2], 16)
                b = int(h[idx+2:idx+4], 16)
                return ((a * 256) + b) / 1000.0
        return None


def run_bridge(obd_host, obd_port, simulate=False):
    # Setup Bluetooth RFCOMM Server Socket
    try:
        bt_server = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        bt_server.bind(("", RFCOMM_CHANNEL))
        bt_server.listen(1)
        bt_server.setblocking(False)
        logger.info("Bluetooth RFCOMM server listening on channel %d.", RFCOMM_CHANNEL)
    except Exception as e:
        logger.error("Failed to initialize Bluetooth RFCOMM socket: %s", e)
        logger.info("Make sure Bluetooth is powered on: 'sudo hciconfig hci0 up' and SPP registered.")
        return 1

    elm = None
    if not simulate:
        elm = ELM327Client(host=obd_host, port=obd_port)

    client_sock = None
    client_addr = None

    last_poll = 0
    poll_interval = 0.05  # 20 Hz target

    # Telemetry state
    current_data = {
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
        # Check for incoming BT client connections
        if client_sock is None:
            try:
                readable, _, _ = select.select([bt_server], [], [], 0.1)
                if readable:
                    client_sock, client_addr = bt_server.accept()
                    client_sock.setblocking(False)
                    logger.info("CamryDash app connected from %s", client_addr)
            except Exception as e:
                logger.debug("BT accept error: %s", e)

        # Maintain Wi-Fi OBD connection if live mode
        if not simulate and elm:
            if elm.sock is None:
                try:
                    elm.connect()
                except Exception as e:
                    logger.warning("Wi-Fi OBD connection failed: %s. Retrying in 3s...", e)
                    time.sleep(3.0)
                    continue

        now = time.time()
        if now - last_poll >= poll_interval:
            last_poll = now

            if simulate:
                sim_tick += 0.05
                current_data["rpm"] = 1800.0 + (sim_tick % 10.0) * 150.0
                current_data["spd"] = 42.0 + (sim_tick % 10.0) * 2.5
                current_data["load"] = 28.0 + (sim_tick % 5.0) * 3.0
                current_data["tps"] = 18.0 + (sim_tick % 5.0) * 2.0
                current_data["maf"] = 14.0 + (sim_tick % 5.0) * 1.5
                current_data["volt"] = 14.15
                current_data["temp"] = 195.0
            else:
                try:
                    # Query high-frequency PIDs (RPM & Speed)
                    r_rpm = elm.query("010C")
                    rpm = TelemetryParser.parse_rpm(r_rpm)
                    if rpm is not None:
                        current_data["rpm"] = round(rpm, 1)

                    r_spd = elm.query("010D")
                    spd = TelemetryParser.parse_speed(r_spd)
                    if spd is not None:
                        current_data["spd"] = round(spd, 1)

                    # Interleave secondary PIDs on alternate ticks
                    tick_mod = int(now * 10) % 5
                    if tick_mod == 0:
                        r_load = elm.query("0104")
                        load = TelemetryParser.parse_percentage(r_load, "4104")
                        if load is not None:
                            current_data["load"] = round(load, 1)
                    elif tick_mod == 1:
                        r_tps = elm.query("0111")
                        tps = TelemetryParser.parse_percentage(r_tps, "4111")
                        if tps is not None:
                            current_data["tps"] = round(tps, 1)
                    elif tick_mod == 2:
                        r_temp = elm.query("0105")
                        temp = TelemetryParser.parse_temp(r_temp)
                        if temp is not None:
                            current_data["temp"] = round(temp, 1)
                    elif tick_mod == 3:
                        r_maf = elm.query("0110")
                        maf = TelemetryParser.parse_maf(r_maf)
                        if maf is not None:
                            current_data["maf"] = round(maf, 1)
                    elif tick_mod == 4:
                        r_volt = elm.query("ATRV")
                        volt = TelemetryParser.parse_voltage(r_volt)
                        if volt is not None:
                            current_data["volt"] = round(volt, 2)

                except Exception as e:
                    logger.warning("OBD query dropped: %s", e)
                    if elm:
                        elm.close()
                    continue

            # Stream payload to Bluetooth client
            if client_sock:
                payload = json.dumps(current_data) + "\n"
                try:
                    client_sock.sendall(payload.encode("utf-8"))
                except Exception as e:
                    logger.info("CamryDash app disconnected: %s", e)
                    try:
                        client_sock.close()
                    except Exception:
                        pass
                    client_sock = None

        time.sleep(0.01)


def main():
    parser = argparse.ArgumentParser(description="CamryDash Wi-Fi OBD to Bluetooth Bridge")
    parser.add_argument("--host", default=DEFAULT_OBD_HOST, help="Wi-Fi OBD dongle IP (default 192.168.0.10)")
    parser.add_argument("--port", type=int, default=DEFAULT_OBD_PORT, help="Wi-Fi OBD dongle port (default 35000)")
    parser.add_argument("--simulate", action="store_true", help="Run with simulated telemetry without physical OBD")
    args = parser.parse_args()

    logger.info("Starting CamryDash Bridge on Raspberry Pi Zero W...")
    try:
        run_bridge(args.host, args.port, args.simulate)
    except KeyboardInterrupt:
        logger.info("Exiting on user request.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
