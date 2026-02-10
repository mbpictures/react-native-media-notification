import MediaControls, {
  ALL_MEDIA_EVENTS,
  type MediaControl,
  type MediaControlEvent,
  type NativeLibraryItem,
} from './NativeMediaControls';
import type { NativeMediaTrackMetadata } from './NativeMediaControls';
import { EventEmitter, EventSubscription } from 'fbemitter';
import {
  AppRegistry,
  type EventSubscription as NativeEventSubscription,
  Image,
} from 'react-native';
import type { ImageSourcePropType } from 'react-native/Libraries/Image/Image';

export interface MediaTrackMetadata extends Omit<
  NativeMediaTrackMetadata,
  'artwork'
> {
  artwork?: string | ImageSourcePropType;
}

export type MediaItemMediaType =
  | 'music'
  | 'podcast'
  | 'radio'
  | 'album'
  | 'artist'
  | 'genre'
  | 'playlist';

export interface LibraryItem extends Omit<
  NativeLibraryItem,
  'mediaType' | 'items'
> {
  items?: LibraryItem[];
  mediaItem?: MediaItemMediaType;
}

export type MediaControlEventData = {
  seekPosition?: number;
  mediaItems?: string[];
  shuffleMode?: boolean;
  repeatMode?: 'off' | 'one' | 'all';
};

const eventEmitter = new EventEmitter();
let unsubscribe: NativeEventSubscription | null = null;

const setUpNativeEventListener = () => {
  if (unsubscribe) return;

  unsubscribe = MediaControls.onEvent((event) => {
    const { command, data } = event;
    eventEmitter.emit(command, data);
  });
};

/**
 * Updates the metadata for the current media track.
 */
export async function updateMetadata(
  metadata: MediaTrackMetadata
): Promise<void> {
  if (metadata.artwork && typeof metadata.artwork !== 'string') {
    metadata.artwork = Image.resolveAssetSource(metadata.artwork).uri;
  }
  return MediaControls.updateMetadata(metadata as NativeMediaTrackMetadata);
}

/**
 * Sets the media library for browsing and playback.
 * @param library The root item of the media library hierarchy.
 */
export function setMediaLibrary(library: LibraryItem) {
  return MediaControls.setMediaLibrary(library);
}

/**
 * Stops the media notification and clears any ongoing playback state.
 */
export async function stopMediaNotification(): Promise<void> {
  return MediaControls.stopMediaNotification();
}

/**
 * Activates or deactivates audio interruption handling.
 */
export async function enableAudioInterruption(enabled: boolean): Promise<void> {
  return MediaControls.enableAudioInterruption(enabled);
}

/**
 * Activates the audio session for media playback.
 * Call this method before starting media playback to ensure proper Control Center integration.
 */
export function enableBackgroundMode(enabled: boolean): void {
  MediaControls.enableBackgroundMode(enabled);
}

export function setControlEnabled(name: MediaControl, enabled: boolean): void {
  if (!ALL_MEDIA_EVENTS.includes(name)) {
    throw new Error(`Unknown media control event: ${name}`);
  }
  MediaControls.setControlEnabled(name, enabled);
}

export function shutdown(): void {
  MediaControls.shutdown();
}

/**
 * Register an event listener for media control events.
 */
export function addEventListener(
  event: MediaControlEvent,
  handler: (data?: MediaControlEventData) => void
): EventSubscription {
  setUpNativeEventListener();
  return eventEmitter.addListener(event, handler);
}

/**
 * Remove a specific event listener for media control events.
 */
export function removeAllListeners(event?: MediaControlEvent): void {
  if (event) {
    eventEmitter.removeAllListeners(event);
  } else {
    ALL_MEDIA_EVENTS.forEach((e) => eventEmitter.removeAllListeners(e));
  }
}

export interface BackgroundEvent {
  command: MediaControlEvent;
  data: any;
}

type BackgroundMessageHandler = (event: BackgroundEvent) => Promise<void>;

/**
 * Sets the background message handler to process media control events when the app is in the background or terminated.
 * @param handler The function to handle background media control events.
 * @param appRegistry Optional AppRegistry instance to use for headless task registration. If not provided, uses the default AppRegistry.
 */
export function setBackgroundMessageHandler(
  handler: BackgroundMessageHandler,
  appRegistry?: typeof AppRegistry
) {
  const registry = appRegistry || AppRegistry;
  registry.registerHeadlessTask('MediaControlsHeadlessTask', () => handler);
}

/**
 * Sets up a foreground event handler to process media control events when the app is in the foreground.
 * @param handler The function to handle media control events.
 */
export function foregroundEventHandler(handler: BackgroundMessageHandler) {
  return MediaControls.onEvent((event) =>
    handler({ command: event.command as MediaControlEvent, data: event.data })
  );
}
