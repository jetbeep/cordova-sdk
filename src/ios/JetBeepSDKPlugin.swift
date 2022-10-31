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
    @objc(initSDK:)
    func initSDK(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR
        )
        print("Just want to check is it work properly \(command.arguments[0])")

        guard let inputArray = command.arguments[0] as? [String],
              inputArray.count == 3  else {

            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: "Input config parameters are wrong!"
            )
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
                    status: CDVCommandStatus_ERROR,
                    messageAs: "unable to cache: \(e)"
                )
            }

        do {
            try JBBeeper.shared.start()
        } catch {
            print(error)
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: "Beeper start error: \(error)"
            )
        }

        JBLocations.shared.startMonitoringFlow(.bluetooth)

        pluginResult = CDVPluginResult(
            status: CDVCommandStatus_OK
        )

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }

    @objc(applyToken:)
    func applyToken(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR
        )
        print("Just want to check is it work properly \(command.arguments[0])")

        guard let tokenHex = command.arguments[0] as? String,
              let token = TokenGenerator.create(hex: tokenHex)
        else {
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: "Input token parameter is wrong!"
            )

            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
            return
        }

        LockersController.shared.apply(token).then { tokenResult in
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAsArrayBuffer: tokenResult.result)

            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }.catch { error in
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: error.localizedDescription)

            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }




    }

    @objc(searchDevices:)
    func searchDevices(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR
        )
        print("Just want to check is it work properly \(command.arguments[0])")

        guard let hexes = command.arguments[0] as? [String] else {
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: "Input config parameters are wrong!"
            )
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
            return
        }

        let tokens = tokensList(with: hexes)

        LockersController.shared.startSearch(for: tokens)

        callbackIdentifier = LockersController.shared.subscribe { event in
            switch event {
            case .onLockerDeviceDetected(let device):
                Log.i("onLockerDeviceDetected \(device)")
                pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_OK,
                    messageAs: device.json(for: .deviceDetected)
                )
            case .onLockerDeviceStateChanged(let device):
                Log.i("onLockerDeviceStateChanged \(device)")
                pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_OK,
                    messageAs: device.json(for: .deviceStateChanged)
                )
            case .onLockerDeviceLosted(let device):
                Log.i("onLockerDeviceLost \(device)")
                pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_OK,
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
            status: CDVCommandStatus_ERROR
        )

        if LockersController.shared.stopSearch() {
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK
            )
            print("Stop search success")
        }

        self.commandDelegate!.send(
            pluginResult,
            callbackId: command.callbackId
        )
    }


    private func tokensList(with hexs: [String]) -> [Token] {
        return hexs.compactMap { hex in
            TokenGenerator.create(hex: hex)
        }
    }

}

private extension LockerDevice {
    func json(for status: JetBeepSDKPlugin.DeviceState) -> String {
        let dictionary =
        ["deviceId" : device.deviceId.description,
         "deviceName": device.shop.name,
         "isConnactable": device.state.contains(.connectable) ? "true": "false",
         "status": status.description]

        guard let jsonData = try? JSONSerialization.data(withJSONObject: dictionary, options: []) else {
            return ""
        }

        guard let jsonString = String(data: jsonData, encoding: String.Encoding.utf8) else {
            return ""
        }

        return jsonString
    }
}
