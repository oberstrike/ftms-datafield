using Toybox.BluetoothLowEnergy as Ble;
import Toybox.BluetoothLowEnergy;
using Toybox.System as Sys;
using Toybox.Lang as Lang;

class FtmsBleClient extends Ble.BleDelegate {

    const SCAN_LOG_INTERVAL_MS = 2000;
    const PAIR_LOG_INTERVAL_MS = 5000;
    const PAIR_TIMEOUT_MS = 45000;
    const FALLBACK_COOLDOWN_MS = 60000;
    const COOLDOWN_LOG_INTERVAL_MS = 5000;
    const RX_TIMEOUT_MS = 30000;
    const RX_VERBOSE_LIMIT = 5;
    const RX_SUMMARY_INTERVAL = 20;
    const VENDOR_RX_VERBOSE_LIMIT = 5;
    const VENDOR_RX_SUMMARY_INTERVAL = 20;
    const FS_BC11B_PREFIX = "FS-BC11B";

    var _device = null;
    var _pairing = false;
    var _started = false;
    var _lastScanLogMs = 0;
    var _lastCooldownLogMs = 0;
    var _rxPacketCount = 0;
    var _vendorRxPacketCount = 0;
    var _pairStartMs = 0;
    var _lastPairWaitLogMs = 0;
    var _pairName = null;
    var _pairRssi = null;
    var _subscriptionStartMs = 0;
    var _waitingForRx = false;
    var _rxTimeoutLogged = false;
    var _pairingWithFallback = false;
    var _connectedWithFallback = false;
    var _profileRegisterExpected = 0;
    var _profileRegisterDone = 0;
    var _profileRegisterFailures = 0;
    var _vendorDiagnosticSubscribed = false;
    var _fallbackCooldownStartMs = 0;
    var _pausedForPhoneBridge = false;

    function initialize() {
        BleDelegate.initialize();
    }

    function start() {
        if (_started) {
            return;
        }

        _started = true;
        _pausedForPhoneBridge = false;
        FtmsState.setStatus("BLE");
        Ble.setDelegate(self);
        _profileRegisterExpected = 2;
        _profileRegisterDone = 0;
        _profileRegisterFailures = 0;

        registerFtmsProfile();
        registerVendorProfile();
    }

    function registerFtmsProfile() as Void {
        var cccd = Ble.cccdUuid();
        var profile = {
            :uuid => FtmsIds.service(),
            :characteristics => [
                { :uuid => FtmsIds.feature() },
                { :uuid => FtmsIds.treadmillData(), :descriptors => [ cccd ] },
                { :uuid => FtmsIds.crossTrainerData(), :descriptors => [ cccd ] },
                { :uuid => FtmsIds.indoorBikeData(), :descriptors => [ cccd ] },
                { :uuid => FtmsIds.trainingStatus(), :descriptors => [ cccd ] },
                { :uuid => FtmsIds.fitnessMachineStatus(), :descriptors => [ cccd ] }
            ]
        };

        try {
            Ble.registerProfile(profile);
            logEvent("profile", "register requested ftms service=" + FtmsIds.service());
        } catch (e instanceof Lang.Exception) {
            logEvent("profile", "register failed ftms err=" + e.getErrorMessage());
            profileRegistrationFailed();
        }
    }

    function registerVendorProfile() as Void {
        var cccd = Ble.cccdUuid();
        var profile = {
            :uuid => FtmsIds.vendorService(),
            :characteristics => [
                { :uuid => FtmsIds.vendorNotify(), :descriptors => [ cccd ] },
                { :uuid => FtmsIds.vendorWrite() }
            ]
        };

        try {
            Ble.registerProfile(profile);
            logEvent("profile", "register requested vendor service=" + FtmsIds.vendorService());
        } catch (e instanceof Lang.Exception) {
            logEvent("profile", "register failed vendor err=" + e.getErrorMessage());
            profileRegistrationFailed();
        }
    }

    function tick() as Void {
        if (!_started) {
            return;
        }

        var now = Sys.getTimer();

        if (_pairing) {
            updatePairWatchdog(now);
            return;
        }

        if (FtmsState.connected && _waitingForRx && _rxPacketCount == 0) {
            updateRxWatchdog(now);
        }
    }

