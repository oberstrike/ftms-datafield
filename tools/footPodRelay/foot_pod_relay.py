#!/usr/bin/env python3
"""Passes the running sensor of the Android bridge on to a watch from this computer.

A watch cannot use the phone as a Bluetooth running sensor (foot pod) while it is connected to the
same phone through Garmin Connect. This relay is the separate device in between:

    treadmill --FTMS--> phone (bridge app) --foot pod--> this computer --foot pod--> watch

It connects to the phone's running sensor as a client, and at the same time offers a running
sensor of its own, to which it forwards every measurement unchanged. The watch is paired with the
relay instead of the phone, so the phone keeps its connection to the watch for the data field.

Needs Linux with BlueZ and an adapter that can be central and peripheral at once.
"""
import argparse
import asyncio
import logging
import struct
import time

from bleak import BleakClient, BleakScanner
from bluez_peripheral.advert import Advertisement
from bluez_peripheral.gatt.characteristic import CharacteristicFlags as Flags
from bluez_peripheral.gatt.characteristic import characteristic
from bluez_peripheral.gatt.service import Service, ServiceCollection
from bluez_peripheral.util import Adapter, get_message_bus

RSC_SERVICE = "00001814-0000-1000-8000-00805f9b34fb"
RSC_MEASUREMENT = "00002a53-0000-1000-8000-00805f9b34fb"
MODEL_NUMBER = "00002a24-0000-1000-8000-00805f9b34fb"
HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"

# The model the bridge app reports; tells its sensor apart from other foot pods such as a chest strap.
BRIDGE_MODEL = "Treadmill foot pod"
# The relay's own model. It differs from the bridge's, so a relay never takes another relay as its source.
RELAY_MODEL = "Treadmill foot pod relay"
# Bluetooth appearance "running walking sensor".
APPEARANCE_RUNNING_SENSOR = 0x0440

SC_OP_SET_CUMULATIVE_VALUE = 0x01
SC_OP_RESPONSE = 0x10
SC_RESULT_SUCCESS = 0x01
SC_RESULT_NOT_SUPPORTED = 0x02
SC_RESULT_INVALID_PARAMETER = 0x03


class WithoutOptionalPropertyNoise(logging.Filter):
    """BlueZ asks the advertisement for optional properties it does not have; that is not an error."""

    def filter(self, record):
        return 'does not have property "TxPower"' not in record.getMessage()


def log(message):
    print(f"{time.strftime('%H:%M:%S')} {message}", flush=True)


def accessed(what, options):
    """Logs one access of the watch to the offered sensor, so a failure can be traced to its last step."""
    device = getattr(options, "device", None) or "?"
    log(f"{device.rsplit('/', 1)[-1]} {what}")


def describe(value):
    """A measurement in readable form, for the log."""
    if len(value) < 4:
        return value.hex(" ")
    flags, speed, cadence = value[0], struct.unpack_from("<H", value, 1)[0], value[3]
    text = f"{speed / 256 * 3.6:.2f} km/h, step rate {cadence}"
    if flags & 0x02 and len(value) >= 8:
        text += f", {struct.unpack_from('<I', value, 4)[0] / 10:.1f} m"
    return text


class RunningSensor(Service):
    """The running sensor offered to the watch, with the same layout as the one of the bridge app."""

    def __init__(self):
        super().__init__("1814", True)
        self.forwarded = 0
        self.last = b""

    @characteristic("2A53", Flags.NOTIFY)
    def measurement(self, options):
        return self.last

    @characteristic("2A54", Flags.READ)
    def feature(self, options):
        accessed("read the feature", options)
        # Total distance is offered, nothing else beyond the mandatory fields.
        return bytes([0x02, 0x00])

    @characteristic("2A5D", Flags.READ)
    def sensor_location(self, options):
        accessed("read the sensor location", options)
        return bytes([0x00])

    @characteristic("2A55", Flags.WRITE | Flags.INDICATE)
    def control_point(self, options):
        return b""

    @control_point.setter
    def control_point(self, value, options):
        request = bytes(value)
        if not request:
            return
        if request[0] != SC_OP_SET_CUMULATIVE_VALUE:
            result = SC_RESULT_NOT_SUPPORTED
        elif len(request) < 5:
            result = SC_RESULT_INVALID_PARAMETER
        else:
            result = SC_RESULT_SUCCESS
        answer = bytes([SC_OP_RESPONSE, request[0], result])
        accessed(f"wrote control point {request.hex(' ')}, answer {answer.hex(' ')}", options)
        # After the write has been confirmed, not from inside it.
        asyncio.get_running_loop().call_later(0.05, self.control_point.changed, answer)

    @property
    def watch_listens(self):
        return self.measurement._notify

    def forward(self, value):
        self.last = value
        self.forwarded += 1
        self.measurement.changed(value)


