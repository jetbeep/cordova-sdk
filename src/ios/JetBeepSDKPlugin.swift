import JetBeepFramework
private extension VendingConnectionState {
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
    func json(for status: JetBeepSDKPlugin.DeviceState) -> String {
        return ["deviceId" : device.deviceId,
                "deviceName": device.shop.name,
                "isConnactable": device.state.contains(.connectable) ? "true": "false",
                "status": status.description].jsonString
    }
}

private extension Shop {
    enum Event: String {
        case entered = "onShopEntered"
        case exit = "onShopExit"
    }

    func json(with event: Event) -> String {
        let dictionary: [String: Any] = ["event": event.rawValue,
                                         "shop": ["shopId": self.id,
                                                  "shopName": self.name.description]]
        return dictionary.jsonString
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

@objc(JetBeepSDKPlugin) class JetBeepSDKPlugin : CDVPlugin {

    enum DeviceState {

        case deviceDetected
        case deviceStateChanged
        case deviceLost

        var description: String {
            switch self {
            case .deviceDetected:
                return "DeviceDetected"
            case .deviceStateChanged:
                return "DeviceStateChanged"
            case .deviceLost:
                return "DeviceLost"
            }
        }
    }



    private var callbackIdentifier = -1
    private var callbackLocationsIdentifier = -1

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
              let tokens = tokensList(with: hexes),
                !tokens.isEmpty else {
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
            case .onLockerDeviceLosted(let device):
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
        let pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: "This functionality triggers automatically at init SDK level."
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
        var pluginResult = CDVPluginResult(
            status: .ok
        )

        Log.d("Subscribe to shop enter/exit")

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
        var pluginResult = CDVPluginResult(
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
        }.jsonString


        var pluginResult = CDVPluginResult(
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

        var pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: deviceArray.jsonString
        )

        Log.d("GetNearbyDevices \(deviceArray.jsonString)")
        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }


    private func tokensList(with hexs: [String]) -> [Token]? {
        return hexs.compactMap { hex in
            if !hex.isEmpty {
              return TokenGenerator.create(hex: hex)
            }
            return nil
        }
    }

}

