import { type BackgroundEvent } from 'react-native-media-notification';
import Sound from 'react-native-sound';
import * as MediaControls from 'react-native-media-notification';

export const tracks = [
  {
    title: 'Relaxing Nature Sounds',
    artist: 'Nature Sounds',
    album: 'Peaceful Moments',
    artwork: 'https://picsum.photos/seed/notification1/300/300',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
  },
  {
    title: 'Upbeat Song',
    artist: 'Happy Band',
    album: 'Feel Good Album',
    artwork: 'https://picsum.photos/seed/notification2/300/300',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3',
  },
  {
    title: 'Classical Piece',
    artist: 'Orchestra',
    album: 'Symphonies',
    artwork: 'https://picsum.photos/seed/notification3/300/300',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3',
  },
];

export let sound: Sound | null = null;
export let duration = 0;
export let playing = false;
export let currentTrackIndex = 0;
let requestPlay = false;
let initialized = false;

const init = () => {
  if (initialized) return;
  // enable audio interruptions
  MediaControls.enableAudioInterruption(true).catch(console.error);
  MediaControls.enableBackgroundMode(true);

  Sound.setCategory('Playback', true);
  initialized = true;
};

export const loadTrack = (
  index: number,
  onDuration?: (duration: number) => void
) => {
  init();

  if (sound) {
    sound.release();
    sound = null;
  }
  const track = tracks[index];
  if (!track) return;
  currentTrackIndex = index;
  sound = new Sound(track.url, undefined, (error, props) => {
    if (error) {
      console.log('Failed to load the sound', error);
      return;
    }
    duration = props.duration ?? 0;
    if (requestPlay) {
      setPlaying(true);
      requestPlay = false;
    }
    if (onDuration) {
      onDuration(duration);
    }
  });
};

export const setPlaying = (newPlaying: boolean, finished?: () => unknown) => {
  if (!sound) {
    requestPlay = true;
    return;
  }
  const track = tracks[currentTrackIndex];
  if (!track) return;
  sound?.getCurrentTime((position) => {
    MediaControls.updateMetadata({
      title: track.title,
      artist: track.artist,
      album: track.album,
      duration,
      isPlaying: newPlaying,
      position,
      artwork: track.artwork,
    }).catch(console.error);
  });
  if (newPlaying && !sound.isLoaded()) {
    requestPlay = true;
    return;
  }
  playing = newPlaying;
  if (newPlaying) {
    sound.play(finished);
  } else {
    sound.pause();
  }
};

export const resolveTrackIndex = (mediaId: string) => {
  return parseInt(mediaId.replace('track-', ''), 10);
};

export const backgroundMusicHandler = async (event: BackgroundEvent) => {
  console.log('Background event received:', event);
  switch (event.command) {
    case 'play':
      setPlaying(true);
      break;
    case 'pause':
      setPlaying(false);
      break;
    case 'stop':
      sound?.stop();
      sound = null;
      break;
    case 'seek':
      if (event.data.position !== undefined) {
        sound?.setCurrentTime(event.data.position);
      }
      break;
    case 'skipToNext':
      currentTrackIndex++;
      if (currentTrackIndex >= tracks.length) {
        currentTrackIndex = 0;
      }
      loadTrack(currentTrackIndex);
      setPlaying(true);
      break;
    case 'skipToPrevious':
      currentTrackIndex--;
      if (currentTrackIndex < 0) {
        currentTrackIndex = tracks.length - 1;
      }
      loadTrack(currentTrackIndex);
      setPlaying(true);
      break;
    case 'seekForward':
      sound?.getCurrentTime((position) => {
        const newPosition = Math.min(position + 15, duration);
        sound?.setCurrentTime(newPosition);
      });
      break;
    case 'seekBackward':
      sound?.getCurrentTime((position) => {
        const newPosition = Math.max(position - 15, 0);
        sound?.setCurrentTime(newPosition);
      });
      break;
    case 'setMediaItems':
      if (event.data.mediaItems && event.data.mediaItems.length > 0) {
        const index = resolveTrackIndex(event.data.mediaItems[0]);
        loadTrack(index);
        sound?.play();
      }
      break;
  }
};
