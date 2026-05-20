class AscentTracker {
    var _baseFtmsPositiveM = null;
    var _lastDistanceM = null;
    var _lastInclinePct = null;

    var totalAscentM = 0.0;
    var source = 0; // 0 none, 1 FTMS, 2 calculated distance*incline, 3 phone bridge

    function reset() {
        _baseFtmsPositiveM = null;
        _lastDistanceM = null;
        _lastInclinePct = null;
        totalAscentM = 0.0;
        source = 0;
    }

    function update(sample) {
        if (sample == null) {
            return totalAscentM;
        }

        if (sample.bridgeAscentM != null) {
            if (sample.bridgeAscentM >= 0) {
                totalAscentM = sample.bridgeAscentM;
                source = 3;
            }

            if (sample.distanceM != null) {
                _lastDistanceM = sample.distanceM;
            }

            return totalAscentM;
        }

        // Best source: cumulative Positive Elevation Gain directly from FTMS.
        if (sample.positiveElevationM != null) {
            if (_baseFtmsPositiveM == null) {
                _baseFtmsPositiveM = sample.positiveElevationM;
            }

            var hm = sample.positiveElevationM - _baseFtmsPositiveM;

            if (hm >= 0) {
                totalAscentM = hm;
                source = 1;
            } else {
                // Machine reset or new workout from the machine side.
                _baseFtmsPositiveM = sample.positiveElevationM;
                totalAscentM = 0.0;
                source = 1;
            }

            if (sample.distanceM != null) {
                _lastDistanceM = sample.distanceM;
            }

            return totalAscentM;
        }

        // Fallback: calculate ascent from distance delta and positive incline.
        // This works reasonably for treadmills and some ellipticals, but is not native Garmin ascent.
        if (sample.distanceM != null && sample.inclinePct != null) {
            if (_lastDistanceM != null) {
                var deltaM = sample.distanceM - _lastDistanceM;

                // Filter obvious reset / corrupt packets.
                if (deltaM > 0 && deltaM < 1000) {
                    var intervalIncline = _lastInclinePct != null ? _lastInclinePct : sample.inclinePct;
                    if (intervalIncline > 0) {
                        totalAscentM += deltaM * intervalIncline / 100.0;
                        source = 2;
                    }
                }
            }

            _lastDistanceM = sample.distanceM;
            _lastInclinePct = sample.inclinePct;
        }

        return totalAscentM;
    }
}