    function stop() {
        logEvent("stop", "requested connected=" + boolText(FtmsState.connected));

        try {
            Ble.setScanState(Ble.SCAN_STATE_OFF);
        } catch (e instanceof Lang.Exception) {
            logEvent("stop", "scan off failed err=" + e.getErrorMessage());
        }

        if (_device != null) {
            try {
                Ble.unpairDevice(_device);
            } catch (e2 instanceof Lang.Exception) {
                logEvent("stop", "unpair failed err=" + e2.getErrorMessage());
            }
        }

        _device = null;
        _pairing = false;
        _pairingWithFallback = false;
        _connectedWithFallback = false;
        _fallbackCooldownStartMs = 0;
        _lastCooldownLogMs = 0;
        _rxPacketCount = 0;
        _vendorRxPacketCount = 0;
        _waitingForRx = false;
        _rxTimeoutLogged = false;
        _vendorDiagnosticSubscribed = false;
        _pausedForPhoneBridge = false;
        _started = false;
        FtmsState.connected = false;
        FtmsState.setStatus("STOP");
    }

    function pauseForPhoneBridge() as Void {
        if (_pausedForPhoneBridge) {
            return;
        }

        _pausedForPhoneBridge = true;
        logEvent("phone", "pause direct ble");

        try {
            Ble.setScanState(Ble.SCAN_STATE_OFF);
        } catch (scanStopError instanceof Lang.Exception) {
            logEvent("phone", "scan off failed err=" + scanStopError.getErrorMessage());
        }

        if (_device != null) {
            try {
                Ble.unpairDevice(_device);
            } catch (unpairError instanceof Lang.Exception) {
                logEvent("phone", "unpair failed err=" + unpairError.getErrorMessage());
            }
        }

        _device = null;
        clearPairingState();
        _waitingForRx = false;
        _rxTimeoutLogged = false;
        _vendorDiagnosticSubscribed = false;
        FtmsState.connected = false;
    }

    function onProfileRegister(uuid as Uuid, status as Status) as Void {
        logEvent("profile", "registered uuid=" + uuid + " status=" + status);

        _profileRegisterDone += 1;
        if (status != Ble.STATUS_SUCCESS) {
            _profileRegisterFailures += 1;
        }

        maybeStartScanAfterProfiles();
    }

    function profileRegistrationFailed() as Void {
        _profileRegisterDone += 1;
        _profileRegisterFailures += 1;
        maybeStartScanAfterProfiles();
    }

    function maybeStartScanAfterProfiles() as Void {
        if (_profileRegisterDone < _profileRegisterExpected) {
            FtmsState.setStatus("REG " + _profileRegisterDone);
            return;
        }

        logEvent("profile", "complete done=" + _profileRegisterDone + " failures=" + _profileRegisterFailures);

        if (_profileRegisterFailures >= _profileRegisterExpected) {
            FtmsState.setStatus("REGERR");
            return;
        }

        FtmsState.setStatus("SCAN");
        startScan();
    }

    function startScan() as Void {
        if (FtmsState.hasFreshPhoneBridge(5000)) {
            logEvent("scan", "skip phoneBridge=fresh");
            return;
        }

        _pausedForPhoneBridge = false;

        if (_pairing || FtmsState.connected) {
            logEvent("scan", "skip pairing=" + boolText(_pairing) + " connected=" + boolText(FtmsState.connected));
            return;
        }

        try {
            Ble.setScanState(Ble.SCAN_STATE_SCANNING);
            logEvent("scan", "started");
        } catch (e instanceof Lang.Exception) {
            FtmsState.setStatus("SCANERR");
            logEvent("scan", "start failed err=" + e.getErrorMessage());
        }
    }

