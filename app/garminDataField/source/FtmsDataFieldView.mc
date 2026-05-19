using Toybox.WatchUi as Ui;

class FtmsDataFieldView extends Ui.SimpleDataField {

    var _bleClient;
    var _phoneBridge;
    var _fitWriter;
    var _lastWrittenPacketCount = 0;

    function initialize() {
        SimpleDataField.initialize();

        // What Garmin shows as the field label.
        label = FtmsVariant.LABEL;

        FtmsState.reset();
        _fitWriter = new FtmsFitWriter(self);
        _phoneBridge = new FtmsPhoneBridgeReceiver();
        _phoneBridge.start();
        _bleClient = new FtmsBleClient();
        _bleClient.start();
    }

    function onHide() {
        // Do not disconnect here: onHide can be called when the user switches
        // activity data pages. The BLE connection should stay alive for the
        // whole recorded activity while this data field is active.
    }

    function shutdown() {
        if (_bleClient != null) {
            _bleClient.stop();
        }
    }

    function compute(info) {
        if (_bleClient != null) {
            if (FtmsState.hasFreshPhoneBridge(5000)) {
                _bleClient.pauseForPhoneBridge();
            } else {
                _bleClient.tick();
            }
        }

        var sample = FtmsState.lastSample;

        if (sample != null && FtmsState.packetCount != _lastWrittenPacketCount) {
            _fitWriter.write(sample);
            _lastWrittenPacketCount = FtmsState.packetCount;
        }

        if (!FtmsState.connected && !FtmsState.phoneBridgeActive) {
            return FtmsState.displayStatus();
        }

        if (FtmsState.phoneBridgeActive && !FtmsState.hasFreshPhoneBridge(5000)) {
            return "PSTALE";
        }

        if (!FtmsState.phoneBridgeActive && FtmsState.isStale(4000)) {
            return "STALE";
        }

        return FtmsMetricFormatter.primary(sample, _fitWriter.getAscentM());
    }
}
