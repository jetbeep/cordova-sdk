# Welcome to Jetbeep Corodova plugin

Here you can find Android and iOS Cordova plugin. If you want native SDK's you can find them at **Android** [link](https://github.com/jetbeep/android-sdk) and **iOS** [link](https://github.com/jetbeep/ios-sdk).

Install process using **npm**:

Get in into your terminal and call at your project folder `cordova plugin add com-jetbeep-plugins-sdk`.

## Android

1. Get in at `jetbeepCordovaDemoApp` folder at your command line.
2. Add Jetbeep plugin `cordova plugin add com-jetbeep-plugins-sdk`
3. Add Android platform `cordova platform add android` if you don't have it.

## iOS integration

After adding plugin into your project and installing relative Cocoapods

1. Get in at `jetbeepCordovaDemoApp` folder at your command line.
2. Add Jetbeep plugin `cordova plugin add com-jetbeep-plugins-sdk`
3. Add iOS platform `cordova platform add ios` if you don't have it.
4. Go at `platforms\ios`
5. Open `Podfile` and add in the end of file:

   ```ruby
   post_install do |installer|
     installer.pods_project.targets.each do |target|
       target.build_configurations.each do |config|
         config.build_settings['BUILD_LIBRARY_FOR_DISTRIBUTION'] = 'YES'
       end
     end
   end
   ```

6. **Save changes**
7. Call `pod update`
8. Don't for get to call `pod update` when you play with Cocoapods in future.

_Now you are ready to go!_