    function onScanResults(scanResults as Iterator) as Void {
        if (FtmsState.hasFreshPhoneBridge(5000)) {
            try {
                Ble.setScanState(Ble.SCAN_STATE_OFF);
            } catch (scanStopError instanceof Lang.Exception) {
                logEvent("scan", "phone bridge scan off failed err=" + scanStopError.getErrorMessage());
            }
            return;
        }

        if (_pairing || FtmsState.connected) {
            return;
        }

        var result = scanResults.next();
        var bestResult = null;
        var bestRssi = -999;
        var bestHasFtms = false;
        var ftmsCandidate = null;
        var fsCandidate = null;
        var fsRssi = -999;
        var seenCount = 0;
        var now = Sys.getTimer();

        while (result != null) {
            if (result instanceof ScanResult) {
                seenCount += 1;
                var hasFtms = advertisesFtms(result);
                var rssi = result.getRssi();

                if (bestResult == null || hasFtms || (!bestHasFtms && rssi > bestRssi)) {
                    bestResult = result;
                    bestRssi = rssi;
                    bestHasFtms = hasFtms;
                }

                if (hasFtms) {
                    ftmsCandidate = result;
                } else if (isFsBc11b(result) && (fsCandidate == null || rssi > fsRssi)) {
                    fsCandidate = result;
                    fsRssi = rssi;
                }
            }

            result = scanResults.next();
        }

        if (ftmsCandidate != null) {
            recordScanDebug(ftmsCandidate, true, true);
            logEvent("scan", "select ftms count=" + seenCount + " name=" + ftmsCandidate.getDeviceName() + " rssi=" + ftmsCandidate.getRssi());
            pairCandidate(ftmsCandidate, false);
            return;
        }

        if (fsCandidate != null) {
            recordScanDebug(fsCandidate, false, true);
            if (fallbackCooldownActive(now)) {
                FtmsState.setStatus("PTIME FS");
                logFallbackCooldown(now, fsCandidate);
                return;
            }

            logEvent("scan", "select fs fallback count=" + seenCount + " name=" + fsCandidate.getDeviceName() + " rssi=" + fsRssi);
            pairCandidate(fsCandidate, true);
            return;
        }

        if (bestResult != null) {
            recordScanDebug(bestResult, bestHasFtms, false);
        } else {
            logEvent("scan", "empty results");
        }
    }

    function advertisesFtms(scanResult as ScanResult) as Lang.Boolean {
        var uuids = scanResult.getServiceUuids();
        var uuid = uuids.next();

        while (uuid != null) {
            if (uuid.equals(FtmsIds.service())) {
                return true;
            }

            uuid = uuids.next();
        }

        return false;
    }

    function isFsBc11b(scanResult as ScanResult) as Lang.Boolean {
        var name = scanResult.getDeviceName();
        return name != null
            && name.length() >= FS_BC11B_PREFIX.length()
            && name.substring(0, FS_BC11B_PREFIX.length()).equals(FS_BC11B_PREFIX);
    }

    function pairCandidate(scanResult as ScanResult, fallback as Lang.Boolean) as Void {
        _pairing = true;
        _pairingWithFallback = fallback;
        _pairStartMs = Sys.getTimer();
        _lastPairWaitLogMs = _pairStartMs;
        _pairName = scanResult.getDeviceName();
        _pairRssi = scanResult.getRssi();
        _waitingForRx = false;
        _rxTimeoutLogged = false;
        _vendorDiagnosticSubscribed = false;
        FtmsState.deviceName = _pairName;

        if (fallback) {
            FtmsState.setStatus("TRYFS 0");
        } else {
            FtmsState.setStatus("PAIR 0");
        }

        logEvent("pair", "attempt fallback=" + boolText(fallback)
            + " mode=scanOn"
            + " name=" + _pairName
            + " rssi=" + _pairRssi
            + " services=" + serviceSummary(scanResult)
            + " raw=" + pairRawSummary(scanResult, fallback)
            + " ad=" + adSummary(scanResult.getRawData()));

        try {
            logEvent("pair", "availableConnections=" + Ble.getAvailableConnectionCount());
        } catch (connCountError instanceof Lang.Exception) {
            logEvent("pair", "availableConnections err=" + connCountError.getErrorMessage());
        }

        try {
            Ble.setConnectionStrategy(Ble.CONNECTION_STRATEGY_DEFAULT);
            logEvent("pair", "strategy default");
        } catch (strategyError instanceof Lang.Exception) {
            logEvent("pair", "strategy default failed err=" + strategyError.getErrorMessage());
        }

        try {
            _device = Ble.pairDevice(scanResult);
            logEvent("pair", "pairDevice returned null=" + boolText(_device == null));
        } catch (e instanceof Lang.Exception) {
            clearPairingState();
            FtmsState.setStatus("PAIRERR");
            logEvent("pair", "failed err=" + e.getErrorMessage());
            startScan();
        }

        if (_device == null) {
            clearPairingState();
            FtmsState.setStatus("PAIRERR");
            logEvent("pair", "failed null device");
            startScan();
        }
    }

