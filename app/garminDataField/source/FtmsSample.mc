using Toybox.System as Sys;

class FtmsSample {
    var kind = FtmsMachineKind.UNKNOWN;
    var timestampMs = 0;
    var rawFlags = 0;

    // Common metrics
    var speedKmh = null;
    var distanceM = null;
    var powerW = null;
    var heartRateBpm = null;
    var elapsedS = null;
    var remainingS = null;

    // Bike metrics
    var cadenceRpm = null;
    var resistance = null;

    // Treadmill / elliptical metrics
    var inclinePct = null;
    var rampDeg = null;
    var positiveElevationM = null;
    var negativeElevationM = null;
    var bridgeAscentM = null;

    // Elliptical metrics
    var stepRateSpm = null;
    var strideCount = null;
    var movementDirection = null;

    // 0 none, 1 FTMS field, 2 calculated by watch, 3 calculated by phone bridge
    var elevationSource = 0;

    function initialize(machineKind) {
        kind = machineKind;
        timestampMs = Sys.getTimer();
    }

    function cadenceOrStepRate() {
        if (cadenceRpm != null) {
            return cadenceRpm;
        }

        if (stepRateSpm != null) {
            return stepRateSpm;
        }

        return null;
    }
}
