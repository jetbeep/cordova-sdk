import JetBeepFramework
import CoreBluetooth
import Foundation
import Promises


private extension Device.ConnectionState {
    var description: String {
        switch self {
        case .connected:
            return "connected"
        case .connecting:
            return "connecting"
        case .notConnected:
            return "notConnected"
        }
    }
}

private extension LockerDevice {
    func json(for status: JetBeepSDKPlugin.DeviceState) -> [String: Any] {
        return ["deviceId" : device.deviceId,
                "deviceName": device.shop.name,
                "isConnectable": device.state.contains(.connectable) ? "true": "false",
                "status": status.description,
                "userData": device.userData?.utf8() ?? "",
                "lockStatuses": device.lockers?.lockStatusesLiteral ?? []
        ]
    }
}

private extension Shop {
    enum Event: String {
        case entered = "onShopEntered"
        case exit = "onShopExit"
    }

    func json(with event: Event) -> [String: Any] {
        return ["event": event.rawValue,
                "shop": ["shopId": self.id,
                         "shopName": self.name.description]]
    }
}

private extension Dictionary where Self == [String: Any] {
    var jsonString: String {
        guard let jsonData = try? JSONSerialization.data(withJSONObject: self, options: []) else {
            return ""
        }

        guard let jsonString = String(data: jsonData, encoding: String.Encoding.utf8) else {
            return ""
        }

        return jsonString
    }
}

extension Array where Element == [String: Any] {
    var jsonString: String {
        guard let jsonData = try? JSONSerialization.data(withJSONObject: self, options: []) else {
            return ""
        }

        guard let jsonString = String(data: jsonData, encoding: String.Encoding.utf8) else {
            return ""
        }

        return jsonString
    }
}

enum BluetoothEvent: String {
    case enabled
    case disabled
}

typealias BluetoothEventCallback = (BluetoothEvent) -> ()

class BluetoothNotifier: NSObject {
    private var lastCallbackId = 0
    private var bluetoothCallbacks = [Int: BluetoothEventCallback]()

    fileprivate var bluetoothSubscribe: CDVInvokedUrlCommand?
    fileprivate var lockersSubscribe: CDVInvokedUrlCommand?
    fileprivate var locationsSubscribe: CDVInvokedUrlCommand?

    public func subscribe(_ callback: @escaping BluetoothEventCallback) -> Int {
        lastCallbackId += 1
        bluetoothCallbacks[lastCallbackId] = callback
        return lastCallbackId
    }

    public func unsubscribe(_ id: Int) {
        bluetoothCallbacks.removeValue(forKey: id)
    }

    func notify(event: BluetoothEvent) {
        DispatchQueue.main.async {
            for i in self.bluetoothCallbacks {
                i.value(event)
            }
        }
    }
}

class BluetoothController: BluetoothNotifier {
    static let shared = BluetoothController()
    private var bluetoothManager: CBCentralManager!

    private override init() {
        super.init()

        bluetoothManager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }

    func bluetoothStatus() -> Promise<BluetoothEvent> {

        return Promise { [self] in
            _ = bluetoothManager.state
        }
        .delay(0.33)
        .then {
            return Promise { [self] in

                if self.bluetoothManager.state == .poweredOn {
                    return Promise(BluetoothEvent.enabled)
                }
                return Promise(BluetoothEvent.disabled)
            }
        }

    }

}

extension BluetoothController: CBCentralManagerDelegate {

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {

        Log.i("New state available \( central.state)")
        switch central.state {
        case .poweredOn:
            return notify(event: .enabled)
        case .poweredOff, .resetting, .unauthorized, .unsupported:
            return notify(event: .disabled)
        case .unknown:
            return notify(event: .disabled)
        }
    }

}


@objc(JetBeepSDKPlugin) class JetBeepSDKPlugin : CDVPlugin {

    enum DeviceState {

        case deviceDetected
        case deviceStateChanged
        case deviceLockStateChanged
        case deviceLost

        var description: String {
            switch self {
            case .deviceDetected:
                return "DeviceDetected"
            case .deviceStateChanged:
                return "DeviceStateChanged"
            case .deviceLockStateChanged:
                return "DeviceLockStateChanged"
            case .deviceLost:
                return "DeviceLost"
            }
        }
    }

    private var callbackIdentifier = -1
    private var callbackLocationsIdentifier = -1
    private var callbackBluetoothIdentifier = -1

