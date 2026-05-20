using Toybox.Application as App;
using Toybox.WatchUi as Ui;

class FtmsDataFieldApp extends App.AppBase {

    var _view = null;

    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() {
        _view = new FtmsDataFieldView();
        return [ _view ];
    }

    function onStop(state) {
        if (_view != null) {
            _view.shutdown();
        }
    }

    function onSettingsChanged() {
        if (_view != null) {
            _view.refreshSettings();
        }
        Ui.requestUpdate();
    }
}
