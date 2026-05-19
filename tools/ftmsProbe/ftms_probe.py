#!/usr/bin/env python3
"""BLE FTMS validation probe for FS-BC11B treadmills.

The probe intentionally does not write FTMS control-point or vendor commands.
It scans without service filters, connects to the selected peripheral, discovers
GATT, and subscribes only to Fitness Machine Treadmill Data notifications.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from bleak import BleakClient, BleakScanner


FTMS_SERVICE_UUID = "00001826-0000-1000-8000-00805f9b34fb"
TREADMILL_DATA_UUID = "00002acd-0000-1000-8000-00805f9b34fb"
VENDOR_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
VENDOR_NOTIFY_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
VENDOR_WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"


@dataclass
class TreadmillSample:
    timestamp_s: float
    raw_hex: str
    flags: int
    speed_kmh: float | None = None
    distance_m: int | None = None
    incline_pct: float | None = None
    ramp_deg: float | None = None
    positive_elevation_m: float | None = None
    negative_elevation_m: float | None = None
    power_w: int | None = None
    heart_rate_bpm: int | None = None
    elapsed_s: int | None = None
    remaining_s: int | None = None
    truncated: bool = False
    parse_error: str | None = None


def u8(data: bytes, offset: int) -> int:
    return data[offset]


def u16(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8)


def s16(data: bytes, offset: int) -> int:
    value = u16(data, offset)
    return value - 0x10000 if value & 0x8000 else value


def u24(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8) | (data[offset + 2] << 16)


def has_flag(flags: int, bit: int) -> bool:
    return (flags & (1 << bit)) != 0


def require(data: bytes, offset: int, length: int) -> None:
    if offset + length > len(data):
        raise ValueError(f"truncated at offset {offset}, need {length}, len={len(data)}")


def parse_treadmill_data(data: bytes) -> TreadmillSample:
    sample = TreadmillSample(time.time(), data.hex(" "), 0)

    try:
        require(data, 0, 2)
        flags = u16(data, 0)
        p = 2
        sample.flags = flags

        # Bit 0 = More Data. If not set, instantaneous speed is present.
        if not has_flag(flags, 0):
            require(data, p, 2)
            sample.speed_kmh = u16(data, p) / 100.0
            p += 2

        if has_flag(flags, 1):
            require(data, p, 2)
            p += 2

        if has_flag(flags, 2):
            require(data, p, 3)
            sample.distance_m = u24(data, p)
            p += 3

        if has_flag(flags, 3):
            require(data, p, 4)
            sample.incline_pct = s16(data, p) / 10.0
            p += 2
            sample.ramp_deg = s16(data, p) / 10.0
            p += 2

        if has_flag(flags, 4):
            require(data, p, 4)
            sample.positive_elevation_m = u16(data, p) / 10.0
            p += 2
            sample.negative_elevation_m = u16(data, p) / 10.0
            p += 2

        if has_flag(flags, 5):
            require(data, p, 2)
            p += 2

        if has_flag(flags, 6):
            require(data, p, 2)
            p += 2

        if has_flag(flags, 7):
            require(data, p, 5)
            p += 5

        if has_flag(flags, 8):
            require(data, p, 1)
            sample.heart_rate_bpm = u8(data, p)
            p += 1

        if has_flag(flags, 9):
            require(data, p, 1)
            p += 1

        if has_flag(flags, 10):
            require(data, p, 2)
            sample.elapsed_s = u16(data, p)
            p += 2

        if has_flag(flags, 11):
            require(data, p, 2)
            sample.remaining_s = u16(data, p)
            p += 2

        if has_flag(flags, 12):
            require(data, p, 4)
            p += 2
            sample.power_w = s16(data, p)

    except ValueError as exc:
        sample.truncated = True
        sample.parse_error = str(exc)

    return sample


def compact_sample_text(sample: TreadmillSample) -> str:
    fields = [f"flags=0x{sample.flags:04x}", f"raw={sample.raw_hex}"]
    if sample.speed_kmh is not None:
        fields.append(f"speed={sample.speed_kmh:.2f}km/h")
    if sample.distance_m is not None:
        fields.append(f"dist={sample.distance_m}m")
    if sample.incline_pct is not None:
        fields.append(f"incl={sample.incline_pct:.1f}%")
    if sample.positive_elevation_m is not None:
        fields.append(f"pos={sample.positive_elevation_m:.1f}m")
    if sample.power_w is not None:
        fields.append(f"power={sample.power_w}W")
    if sample.heart_rate_bpm is not None:
        fields.append(f"hr={sample.heart_rate_bpm}bpm")
    if sample.elapsed_s is not None:
        fields.append(f"elapsed={sample.elapsed_s}s")
    if sample.truncated:
        fields.append(f"TRUNCATED={sample.parse_error}")
    return " ".join(fields)


def lower_uuid(value: str) -> str:
    return value.lower()


def matches_uuid(value: str, target: str) -> bool:
    return lower_uuid(value) == lower_uuid(target)


def adv_summary(device: Any, adv: Any) -> dict[str, Any]:
    manufacturer_data = {
        f"0x{company_id:04x}": bytes(payload).hex(" ")
        for company_id, payload in getattr(adv, "manufacturer_data", {}).items()
    }
    service_data = {
        uuid: bytes(payload).hex(" ")
        for uuid, payload in getattr(adv, "service_data", {}).items()
    }

    return {
        "address": getattr(device, "address", None),
        "device_name": getattr(device, "name", None),
        "local_name": getattr(adv, "local_name", None),
        "rssi": getattr(adv, "rssi", getattr(device, "rssi", None)),
        "service_uuids": list(getattr(adv, "service_uuids", []) or []),
        "manufacturer_data": manufacturer_data,
        "service_data": service_data,
        "tx_power": getattr(adv, "tx_power", None),
    }


def target_matches(summary: dict[str, Any], prefix: str, address: str | None) -> bool:
    if address and summary["address"] and summary["address"].lower() == address.lower():
        return True

    for name_key in ("local_name", "device_name"):
        name = summary.get(name_key)
        if isinstance(name, str) and name.startswith(prefix):
            return True

    return False


def print_adv(label: str, summary: dict[str, Any]) -> None:
    print(f"{label} {summary['address']} name={summary['local_name'] or summary['device_name']}")
    print(f"  rssi={summary['rssi']} tx_power={summary['tx_power']}")
    print(f"  services={summary['service_uuids']}")
    print(f"  manufacturer_data={summary['manufacturer_data']}")
    if summary["service_data"]:
        print(f"  service_data={summary['service_data']}")


def find_characteristic(services: Any, uuid: str) -> Any | None:
    for service in services:
        for char in service.characteristics:
            if matches_uuid(char.uuid, uuid):
                return char
    return None


def print_services(services: Any) -> None:
    print("GATT services:")
    for service in services:
        marker = ""
        if matches_uuid(service.uuid, FTMS_SERVICE_UUID):
            marker = " [FTMS]"
        elif matches_uuid(service.uuid, VENDOR_SERVICE_UUID):
            marker = " [VENDOR FFF0]"
        print(f"SERVICE {service.uuid}{marker}")
        for char in service.characteristics:
            char_marker = ""
            if matches_uuid(char.uuid, TREADMILL_DATA_UUID):
                char_marker = " [TREADMILL DATA]"
            elif matches_uuid(char.uuid, VENDOR_NOTIFY_UUID):
                char_marker = " [VENDOR FFF1 NOTIFY]"
            elif matches_uuid(char.uuid, VENDOR_WRITE_UUID):
                char_marker = " [VENDOR FFF2 WRITE]"
            print(f"  CHAR {char.uuid} props={list(char.properties)}{char_marker}")


async def scan_for_target(args: argparse.Namespace) -> tuple[Any | None, Any | None]:
    print(f"Scanning {args.scan_seconds:.1f}s without service UUID filter...")
    scanner = BleakScanner()
    await scanner.start()
    await asyncio.sleep(args.scan_seconds)
    await scanner.stop()

    matches: list[tuple[Any, Any, dict[str, Any]]] = []
    for _, (device, adv) in scanner.discovered_devices_and_advertisement_data.items():
        summary = adv_summary(device, adv)
        if args.list_all:
            print_adv("ADV", summary)
        if target_matches(summary, args.target_prefix, args.address):
            matches.append((device, adv, summary))

    if not matches:
        print(f"No target found for prefix={args.target_prefix!r} address={args.address!r}")
        return None, None

    matches.sort(key=lambda item: item[2]["rssi"] if item[2]["rssi"] is not None else -999, reverse=True)
    device, adv, summary = matches[0]
    print_adv("TARGET", summary)
    return device, adv


async def run_probe(args: argparse.Namespace) -> int:
    device, _ = await scan_for_target(args)
    if device is None:
        return 2

    if args.no_connect:
        return 0

    jsonl = None
    if args.jsonl:
        jsonl_path = Path(args.jsonl)
        jsonl_path.parent.mkdir(parents=True, exist_ok=True)
        jsonl = jsonl_path.open("a", encoding="utf-8")

    notification_count = 0

    def on_treadmill_data(_sender: Any, data: bytearray) -> None:
        nonlocal notification_count
        notification_count += 1
        sample = parse_treadmill_data(bytes(data))
        print(f"RX {notification_count:04d} {compact_sample_text(sample)}")
        if jsonl is not None:
            jsonl.write(json.dumps(asdict(sample), sort_keys=True) + "\n")
            jsonl.flush()

    try:
        print(f"Connecting to {device.address} timeout={args.connect_timeout:.1f}s...")
        async with BleakClient(device, timeout=args.connect_timeout, pair=False) as client:
            print(f"CONNECTED={client.is_connected}")
            print_services(client.services)

            treadmill_char = find_characteristic(client.services, TREADMILL_DATA_UUID)
            if treadmill_char is None:
                print("FTMS Treadmill Data characteristic 0x2ACD was not discovered.")
                return 3

            print(
                "Subscribing to FTMS Treadmill Data 0x2ACD only "
                f"for {args.notify_seconds:.1f}s..."
            )
            await client.start_notify(treadmill_char, on_treadmill_data)
            await asyncio.sleep(args.notify_seconds)
            await client.stop_notify(treadmill_char)
            print(f"Notifications received={notification_count}")

    finally:
        if jsonl is not None:
            jsonl.close()

    return 0 if notification_count > 0 else 4


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate FS-BC11B FTMS treadmill BLE behavior from Linux."
    )
    parser.add_argument("--target-prefix", default="FS-BC11B")
    parser.add_argument("--address", help="Connect to this BLE address instead of matching by name.")
    parser.add_argument("--scan-seconds", type=float, default=10.0)
    parser.add_argument("--connect-timeout", type=float, default=30.0)
    parser.add_argument("--notify-seconds", type=float, default=30.0)
    parser.add_argument("--jsonl", help="Optional JSONL output path for parsed samples.")
    parser.add_argument("--list-all", action="store_true", help="Print every discovered advertisement.")
    parser.add_argument("--no-connect", action="store_true", help="Only scan and print the selected target.")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        return asyncio.run(run_probe(args))
    except KeyboardInterrupt:
        return 130
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
