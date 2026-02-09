<h1 align="center">
    Welcome to react-native-media-notification👋<br />
</h1>
<h3 align="center">
    Your React Native library for media notifications and controls
</h3>
<p align="center">
    <a href="LICENSE" target="_blank">
      <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge" />
    </a>
    <img alt="Build Status" src="https://img.shields.io/github/actions/workflow/status/mbpictures/react-native-media-notification/ci.yml?style=for-the-badge" />
    <a href="https://badge.fury.io/js/react-native-media-notification">
        <img src="https://img.shields.io/npm/v/react-native-media-notification?style=for-the-badge" alt="npm version">
    </a>
</p>

> A react native package for media notifications and controls, using AndroidX Media3 for Android and the ControlCenter API for iOS.

## Features

- 🎵 Media Notifications with Play/Pause/Stop Controls
- ⏭️ Skip Forward/Backward Support
- 🎨 Album Artwork Support (URL-based)
- 🔊 Audio Interruption Handling
- 📱 iOS Control Center Integration
- 🚗 Apple CarPlay Support
- 🤖 Android Media3 Session Support
- 🎯 TypeScript Support
- ⚡ New Architecture (Turbo Modules) Ready

## Installation

```bash
npm install react-native-media-notification
```

```bash
yarn add react-native-media-notification
```

### iOS

```bash
cd ios && pod install
```

#### Background Audio

Add the `audio` background mode to your `Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>
```

#### CarPlay

To enable Apple CarPlay support, your app must adopt the **UIScene lifecycle** with separate scene delegates for the phone display and CarPlay. The following steps are required:

##### 1. Entitlement

Your app must have the `com.apple.developer.carplay-audio` entitlement. Request this from Apple via the [CarPlay entitlement request form](https://developer.apple.com/contact/carplay/).

Create (or update) your entitlements file (e.g. `YourApp.entitlements`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.carplay-audio</key>
    <true/>
</dict>
</plist>
```

Make sure it is referenced in your Xcode build settings under **Code Signing Entitlements**.

##### 2. Info.plist Scene Configuration

Add the `UIApplicationSceneManifest` to your `Info.plist` with both a phone and CarPlay scene:

```xml
<key>UIApplicationSceneManifest</key>
<dict>
    <key>UIApplicationSupportsMultipleScenes</key>
    <true/>
    <key>UISceneConfigurations</key>
    <dict>
        <key>CPTemplateApplicationSceneSessionRoleApplication</key>
        <array>
            <dict>
                <key>UISceneClassName</key>
                <string>CPTemplateApplicationScene</string>
                <key>UISceneConfigurationName</key>
                <string>CarPlay</string>
                <key>UISceneDelegateClassName</key>
                <string>$(PRODUCT_MODULE_NAME).CarSceneDelegate</string>
            </dict>
        </array>
        <key>UIWindowSceneSessionRoleApplication</key>
        <array>
            <dict>
                <key>UISceneClassName</key>
                <string>UIWindowScene</string>
                <key>UISceneConfigurationName</key>
                <string>Phone</string>
                <key>UISceneDelegateClassName</key>
                <string>$(PRODUCT_MODULE_NAME).PhoneSceneDelegate</string>
            </dict>
        </array>
    </dict>
</dict>
```

##### 3. AppDelegate Scene Routing

Add the `application(_:configurationForConnecting:options:)` method to your `AppDelegate` to route each scene session to the correct delegate:

```swift
func application(
  _ application: UIApplication,
  configurationForConnecting connectingSceneSession: UISceneSession,
  options: UIScene.ConnectionOptions
) -> UISceneConfiguration {
  if connectingSceneSession.role == UISceneSession.Role.carTemplateApplication {
    let scene = UISceneConfiguration(name: "CarPlay", sessionRole: connectingSceneSession.role)
    scene.delegateClass = CarSceneDelegate.self
    return scene
  } else {
    let scene = UISceneConfiguration(name: "Phone", sessionRole: connectingSceneSession.role)
    scene.delegateClass = PhoneSceneDelegate.self
    return scene
  }
}
```

##### 4. PhoneSceneDelegate

Create a `PhoneSceneDelegate.swift` that manages the phone window. With the scene lifecycle, window creation moves from the `AppDelegate` into this scene delegate:

```swift
import Foundation
import UIKit

class PhoneSceneDelegate: UIResponder, UIWindowSceneDelegate {
  var window: UIWindow?

  func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
    if session.role != .windowApplication {
      return
    }

    guard let appDelegate = (UIApplication.shared.delegate as? AppDelegate) else { return }
    guard let windowScene = (scene as? UIWindowScene) else { return }

    let window = UIWindow(windowScene: windowScene)
    window.rootViewController = appDelegate.window?.rootViewController
    self.window = window
    window.makeKeyAndVisible()
  }
}
```

> **Important**: Do not create a new `UIViewController` and assign the React Native root view to it. The root view is already associated with a view controller by `RCTReactNativeFactory`. Reuse the existing `rootViewController` from the AppDelegate's window to avoid a `UIViewControllerHierarchyInconsistency` crash.

##### 5. CarSceneDelegate

Create a `CarSceneDelegate.swift` that bridges to the library's `CarPlaySceneDelegate`:

```swift
import Foundation
import CarPlay
import MediaControls

class CarSceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
  func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController) {
    CarPlaySceneDelegate.connect(with: interfaceController)
  }

  func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didDisconnectInterfaceController interfaceController: CPInterfaceController) {
    CarPlaySceneDelegate.disconnect()
  }
}
```

##### 6. Add Files to Xcode

Make sure both `PhoneSceneDelegate.swift` and `CarSceneDelegate.swift` are added to your Xcode project (right click, "Add Files", and choose "Reference to file" in the dropdown).

### Android

#### Android Auto
To enable Android Auto support, you need to add the following in the application tag of your `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourapp">
    <!--...-->
    <application>
        <!--...-->
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />
    </application>
