package com.jetbeepsdk.plugin;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.jetbeep.JetBeepRegistrationType;
import com.jetbeep.JetBeepSDK;
import com.jetbeep.background.scanner.BleScanner;
import com.jetbeep.connection.locker.DeviceStatusCallback;
import com.jetbeep.connection.locker.LockerDevice;
import com.jetbeep.connection.locker.Lockers;
import com.jetbeep.connection.locker.Token;
import com.jetbeep.connection.locker.TokenResult;
import com.jetbeep.locations.LocationCallbacks;
import com.jetbeep.locations.Locations;
import com.jetbeep.model.entities.Merchant;
import com.jetbeep.model.entities.Shop;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.GlobalScope;

public class JetBeepSDKPlugin extends CordovaPlugin {

    private static final String TAG = "JetBeepSDKPlugin";
    private Lockers lockers = null;
    private CallbackContext devicesCallback = null;

    private enum DeviceStatus {
        DeviceDetected,
        DeviceStateChanged,
        DeviceLost,
    }

    private DeviceStatusCallback lockersListener = new DeviceStatusCallback() {

        @Override
        public void onLockerDeviceStatusChanged(List<LockerDevice> list) {
            log("onLockerDeviceStatusChanged, size = " + list.size());
            if (devicesCallback != null) {
                for (LockerDevice d : list) {
                    devicesCallback.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                            getResultAsString(d, DeviceStatus.DeviceStateChanged)));
                }
            }
        }

        @Override
        public void onLockerDeviceLost(LockerDevice lockerDevice) {
            log("onLockerDeviceLost = " + lockerDevice);
            if (devicesCallback != null) {
                devicesCallback.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                        getResultAsString(lockerDevice, DeviceStatus.DeviceLost)));
            }
        }

        @Override
        public void onLockerDeviceDetected(LockerDevice lockerDevice) {
            log("onLockerDeviceDetected = " + lockerDevice);
            if (devicesCallback != null) {
                devicesCallback.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                        getResultAsString(lockerDevice, DeviceStatus.DeviceDetected)));
            }
        }

        /*
        Response: async callback with device status json:
        {
            "deviceId": String,
            "deviceName": String,
            "isConnactable": String, //"true", "false"
            "status": String, // "DeviceDetected", "DeviceStateChanged", "DeviceLost"
        }
        */
        private JSONObject getResultAsString(LockerDevice lockerDevice, DeviceStatus status) {
            JSONObject result = new JSONObject();
            try {
                result.put("deviceId", String.valueOf(lockerDevice.getDevice().getDeviceId()));
                result.put("deviceName", lockerDevice.getDevice().getShopName());
                result.put("isConnactable",
                        String.valueOf(lockerDevice.getDevice().isConnectable()));
                result.put("status", status.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return result;
        }


    };

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        log("execute action -> " + action);
        switch (action) {
            case "initSDK": {
                initSDK(args.getString(0), callbackContext);
                return true;
            }
            case "searchDevices": {
                searchDevices(args.getString(0), callbackContext);
                return true;
            }
            case "stopSearching": {
                stopSearching(args.getString(0), callbackContext);
                return true;
            }
            case "applyToken": {
                applyToken(args.getString(0), callbackContext);
                return true;
            }
            case "isPermissionGranted": {
                isPermissionGranted(callbackContext);
                return true;
            }
            case "requestPermissions": {
                requestPermissions(callbackContext);
                return true;
            }
            case "enableBeeper": {
                enableBeeper(callbackContext);
                return true;
            }
        }
        return false;
    }

    private void isPermissionGranted(CallbackContext callbackContext) {
        boolean bt = isBluetoothPermissionsGranted(cordova.getContext());
        boolean location = isLocationPermissionsGranted(cordova.getContext());
        JSONObject result = new JSONObject();
        try {
            result.put("isBtReady", bt);
            result.put("isLocationGranted", location);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                    result.toString()));
        } catch (JSONException e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }
    }

    private void requestPermissions(CallbackContext callbackContext) {
        log("requestPermissions");
        requestPermissionsImpl();
    }

    private void enableBeeper(CallbackContext callbackContext) {
        runInUiThread(() -> {
            log("enableBeeper");
            JetBeepSDK sdk = JetBeepSDK.INSTANCE;
            if (isBluetoothPermissionsGranted(cordova.getContext())
                /*&& isLocationPermissionsGranted(cordova.getContext())*/) {
                if (sdk.isInitialized()) {
                    try {
                        if (!sdk.getBackgroundActive()) {
                            sdk.enableBackground();
                        }

                        BleScanner scanner = JetBeepSDK.INSTANCE.getBleScanner();
                        if (!scanner.isForegroundScannerStarted()) {
                            scanner.startForegroundScanner();
                        }

                        Toast.makeText(webView.getContext(), "Beeper enabled", Toast.LENGTH_LONG).show();
                        lockers = sdk.getConnections().getLockers();
                        callbackContext.success();
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                } else {
                    callbackContext.error("Sdk not initialized");
                }
            } else {
                callbackContext.error("No permissions");
            }
        });
    }

    private void initSDK(String msg, CallbackContext callbackContext) {
        if (msg == null || msg.length() == 0) {
            callbackContext.error("Empty message!");
            log("init sdk error: Empty message!");
        } else {
            try {
                JSONArray params = new JSONArray(msg);
                String appName = (String) params.get(0);
                String appToken = (String) params.get(1);
                String serviceUUID = (String) params.get(2);

                //initInUi(serviceUUID, appName, appToken, callbackContext);
                runInUiThread(() -> {
                    Log.d(TAG,
                            "java, init sdk: appName = " + appName + ", appToken = " + appToken + ", " +
                                    "serviceUUID = " + serviceUUID + "thread = " + Thread.currentThread());

                    JetBeepSDK sdk = JetBeepSDK.INSTANCE;
                    sdk.init((Application) webView.getContext().getApplicationContext(),
                            serviceUUID, appName, appToken, JetBeepRegistrationType.ANONYMOUS,
                            false);
                    sdk.getRepository().trySync();
                    //lockers = JetBeepSDK.INSTANCE.getConnections().getLockers();
                    Toast.makeText(webView.getContext(), "Sdk was initialized",
                            Toast.LENGTH_LONG).show();
                    callbackContext.success("SDK initialized successfully");
                    log("Sdk was initialized");
                });

                //callbackContext.success("SDK initialized successfully");
            } catch (JSONException e) {
                e.printStackTrace();
                callbackContext.error("Failed to init sdk");
                log("Failed to init sdk");
            }
        }
    }

    private LocationCallbacks locationCallbacks = new LocationCallbacks() {
        @Override
        public void onMerchantEntered(@NonNull Merchant merchant, @NonNull Shop shop) {
            log("onMerchantEntered " + shop.getName());
        }

        @Override
        public void onMerchantExit(@NonNull Merchant merchant) {
            log("onMerchantExit " + merchant.getName());
        }

        @Override
        public void onShopEntered(@NonNull Shop shop) {
            log("onShopEntered " + shop.getName());
        }

        @Override
        public void onShopExit(@NonNull Shop shop) {
            log("onShopExit " + shop.getName());
        }
    };

    private void searchDevices(String msg, CallbackContext callbackContext) {
        if (msg == null || msg.length() == 0) {
            callbackContext.error("Empty message!");
            log("searchDevices error: Empty message!");
        } else {
            runInUiThread(() -> {
                Locations loc = JetBeepSDK.INSTANCE.getLocations();
                loc.subscribe(locationCallbacks);

                try {
                    JSONArray tokensForSearch = new JSONArray(msg);
                    if (tokensForSearch.length() > 0) {
                        List<Token> tokens = new ArrayList<>();
                        for (int i = 0; i < tokensForSearch.length(); i++) {
                            String tokenString = (String) tokensForSearch.get(i);
                            log("create token from: " + tokenString);
                            Token token = Token.Companion.createToken(tokenString);
                            tokens.add(token);
                        }
                        if (!tokens.isEmpty()) {
                            if (lockers != null) {

                                lockers.subscribe(lockersListener);
                                lockers.startSearch(tokens);
                                devicesCallback = callbackContext;
                                Toast.makeText(webView.getContext(), "searchDevices " + msg,
                                        Toast.LENGTH_LONG).show();
                                //callbackContext.success(msg);
                                PluginResult result = new PluginResult(PluginResult.Status.OK);
                                result.setKeepCallback(true);
                                devicesCallback.sendPluginResult(result);
                            } else {
                                callbackContext.error("Lockers is null!");
                            }
                        } else {
                            callbackContext.error("Tokens is empty");
                        }
                    } else {
                        callbackContext.error("Failed to parse json");
                    }
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    private void stopSearching(String msg, CallbackContext callbackContext) {
        runInUiThread(() -> {
            if (lockers != null) {
                lockers.stopSearch();
                lockers.unsubscribe(lockersListener);
            }
            // TODO remove this
            Locations loc = JetBeepSDK.INSTANCE.getLocations();
            loc.unsubscribe(locationCallbacks);
            //
            cordova.getActivity().runOnUiThread(() -> {
                JetBeepSDK.INSTANCE.getBleScanner().stopForegroundScanner();
            });
            // TODO need to check this
            if (devicesCallback != null) {
                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(false);
                devicesCallback.sendPluginResult(result);
            }
            // TODO end
            devicesCallback = null;
            Toast.makeText(webView.getContext(), "stopSearching " + msg, Toast.LENGTH_LONG).show();
            callbackContext.success(msg);
        });
    }

    private void applyToken(String msg, CallbackContext callbackContext) {
        if (msg == null || msg.length() == 0) {
            callbackContext.error("Empty message!");
        } else {
            new Thread(() -> {
                try {
                    if (lockers != null) {
                        Token token = Token.Companion.createToken(msg);
                        lockers.apply(token, new Continuation<TokenResult>() {
                            @NonNull
                            @Override
                            public CoroutineContext getContext() {
                                return GlobalScope.INSTANCE.getCoroutineContext();
                            }

                            @Override
                            public void resumeWith(@NonNull Object o) {
                                log("###Apply result: " + o);
//                        Toast.makeText(webView.getContext(), "applyToken " + msg, Toast
//                        .LENGTH_LONG).show();
//                        callbackContext.success(msg);
                            }
                        });
                        log("###waitong for apply...");
                    }
                    runInUiThread(() -> {
                        Toast.makeText(webView.getContext(), "applyToken " + msg,
                                Toast.LENGTH_LONG).show();
                    });
                    callbackContext.success(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    log("Error to apply: " + e);
                    callbackContext.error(e.toString());
                }
            }).start();
        }
    }

    private void runInUiThread(Runnable runnable) {
        cordova.getActivity().runOnUiThread(runnable);
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    private boolean isLocationPermissionsGranted(Context context) {
        int permissionStateCoarse = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );
        int permissionStateFine = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        );
        return permissionStateFine == PackageManager.PERMISSION_GRANTED
                || permissionStateCoarse == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isBluetoothPermissionsGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int permissionBtScan = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
            );
            int permissionBtAdvertise = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            );
            int permissionBtConnect = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
            );
            return permissionBtScan == PackageManager.PERMISSION_GRANTED && permissionBtAdvertise == PackageManager.PERMISSION_GRANTED && permissionBtConnect == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermissionsImpl() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        ActivityCompat.requestPermissions(cordova.getActivity(), permissions, 345);
    }
}