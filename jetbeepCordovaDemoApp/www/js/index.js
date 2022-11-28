/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

// Wait for the deviceready event before using any of Cordova's device APIs.
// See https://cordova.apache.org/docs/en/latest/cordova/events/events.html#deviceready
document.addEventListener('deviceready', onDeviceReady, false);

document.getElementById("bluetoothStateButton").addEventListener("click", bluetoothState);
document.getElementById("subscribeBluetoothEventsButton").addEventListener("click", subscribeBluetoothEvents);
document.getElementById("unsubscribeBluetoothEventsButton").addEventListener("click", unsubscribeBluetoothEvents);

document.getElementById("isPermissionGrantedButton").addEventListener("click", isPermissionGranted);
document.getElementById("requestPermissionsButton").addEventListener("click", requestPermissions);
document.getElementById("enableBeeperButton").addEventListener("click", enableBeeper);
document.getElementById("getNearbyDevicesButton").addEventListener("click", getNearbyDevices);
document.getElementById("getEnteredShopsButton").addEventListener("click", getEnteredShops);
document.getElementById("subscribeToLocationsButton").addEventListener("click", subscribeToLocations);
document.getElementById("unsubscribeFromLocationsButton").addEventListener("click", initSDK);


document.getElementById("initSDKButton").addEventListener("click", initSDK);
document.getElementById("tokenButton").addEventListener("click", searchDevices);
document.getElementById("applyTokenButton").addEventListener("click", applyToken);
document.getElementById("stopSearchButton").addEventListener("click", stopSearching);

document.getElementById('appNameKey').value = 'gls-app';
document.getElementById('appToken').value = '4db96549-ea58-4cc1-bb5c-4ee9de416585';
document.getElementById('serviceUUID').value = '17a7';

// var cordova = require('cordova');
cordova.fireWindowEvent('deviceStatus');

var plugins = cordova.require("cordova/plugin_list").metadata;

window.addEventListener('deviceStatus', searchDevices);

function logger(log) {
    const container = document.querySelector('.founded-devices');
    container.classList.remove('founded-devices__empty');
    container.innerHTML = container.innerHTML + JSON.stringify(log) + "<br />";
    console.log(JSON.stringify(log));
}

function onDeviceReady() {
    // Cordova is now initialized. Have fun!
    console.log('Running cordova-' + cordova.platformId + '@' + cordova.version);
    document.getElementById('deviceready').classList.add('ready');

    var plugins = cordova.require("cordova/plugin_list").metadata;
    if (typeof plugins['com-jetbeep-plugins-sdk'] === "undefined")
    {
        logger('Jetbeep plugin is not installed!!! Please check readme at https://github.com/jetbeep/cordova-sdk')
    } else {
        logger('Jetbeep plugin is installed. We are ready to go!')
    }

    app.receivedEvent('deviceready');
}

function isPermissionGranted() {
    // Check for permissions
   logger('isPermissionGranted function call');
   jetbeepsdkplugin
   .isPermissionGranted("",
   function(success){
    logger('permissions granted: success')
    logger(success)
   },
   function(error){
    logger('permissions granted: error')
    logger(error)
   })
}

function requestPermissions() {
    // Request for permissions
   logger('requestPermissions function call');
   jetbeepsdkplugin
   .requestPermissions("",
   function(success){
    logger('request permissions: success')
    logger(success)
   },
   function(error){
    logger('request permissions: error')
    logger(error)
   })
}

function enableBeeper() {
    // Enable beeper
   logger('enableBeeper function call');
   jetbeepsdkplugin
   .enableBeeper("",
   function(success){
    logger('enable beeper: success')
    logger(success)
   },
   function(error){
    logger('enable beeper: error')
    logger(error)
   })
}

function initSDK(appNameKey, appToken, serviceUUID){
    logger('initSDK');
    let x = document.getElementById('appNameKey').value
    let y = document.getElementById('appToken').value
    let z = document.getElementById('serviceUUID').value
    console.log(x);
    console.log(y);
    console.log(z);
    const array = [x, y, z]

    logger(array)

    jetbeepsdkplugin.initSDK(
                        [x, y, z],
                        function(success) {
                            logger('initSDK: success');
                            logger(success)
                        },
                        function(error) {
                            logger('initSDK: error');
                            logger(error)
                        }
                        );
}

function getNearbyDevices() {
    // Get nearby devices
   logger('getNearbyDevices function call');
   jetbeepsdkplugin
   .getNearbyDevices("",
   function(success){
    logger('get nearby devices: success')
    logger(success)
   },
   function(error){
    logger('get nearby devices: error')
    logger(error)
   })
}

function getEnteredShops() {
    // Get entered shops
   logger('getEnteredShops function call');
   jetbeepsdkplugin
   .getEnteredShops("",
   function(success){
    logger('get entered shops: success')
    logger(success)
   },
   function(error){
    logger('get entered shops: error')
    logger(error)
   })
}

function subscribeToLocations() {
    // Subscribe to get shop enter/exit
   logger('subscribeToLocations function call');
   jetbeepsdkplugin
   .subscribeToLocations("",
   function(success){
    logger('subscribeToLocations event: success')
    logger(success)
   },
   function(error){
    logger('subscribeToLocations event: error')
    logger(error)
   })
}

function unsubscribeFromLocations() {
    // Subscribe to get shop enter/exit
   logger('subscribeToLocations function call');
   jetbeepsdkplugin
   .getEnteredShops("",
   function(success){
    logger('get shop event:')
    logger(success)
   },
   function(error){
    logger('get shop error: error')
    logger(error)
   })
}


function searchDevices() {
    logger('searchDevices');
    let tokenHex = document.getElementById('tokenHex').value

    logger(tokenHex);

    jetbeepsdkplugin.searchDevices(
                                 [tokenHex],
                                 function(success) {
                                    logger('searchDevices: success');
                                    logger(success)
                                 },
                                 function(error) {
                                    logger('searchDevices: error');
                                    logger(error)
                                 }
                                 );
}

function applyToken() {
    logger('applyToken');
    let tokenHex = document.getElementById('tokenHex').value
    jetbeepsdkplugin.applyToken(tokenHex,
    function(success) {
        logger('applyToken: success');
        logger(success)
    },
    function(error) {
        logger('applyToken: error');
        logger(error)
    });
}

function stopSearching() {
    logger('stopSearching');
    jetbeepsdkplugin.stopSearching("" ,
    function() {
        logger('stopSearching: success');
    },
    function(error) {
        logger('stopSearching: failure');
    });
}


function bluetoothState() {
    logger('bluetoothState');

    jetbeepsdkplugin.bluetoothState("",
    function(success) {
        logger('bluetoothState:');
        logger(success)
    },
    function(error) {
        logger('bluetoothState: error');
        logger(error)
    });
}

function subscribeBluetoothEvents() {
    logger('subscribeBluetoothEvents');

    jetbeepsdkplugin.subscribeBluetoothEvents("",
    function(success) {
        logger('subscribeBluetoothEvents:');
        logger(success)
    },
    function(error) {
        logger('subscribeBluetoothEvents: error');
        logger(error)
    });
}

function unsubscribeBluetoothEvents() {
    logger('unsubscribeBluetoothEvents');

    jetbeepsdkplugin.unsubscribeBluetoothEvents("",
    function(success) {
        logger('unsubscribeBluetoothEvents:');
        logger(success)
    },
    function(error) {
        logger('unsubscribeBluetoothEvents: error');
        logger(error)
    });
}
