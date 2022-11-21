package com.jetbeepsdk.plugin;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.jetbeep.JetBeepRegistrationType;
import com.jetbeep.JetBeepSDK;
import com.jetbeep.background.scanner.BleScanner;
import com.jetbeep.connection.locker.DeviceStatusCallback;
import com.jetbeep.connection.locker.LockerDevice;
import com.jetbeep.connection.locker.Lockers;
import com.jetbeep.connection.locker.Token;
import com.jetbeep.connection.locker.TokenResult;
import com.jetbeep.locations.LocationCallbacks;
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
    private CallbackContext jsLocationsCallback = null;

    private enum DeviceStatus {
        DeviceDetected,
        DeviceStateChanged,
        DeviceLost,
        None
    }

    private enum LocationsEvents {
        onShopEntered,
        onShopExit,
        onMerchantEntered,
        onMerchantExit,
    }

    private final DeviceStatusCallback lockersListener = new DeviceStatusCallback() {

        @Override
        public void onLockerDeviceStatusChanged(List<LockerDevice> list) {
            log("onLockerDeviceStatusChanged = " + list);
            if (devicesCallback != null) {
                for (LockerDevice d : list) {
                    sendLockerDeviceEvent(d, DeviceStatus.DeviceStateChanged);
                }
            }
        }

        @Override
        public void onLockerDeviceLost(LockerDevice lockerDevice) {
            log("onLockerDeviceLost = " + lockerDevice);
            sendLockerDeviceEvent(lockerDevice, DeviceStatus.DeviceLost);
        }

        @Override
        public void onLockerDeviceDetected(LockerDevice lockerDevice) {
            log("onLockerDeviceDetected = " + lockerDevice);
            sendLockerDeviceEvent(lockerDevice, DeviceStatus.DeviceDetected);
        }

    };

    private final LocationCallbacks locationCallbacks = new LocationCallbacks() {

        @Override
        public void onShopExit(@NonNull Shop shop) {
            log("onShopExit: " + shop);
            if (jsLocationsCallback != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
                        onEvent(LocationsEvents.onShopExit, shop));
                pluginResult.setKeepCallback(true);
                jsLocationsCallback.sendPluginResult(pluginResult);
            }
        }

        @Override
        public void onShopEntered(@NonNull Shop shop) {
            log("onShopEntered " + shop);
            if (jsLocationsCallback != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
                        onEvent(LocationsEvents.onShopEntered, shop));
                pluginResult.setKeepCallback(true);
                jsLocationsCallback.sendPluginResult(pluginResult);
            }
        }

        @Override
        public void onMerchantExit(@NonNull Merchant merchant) {
            /*if (jsLocationsCallback != null) {
                jsLocationsCallback.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                        onEvent(LocationsEvents.onMerchantExit, merchant)));
            }*/
        }

        @Override
        public void onMerchantEntered(@NonNull Merchant merchant, @NonNull Shop shop) {
            /*if (jsLocationsCallback != null) {
                jsLocationsCallback.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                        onEvent(LocationsEvents.onMerchantEntered, merchant)));
            }*/
        }

        /* Example of Shop object:
            {
                "event" : String // onShopEntered, onShopExit, onMerchantEntered, onMerchantExit,
                "shop" : {
                    "shopId" : int,
                    "shopName" : String
                }
            }
        */
        private String onEvent(LocationsEvents events, Object obj) {
            JSONObject result = new JSONObject();
            try {
                result.put("event", events.toString());
                if (obj instanceof Shop) {
                    result.put("shop", shopToJson((Shop) obj));
                } else if (obj instanceof Merchant) {
                    result.put("merchant", merchantToJson((Merchant) obj));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        /* Merchant object
            {
                "merchantId" : int,
                "merchantName" : String,
                "merchantImage" : String
            }
        */
        private JSONObject merchantToJson(Merchant merchant) {
            JSONObject result = new JSONObject();
            try {
                result.put("merchantId", merchant.getId());
                result.put("merchantName", merchant.getName());
                result.put("merchantImage", merchant.getImage());
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
            case "subscribeToLocations": {
                subscribeToLocations(callbackContext);
                return true;
            }
            case "unsubscribeFromLocations": {
                unsubscribeFromLocations(callbackContext);
                return true;
            }
            case "getEnteredShops": {
                getEnteredShops(callbackContext);
                return true;
            }
            case "getNearbyDevices": {
                getNearbyDevices(callbackContext);
                return true;
            }
        }
        return false;
    }

    private void subscribeToLocations(CallbackContext callbackContext) {
        log("subscribeToLocations");

        JetBeepSDK.INSTANCE.getLocations().subscribe(locationCallbacks);

        jsLocationsCallback = callbackContext;

        startForegroundScanner();

        /*PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);*/
    }

    private void unsubscribeFromLocations(CallbackContext callbackContext) {
        log("unsubscribeFromLocations");
        JetBeepSDK.INSTANCE.getLocations().unsubscribe(locationCallbacks);

        jsLocationsCallback = null;

        stopForegroundScanner();

        /*PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(false);
        callbackContext.sendPluginResult(result);*/
        callbackContext.success();
    }

    /**
     * returns: json array of Shops
     * [{
     * "shopId" : int,
     * "shopName" : String
     * }]
     */
    private void getEnteredShops(CallbackContext callbackContext) {
        runInUiThread(() -> {
            try {
                List<Shop> shops = JetBeepSDK.INSTANCE.getLocations().getEnteredShops();
                JSONArray jsonShopList = new JSONArray();
                for (Shop s : shops) {
                    jsonShopList.put(shopToJson(s));
                }
                callbackContext.success(jsonShopList);
            } catch (Exception e) {
                e.printStackTrace();
                callbackContext.error(e.getMessage());
            }
        });
    }

    private void getNearbyDevices(CallbackContext callbackContext) {
        runInUiThread(() -> {
            try {
                List<LockerDevice> devices =
                        JetBeepSDK.INSTANCE.getConnections().getLockers().getVisibleDevices();
                log("NearbyDevices: " + devices);
                JSONArray result = new JSONArray();
                for (LockerDevice device : devices) {
                    result.put(lockerDeviceToJson(device, DeviceStatus.None));
                }
                callbackContext.success(result);
            } catch (Exception e) {
                e.printStackTrace();
                callbackContext.error(e.getMessage());
            }
        });
    }

    private void sendLockerDeviceEvent(LockerDevice lockerDevice, DeviceStatus deviceStatus) {
        if (devicesCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK,
                    lockerDeviceToJson(lockerDevice, deviceStatus));
            result.setKeepCallback(true);
            devicesCallback.sendPluginResult(result);
        }
    }

    /*
        Response: async callback with device status json:
        {
            "deviceId": String,
            "deviceName": String,
            "isConnectable": String, //"true", "false"
            "status": String, // "DeviceDetected", "DeviceStateChanged", "DeviceLost"
        }
        */
    private JSONObject lockerDeviceToJson(LockerDevice lockerDevice, DeviceStatus status) {
        JSONObject result = new JSONObject();
        try {
            result.put("deviceId", String.valueOf(lockerDevice.getDevice().getDeviceId()));
            result.put("deviceName", lockerDevice.getDevice().getShopName());
            result.put("isConnectable",
                    String.valueOf(lockerDevice.getDevice().isConnectable()));
            if (status != DeviceStatus.None) {
                result.put("status", status.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    private JSONObject shopToJson(Shop shop) {
        JSONObject result = new JSONObject();
        try {
            result.put("shopId", shop.getId());
            result.put("shopName", shop.getName());
//                result.put("merchantId", shop.getMerchantId());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void isPermissionGranted(CallbackContext callbackContext) {
        boolean bt = isBluetoothPermissionsGranted(cordova.getContext());
        boolean location = isLocationPermissionsGranted(cordova.getContext());

        log("isPermissionGranted, bt = " + bt + ", location = " + location);

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
        log("enableBeeper");
        runInUiThread(() -> {
            JetBeepSDK sdk = JetBeepSDK.INSTANCE;

            /*sdk.getRepository().getShops().getAllAsLiveData().observeForever(shops -> {
                log("$$$SHOPS: " + shops);
            });*/
            if (isBluetoothPermissionsGranted(cordova.getContext())
                /*&& isLocationPermissionsGranted(cordova.getContext())*/) {
                if (sdk.isInitialized()) {
                    try {
                        if (!sdk.getBackgroundActive()) {
                            sdk.enableBackground();
                        }

                        //startForegroundScanner();

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
                    log("java, init sdk: appName = " + appName + ", appToken = " + appToken + ", " +
                            "serviceUUID = " + serviceUUID);

                    JetBeepSDK sdk = JetBeepSDK.INSTANCE;

                    sdk.init((Application) webView.getContext().getApplicationContext(),
                            serviceUUID, appName, appToken, JetBeepRegistrationType.ANONYMOUS,
                            false);
                    sdk.getRepository().trySync();

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

    private void searchDevices(String msg, CallbackContext callbackContext) {
        if (msg == null || msg.length() == 0) {
            callbackContext.error("Empty message!");
            log("searchDevices error: Empty message!");
        } else {
            runInUiThread(() -> {
                try {
                    JSONArray tokensForSearch = new JSONArray(msg);
                    List<Token> tokens = null;
                    if (tokensForSearch.length() > 0) {
                        for (int i = 0; i < tokensForSearch.length(); i++) {
                            String tokenString = (String) tokensForSearch.get(i);
                            if (tokenString.isEmpty()) {
                                continue;
                            }
                            log("create token from: " + tokenString);
                            Token token = Token.Companion.createToken(tokenString);
                            if (tokens == null) {
                                tokens = new ArrayList<>();
                            }
                            tokens.add(token);
                        }
                    }

                    if (lockers == null) {
                        callbackContext.error("Lockers is null!");
                        return;
                    }

                    lockers.subscribe(lockersListener);

                    startForegroundScanner();

                    devicesCallback = callbackContext;

                    // send existing result
                    List<LockerDevice> devices =
                            JetBeepSDK.INSTANCE.getConnections().getLockers().getVisibleDevices();
                    for (LockerDevice device : devices) {
                        sendLockerDeviceEvent(device, DeviceStatus.DeviceDetected);
                    }

                    lockers.startSearch(tokens);
                    log("search started");

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

            // TODO need to check this
            if (devicesCallback != null) {
                // TODO status NO_RESULT
                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(false);
                devicesCallback.sendPluginResult(result);
            }
            // TODO end

            devicesCallback = null;

            stopForegroundScanner();

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

                        log("###waitong for apply...");

                        lockers.apply(token, new Continuation<TokenResult>() {
                            @NonNull
                            @Override
                            public CoroutineContext getContext() {
                                return GlobalScope.INSTANCE.getCoroutineContext();
                            }

                            @Override
                            public void resumeWith(@NonNull Object o) {
                                log("###Apply result: " + o);
                                callbackContext.success();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    log("Error to apply: " + e);
                    callbackContext.error(e.toString());
                }
            }).start();
        }
    }

    private void startForegroundScanner() {
        runInUiThread(() -> {
            BleScanner scanner = JetBeepSDK.INSTANCE.getBleScanner();
            if (!scanner.isForegroundScannerStarted()) {
                scanner.startForegroundScanner();
            }
        });
    }

    private void stopForegroundScanner() {
        if (devicesCallback == null && jsLocationsCallback == null) {
            runInUiThread(() -> JetBeepSDK.INSTANCE.getBleScanner().stopForegroundScanner());
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