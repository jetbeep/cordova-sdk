var exec = require('cordova/exec');

exports.initSDK = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'initSDK', [arg0]);
};

exports.searchDevices = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'searchDevices', [arg0]);
};

exports.stopSearching = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'stopSearching', [arg0]);
};

exports.applyToken = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'applyToken', [arg0]);
};

exports.isPermissionGranted = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'isPermissionGranted', [arg0]);
};

exports.requestPermissions = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'requestPermissions', [arg0]);
};

exports.enableBeeper = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'enableBeeper', [arg0]);
};

exports.subscribeToLocations = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'subscribeToLocations', [arg0]);
};

exports.unsubscribeFromLocations = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'unsubscribeFromLocations', [arg0]);
};

exports.getEnteredShops = function(arg0, success, error) {
    exec(success, error, 'JetBeepSDKPlugin', 'getEnteredShops', [arg0]);
};
