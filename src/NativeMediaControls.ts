import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type {
  EventEmitter,
  UnsafeObject,
} from 'react-native/Libraries/Types/CodegenTypes';

// Event types
export const ALL_MEDIA_EVENTS = [
  'play',
  'pause',
  'stop',
  'skipToNext',
  'skipToPrevious',
  'seekForward',
  'seekBackward',
  'seek',
  'shuffle',
  'repeatMode',
] as const;
export type MediaControl = (typeof ALL_MEDIA_EVENTS)[number];

export type MediaControlEvent =
  | MediaControl
  | 'duck'
  | 'unDuck'
  | 'setMediaItems';

export interface NativeMediaTrackMetadata {
  id?: string;
  title?: string;
  artist?: string;
  album?: string;
  duration?: number;
  artwork?: string;
  position?: number;
  isPlaying?: boolean;
  repeatMode?: 'off' | 'one' | 'all';
  shuffle?: boolean;
}

export interface NativeEvent {
  command: string;
  data: UnsafeObject; // Position in seconds for seek events
}

export interface NativeLibraryItem {
  id: string;
  title?: string;
  artist?: string;
  album?: string;
  artwork?: string;
  duration?: number;
  playable?: boolean;
  browsable?: boolean;
  mediaType?: string;

  items?: UnsafeObject[];
}

export interface Spec extends TurboModule {
  setControlEnabled(name: string, enabled: boolean): void;
  updateMetadata(metadata: NativeMediaTrackMetadata): Promise<void>;
  stopMediaNotification(): Promise<void>;

  // Audio interruption handling
  enableAudioInterruption(enabled: boolean): Promise<void>;

  // Audio session activation
  enableBackgroundMode(enabled: boolean): void;

  shutdown(): void;

  setMediaLibrary(library: NativeLibraryItem): void;

  // Event listeners (native events will be emitted)
  readonly onEvent: EventEmitter<NativeEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('MediaControls');