    @objc(initSDK:)
    func initSDK(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: .error
        )
        Log.d("SDK init parameters set \(command.arguments[0])")

        guard let inputArray = command.arguments[0] as? [String],
              inputArray.count == 3  else {
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Number of input config parameters are wrong! It should be 3 of them"
            )
            Log.d("Number of input config parameters are wrong! It should be 3 of them")
            return
        }

        let appNameKey = inputArray[0]
        let appToken = inputArray[1]
        let serviceUUID = inputArray[2]

        Log.isLoggingEnabled = true
        Log.setupTransferLogsFlow(.shake)

        JetBeep.shared.serverType = .production
        JetBeep.shared.registrationType = .anonymous
        JetBeep.shared.setup(appName: appNameKey, appTokenKey: appToken)
        JetBeep.shared.serviceUUID = "0" + serviceUUID


        JetBeep.shared.sync()
            .then { _ in
                Log.d("cached successfully")
            }.catch { e in
                pluginResult = CDVPluginResult(
                    status: .error,
                    messageAs: "unable to cache: \(e)"
                )
                Log.d("unable to cache: \(e)")
            }

        do {
            try JBBeeper.shared.start()
        } catch {
            print(error)
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Beeper start with error: \(error)"
            )
            Log.d("Beeper start with error: \(error)")
        }

        pluginResult = CDVPluginResult(
            status: .ok
        )

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }


    @objc(initWithOfflineConfig:)
    func initWithOfflineConfig(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: .error
        )
        Log.d("SDK init parameters set \(command.arguments[0])")

        guard let inputArray = command.arguments[0] as? [String],
              inputArray.count == 2  else {
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Number of input config parameters are wrong! It should be 3 of them"
            )
            Log.d("Number of input config parameters are wrong! It should be 3 of them")
            return
        }

        let serviceUUID = inputArray[0]
        let json = inputArray[1]


        Log.isLoggingEnabled = true

        JetBeep.shared.serviceUUID = "0" + serviceUUID

        do {
            let config = try OfflineConfig.fromJSON(json)
            JetBeep.shared.offlineConfig = config
        } catch {
            print("Error \(error)")
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: error.localizedDescription
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
            return
        }


        JetBeep.shared.sync()
            .then { _ in
                Log.d("cached successfully")
            }.catch { e in
                pluginResult = CDVPluginResult(
                    status: .error,
                    messageAs: "unable to cache: \(e)"
                )
                Log.d("unable to cache: \(e)")
            }

        do {
            try JBBeeper.shared.start()
        } catch {
            print(error)
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Beeper start with error: \(error)"
            )
            Log.d("Beeper start with error: \(error)")
        }

        pluginResult = CDVPluginResult(
            status: .ok
        )

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(applyToken:)
    func applyToken(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: .error
        )
        Log.d("Provided token value\(command.arguments[0])")

        guard let tokenHex = command.arguments[0] as? String,
              let token = TokenGenerator.create(hex: tokenHex)
        else {
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Provided token parameter is wrong!"
            )

            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
            return
        }

        LockersController.shared.apply(token).then { tokenResult in
            pluginResult = CDVPluginResult(
                status: .ok,
                messageAsArrayBuffer: tokenResult.result)

            Log.d("Get token result: success!")

            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId)

        }.catch { error in
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: error.localizedDescription)
            Log.d("Get token result: error - \(error.localizedDescription)!")
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }
    }

    @objc(searchDevices:)
    func searchDevices(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: .error,
            messageAs: "Invalid tokens"
        )
        Log.d("Search devices values: \(command.arguments[0])")

        guard let hexes = command.arguments[0] as? [String],
              let tokens = tokensList(with: hexes)
        else {
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Input search devices parameters are wrong!"
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
            return
        }

        clearLockersSubscriber()

        BluetoothController.shared.lockersSubscribe = command

        LockersController.shared.startSearch(for: tokens)

        callbackIdentifier = LockersController.shared.subscribe { event in
            switch event {
            case .onLockerDeviceDetected(let device):
                Log.i("onLockerDeviceDetected \(device)")
                pluginResult = CDVPluginResult(
                    status: .ok,
                    messageAs: device.json(for: .deviceDetected)
                )
            case .onLockerDeviceStateChanged(let device):
                Log.i("onLockerDeviceStateChanged \(device)")
                pluginResult = CDVPluginResult(
                    status: .ok,
                    messageAs: device.json(for: .deviceStateChanged)
                )
            case .onLockerDeviceLockStateChanged(let device):
                Log.i("onLockerDeviceLockStateChanged \(device)")
                pluginResult = CDVPluginResult(
                    status: .ok,
                    messageAs: device.json(for: .deviceLockStateChanged)
                )
            case .onLockerDeviceLost(let device):
                Log.i("onLockerDeviceLost \(device)")
                pluginResult = CDVPluginResult(
                    status: .ok,
                    messageAs: device.json(for: .deviceLost)
                )
            }

            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }
    }

    @objc(stopSearching:)
    func stopSearching(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: .error
        )

        clearLockersSubscriber()

        if LockersController.shared.stopSearch() {
            pluginResult = CDVPluginResult(
                status: .ok
            )
            Log.d("Stop search finish with success")
        } else {
            pluginResult = CDVPluginResult(
                status: .error,
                messageAs: "Stop search finish with error"
            )
            Log.d("Stop search finish with error")
        }

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }


    @objc(isPermissionGranted:)
    func isPermissionGranted(command: CDVInvokedUrlCommand) {
        let dictionary: [String: Any] = ["isBtReady": true,
                                         "isLocationGranted": true]
        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: dictionary.jsonString
        )

        Log.d("Permission Granted action is fired")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(requestPermissions:)
    func requestPermissions(command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: "This functionality triggers automatically at init SDK level."
        )

        Log.d("Request permissions action is fired")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }


    @objc(enableBeeper:)
    func enableBeeper(command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: "Locations and devices search started."
        )

        JBLocations.shared.startMonitoringFlow(.bluetooth)
        VendingController.shared.start()

        Log.d("EnableBeeper fired")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(subscribeToLocations:)
    func subscribeToLocations(command: CDVInvokedUrlCommand) {

        clearLocationsSubscriber()
        var pluginResult = CDVPluginResult(
            status: .ok
        )

        Log.d("Subscribe to shop enter/exit")

        BluetoothController.shared.locationsSubscribe = command

        callbackLocationsIdentifier = JBLocations.shared.subscribe { event in
            switch event {
            case .merchantEntered, .merchantExited:
                break
            case .shopEntered(let shop, _):
                Log.d("entered at shop: \(shop.json(with: .entered))")
                pluginResult = CDVPluginResult(status: .ok,
                                               messageAs: shop.json(with: .entered))
            case .shopExited(let shop, _):
                Log.d("exit from shop: \(shop.json(with: .exit))")
                pluginResult = CDVPluginResult(status: .ok,
                                               messageAs: shop.json(with: .exit))
            }

            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }

    }


    @objc(unsubscribeFromLocations:)
    func unsubscribeFromLocations(command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(
            status: .ok
        )
        Log.d("Unsubscribe from shop enter/exit events listiner")
        JBLocations.shared.unsubscribe(callbackLocationsIdentifier)

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(getEnteredShops:)
    func getEnteredShops(command: CDVInvokedUrlCommand) {
        Log.d("Get entered shops fired")
        let json = JBLocations.shared.enteredShops.map { shop in
            return ["shopId" : shop.id,
                    "shopName" : shop.name]
        }


        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: json
        )

        Log.d("Get entered shops list \(json)")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(getNearbyDevices:)
    func getNearbyDevices(command: CDVInvokedUrlCommand) {
        Log.d("GetNearbyDevices fired")
        let deviceArray = VendingController.shared.devices.map { device in
            let isConnectable = device.state.contains(.connectable) ? "true" : "false"
            return ["deviceId" : device.deviceId,
                    "deviceName" : device.shop.name,
                    "isConnectable": isConnectable]
        }

        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: deviceArray
        )

        Log.d("GetNearbyDevices \(deviceArray.jsonString)")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(bluetoothState:)
    func bluetoothState(command: CDVInvokedUrlCommand) {
        Log.d("Bluetooth state fired")
        fireBluetoothState(command: command)
    }


    @objc(subscribeBluetoothEvents:)
    func subscribeBluetoothEvents(command: CDVInvokedUrlCommand) {
        Log.d("Subscribe to bluetooth on/off")

        clearBluetoothSubscriber()

        BluetoothController.shared.bluetoothSubscribe = command

        fireBluetoothState(command: command, keepAlive: true)

        callbackBluetoothIdentifier = BluetoothController.shared.subscribe { event in
            let dictionary: [String: Any] = ["bluetooth": event.rawValue]

            let pluginResult = CDVPluginResult(
                status: .ok,
                messageAs: dictionary
            )

            pluginResult?.setKeepCallbackAs(true)

            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }
    }

    private func fireBluetoothState(command: CDVInvokedUrlCommand, keepAlive: Bool = false) {
        BluetoothController.shared
            .bluetoothStatus()
            .then { state in
                let dictionary: [String: Any] = ["bluetooth": state.rawValue]

                let pluginResult = CDVPluginResult(
                    status: .ok,
                    messageAs: dictionary
                )

                Log.d("Bluetooth state \(dictionary.jsonString)")

                pluginResult?.setKeepCallbackAs(keepAlive)

                self.commandDelegate!.send(
                    pluginResult,
                    callbackId: command.callbackId
                )
            }

    }


    @objc(unsubscribeBluetoothEvents:)
    func unsubscribeBluetoothEvents(command: CDVInvokedUrlCommand) {

        clearBluetoothSubscriber()

        let pluginResult = CDVPluginResult(
            status: .ok
        )

        Log.d("Unsubscribe from bluetooth on/off events listiner")

        BluetoothController.shared.unsubscribe(callbackBluetoothIdentifier)

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    private func clearBluetoothSubscriber() {
        guard let bluetoothSubscribe = BluetoothController.shared.bluetoothSubscribe else {
            return
        }
        let pluginResult = CDVPluginResult(
            status: .noResult
        )

        pluginResult?.setKeepCallbackAs(false)

        self.commandDelegate!.send(
            pluginResult,
            callbackId: bluetoothSubscribe.callbackId
        )

        BluetoothController.shared.bluetoothSubscribe = nil
    }

    private func clearLockersSubscriber() {
        guard let lockersSubscribe = BluetoothController.shared.lockersSubscribe else {
            return
        }
        let pluginResult = CDVPluginResult(
            status: .noResult
        )

        pluginResult?.setKeepCallbackAs(false)

        self.commandDelegate!.send(
            pluginResult,
            callbackId: lockersSubscribe.callbackId
        )

        BluetoothController.shared.lockersSubscribe = nil
    }

    private func clearLocationsSubscriber() {
        guard let locationsSubscribe = BluetoothController.shared.locationsSubscribe else {
            return
        }
        let pluginResult = CDVPluginResult(
            status: .noResult
        )

        pluginResult?.setKeepCallbackAs(false)

        self.commandDelegate!.send(
            pluginResult,
            callbackId: locationsSubscribe.callbackId
        )

        BluetoothController.shared.locationsSubscribe = nil
    }


    private func tokensList(with hexs: [String]) -> [Token]? {
        return hexs.compactMap { hex in
            if !hex.isEmpty {
                return TokenGenerator.create(hex: hex)
            }
            return nil
        }
    }


    @objc(subscribeLogEvents:)
    func subscribeLogEvents(command: CDVInvokedUrlCommand) {

        Log.d("Subscribe Log Events")

        var pluginResult = CDVPluginResult(
            status: .ok
        )

        Log.logCompletion = { value in
            pluginResult = CDVPluginResult(status: .ok,
                                           messageAs: value)
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }
    }

    @objc(unsubscribeLogEvents:)
    func unsubscribeLogEvents(command: CDVInvokedUrlCommand) {

        let pluginResult = CDVPluginResult(
            status: .ok
        )

        Log.logCompletion = nil

        pluginResult?.setKeepCallbackAs(false)
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(gpsState:)
    func gpsState(command: CDVInvokedUrlCommand) {
        let dictionary: [String: Any] = ["isGpsEnabled": "enabled"]

        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: dictionary
        )

        Log.d("GPS state action is fired")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(subscribeGpsEvents:)
    func subscribeGpsEvents(command: CDVInvokedUrlCommand) {
        let dictionary: [String: Any] = ["isGpsEnabled": "enabled"]

        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: dictionary
        )

        Log.d("Subscribe GPS events state action is fired")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(unsubscribeGpsEvents:)
    func unsubscribeGpsEvents(command: CDVInvokedUrlCommand) {

        let pluginResult = CDVPluginResult(
            status: .ok
        )

        Log.d("Subscribe GPS events state action is fired")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }
}
