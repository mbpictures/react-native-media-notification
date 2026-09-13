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
- 🚗 Android Auto integration
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
MediaControls.addEventListener('skipToQueueItem', (data) => {
  console.log('Queue entry selected:', data?.queueIndex, data?.mediaId);
});

// Seeking
MediaControls.addEventListener('seekForward', () => {});
MediaControls.addEventListener('seekBackward', () => {});
MediaControls.addEventListener('seek', (data) => {
  console.log('Seek to position:', data?.position);
});

// Interruptions
MediaControls.addEventListener('duck', () => {}); // reduce volume for interruption
MediaControls.addEventListener('unduck', () => {}); // restore volume after interruption

// Android Auto (Android only)
MediaControls.addEventListener('carConnected', () => {}); // head unit attached
MediaControls.addEventListener('carDisconnected', () => {}); // head unit gone
```

**Note**: On iOS, seekForward and seekBackward are not fired, even when technically triggered. Instead, a seek event with the corresponding timestamp is fired.

### Stop Media Notification

```typescript
await MediaControls.stopMediaNotification();
```

### Custom Buttons (Android Auto burger menu)

Register additional buttons that appear in the Android Auto overflow ("burger") menu.
When pressed, an event with `command === button.eventId` is emitted, so you can listen
for it through `addEventListener` (or your foreground/background event handler).

```typescript
MediaControls.setCustomButtons([
  { eventId: 'like',    icon: 'ic_thumb_up',   displayName: 'Like' },
  { eventId: 'dislike', icon: 'ic_thumb_down', displayName: 'Dislike' },
]);

const sub = MediaControls.addEventListener('like', () => {
  console.log('Like pressed from Android Auto');
});

// Pass an empty array to clear previously registered buttons:
// MediaControls.setCustomButtons([]);
```

#### Configuring icons (Android)

`icon` is the **name of an Android drawable** that exists in your app. Place the icon
under `android/app/src/main/res/drawable*/` (e.g. as a vector or PNG):

```
android/app/src/main/res/
├── drawable/ic_thumb_up.xml         # vector drawable
├── drawable-mdpi/ic_thumb_down.png  # or rasterized variants
├── drawable-hdpi/ic_thumb_down.png
└── ...
```

Then reference it by name (without extension or `@drawable/` prefix):

```typescript
{ eventId: 'like', icon: 'ic_thumb_up', displayName: 'Like' }
```

Recommendations:
- Use a **white, monochrome vector drawable** sized 24dp × 24dp — Android Auto tints
  the icon to match its theme.
- If the drawable cannot be resolved at runtime the button is silently skipped, so
  double-check the resource name.

> **iOS:** `setCustomButtons` is currently a no-op on iOS.

### Playback Queue (Android Auto)

Publish the tracks around the current one - already played and upcoming - so Android
Auto can show them on its queue screen.
Selecting an entry emits `skipToQueueItem`; your player decides what actually plays.

```typescript
MediaControls.setQueue(
  tracks.map((track) => ({
    id: track.id, // must match the `id` passed to updateMetadata
    title: track.title,
    artist: track.artist,
    artwork: track.artwork,
    duration: track.duration,
  })),
  currentIndex,
  'Queue' // optional title of the queue screen
);

MediaControls.addEventListener('skipToQueueItem', ({ queueIndex }) => {
  player.skipTo(queueIndex);
});

// Keep calling updateMetadata({ id, ... }) as usual; the queue entry with the
// same id is shown as playing.
```

Notes:
- Pass the queue in the order it will actually play (already shuffled, if shuffle is on).
- Call `setQueue` again whenever the queue changes; it does not need to be repeated on
  every track change as long as the new track is part of the queue.
- If the same track is queued more than once, give each entry a distinct `queueId`.
- If the current track (by `id`) is not in the queue, only the current track is shown.
- Pass `[]` to clear the queue.

> **iOS:** `setQueue` is currently a no-op on iOS.

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

#### `setCustomButtons(buttons: CustomButton[]): void`

**Android only.** Registers extra buttons that appear in the Android Auto overflow
("burger") menu. Pressing one emits an event with `command === button.eventId` —
listen for it via `addEventListener(eventId, handler)`. Pass `[]` to clear.

#### `setQueue(items: QueueItem[], currentIndex?: number, title?: string): void`

**Android only.** Publishes the playback queue to the Android Auto queue screen.
The entry whose `id` matches the current track's metadata `id` is shown as playing;
`currentIndex` picks between duplicates. Selecting an entry emits `skipToQueueItem`
with `queueIndex` and `mediaId`. Pass `[]` to clear.

#### `isCarConnected(): boolean`

**Android only** (always `false` on iOS). Whether Android Auto is attached right
now. Unlike the `carConnected` / `carDisconnected` events, which only report the
transition, this can be asked at any time, including when the app was launched
with a car already connected.

#### `addEventListener(event: MediaControlEvent | string, handler: Function): EventSubscription`

Registers an event listener for a specific media control event. Also accepts any
`eventId` registered via `setCustomButtons`. Returns a function to remove the listener.

#### `removeAllListeners(event?: MediaControlEvent | string): void`

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
  isLoading?: boolean;  // report a buffering state while the track loads
  shuffle?: boolean;    // optional, default is false
  repeat?: 'off' | 'all' | 'one'; // optional, default is
}

type MediaControlEvent =
  | 'play'
  | 'pause'
  | 'stop'
  | 'skipToNext'
  | 'skipToPrevious'
  | 'skipToQueueItem'
  | 'seekForward'
  | 'seekBackward'
  | 'seek';

type MediaControlEventData = {
  position?: number;   // for seek events
  queueIndex?: number; // for skipToQueueItem events
  mediaId?: string;    // for skipToQueueItem events
};

interface QueueItem {
  id: string;        // same id as passed to updateMetadata
  queueId?: string;  // unique per entry, needed only for duplicates
  title?: string;
  artist?: string;
  album?: string;
  artwork?: string;  // URL
  duration?: number; // in seconds
}

interface CustomButton {
  /** Identifier emitted as the event command when this button is pressed. */
  eventId: string;
  /** Drawable resource name (Android) used as the button icon. */
  icon: string;
  /** Optional label shown next to the icon (e.g. in the Android Auto overflow menu). */
  displayName?: string;
}
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
