module FtmsMetricFormatter {

    function primary(sample, ascentM, metric) {
        var value = valueForMetric(sample, ascentM, metric);
        if (value == null) {
            return "--";
        }

        if (metric == FtmsVariant.METRIC_ELAPSED) {
            return formatElapsed(value);
        }

        if (metric == FtmsVariant.METRIC_DISTANCE) {
            if (value >= 1000.0) {
                return (value / 1000.0).format("%.2f");
            }

            return value.format("%.0f");
        }

        if (metric == FtmsVariant.METRIC_ASCENT) {
            if (value < 10.0) {
                return value.format("%.1f");
            }

            return value.format("%.0f");
        }

        if (metric == FtmsVariant.METRIC_SPEED) {
            return value.format("%.1f");
        }

        if (metric == FtmsVariant.METRIC_INCLINE
            || metric == FtmsVariant.METRIC_RESISTANCE
            || metric == FtmsVariant.METRIC_CADENCE) {
            return value.format("%.1f");
        }

        return value.format("%.0f");
    }

    function valueForMetric(sample, ascentM, metric) {
        if (metric == FtmsVariant.METRIC_ASCENT) {
            return ascentM;
        }

        if (sample == null) {
            return null;
        }

        if (metric == FtmsVariant.METRIC_SPEED) {
            return sample.speedKmh;
        }

        if (metric == FtmsVariant.METRIC_DISTANCE) {
            return sample.distanceM;
        }

        if (metric == FtmsVariant.METRIC_POWER) {
            return sample.powerW;
        }

        if (metric == FtmsVariant.METRIC_CADENCE) {
            return sample.cadenceOrStepRate();
        }

        if (metric == FtmsVariant.METRIC_INCLINE) {
            return sample.inclinePct;
        }

        if (metric == FtmsVariant.METRIC_HEART_RATE) {
            return sample.heartRateBpm;
        }

        if (metric == FtmsVariant.METRIC_ELAPSED) {
            return sample.elapsedS;
        }

        if (metric == FtmsVariant.METRIC_RESISTANCE) {
            return sample.resistance;
        }

        return null;
    }

    function formatElapsed(seconds) {
        var total = seconds.toNumber();
        var hours = total / 3600;
        var minutes = (total % 3600) / 60;
        var secs = total % 60;

        if (hours > 0) {
            return hours.format("%d") + ":" + minutes.format("%02d") + ":" + secs.format("%02d");
        }

        return minutes.format("%d") + ":" + secs.format("%02d");
    }
}
