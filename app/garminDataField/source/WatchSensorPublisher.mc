using Toybox.Activity as Act;
using Toybox.Communications as Comm;
using Toybox.System as Sys;

class WatchSensorTransmitListener extends Comm.ConnectionListener {

    // True while the newest message is not known to have reached the phone.
    var failed = false;

    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() {
        failed = false;
    }

    function onError() {
        failed = true;
    }
}

// Sends what the watch itself measures (heart rate, and power and cadence from sensors paired
// with it) to the phone, which shows it next to the equipment's values. With it goes the state
// of the activity timer, so the phone can start and end its session together with the activity.
class WatchSensorPublisher {

    // Timer states as the phone app reads them; independent of the watch's own numbering.
    const TIMER_OFF = 0;
    const TIMER_STOPPED = 1;
    const TIMER_PAUSED = 2;
    const TIMER_RUNNING = 3;
    const TIMER_REPEAT_INTERVAL_MS = 5000;
    // A watch connected to the equipment itself repeats the state this often for a minute after
    // a change, and rarely afterwards: a phone app opened later still takes over, but a watch
    // used without the phone does not keep its phone link busy.
    const TIMER_REPEAT_ALWAYS_MS = 60000;
    const TIMER_REPEAT_RARE_INTERVAL_MS = 30000;

    // Named after its first content. Phone apps from before power and cadence were added know
    // this type and read only "hr", so old and new versions keep working together.
    const MESSAGE_TYPE = "garmin_hr";
    const MIN_SEND_INTERVAL_MS = 1000;
    const UNCHANGED_SEND_INTERVAL_MS = 3000;
    const PHONE_BRIDGE_MAX_AGE_MS = 10000;
    const MAX_HEART_RATE_BPM = 255;
    const MAX_POWER_W = 3000;
    const MAX_CADENCE_RPM = 300;

    var _lastSentAtMs = 0;
    var _lastSentBpm = null;
    var _lastSentPower = null;
    var _lastSentCadence = null;
    // An activity that has not started yet needs no announcement.
    var _lastSentTimer = TIMER_OFF;
    // True once an activity was announced; only then is a lost message worth repeating.
    var _activityAnnounced = false;
    var _timerChangedAtMs = 0;
    var _listener;

    function initialize() {
        _listener = new WatchSensorTransmitListener();
    }

    function publish(info) {
        var timer = timerStateFromInfo(info);
        // The sensor values are only of use to a running phone bridge. Without one, only the
        // timer state goes out, and rarely: transmitting keeps the phone link busy.
        if (!FtmsState.hasFreshPhoneBridge(PHONE_BRIDGE_MAX_AGE_MS)) {
            announceTimer(timer);
            return;
        }

        var bpm = heartRateFromInfo(info);
        var power = powerFromInfo(info);
        var cadence = cadenceFromInfo(info);
        if (bpm == null && power == null && cadence == null && timer == null) {
            return;
        }

        var unchanged = (bpm == _lastSentBpm) && (power == _lastSentPower) && (cadence == _lastSentCadence)
            && (timer == _lastSentTimer);
        var now = Sys.getTimer();
        var minInterval = unchanged ? UNCHANGED_SEND_INTERVAL_MS : MIN_SEND_INTERVAL_MS;
        if (_lastSentAtMs > 0 && (now - _lastSentAtMs) < minInterval) {
            return;
        }

        send(bpm, power, cadence, timer);
    }

    // Without a phone bridge: tells the phone when the timer state changes, and keeps telling
    // it that an activity records, so a phone app opened later starts its session too. A watch
    // connected to the equipment itself needs no phone bridge and repeats it rarely after a while.
    function announceTimer(timer) {
        if (timer == null) {
            return;
        }

        var recording = (timer == TIMER_RUNNING) || (timer == TIMER_PAUSED);
        var changed = (timer != _lastSentTimer);
        var now = Sys.getTimer();
        if (changed) {
            _timerChangedAtMs = now;
        }

        var repeat = (_listener.failed && _activityAnnounced) || recording;
        if (!changed && !repeat) {
            return;
        }

        var changedRecently = (now - _timerChangedAtMs) < TIMER_REPEAT_ALWAYS_MS;
        var minInterval = TIMER_REPEAT_INTERVAL_MS;
        if (changed && !_listener.failed) {
            minInterval = MIN_SEND_INTERVAL_MS;
        } else if (FtmsState.connected && !changedRecently && !_listener.failed) {
            minInterval = TIMER_REPEAT_RARE_INTERVAL_MS;
        }
        if (_lastSentAtMs > 0 && (now - _lastSentAtMs) < minInterval) {
            return;
        }

        send(null, null, null, timer);
    }

    // The activity was saved or discarded. The data field may not be asked to compute again
    // after that, so the phone is told at once.
    function announceActivityEnded() {
        send(null, null, null, TIMER_OFF);
    }

    // Every message is complete: a value that is missing here is missing on the watch.
    function send(bpm, power, cadence, timer) {
        var message = { "type" => MESSAGE_TYPE };
        if (bpm != null) {
            message.put("hr", bpm);
        }
        if (power != null) {
            message.put("pwr", power);
        }
        if (cadence != null) {
            message.put("cad", cadence);
        }
        if (timer != null) {
            message.put("timer", timer);
        }

        try {
            Comm.transmit(message, {}, _listener);
            _lastSentAtMs = Sys.getTimer();
            _lastSentBpm = bpm;
            _lastSentPower = power;
            _lastSentCadence = cadence;
            if (timer != null) {
                _lastSentTimer = timer;
                if (timer != TIMER_OFF) {
                    _activityAnnounced = true;
                }
            }
        } catch (e) {
            Sys.println("Watch sensor transmit failed err=" + e.getErrorMessage());
        }
    }

    function timerStateFromInfo(info) {
        if (info == null || !(info has :timerState) || info.timerState == null) {
            return null;
        }

        var state = info.timerState;
        if (state == Act.TIMER_STATE_ON) {
            return TIMER_RUNNING;
        }
        if (state == Act.TIMER_STATE_PAUSED) {
            return TIMER_PAUSED;
        }
        if (state == Act.TIMER_STATE_STOPPED) {
            return TIMER_STOPPED;
        }
        return TIMER_OFF;
    }

    function heartRateFromInfo(info) {
        if (info == null || !(info has :currentHeartRate) || info.currentHeartRate == null) {
            return null;
        }

        var bpm = info.currentHeartRate.toNumber();
        if (bpm <= 0 || bpm > MAX_HEART_RATE_BPM) {
            return null;
        }

        return bpm;
    }

    // Power of a sensor paired with the watch, such as power meter pedals. Zero is a value.
    function powerFromInfo(info) {
        if (info == null || !(info has :currentPower) || info.currentPower == null) {
            return null;
        }

        var watts = info.currentPower.toNumber();
        if (watts < 0 || watts > MAX_POWER_W) {
            return null;
        }

        return watts;
    }

    function cadenceFromInfo(info) {
        if (info == null || !(info has :currentCadence) || info.currentCadence == null) {
            return null;
        }

        var cadence = info.currentCadence.toNumber();
        if (cadence < 0 || cadence > MAX_CADENCE_RPM) {
            return null;
        }

        return cadence;
    }
}
