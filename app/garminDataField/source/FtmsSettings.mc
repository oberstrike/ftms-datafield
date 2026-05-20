using Toybox.Application.Properties as Properties;
using Toybox.Lang as Lang;

module FtmsSettings {
    const PROP_PRIMARY_METRIC = "PrimaryMetric";

    function primaryMetric() {
        var value = Properties.getValue(PROP_PRIMARY_METRIC);
        if (value instanceof Lang.Number) {
            return normalizeMetric(value);
        }

        return normalizeMetric(FtmsVariant.DEFAULT_PRIMARY_METRIC);
    }

    function normalizeMetric(metric) {
        if (metric < FtmsVariant.METRIC_ASCENT || metric > FtmsVariant.METRIC_RESISTANCE) {
            return FtmsVariant.METRIC_ASCENT;
        }

        return metric;
    }

    function metricLabel(metric) {
        if (metric == FtmsVariant.METRIC_SPEED) {
            return "FTMS SPD";
        }

        if (metric == FtmsVariant.METRIC_DISTANCE) {
            return "FTMS DST";
        }

        if (metric == FtmsVariant.METRIC_POWER) {
            return "FTMS PWR";
        }

        if (metric == FtmsVariant.METRIC_CADENCE) {
            return "FTMS CAD";
        }

        if (metric == FtmsVariant.METRIC_INCLINE) {
            return "FTMS INC";
        }

        if (metric == FtmsVariant.METRIC_HEART_RATE) {
            return "FTMS HR";
        }

        if (metric == FtmsVariant.METRIC_ELAPSED) {
            return "FTMS TIME";
        }

        if (metric == FtmsVariant.METRIC_RESISTANCE) {
            return "FTMS RES";
        }

        return "FTMS ASC";
    }
}
