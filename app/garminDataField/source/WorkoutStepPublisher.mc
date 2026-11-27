using Toybox.Activity as Act;
using Toybox.Communications as Comm;
using Toybox.System as Sys;

class WorkoutTransmitListener extends Comm.ConnectionListener {

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

// Tells the phone which step of a workout the watch is in and which one follows, as far as the
// watch says so to a data field, together with how many steps and laps have ended and how long
// the step has lasted. A message goes out when something of that changed, and every few seconds
// while the watch is in a step, so that a phone that joins late or loses a message catches up and
// its countdown stays with the watch's. It only goes to a running phone bridge.
//
// The watch does not say how much of a step is left. The time a step has lasted is therefore
// taken from the activity timer, which stands still while the activity is stopped: its value is
// noted when a workout starts and whenever a step ends.
//
// Nothing in this class may stop the data field, so every question to the watch is asked inside
// a try.
class WorkoutStepPublisher {

    const MESSAGE_TYPE = "garmin_workout";
    const MIN_SEND_INTERVAL_MS = 2000;
    const RETRY_INTERVAL_MS = 5000;
    const REPEAT_INTERVAL_MS = 10000;
    const PHONE_BRIDGE_MAX_AGE_MS = 10000;
    const MAX_TEXT_LENGTH = 60;

    var _lastSentAtMs = 0;
    var _lastSentContent = null;
    var _workoutsStarted = 0;
    var _stepsCompleted = 0;
    var _laps = 0;
    // The activity timer when the step the watch is in began; null while that is not known.
    var _stepStartedAtTimerMs = null;
    var _listener;

    function initialize() {
        _listener = new WorkoutTransmitListener();
    }

    function onWorkoutStarted() {
        _workoutsStarted++;
        _stepStartedAtTimerMs = timerNow();
    }

    function onWorkoutStepComplete() {
        _stepsCompleted++;
        _stepStartedAtTimerMs = timerNow();
    }

    function onTimerLap() {
        _laps++;
    }

    // The activity was saved or discarded; the next one counts from zero.
    function onTimerReset() {
        _workoutsStarted = 0;
        _stepsCompleted = 0;
        _laps = 0;
        _stepStartedAtTimerMs = null;
        _lastSentContent = null;
    }

    // The activity timer in milliseconds, or null if the watch does not say.
    function timerNow() {
        try {
            var info = Act.getActivityInfo();
            if (info != null && (info has :timerTime) && info.timerTime != null) {
                return info.timerTime;
            }
        } catch (e) {
        }
        return null;
    }

    function publish() {
        if (!FtmsState.hasFreshPhoneBridge(PHONE_BRIDGE_MAX_AGE_MS)) {
            return;
        }

        var now = Sys.getTimer();
        var minInterval = _listener.failed ? RETRY_INTERVAL_MS : MIN_SEND_INTERVAL_MS;
        if (_lastSentAtMs > 0 && (now - _lastSentAtMs) < minInterval) {
            return;
        }

        var message = {
            "type" => MESSAGE_TYPE,
            "started" => _workoutsStarted,
            "done" => _stepsCompleted,
            "laps" => _laps
        };
        try {
            if (Act has :getCurrentWorkoutStep) {
                var current = Act.getCurrentWorkoutStep();
                if (current != null) {
                    message.put("cur", describe(current));
                }
                var next = Act.getNextWorkoutStep();
                if (next != null) {
                    message.put("next", describe(next));
                }
            } else {
                message.put("err", "unsupported");
            }
        } catch (e) {
            message.put("err", errorText(e));
        }

        // Compared without the time the step has lasted, which changes all the time.
        var content = message.toString();
        var inStep = message.hasKey("cur");
        if (content.equals(_lastSentContent) && !_listener.failed) {
            if (!inStep || (now - _lastSentAtMs) < REPEAT_INTERVAL_MS) {
                return;
            }
        }

        var timer = timerNow();
        if (inStep && _stepStartedAtTimerMs != null && timer != null && timer >= _stepStartedAtTimerMs) {
            message.put("el", timer - _stepStartedAtTimerMs);
        }

        try {
            Comm.transmit(message, {}, _listener);
            _lastSentAtMs = now;
            _lastSentContent = content;
        } catch (e) {
            Sys.println("Workout transmit failed err=" + errorText(e));
        }
    }

    // What the watch says about one step: intensity, name, notes, sport, and its duration and
    // target. An interval step carries an active and a rest part and the number of the repetition.
    function describe(info) {
        var step = {};
        if (info has :intensity) {
            putValue(step, "int", info.intensity);
        }
        if (info has :name) {
            putText(step, "name", info.name);
        }
        if (info has :notes) {
            putText(step, "notes", info.notes);
        }
        if (info has :sport) {
            putValue(step, "sport", info.sport);
        }
        if (info has :subSport) {
            putValue(step, "sub", info.subSport);
        }

        if (!(info has :step) || info.step == null) {
            return step;
        }
        var detail = info.step;
        if (detail instanceof Act.WorkoutIntervalStep) {
            putValue(step, "rep", detail.repetitionNumber);
            if (detail.activeStep != null) {
                step.put("active", describeStep(detail.activeStep));
            }
            if (detail.restStep != null) {
                step.put("rest", describeStep(detail.restStep));
            }
        } else {
            step.put("step", describeStep(detail));
        }
        return step;
    }

    function describeStep(detail) {
        var step = {};
        if (detail has :durationType) {
            putValue(step, "dt", detail.durationType);
        }
        if (detail has :durationValue) {
            putValue(step, "dv", detail.durationValue);
        }
        if (detail has :targetType) {
            putValue(step, "tt", detail.targetType);
        }
        if (detail has :targetValueLow) {
            putValue(step, "lo", detail.targetValueLow);
        }
        if (detail has :targetValueHigh) {
            putValue(step, "hi", detail.targetValueHigh);
        }
        return step;
    }

    function putValue(target, key, value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    function putText(target, key, value) {
        if (value == null) {
            return;
        }

        var text = value.toString();
        if (text.length() == 0) {
            return;
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        target.put(key, text);
    }

    function errorText(e) {
        var text = e.getErrorMessage();
        return text == null ? "error" : text;
    }
}
