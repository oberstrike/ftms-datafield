using Toybox.Communications as Comm;
using Toybox.System as Sys;

class GarminHeartRateTransmitListener extends Comm.ConnectionListener {

    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() {
    }

    function onError() {
    }
}

class GarminHeartRatePublisher {

    const MESSAGE_TYPE = "garmin_hr";
    const MIN_SEND_INTERVAL_MS = 1000;
    const UNCHANGED_SEND_INTERVAL_MS = 3000;

    var _lastSentAtMs = 0;
    var _lastSentBpm = null;
    var _listener;

    function initialize() {
        _listener = new GarminHeartRateTransmitListener();
    }

    function publish(info) {
        var bpm = heartRateFromInfo(info);
        if (bpm == null) {
            return;
        }

        var now = Sys.getTimer();
        var minInterval = (_lastSentBpm == bpm) ? UNCHANGED_SEND_INTERVAL_MS : MIN_SEND_INTERVAL_MS;
        if (_lastSentAtMs > 0 && (now - _lastSentAtMs) < minInterval) {
            return;
        }

        try {
            Comm.transmit(
                {
                    "type" => MESSAGE_TYPE,
                    "hr" => bpm
                },
                {},
                _listener
            );
            _lastSentAtMs = now;
            _lastSentBpm = bpm;
        } catch (e) {
            Sys.println("Garmin HR transmit failed err=" + e.getErrorMessage());
        }
    }

    function heartRateFromInfo(info) {
        if (info == null || !(info has :currentHeartRate) || info.currentHeartRate == null) {
            return null;
        }

        var bpm = info.currentHeartRate.toNumber();
        if (bpm <= 0 || bpm > 255) {
            return null;
        }

        return bpm;
    }
}