    function recordScanDebug(scanResult as ScanResult, hasFtms as Lang.Boolean, forceLog as Lang.Boolean) as Void {
        var name = scanResult.getDeviceName();
        var rssi = scanResult.getRssi();
        var services = serviceSummary(scanResult);
        var rawData = scanResult.getRawData();
        var raw = isFsBc11b(scanResult) ? rawPrefixLimit(rawData, rawData.size()) : rawPrefix(rawData);
        var ad = adSummary(rawData);

        FtmsState.recordScanResult(name, rssi, hasFtms, services, raw);

        var now = Sys.getTimer();
        if (forceLog || (now - _lastScanLogMs) >= SCAN_LOG_INTERVAL_MS) {
            _lastScanLogMs = now;
            logEvent("scan", "seen name=" + name
                + " rssi=" + rssi
                + " has1826=" + hasFtms
                + " services=" + services
                + " raw=" + raw
                + " ad=" + ad);
        }
    }

    function pairRawSummary(scanResult as ScanResult, fallback as Lang.Boolean) as Lang.String {
        var raw = scanResult.getRawData();
        return fallback ? rawPrefixLimit(raw, raw.size()) : rawPrefix(raw);
    }

    function serviceSummary(scanResult as ScanResult) as Lang.String {
        var services = scanResult.getServiceUuids();
        var uuid = services.next();
        var summary = "";
        var count = 0;

        while (uuid != null && count < 4) {
            if (count > 0) {
                summary += ",";
            }

            summary += uuid;
            count += 1;
            uuid = services.next();
        }

        if (count == 0) {
            return "none";
        }

        if (uuid != null) {
            summary += ",...";
        }

        return summary;
    }

    function rawPrefix(raw as Lang.ByteArray) as Lang.String {
        return rawPrefixLimit(raw, 8);
    }

    function rawPrefixLimit(raw as Lang.ByteArray, maxBytes as Lang.Number) as Lang.String {
        var max = raw.size();
        if (max > maxBytes) {
            max = maxBytes;
        }

        var text = "len=" + raw.size() + ":";
        for (var i = 0; i < max; i += 1) {
            if (i > 0) {
                text += ".";
            }

            text += u8(raw, i);
        }

        return text;
    }

    function u8(bytes as Lang.ByteArray, pos as Lang.Number) as Lang.Number {
        return bytes.decodeNumber(
            Lang.NUMBER_FORMAT_UINT8,
            {
                :offset => pos,
                :endianness => Lang.ENDIAN_LITTLE
            }
        );
    }

    function onConnectedStateChanged(device as Device, state as ConnectionState) as Void {
        var name = device == null ? "null" : device.getName();
        logEvent("conn", "state=" + state
            + " name=" + name
            + " pairingFallback=" + boolText(_pairingWithFallback)
            + " connectedFallback=" + boolText(_connectedWithFallback));

        if (state == Ble.CONNECTION_STATE_CONNECTED) {
            handleConnectionEstablished(device, "callback");
        } else {
            FtmsState.connected = false;
            _device = null;
            clearPairingState();
            _connectedWithFallback = false;
            _waitingForRx = false;
            _rxTimeoutLogged = false;
            _vendorDiagnosticSubscribed = false;
            FtmsState.setStatus("DISC");
            startScan();
        }
    }

    function handleConnectionEstablished(device as Device, source as Lang.String) as Void {
        var wasFallback = _pairingWithFallback;
        _device = device;
        _connectedWithFallback = wasFallback;
        clearPairingState();
        _rxPacketCount = 0;
        _vendorRxPacketCount = 0;
        _vendorDiagnosticSubscribed = false;
        _fallbackCooldownStartMs = 0;
        _lastCooldownLogMs = 0;
        FtmsState.connected = true;
        FtmsState.deviceName = device.getName();
        FtmsState.setStatus(source.equals("poll") ? "CONN POLL" : "CONN");
        logEvent("conn", "established source=" + source
            + " fallback=" + boolText(wasFallback)
            + " name=" + device.getName()
            + " isConnected=" + boolText(device.isConnected()));
        subscribeToFtms(device);
    }

