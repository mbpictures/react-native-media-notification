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

## License

MIT

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.