class Battery(Service):
    """Some watches expect a sensor to report a battery level."""

    def __init__(self):
        super().__init__("180F", True)

    @characteristic("2A19", Flags.READ)
    def level(self, options):
        accessed("read the battery level", options)
        return bytes([100])


class DeviceInformation(Service):
    """What the bridge app's own sensor says about itself, so the watch finds the same here."""

    def __init__(self):
        super().__init__("180A", True)

    @characteristic("2A29", Flags.READ)
    def manufacturer(self, options):
        accessed("read the manufacturer", options)
        return b"FTMS Bridge"

    @characteristic("2A24", Flags.READ)
    def model(self, options):
        accessed("read the model", options)
        return RELAY_MODEL.encode()


async def open_adapter(bus, name):
    """The adapter [name]. Addressed directly, because BlueZ may list entries that are no adapters."""
    path = f"/org/bluez/{name}"
    return Adapter(bus.get_proxy_object("org.bluez", path, await bus.introspect("org.bluez", path)))


async def find_source(args, own_address, rejected):
    """The phone's running sensor, or None if it is not visible right now."""
    found = await BleakScanner.discover(timeout=args.scan_seconds, return_adv=True, service_uuids=[RSC_SERVICE])
    wanted = args.source_name.lower()
    candidates = [
        (device, advertisement)
        for device, advertisement in found.values()
        if RSC_SERVICE in advertisement.service_uuids
        # A chest strap offers a foot pod as well; the bridge offers nothing but the foot pod.
        and HEART_RATE_SERVICE not in advertisement.service_uuids
        and device.address.upper() != own_address
        and device.address not in rejected
        and wanted in (advertisement.local_name or "").lower()
    ]
    candidates.sort(key=lambda pair: pair[1].rssi, reverse=True)
    return candidates[0][0] if candidates else None


async def relay_from(device, sensor, rejected):
    """Forwards the measurements of [device] until its connection ends."""
    gone = asyncio.Event()
    async with BleakClient(device, disconnected_callback=lambda _: gone.set(), timeout=20.0) as client:
        try:
            model = bytes(await client.read_gatt_char(MODEL_NUMBER)).decode("utf-8", "replace")
        except Exception:  # noqa: BLE001 - a sensor without a model is simply not the bridge
            model = ""
        if model != BRIDGE_MODEL:
            # Another foot pod, for example a chest strap. Leave it alone from now on.
            log(f"{device.address} is not the bridge (model {model!r}), ignoring it")
            rejected.add(device.address)
            return
        log(f"connected to the bridge's sensor at {device.address}")
        await client.start_notify(RSC_MEASUREMENT, lambda _, value: sensor.forward(bytes(value)))
        await gone.wait()
    log("the bridge's sensor is gone")


async def report(sensor):
    """Logs every ten seconds what is passing through, and every change of the watch's subscription."""
    listening, forwarded = None, 0
    while True:
        await asyncio.sleep(1)
        if sensor.watch_listens != listening:
            listening = sensor.watch_listens
            log("the watch is listening" if listening else "no watch is listening")
        if int(time.time()) % 10 == 0 and sensor.forwarded != forwarded:
            forwarded = sensor.forwarded
            log(f"forwarded {forwarded} measurements, last: {describe(sensor.last)}")


async def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--name", default="FTMS Bridge", help="name under which the watch sees the sensor")
    parser.add_argument("--adapter", default="hci0", help="Bluetooth adapter to use")
    parser.add_argument("--source-name", default="", help="only take a source whose Bluetooth name contains this")
    parser.add_argument("--scan-seconds", type=float, default=5.0, help="length of one search for the source")
    parser.add_argument("--retry-seconds", type=float, default=5.0, help="pause between two searches")
    args = parser.parse_args()

    logging.getLogger().addFilter(WithoutOptionalPropertyNoise())
    bus = await get_message_bus()
    adapter = await open_adapter(bus, args.adapter)
    own_address = (await adapter.get_address()).upper()
    sensor = RunningSensor()
    services = ServiceCollection([sensor, DeviceInformation(), Battery()])
    await services.register(bus, adapter=adapter)
    advertisement = Advertisement(args.name, ["1814"], APPEARANCE_RUNNING_SENSOR, 0)
    await advertisement.register(bus, adapter)
    log(f"offering the running sensor {args.name!r} at {own_address}")

    reporter = asyncio.create_task(report(sensor))
    rejected = set()
    waiting_logged = False
    try:
        while True:
            try:
                device = await find_source(args, own_address, rejected)
                if device is None:
                    if not waiting_logged:
                        log("waiting for the bridge's sensor (bridge running with the running sensor switched on?)")
                        waiting_logged = True
                else:
                    waiting_logged = False
                    await relay_from(device, sensor, rejected)
            except Exception as error:  # noqa: BLE001 - keep the relay alive across Bluetooth hiccups
                log(f"source connection failed: {error!r}")
            await asyncio.sleep(args.retry_seconds)
    finally:
        reporter.cancel()
        await services.unregister()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