    function subscribeToFtms(device as Device) as Void {
        inspectVendorService(device);

        var service = device.getService(FtmsIds.service());
        if (service == null) {
            FtmsState.setStatus(_connectedWithFallback ? "NOSVC FS" : "NOSVC");
            logEvent("discover", "service missing fallback=" + boolText(_connectedWithFallback) + " device=" + device.getName());
            subscribeVendorIfPresent(device);
            return;
        }

        logEvent("discover", "ftms service found uuid=" + service.getUuid() + " fallback=" + boolText(_connectedWithFallback));
        requestFeatureReadIfPresent(service);

        var subscriptions = 0;
        subscriptions += subscribeIfPresent(service, FtmsIds.treadmillData());
        subscriptions += subscribeIfPresent(service, FtmsIds.crossTrainerData());
        subscriptions += subscribeIfPresent(service, FtmsIds.indoorBikeData());

        if (subscriptions == 0) {
            FtmsState.setStatus(_connectedWithFallback ? "NOCHAR FS" : "NOCHAR");
            logEvent("discover", "no subscribable data chars fallback=" + boolText(_connectedWithFallback));
            subscribeVendorIfPresent(device);
        } else {
            _subscriptionStartMs = Sys.getTimer();
            _waitingForRx = true;
            _rxTimeoutLogged = false;
            FtmsState.setStatus("WAITRX");
            logEvent("discover", "subscription requests=" + subscriptions);
        }
    }

    function inspectVendorService(device as Device) as Void {
        var service = device.getService(FtmsIds.vendorService());
        if (service == null) {
            logEvent("vendor", "service missing uuid=" + FtmsIds.vendorService());
            return;
        }

        logEvent("vendor", "service found uuid=" + service.getUuid());

        var notifyChar = service.getCharacteristic(FtmsIds.vendorNotify());
        logEvent("vendor", "char " + charLabel(FtmsIds.vendorNotify()) + " present=" + boolText(notifyChar != null));
        if (notifyChar != null) {
            var cccd = notifyChar.getDescriptor(Ble.cccdUuid());
            logEvent("vendor", "char " + charLabel(FtmsIds.vendorNotify()) + " cccd=" + boolText(cccd != null));
        }

        var writeChar = service.getCharacteristic(FtmsIds.vendorWrite());
        logEvent("vendor", "char " + charLabel(FtmsIds.vendorWrite()) + " present=" + boolText(writeChar != null) + " write=false");
    }

    function subscribeVendorIfPresent(device as Device) as Lang.Number {
        var service = device.getService(FtmsIds.vendorService());
        if (service == null) {
            return 0;
        }

        var ch = service.getCharacteristic(FtmsIds.vendorNotify());
        if (ch == null) {
            return 0;
        }

        var cccd = ch.getDescriptor(Ble.cccdUuid());
        if (cccd == null) {
            return 0;
        }

        try {
            _vendorDiagnosticSubscribed = true;
            cccd.requestWrite([0x01, 0x00]b);
            FtmsState.setStatus("WAITV");
            logEvent("vendor", "diagnostic notify requested");
            return 1;
        } catch (e instanceof Lang.Exception) {
            _vendorDiagnosticSubscribed = false;
            FtmsState.setStatus("CCCDERR");
            logEvent("vendor", "diagnostic notify failed err=" + e.getErrorMessage());
            return 0;
        }
    }

    function requestFeatureReadIfPresent(service as Service) as Void {
        var feature = service.getCharacteristic(FtmsIds.feature());
        logEvent("discover", "char " + charLabel(FtmsIds.feature()) + " present=" + boolText(feature != null));

        if (feature == null) {
            return;
        }

        try {
            feature.requestRead();
            logEvent("read", "feature requested");
        } catch (e instanceof Lang.Exception) {
            logEvent("read", "feature request failed err=" + e.getErrorMessage());
        }
    }

    function subscribeIfPresent(service as Service, charUuid as Uuid) as Lang.Number {
        var ch = service.getCharacteristic(charUuid);
        if (ch == null) {
            logEvent("discover", "char " + charLabel(charUuid) + " present=false");
            return 0;
        }

        logEvent("discover", "char " + charLabel(charUuid) + " present=true uuid=" + ch.getUuid());
        var cccd = ch.getDescriptor(Ble.cccdUuid());
        if (cccd == null) {
            logEvent("discover", "char " + charLabel(charUuid) + " cccd=false");
            return 0;
        }

        logEvent("discover", "char " + charLabel(charUuid) + " cccd=true write=notify");

        try {
            // 0x0001 = notifications enabled.
            cccd.requestWrite([0x01, 0x00]b);
            return 1;
        } catch (e instanceof Lang.Exception) {
            FtmsState.setStatus("CCCDERR");
            logEvent("cccd", "write failed char=" + charLabel(charUuid) + " err=" + e.getErrorMessage());
            return 0;
        }
    }

