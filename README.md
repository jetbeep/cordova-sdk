# Welcome to Jetbeep Corodova plugin

Here you can find Android and iOS Cordova plugins. If you want native SDK you can find it at **Android** [link](https://github.com/jetbeep/android-sdk) and **iOS** [link](https://github.com/jetbeep/ios-sdk).

Install process using **npm**:

Get in into your terminal and call your project folder `cordova plugin add com-jetbeep-plugins-sdk`.

## Android

1. Get in the `jetbeepCordovaDemoApp` folder at your command line.
2. Add Jetbeep plugin `cordova plugin add com-jetbeep-plugins-sdk`
3. Add Android platform `cordova platform add android` if you don't have it.

## iOS integration

After adding a plugin to your project and installing relative Cocoapods

1. Get in the `jetbeepCordovaDemoApp` folder at your command line.
2. Add Jetbeep plugin `cordova plugin add com-jetbeep-plugins-sdk`
3. `cordova plugin add cordova-plugin-add-swift-support`
4. If you don't have the iOS platform `cordova platform add ios`.
5. Go to `platforms\ios`
7. Set to `platform :ios, '13.0'`
8. Open `Podfile` and add at the end of the file:

   ```ruby
   post_install do |installer|
     installer.pods_project.targets.each do |target|
       target.build_configurations.each do |config|
         config.build_settings['BUILD_LIBRARY_FOR_DISTRIBUTION'] = 'YES'
       end
     end
   end
   ```
9. **Save changes**
10. Call `pod update`
11. Don't forget to call `pod update` when you play with Cocoapods in the future.
12. **Open your project and bump the target version to ios 13
    
_Now you are ready to go!_
