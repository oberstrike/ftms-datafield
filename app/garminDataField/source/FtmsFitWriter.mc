using Toybox.FitContributor as Fit;
using Toybox.System as Sys;

class FtmsFitWriter {

    // Keep record fields under the small Data Field FIT payload budget.
    const FIELD_HM_RECORD = 0;
    const FIELD_HM_SESSION = 1;
    const FIELD_HM_SOURCE = 2;
    const FIELD_POWER = 3;
    const FIELD_SPEED = 4;
    const FIELD_DISTANCE = 5;
    const FIELD_CADENCE_OR_STEP = 6;
    const FIELD_INCLINE = 7;
    const FIELD_MACHINE = 8;
    const FIELD_HEART_RATE = 9;
    const FIELD_ELAPSED = 10;
    const FIELD_RESISTANCE = 11;

    var _ascentTracker;

    var _hmRecordField;
    var _hmSessionField;
    var _hmSourceField;
    var _powerField;
    var _speedField;
    var _distanceField;
    var _cadenceOrStepField;
    var _inclineField;
    var _machineField;
    var _heartRateField;
    var _elapsedField;
    var _resistanceField;

    function initialize(ownerDataField) {
        _ascentTracker = new AscentTracker();

        _hmRecordField = ownerDataField.createField(
            "hm_gelaufen",
            FIELD_HM_RECORD,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "m" }
        );

        _hmSessionField = ownerDataField.createField(
            "hm_gelaufen_summe",
            FIELD_HM_SESSION,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_SESSION, :units => "m" }
        );

        _hmSourceField = ownerDataField.createField(
            "hm_quelle",
            FIELD_HM_SOURCE,
            Fit.DATA_TYPE_UINT8,
            { :mesgType => Fit.MESG_TYPE_SESSION }
        );

        _powerField = ownerDataField.createField(
            "ftms_power",
            FIELD_POWER,
            Fit.DATA_TYPE_SINT16,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "W" }
        );

        _speedField = ownerDataField.createField(
            "ftms_speed",
            FIELD_SPEED,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "km/h" }
        );

        _distanceField = ownerDataField.createField(
            "ftms_distance",
            FIELD_DISTANCE,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "m" }
        );

        _cadenceOrStepField = ownerDataField.createField(
            "ftms_cad_step",
            FIELD_CADENCE_OR_STEP,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "rpm/spm" }
        );

        _inclineField = ownerDataField.createField(
            "ftms_incline",
            FIELD_INCLINE,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "%" }
        );

        _machineField = ownerDataField.createField(
            "ftms_machine",
            FIELD_MACHINE,
            Fit.DATA_TYPE_UINT8,
            { :mesgType => Fit.MESG_TYPE_RECORD }
        );

        _heartRateField = ownerDataField.createField(
            "ftms_hr",
            FIELD_HEART_RATE,
            Fit.DATA_TYPE_UINT8,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "bpm" }
        );

        _elapsedField = ownerDataField.createField(
            "ftms_elapsed",
            FIELD_ELAPSED,
            Fit.DATA_TYPE_UINT16,
            { :mesgType => Fit.MESG_TYPE_RECORD, :units => "s" }
        );

        _resistanceField = ownerDataField.createField(
            "ftms_resistance",
            FIELD_RESISTANCE,
            Fit.DATA_TYPE_FLOAT,
            { :mesgType => Fit.MESG_TYPE_RECORD }
        );
    }

    function reset() {
        _ascentTracker.reset();
    }

    function getAscentM() {
        return _ascentTracker.totalAscentM;
    }

    function write(sample) {
        if (sample == null) {
            return;
        }

        // Do not write stale FTMS values after a BLE dropout.
        if ((Sys.getTimer() - sample.timestampMs) > 4000) {
            return;
        }

        var hm = _ascentTracker.update(sample);

        setField(_hmRecordField, hm);
        setField(_hmSessionField, hm);
        setField(_hmSourceField, _ascentTracker.source);
        setField(_machineField, sample.kind);

        if (sample.powerW != null) {
            setField(_powerField, sample.powerW);
        }

        if (sample.speedKmh != null) {
            setField(_speedField, sample.speedKmh);
        }

        if (sample.distanceM != null) {
            setField(_distanceField, sample.distanceM);
        }

        var cadenceOrStep = sample.cadenceOrStepRate();
        if (cadenceOrStep != null) {
            setField(_cadenceOrStepField, cadenceOrStep);
        }

        if (sample.inclinePct != null) {
            setField(_inclineField, sample.inclinePct);
        }

        if (sample.heartRateBpm != null) {
            setField(_heartRateField, sample.heartRateBpm);
        }

        if (sample.elapsedS != null) {
            setField(_elapsedField, sample.elapsedS);
        }

        if (sample.resistance != null) {
            setField(_resistanceField, sample.resistance);
        }
    }

    function setField(field, value) {
        if (field != null && value != null) {
            field.setData(value);
        }
    }
}