    function onDescriptorWrite(descriptor as Descriptor, status as Status) as Void {
        var ch = descriptor.getCharacteristic();
        var charUuid = ch == null ? null : ch.getUuid();
        logEvent("cccd", "write done desc=" + descriptor.getUuid()
            + " char=" + charLabel(charUuid)
            + " status=" + status);

        if (status == Ble.STATUS_SUCCESS) {
            if (charUuid != null && charUuid.equals(FtmsIds.vendorNotify())) {
                FtmsState.setStatus("WAITV");
            } else {
                FtmsState.setStatus("WAITRX");
            }
        } else {
            FtmsState.setStatus("CCCDERR");
        }
    }

    function onCharacteristicRead(characteristic as Characteristic, status as Status, value as Lang.ByteArray) as Void {
        logEvent("read", "done char=" + charLabel(characteristic.getUuid())
            + " status=" + status
            + " raw=" + rawPrefixLimit(value, 16));
    }

    function onCharacteristicChanged(characteristic as Characteristic, value as Lang.ByteArray) as Void {
        var uuid = characteristic.getUuid();
        var sample = null;

        if (uuid.equals(FtmsIds.vendorNotify())) {
            recordVendorRx(value);
            return;
        }

        _rxPacketCount += 1;

        if (uuid.equals(FtmsIds.treadmillData())) {
            sample = FtmsParser.parseTreadmill(value);
        } else if (uuid.equals(FtmsIds.crossTrainerData())) {
            sample = FtmsParser.parseCrossTrainer(value);
        } else if (uuid.equals(FtmsIds.indoorBikeData())) {
            sample = FtmsParser.parseIndoorBike(value);
        }

        var shouldLog = _rxPacketCount <= RX_VERBOSE_LIMIT || (_rxPacketCount % RX_SUMMARY_INTERVAL) == 0;
        if (shouldLog) {
            logEvent("rx", "count=" + _rxPacketCount
                + " char=" + charLabel(uuid)
                + " raw=" + rawPrefixLimit(value, 16)
                + " sample=" + sampleSummary(sample));
        }

        if (sample != null) {
            _waitingForRx = false;
            FtmsState.update(sample);
        } else {
            FtmsState.setStatus("RXUNK");
            logEvent("rx", "unhandled char=" + uuid + " raw=" + rawPrefixLimit(value, 16));
        }
    }

    function recordVendorRx(value as Lang.ByteArray) as Void {
        _vendorRxPacketCount += 1;

        var shouldLog = _vendorRxPacketCount <= VENDOR_RX_VERBOSE_LIMIT
            || (_vendorRxPacketCount % VENDOR_RX_SUMMARY_INTERVAL) == 0;

        if (shouldLog) {
            logEvent("vendor", "rx count=" + _vendorRxPacketCount + " raw=" + rawPrefixLimit(value, 20));
        }

        FtmsState.setStatus("VRX " + _vendorRxPacketCount);
    }

    function logEvent(tag as Lang.String, message as Lang.String) as Void {
        Sys.println("FTMS " + tag + " " + message);
    }

    function updatePairWatchdog(now as Lang.Number) as Void {
        var elapsedMs = now - _pairStartMs;
        var elapsedS = elapsedMs / 1000;
        var connected = pollPairConnected();

        if (_pairingWithFallback) {
            FtmsState.setStatus("TRYFS " + elapsedS);
        } else {
            FtmsState.setStatus("PAIR " + elapsedS);
        }

        if (connected) {
            logEvent("pair", "poll connected=true elapsedMs=" + elapsedMs
                + " fallback=" + boolText(_pairingWithFallback)
                + " name=" + _pairName
                + " rssi=" + _pairRssi);
            handleConnectionEstablished(_device, "poll");
            return;
        }

        if ((now - _lastPairWaitLogMs) >= PAIR_LOG_INTERVAL_MS) {
            _lastPairWaitLogMs = now;
            logEvent("pair", "poll connected=false elapsedMs=" + elapsedMs
                + " fallback=" + boolText(_pairingWithFallback)
                + " name=" + _pairName
                + " rssi=" + _pairRssi
                + " deviceHandle=" + boolText(_device != null));
        }

        if (elapsedMs < PAIR_TIMEOUT_MS) {
            return;
        }

        var wasFallback = _pairingWithFallback;
        logEvent("pair", "timeout elapsedMs=" + elapsedMs
            + " fallback=" + boolText(wasFallback)
            + " name=" + _pairName
            + " rssi=" + _pairRssi
            + " deviceHandle=" + boolText(_device != null));

        if (_device != null) {
            try {
                Ble.unpairDevice(_device);
                logEvent("pair", "timeout unpair requested");
            } catch (e instanceof Lang.Exception) {
                logEvent("pair", "timeout unpair failed err=" + e.getErrorMessage());
            }
        }

        _device = null;
        clearPairingState();
        FtmsState.connected = false;
        if (wasFallback) {
            _fallbackCooldownStartMs = now;
            _lastCooldownLogMs = 0;
        }
        FtmsState.setStatus(wasFallback ? "PTIME FS" : "PTIME");
        startScan();
    }