</manifest>
```

And create the file `android/app/src/main/res/xml/automotive_app_desc.xml` with the following content:

```xml
<automotiveApp>
  <uses name="media"/>
</automotiveApp>
```

#### Customize Appearance

If you want to customize the small media notification icon on Android, you can add the following to your `android/app/src/main/res/values/styles.xml`:

```xml
<resources>
  <!--...-->
  <drawable name="media3_notification_small_icon">@drawable/my_custom_icon</drawable>
</resources>
```

You can also customize all action icons by overwriting the default icon drawables (e.g. `media3_icon_play`, `media3_icon_pause`, ...)

## Usage

### Basic Setup

```typescript
import * as MediaControls from 'react-native-media-notification';

// register event listeners
const removePlayListener = MediaControls.addEventListener('play', () => {
  console.log('Play button pressed');
});

const removePauseListener = MediaControls.addEventListener('pause', () => {
  console.log('Pause button pressed');
});

// Cleanup
useEffect(() => {
  return () => {
    removePlayListener.remove();
    removePauseListener.remove();
  };
}, []);
```

### Update Metadata or Create Media Notification

```typescript
await MediaControls.updateMetadata({
  title: 'Song Title',
  artist: 'Artist Name',
  album: 'Album Name',
  duration: 240, // in seconds
  position: 30,  // current position in seconds
  isPlaying: true,
  artwork: 'https://example.com/artwork.jpg',
  shuffle: false, // optional, default is false
  repeat: 'one',  // optional, default is 'off', can be 'all', 'one', or 'off'
});
```

### All available Events

```typescript
// Playback Controls
MediaControls.addEventListener('play', () => {});
MediaControls.addEventListener('pause', () => {});
MediaControls.addEventListener('stop', () => {});
MediaControls.addEventListener('shuffle', () => {});
MediaControls.addEventListener('repeatMode', () => {});

// Navigation
MediaControls.addEventListener('skipToNext', () => {});
MediaControls.addEventListener('skipToPrevious', () => {});

// Seeking
MediaControls.addEventListener('seekForward', () => {});
MediaControls.addEventListener('seekBackward', () => {});
MediaControls.addEventListener('seek', (data) => {
  console.log('Seek to position:', data?.position);
});

// Interruptions
MediaControls.addEventListener('duck', () => {}); // reduce volume for interruption
MediaControls.addEventListener('unduck', () => {}); // restore volume after interruption
```

**Note**: On iOS, seekForward and seekBackward are not fired, even when technically triggered. Instead, a seek event with the corresponding timestamp is fired.

### Stop Media Notification

```typescript
await MediaControls.stopMediaNotification();
```

## Planned
- Full Android Auto Support (Headless Tasks, Voice Commands, Media Library)
- Add more actions/metadata information and better customization

## API Reference

### Functions

#### `updateMetadata(metadata: MediaTrackMetadata): Promise<void>`

Updates the media track metadata for the notification. When called for the first time, it creates the media notification.

**Parameter:**
- `metadata`: Object with media track information, including title, artist, album, duration, artwork URL, current position, and playback state.

#### `stopMediaNotification(): Promise<void>`

Stops media notification and removes audio focus (if enabled)

#### `enableAudioInterruption(enabled: boolean): Promise<void>`

Enable or disable audio interruption handling. When enabled, the media controls will respond to audio interruptions (like incoming calls) by pausing playback and resuming when the interruption ends.

#### `enableBackgroundMode(enabled: boolean): Promise<void>`

**iOS ONLY**. Enable or disable background mode for iOS.

#### `addEventListener(event: MediaControlEvent, handler: Function): EventSubscription`

Registers an event listener for a specific media control event. Returns a function to remove the listener.

#### `removeAllListeners(event?: MediaControlEvent): void`

Removes all event listeners for an specific event or for all events, when no event has been specified.

### Types

```typescript
interface MediaTrackMetadata {
  title: string;
  artist: string;
  album?: string;
  duration?: number;    // in seconds
  artwork?: string;     // URL for album artwork
  position?: number;    // current position in seconds
  isPlaying?: boolean;
  shuffle?: boolean;    // optional, default is false
  repeat?: 'off' | 'all' | 'one'; // optional, default is
}

type MediaControlEvent =
  | 'play'
  | 'pause'
  | 'stop'
  | 'skipToNext'
  | 'skipToPrevious'
  | 'seekForward'
  | 'seekBackward'
  | 'seek';

type MediaControlEventData = {
  position?: number; // for seek events
};
```

## Platform specific notes

### Android

- Uses AndroidX Media3
- Creates notification channel automatically

### iOS

- Uses MPNowPlayingInfoCenter and MPRemoteCommandCenter
- Integrates in Control Center and Lock Screen
- Supports ear bud and other external controls
- Requires Background Audio Capability for Background Playback
- CarPlay uses CPTabBarTemplate with browsable media library
- CarPlay requires `com.apple.developer.carplay-audio` entitlement and scene configuration in Info.plist

## License

MIT

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.
