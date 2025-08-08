# React Native Media Controls

A react native package for media notifications and controls, using AndroidX Media3 for Android and the ControlCenter API for iOS.

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
npm install react-native-media-controls
```

```bash
yarn add react-native-media-controls
```

### iOS

```bash
cd ios && pod install
```

### Android

No additional setup required.

#### Customize Appearance

If you want to customize the small media notification icon on Android, you can add the following to your `android/app/src/main/res/values/styles.xml`:

```xml
<resources>
  <!--...-->
  <drawable name="media3_notification_small_icon">@drawable/my_custom_icon</drawable>
</resources>
```

## Verwendung

### Basic Setup

```typescript
import * as MediaControls from 'react-native-media-controls';

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
  artwork: 'https://example.com/artwork.jpg'
});
```

### All available Events

```typescript
// Playback Controls
MediaControls.addEventListener('play', () => {});
MediaControls.addEventListener('pause', () => {});
MediaControls.addEventListener('stop', () => {});

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

### Stop Media Notification

```typescript
await MediaControls.stopMediaNotification();
```

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

#### `addEventListener(event: MediaControlEvent, handler: Function): EventSubscription`

Registers an event listener for a specific media control event. Returns a function to remove the listener.

#### `removeAllListeners(event?: MediaControlEvent): void`

Entfernt alle Event Listener für ein bestimmtes Event oder alle Events, wenn kein Event angegeben ist.

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
  position?: number; // für seek events
};
```

## Plattform-spezifische Hinweise

### Android

- Uses AndroidX Media3
- Creates notification channel automatically

### iOS

- Uses MPNowPlayingInfoCenter and MPRemoteCommandCenter
- Integrates in Control Center and Lock Screen
- Unterstützt AirPods und andere externe Controls
- Requires Background Audio Capability for Background Playback

## Lizenz

MIT

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.