    function fallbackCooldownActive(now as Lang.Number) as Lang.Boolean {
        return _fallbackCooldownStartMs > 0 && elapsedSince(now, _fallbackCooldownStartMs) < FALLBACK_COOLDOWN_MS;
    }

    function logFallbackCooldown(now as Lang.Number, scanResult as ScanResult) as Void {
        if ((now - _lastCooldownLogMs) < COOLDOWN_LOG_INTERVAL_MS) {
            return;
        }

        _lastCooldownLogMs = now;
        var remainingMs = FALLBACK_COOLDOWN_MS - elapsedSince(now, _fallbackCooldownStartMs);
        logEvent("pair", "cooldown remainingMs=" + remainingMs
            + " name=" + scanResult.getDeviceName()
            + " rssi=" + scanResult.getRssi()
            + " raw=" + pairRawSummary(scanResult, true)
            + " ad=" + adSummary(scanResult.getRawData()));
    }

    function pollPairConnected() as Lang.Boolean {
        if (_device == null) {
            return false;
        }

        try {
            return _device.isConnected();
        } catch (e instanceof Lang.Exception) {
            logEvent("pair", "poll isConnected failed err=" + e.getErrorMessage());
            return false;
        }
    }

    function elapsedSince(now as Lang.Number, startMs as Lang.Number) as Lang.Number {
        var elapsed = now - startMs;
        if (elapsed < 0) {
            return 0;
        }

        return elapsed;
    }

    function updateRxWatchdog(now as Lang.Number) as Void {
        var elapsedMs = now - _subscriptionStartMs;

        if (elapsedMs < RX_TIMEOUT_MS || _rxTimeoutLogged) {
            return;
        }

        _rxTimeoutLogged = true;
        _waitingForRx = false;
        FtmsState.setStatus("NORX");
        logEvent("rx", "timeout elapsedMs=" + elapsedMs
            + " fallback=" + boolText(_connectedWithFallback)
            + " packets=" + _rxPacketCount);
    }

    function clearPairingState() as Void {
        _pairing = false;
        _pairingWithFallback = false;
        _pairStartMs = 0;
        _lastPairWaitLogMs = 0;
        _pairName = null;
        _pairRssi = null;
    }

    function adSummary(raw as Lang.ByteArray) as Lang.String {
        var pos = 0;
        var text = "";
        var entries = 0;

        while (pos < raw.size() && entries < 8) {
            var len = u8(raw, pos);
            if (len == 0) {
                break;
            }

            var typePos = pos + 1;
            var next = pos + 1 + len;
            if (typePos >= raw.size() || next > raw.size()) {
                text += entrySep(entries) + "bad@" + pos + "/len=" + len;
                entries += 1;
                break;
            }

            var type = u8(raw, typePos);
            text += entrySep(entries) + adEntrySummary(raw, type, typePos + 1, len - 1);
            entries += 1;
            pos = next;
        }

        if (text.equals("")) {
            return "none";
        }

        if (pos < raw.size()) {
            text += ",...";
        }

        return text;
    }

    function entrySep(count as Lang.Number) as Lang.String {
        return count == 0 ? "" : ",";
    }

