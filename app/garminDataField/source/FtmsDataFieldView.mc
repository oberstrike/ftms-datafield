using Toybox.WatchUi as Ui;

class FtmsDataFieldView extends Ui.SimpleDataField {

    var _bleClient;
    var _phoneBridge;
    var _fitWriter;
    var _heartRatePublisher;
    var _lastWrittenPacketCount = 0;
    var _primaryMetric = FtmsVariant.METRIC_ASCENT;

    function initialize() {
        SimpleDataField.initialize();

        refreshSettings();

        FtmsState.reset();
        _fitWriter = new FtmsFitWriter(self);
        _heartRatePublisher = new GarminHeartRatePublisher();
        _phoneBridge = new FtmsPhoneBridgeReceiver();
        _phoneBridge.start();
        _bleClient = new FtmsBleClient();
        _bleClient.start();
    }

    function refreshSettings() {
        _primaryMetric = FtmsSettings.primaryMetric();
        label = FtmsSettings.metricLabel(_primaryMetric);
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
        if (_heartRatePublisher != null) {
            _heartRatePublisher.publish(info);
        }

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

        return FtmsMetricFormatter.primary(sample, _fitWriter.getAscentM(), _primaryMetric);
    }
}
