package com.jetbeepsdk.plugin;

import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.jetbeep.JetBeepRegistrationType;
import com.jetbeep.JetBeepSDK;
import com.jetbeep.OfflineConfig;
import com.jetbeep.background.LockStatus;
import com.jetbeep.background.UserData;
import com.jetbeep.background.scanner.BleScanner;
import com.jetbeep.connection.locker.DeviceStatusCallback;
import com.jetbeep.connection.locker.LockerDevice;
import com.jetbeep.connection.locker.Lockers;
import com.jetbeep.connection.locker.Token;
import com.jetbeep.connection.locker.TokenResult;
import com.jetbeep.locations.LocationCallbacks;
import com.jetbeep.logger.JBLog;
import com.jetbeep.logger.LogCallback;
import com.jetbeep.logger.LogLine;
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
    private static final int REQUEST_ENABLE_BT = 748;
    private Lockers lockers = null;
    private CallbackContext devicesCallback = null;
    private CallbackContext jsLocationsCallback = null;
    private CallbackContext bluetoothStateCallback = null;
    private CallbackContext loggerCallBack = null;

    private IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

    private enum DeviceStatus {
        DeviceDetected,
        DeviceStateChanged,
        DeviceLost,
        DeviceLockStateChanged,
        None
    }

    private enum LocationsEvents {
        onShopEntered,
        onShopExit,
        onMerchantEntered,
        onMerchantExit,
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        log("execute action -> " + action);
        switch (action) {
            case "initSDK": {
                initSDK(args.getString(0), callbackContext);
                return true;
            }
            case "initWithOfflineConfig": {
                initWithOfflineConfig(args.getString(0), callbackContext);
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
            case "bluetoothState": {
                bluetoothState(callbackContext);
                return true;
            }
            case "subscribeBluetoothEvents": {
                subscribeBluetoothEvents(callbackContext);
                return true;
            }
            case "unsubscribeBluetoothEvents": {
                unsubscribeBluetoothEvents(callbackContext);
                return true;
            }
            case "enableBluetooth": {
                enableBluetooth(callbackContext);
                return true;
            }
            case "subscribeLogEvents": {
                subscribeLogEvents(callbackContext);
                return true;
            }
            case "unsubscribeLogEvents": {
                unsubscribeLogEvents(callbackContext);
                return true;
            }
        }
        return false;
    }

    private final DeviceStatusCallback lockersListener = new DeviceStatusCallback() {

        @Override
        public void onLockerDeviceLockStateChanged(LockerDevice lockerDevice) {
            log("onLockerDeviceLockStateChanged = " + lockerDevice);
            sendLockerDeviceEvent(lockerDevice, DeviceStatus.DeviceLockStateChanged);
        }

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

    private LogCallback jetbeepLoggerListener = new LogCallback() {
        @Override
        public void onLogLine(@NonNull LogLine logLine) {
            String message = logLine.getTag() + ": " + logLine.getMessage();
            PluginResult result = new PluginResult(PluginResult.Status.OK, message);
            result.setKeepCallback(true);
            loggerCallBack.sendPluginResult(result);
        }
    };

    private void bluetoothState(CallbackContext callbackContext) {
        runInUiThread(() -> {
            boolean result = false;
            BluetoothManager bluetoothManager =
                    (BluetoothManager) cordova.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                BluetoothAdapter adapter = bluetoothManager.getAdapter();
                result = adapter != null && adapter.isEnabled();
            }
            sendBluetoothState(callbackContext, false, result);
        });
    }

    private void subscribeBluetoothEvents(CallbackContext callbackContext) {
        runInUiThread(() -> {
            bluetoothStateCallback = callbackContext;
            cordova.getActivity().registerReceiver(bluetoothStateChangeReceiver, filter);
        });
    }

    private void unsubscribeBluetoothEvents(CallbackContext callbackContext) {
        runInUiThread(() -> {
            bluetoothStateCallback = null;
            cordova.getActivity().unregisterReceiver(bluetoothStateChangeReceiver);
            callbackContext.success();
        });
    }

    private void subscribeToLocations(CallbackContext callbackContext) {
        if (!isSdkInitialized(callbackContext)) return;

        log("subscribeToLocations");

        JetBeepSDK.INSTANCE.getLocations().subscribe(locationCallbacks);

        jsLocationsCallback = callbackContext;

        startForegroundScanner();

        /*PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);*/
    }

    private void unsubscribeFromLocations(CallbackContext callbackContext) {
        if (!isSdkInitialized(callbackContext)) return;

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
        if (!isSdkInitialized(callbackContext)) return;

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
        if (!isSdkInitialized(callbackContext)) return;

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
            "status": String, // "DeviceDetected", "DeviceStateChanged", "DeviceLost",
            "userData": String,
            "lockStatuses": String[]
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
            // add user data
            UserData userData = lockerDevice.getDevice().getUserData();
            result.put("userData", userData != null ? userData.utf8() : "");

            // add lock statuses
            List<LockStatus> listOfLockStatuses = lockerDevice.getDevice().getLockStatus();
            JSONArray lockStatuses = new JSONArray();
//              StringBuilder stringBuilder = new StringBuilder();
            if (listOfLockStatuses != null) {
                for (int i = 0; i < listOfLockStatuses.size(); i++) {
                    lockStatuses.put(listOfLockStatuses.get(i).name());
//                      stringBuilder.append(listOfLockStatuses.get(i).name().substring(0, 1));
//                      stringBuilder.append(",");
                }
//                  Toast.makeText(cordova.getContext(), stringBuilder.toString(), Toast.LENGTH_LONG).show();
            }
            result.put("lockStatuses", lockStatuses);
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
        boolean location =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bt || isLocationPermissionsGranted(cordova.getContext());

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

    private void enableBluetooth(CallbackContext callbackContext) {
        runInUiThread(() -> {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(cordova.getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                callbackContext.error("android.permission.BLUETOOTH_CONNECT permission not " +
                        "granted.");
                return;
            }
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.getActivity().startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            callbackContext.success();
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

                    Application app = (Application) webView.getContext().getApplicationContext();
                    sdk.init(app, serviceUUID, appName, appToken,
                            JetBeepRegistrationType.ANONYMOUS, false);
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

    private void initWithOfflineConfig(String msg, CallbackContext callbackContext) {
        if (msg == null || msg.length() == 0) {
            callbackContext.error("Empty message!");
            log("init sdk error: Empty message!");
        } else {
            try {
                JSONArray params = new JSONArray(msg);
                String serviceUUID = (String) params.get(0);
                String jsonConfig = (String) params.get(1);

                //initInUi(serviceUUID, jsonConfig, callbackContext);
                runInUiThread(() -> {
                    log("java, init sdk: serviceUUID = " + serviceUUID + ", jsonConfig = " + jsonConfig);

                    JetBeepSDK sdk = JetBeepSDK.INSTANCE;
                    Application app = (Application) webView.getContext().getApplicationContext();

                    try {
                        OfflineConfig config = OfflineConfig.Companion.fromJson(jsonConfig);
                        sdk.init(app, serviceUUID, config);
//                        sdk.getLogger().setRemoteLogging(true);
                        sdk.getRepository().trySync();
                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error("Failed to init sdk, " + e.getMessage());
                        log("Failed to init sdk");
                        return;
                    }

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
        if (!isSdkInitialized(callbackContext)) return;

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
        if (!isSdkInitialized(callbackContext)) return;

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
        if (!isSdkInitialized(callbackContext)) return;

        if (msg == null || msg.length() == 0) {
            callbackContext.error("Empty message!");
        } else {
            new Thread(() -> {
                try {
                    if (lockers != null) {
                        Token token = Token.Companion.createToken(msg);

                        log("###waiting for apply...");

                        TokenResult result = (TokenResult) lockers.apply(token,
                                new Continuation<TokenResult>() {
                                    @NonNull
                                    @Override
                                    public CoroutineContext getContext() {
                                        return GlobalScope.INSTANCE.getCoroutineContext();
                                    }

                                    @Override
                                    public void resumeWith(@NonNull Object o) {
                                        log("resumeWith: " + o);
                                    }
                                });
                        log("Apply result: " + result);
                        callbackContext.success();
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

    private boolean isSdkInitialized(CallbackContext callbackContext) {
        if (!JetBeepSDK.INSTANCE.isInitialized()) {
            callbackContext.error("Sdk not initialized");
            return false;
        }
        return true;
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

    private void subscribeLogEvents(CallbackContext callbackContext) {
        if (!isSdkInitialized(callbackContext)) return;

        loggerCallBack = callbackContext;
        JBLog logger = JetBeepSDK.INSTANCE.getLogger();
        logger.setRemoteLogging(true);
        logger.subscribe(jetbeepLoggerListener);
    }

    private void unsubscribeLogEvents(CallbackContext callbackContext) {
        if (!isSdkInitialized(callbackContext)) return;

        loggerCallBack = null;
        JetBeepSDK.INSTANCE.getLogger().unsubscribe(jetbeepLoggerListener);
        callbackContext.success();
    }

    private void requestPermissionsImpl() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
//                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        ActivityCompat.requestPermissions(cordova.getActivity(), permissions, 345);
    }

    private BroadcastReceiver bluetoothStateChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    // bt is enabled
                    sendBluetoothState(bluetoothStateCallback, true, true);
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    // bt is disabled
                    sendBluetoothState(bluetoothStateCallback, true, false);
                }
            }
        }
    };

    private void sendBluetoothState(CallbackContext callbackContext, boolean setKeepCallback,
                                    boolean btState) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK,
                    btStateToJson(btState));
            if (setKeepCallback) {
                result.setKeepCallback(true);
            }
            callbackContext.sendPluginResult(result);
        }
    }

    private JSONObject btStateToJson(boolean btState) {
        JSONObject result = new JSONObject();
        try {
            String state = btState ? "enabled" : "disabled";
            result.put("bluetooth", state);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }
}