    function adEntrySummary(raw as Lang.ByteArray, type as Lang.Number, dataStart as Lang.Number, dataLen as Lang.Number) as Lang.String {
        if (type == 1 && dataLen >= 1) {
            return "flags=" + u8(raw, dataStart);
        }

        if (type == 255) {
            if (dataLen >= 2) {
                return "mfg=" + u16Raw(raw, dataStart)
                    + "/" + rawRange(raw, dataStart + 2, dataLen - 2, 16);
            }

            return "mfg=" + rawRange(raw, dataStart, dataLen, 16);
        }

        if ((type == 8 || type == 9) && dataLen > 0) {
            return "name=" + asciiRange(raw, dataStart, dataLen, 16);
        }

        if ((type == 2 || type == 3) && dataLen >= 2) {
            return "svc16=" + service16List(raw, dataStart, dataLen);
        }

        if ((type == 6 || type == 7) && dataLen >= 16) {
            return "svc128=" + rawRange(raw, dataStart, dataLen, 16);
        }

        return "type=" + type + "/len=" + dataLen + "/" + rawRange(raw, dataStart, dataLen, 12);
    }

    function rawRange(raw as Lang.ByteArray, start as Lang.Number, len as Lang.Number, maxBytes as Lang.Number) as Lang.String {
        var max = len;
        if (max > maxBytes) {
            max = maxBytes;
        }

        var text = "";
        for (var i = 0; i < max; i += 1) {
            if (i > 0) {
                text += ".";
            }

            text += u8(raw, start + i);
        }

        if (len > maxBytes) {
            text += "...";
        }

        return text;
    }

    function asciiRange(raw as Lang.ByteArray, start as Lang.Number, len as Lang.Number, maxChars as Lang.Number) as Lang.String {
        var max = len;
        if (max > maxChars) {
            max = maxChars;
        }

        var text = "";
        for (var i = 0; i < max; i += 1) {
            var value = u8(raw, start + i);
            if (value >= 32 && value <= 126) {
                text += value.toChar().toString();
            } else {
                text += ".";
            }
        }

        if (len > maxChars) {
            text += "...";
        }

        return text;
    }

    function service16List(raw as Lang.ByteArray, start as Lang.Number, len as Lang.Number) as Lang.String {
        var text = "";
        var count = len / 2;
        if (count > 6) {
            count = 6;
        }

        for (var i = 0; i < count; i += 1) {
            if (i > 0) {
                text += ".";
            }

            text += u16Raw(raw, start + (i * 2));
        }

        if ((len / 2) > count) {
            text += "...";
        }

        return text;
    }

    function u16Raw(raw as Lang.ByteArray, pos as Lang.Number) as Lang.Number {
        return u8(raw, pos) | (u8(raw, pos + 1) << 8);
    }

    function boolText(value as Lang.Boolean) as Lang.String {
        return value ? "true" : "false";
    }

    function charLabel(uuid) as Lang.String {
        if (uuid == null) {
            return "null";
        }

        if (uuid.equals(FtmsIds.feature())) {
            return "feature";
        }

        if (uuid.equals(FtmsIds.treadmillData())) {
            return "treadmill";
        }

        if (uuid.equals(FtmsIds.crossTrainerData())) {
            return "crossTrainer";
        }

        if (uuid.equals(FtmsIds.indoorBikeData())) {
            return "indoorBike";
        }

        if (uuid.equals(FtmsIds.trainingStatus())) {
            return "trainingStatus";
        }

        if (uuid.equals(FtmsIds.fitnessMachineStatus())) {
            return "machineStatus";
        }

        if (uuid.equals(FtmsIds.controlPoint())) {
            return "controlPoint";
        }

        if (uuid.equals(FtmsIds.vendorService())) {
            return "vendorService";
        }

        if (uuid.equals(FtmsIds.vendorNotify())) {
            return "vendorNotify";
        }

        if (uuid.equals(FtmsIds.vendorWrite())) {
            return "vendorWrite";
        }

        if (uuid.equals(Ble.cccdUuid())) {
            return "cccd";
        }

        return "" + uuid;
    }

    function sampleSummary(sample) as Lang.String {
        if (sample == null) {
            return "null";
        }

        var text = "kind=" + FtmsMachineKind.label(sample.kind) + " flags=" + sample.rawFlags;
        text += metricText(" speed", sample.speedKmh);
        text += metricText(" dist", sample.distanceM);
        text += metricText(" incl", sample.inclinePct);
        text += metricText(" elev+", sample.positiveElevationM);
        text += metricText(" power", sample.powerW);
        text += metricText(" hr", sample.heartRateBpm);
        text += metricText(" cad", sample.cadenceOrStepRate());
        text += metricText(" elapsed", sample.elapsedS);
        return text;
    }

    function metricText(label as Lang.String, value) as Lang.String {
        if (value == null) {
            return "";
        }

        return label + "=" + value;
    }
}
