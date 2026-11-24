using Toybox.Communications as Comm;
using Toybox.Lang as Lang;
using Toybox.System as Sys;

class FtmsPhoneBridgeReceiver {

    // Sequence number of the newest message handled; -1 when none is known.
    var _lastSeq = -1;

    function initialize() {
    }

    function start() as Void {
        try {
            Comm.registerForPhoneAppMessages(method(:onPhoneAppMessage));
            Sys.println("FTMS phone bridge registered");
        } catch (e instanceof Lang.Exception) {
            Sys.println("FTMS phone bridge register failed err=" + e.getErrorMessage());
        }
    }

    function onPhoneAppMessage(msg as Comm.PhoneAppMessage) as Void {
        var data = msg.data;
        if (!(data instanceof Lang.Dictionary)) {
            Sys.println("FTMS phone ignored non-dictionary");
            return;
        }

        var version = numberValue(data, "v", 0);
        var type = data["type"];
        var kind = numberValue(data, "kind", 0);

        var seq = numberValue(data, "seq", -1);

        if (version == 2 && type != null && type.equals("ftms_stop")) {
            _lastSeq = -1;
            FtmsState.stopPhoneBridge();
            Sys.println("FTMS phone stop seq=" + seq);
            return;
        }

        if (isOutdated(seq)) {
            Sys.println("FTMS phone ignored outdated seq=" + seq + " last=" + _lastSeq);
            return;
        }

        if (version == 2 && type != null && type.equals("ftms_pause")) {
            rememberSeq(seq);
            FtmsState.pausePhoneBridge();
            return;
        }

        if (version == 2 && type != null && type.equals("ftms_wait")) {
            rememberSeq(seq);
            FtmsState.waitForPhoneBridge();
            return;
        }

        if (version == 2 && type != null && type.equals("ftms_resume")) {
            rememberSeq(seq);
            FtmsState.resumePhoneBridge();
            Sys.println("FTMS phone resume seq=" + seq);
            return;
        }

        if ((version != 1 && version != 2) || type == null || !type.equals("ftms") || !isSupportedKind(kind)) {
            Sys.println("FTMS phone ignored protocol");
            return;
        }

        rememberSeq(seq);

        var sample = new FtmsSample(kind);
        sample.timestampMs = Sys.getTimer();
        sample.rawFlags = numberValue(data, "flags", 0);
        sample.elevationSource = 3;

        var speed100 = optionalNumber(data, "speed100");
        if (speed100 != null) {
            sample.speedKmh = speed100 / 100.0;
        }

        var dist10 = optionalNumber(data, "dist10");
        if (dist10 != null) {
            sample.distanceM = dist10 / 10.0;
        } else {
            var ftmsDist = optionalNumber(data, "ftmsDist");
            if (ftmsDist != null) {
                sample.distanceM = ftmsDist.toFloat();
            }
        }

        var incl10 = optionalNumber(data, "incl10");
        if (incl10 != null) {
            sample.inclinePct = incl10 / 10.0;
        }

        var ascent10 = optionalNumber(data, "ascent10");
        if (ascent10 != null) {
            sample.bridgeAscentM = ascent10 / 10.0;
        }

        sample.powerW = optionalNumber(data, "power");
        sample.heartRateBpm = optionalNumber(data, "hr");
        sample.elapsedS = optionalNumber(data, "elapsed");
        sample.remainingS = optionalNumber(data, "remaining");

        var cad10 = optionalNumber(data, "cad10");
        if (cad10 != null) {
            sample.cadenceRpm = cad10 / 10.0;
        }

        var step10 = optionalNumber(data, "step10");
        if (step10 != null) {
            sample.stepRateSpm = step10 / 10.0;
        }

        var res10 = optionalNumber(data, "res10");
        if (res10 != null) {
            sample.resistance = res10 / 10.0;
        }

        FtmsState.updatePhoneBridge(sample);
        Sys.println("FTMS phone rx seq=" + seq
            + " kind=" + kind
            + " speed=" + sample.speedKmh
            + " dist=" + sample.distanceM
            + " ascent=" + sample.bridgeAscentM
            + " cadStep=" + sample.cadenceOrStepRate()
            + " elapsed=" + sample.elapsedS);
    }

    // A message is outdated when it carries a sequence number that is not newer than the last
    // one handled, i.e. it was delivered late or twice. This is only judged while the bridge is
    // fresh: after a silence the phone app may have restarted and begun counting from 1 again.
    function isOutdated(seq as Lang.Number) as Lang.Boolean {
        return seq >= 0
            && _lastSeq >= 0
            && seq <= _lastSeq
            && FtmsState.hasFreshPhoneBridge(5000);
    }

    function rememberSeq(seq as Lang.Number) as Void {
        if (seq >= 0) {
            _lastSeq = seq;
        }
    }

    function isSupportedKind(kind as Lang.Number) as Lang.Boolean {
        return kind == FtmsMachineKind.INDOOR_BIKE
            || kind == FtmsMachineKind.TREADMILL
            || kind == FtmsMachineKind.CROSS_TRAINER;
    }

    function optionalNumber(data as Lang.Dictionary, key as Lang.String) {
        if (!data.hasKey(key)) {
            return null;
        }

        var value = data[key];
        if (value instanceof Lang.Number || value instanceof Lang.Float || value instanceof Lang.Double) {
            return value;
        }

        return null;
    }

    function numberValue(data as Lang.Dictionary, key as Lang.String, fallback as Lang.Number) as Lang.Number {
        var value = optionalNumber(data, key);
        return value == null ? fallback : value;
    }
}
