import MediaControls, {
  ALL_MEDIA_EVENTS,
  type MediaControlEvent,
} from './NativeMediaControls';
import type { MediaTrackMetadata } from './NativeMediaControls';
import { EventEmitter, EventSubscription } from 'fbemitter';
import { type EventSubscription as NativeEventSubscription } from 'react-native';

export type MediaControlEventData = {
  position?: number; // für seek events
};

const eventEmitter = new EventEmitter();
let unsubscribe: NativeEventSubscription | null = null;

const setUpNativeEventListener = () => {
  if (unsubscribe) return;

  unsubscribe = MediaControls.onEvent((event) => {
    const { command, seekPosition } = event;
    eventEmitter.emit(command, { position: seekPosition });
  });
};

/**
 * Updates the metadata for the current media track.
 */
export async function updateMetadata(
  metadata: MediaTrackMetadata
): Promise<void> {
  return MediaControls.updateMetadata(metadata);
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

export function setControlEnabled(
  name: MediaControlEvent,
  enabled: boolean
): void {
  if (!ALL_MEDIA_EVENTS.includes(name)) {
    throw new Error(`Unknown media control event: ${name}`);
  }
  MediaControls.setControlEnabled(name, enabled);
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

// Export types
export type { MediaTrackMetadata };
