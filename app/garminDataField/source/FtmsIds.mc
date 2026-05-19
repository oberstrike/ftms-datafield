using Toybox.BluetoothLowEnergy as Ble;

module FtmsIds {

    var _service = null;
    var _treadmillData = null;
    var _crossTrainerData = null;
    var _indoorBikeData = null;
    var _feature = null;
    var _trainingStatus = null;
    var _fitnessMachineStatus = null;
    var _controlPoint = null;
    var _vendorService = null;
    var _vendorNotify = null;
    var _vendorWrite = null;

    // Fitness Machine Service
    function service() {
        if (_service == null) {
            _service = Ble.stringToUuid("00001826-0000-1000-8000-00805f9b34fb");
        }

        return _service;
    }

    // FTMS data characteristics
    function treadmillData() {
        if (_treadmillData == null) {
            _treadmillData = Ble.stringToUuid("00002acd-0000-1000-8000-00805f9b34fb");
        }

        return _treadmillData;
    }

    function crossTrainerData() {
        if (_crossTrainerData == null) {
            _crossTrainerData = Ble.stringToUuid("00002ace-0000-1000-8000-00805f9b34fb");
        }

        return _crossTrainerData;
    }

    function indoorBikeData() {
        if (_indoorBikeData == null) {
            _indoorBikeData = Ble.stringToUuid("00002ad2-0000-1000-8000-00805f9b34fb");
        }

        return _indoorBikeData;
    }

    // Optional, useful later if you want status/debug/control.
    function feature() {
        if (_feature == null) {
            _feature = Ble.stringToUuid("00002acc-0000-1000-8000-00805f9b34fb");
        }

        return _feature;
    }

    function trainingStatus() {
        if (_trainingStatus == null) {
            _trainingStatus = Ble.stringToUuid("00002ad3-0000-1000-8000-00805f9b34fb");
        }

        return _trainingStatus;
    }

    function fitnessMachineStatus() {
        if (_fitnessMachineStatus == null) {
            _fitnessMachineStatus = Ble.stringToUuid("00002ada-0000-1000-8000-00805f9b34fb");
        }

        return _fitnessMachineStatus;
    }

    function controlPoint() {
        if (_controlPoint == null) {
            _controlPoint = Ble.stringToUuid("00002ad9-0000-1000-8000-00805f9b34fb");
        }

        return _controlPoint;
    }

    // Vendor diagnostic service seen by the phone BLE scan.
    function vendorService() {
        if (_vendorService == null) {
            _vendorService = Ble.stringToUuid("0000fff0-0000-1000-8000-00805f9b34fb");
        }

        return _vendorService;
    }

    function vendorNotify() {
        if (_vendorNotify == null) {
            _vendorNotify = Ble.stringToUuid("0000fff1-0000-1000-8000-00805f9b34fb");
        }

        return _vendorNotify;
    }

    function vendorWrite() {
        if (_vendorWrite == null) {
            _vendorWrite = Ble.stringToUuid("0000fff2-0000-1000-8000-00805f9b34fb");
        }

        return _vendorWrite;
    }
}
