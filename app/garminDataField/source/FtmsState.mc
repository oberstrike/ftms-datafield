using Toybox.System as Sys;

module FtmsState {
    var connected = false;
    var statusText = "INIT";
    var deviceName = null;
    var packetCount = 0;
    var lastSample = null;
    var scanResultCount = 0;
    var ftmsScanCount = 0;
    var lastScanName = null;
    var lastScanRssi = null;
    var lastScanHasFtms = false;
    var lastScanServices = "";
    var lastScanRaw = "";
    var debugText = "SCAN 0";
    var phoneBridgeActive = false;
    var lastPhoneBridgeMs = 0;

    function reset() {
        connected = false;
        statusText = "INIT";
        deviceName = null;
        packetCount = 0;
        lastSample = null;
        scanResultCount = 0;
        ftmsScanCount = 0;
        lastScanName = null;
        lastScanRssi = null;
        lastScanHasFtms = false;
        lastScanServices = "";
        lastScanRaw = "";
        debugText = "SCAN 0";
        phoneBridgeActive = false;
        lastPhoneBridgeMs = 0;
    }

    function setStatus(text) {
        statusText = text;
    }

    function update(sample) {
        if (sample != null) {
            lastSample = sample;
            packetCount += 1;
            statusText = FtmsMachineKind.label(sample.kind);
        }
    }

    function updatePhoneBridge(sample) {
        if (sample != null) {
            lastSample = sample;
            packetCount += 1;
            phoneBridgeActive = true;
            lastPhoneBridgeMs = Sys.getTimer();
            statusText = "PHONE";
        }
    }

    function stopPhoneBridge() {
        phoneBridgeActive = false;
        statusText = "STOP";
    }

    function recordScanResult(name, rssi, hasFtms, services, rawPrefix) {
        scanResultCount += 1;
        if (hasFtms) {
            ftmsScanCount += 1;
        }

        lastScanName = name;
        lastScanRssi = rssi;
        lastScanHasFtms = hasFtms;
        lastScanServices = services;
        lastScanRaw = rawPrefix;
        debugText = buildScanDebugText();
    }

    function displayStatus() {
        if (phoneBridgeActive && !hasFreshPhoneBridge(5000)) {
            return "PSTALE";
        }

        if (!connected && statusText.equals("SCAN")) {
            return debugText;
        }

        return statusText;
    }

    function buildScanDebugText() {
        if (scanResultCount == 0) {
            return "SCAN 0";
        }

        var prefix = lastScanHasFtms ? "FTMS" : "NO1826";
        return prefix + " " + shortName(lastScanName) + " " + lastScanRssi;
    }

    function shortName(name) {
        if (name == null || name.length() == 0) {
            return "NONAME";
        }

        if (name.length() > 8) {
            return name.substring(0, 8);
        }

        return name;
    }

    function isStale(maxAgeMs) {
        if (lastSample == null) {
            return true;
        }

        return (Sys.getTimer() - lastSample.timestampMs) > maxAgeMs;
    }

    function hasFreshPhoneBridge(maxAgeMs) {
        return phoneBridgeActive && (Sys.getTimer() - lastPhoneBridgeMs) <= maxAgeMs;
    }
}
