import { useEffect, useRef, useState } from 'react';
import { View, Button, Text, StyleSheet } from 'react-native';
import * as MediaControls from 'react-native-media-controls';
import Sound from 'react-native-sound';

const tracks = [
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

export default function App() {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentPosition, setCurrentPosition] = useState(0);
  const [currentTrack, setCurrentTrack] = useState(0);
  const sound = useRef<Sound | null>(null);

  const handleSetCurrentTrack = (index: number) => {
    if (sound.current) {
      sound.current.stop();
      sound.current = null;
    }
    const track = tracks[index];
    if (!track) return;
    MediaControls.updateMetadata({
      title: track.title,
      artist: track.artist,
      album: track.album,
      duration: 0,
      position: 0,
      isPlaying: isPlaying,
      artwork: track.artwork,
    }).catch(console.error);
    setCurrentTrack(index);
    setCurrentPosition(0);
    setIsPlaying(false);
  };
  useEffect(() => {
    // Event Listeners Setup
    const playListener = MediaControls.addEventListener('play', () => {
      console.log('Play event received');
      setIsPlaying(true);
    });

    const pauseListener = MediaControls.addEventListener('pause', () => {
      console.log('Pause event received');
      setIsPlaying(false);
    });

    const stopListener = MediaControls.addEventListener('stop', () => {
      console.log('Stop event received');
      stopPlayback().catch(console.error);
    });

    const nextListener = MediaControls.addEventListener('skipToNext', () => {
      console.log('Skip to next event received');
      nextTrack();
    });

    const prevListener = MediaControls.addEventListener(
      'skipToPrevious',
      () => {
        console.log('Skip to previous event received');
        prevTrack();
      }
    );

    const seekListener = MediaControls.addEventListener('seek', (data) => {
      console.log('Seek event received, position:', data?.position);
      if (data?.position) {
        setCurrentPosition(data.position);
      }
    });

    const seekForwardListener = MediaControls.addEventListener(
      'seekForward',
      () => {
        console.log('Seek forward event received');
        setCurrentPosition((prev) =>
          Math.min(prev + 15, sound.current?.getDuration() ?? 0)
        );
      }
    );

    const seekBackwardListener = MediaControls.addEventListener(
      'seekBackward',
      () => {
        console.log('Seek backward event received');
        setCurrentPosition((prev) => Math.max(prev - 15, 0));
      }
    );

    // Cleanup
    return () => {
      playListener.remove();
      pauseListener.remove();
      stopListener.remove();
      nextListener.remove();
      prevListener.remove();
      seekListener.remove();
      seekForwardListener.remove();
      seekBackwardListener.remove();
    };
  }, [isPlaying]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    MediaControls.setControlEnabled('play', true);
    MediaControls.setControlEnabled('pause', true);
    MediaControls.setControlEnabled('seek', true);
    MediaControls.setControlEnabled('skipToPrevious', true);
    MediaControls.setControlEnabled('skipToNext', true);
    MediaControls.setControlEnabled('seekForward', true);
    MediaControls.setControlEnabled('seekBackward', true);
    MediaControls.setControlEnabled('stop', true);

    // Audio Interruptions aktivieren
    MediaControls.enableAudioInterruption(true).catch(console.error);

    Sound.setCategory('Playback', true);
  }, []);

  // Simuliere Playback Progress
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isPlaying) {
      interval = setInterval(() => {
        setCurrentPosition((prev) => {
          const newPosition = prev + 1;
          if (sound.current && newPosition >= sound.current.getDuration()) {
            // Auto next track
            const nextTrackIndex = (currentTrack + 1) % tracks.length;
            setCurrentTrack(nextTrackIndex);
            return 0;
          }
          return newPosition;
        });
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isPlaying, currentTrack]);

  const togglePlayPause = async () => {
    const track = tracks[currentTrack];
    if (track) {
      if (sound.current === null) {
        await new Promise<void>((resolve, reject) => {
          sound.current = new Sound(track.url, undefined, (error) => {
            if (error) {
              return reject(error);
            }
            resolve();
          });
        });
      }

      sound.current?.getCurrentTime((position) => {
        MediaControls.updateMetadata({
          title: track.title,
          artist: track.artist,
          album: track.album,
          duration: sound.current?.getDuration(),
          position,
          isPlaying: !isPlaying,
          artwork: track.artwork,
        }).catch(console.error);
      });
    }
    if (isPlaying) {
      setIsPlaying(false);
      sound.current?.pause();
    } else {
      setIsPlaying(true);
      sound.current?.play();
    }
  };

  const stopPlayback = async () => {
    try {
      setIsPlaying(false);
      setCurrentPosition(0);
      await MediaControls.stopMediaNotification();
    } catch (error) {
      console.error('Error while stopping: ', error);
    }
  };

  const nextTrack = () => {
    const nextTrackIndex = (currentTrack + 1) % tracks.length;
    handleSetCurrentTrack(nextTrackIndex);
  };

  const prevTrack = () => {
    const prevTrackIndex =
      currentTrack === 0 ? tracks.length - 1 : currentTrack - 1;
    handleSetCurrentTrack(prevTrackIndex);
  };

  const formatTime = (s: number) => {
    const minutes = Math.floor(s / 60);
    const seconds = Math.floor(s % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const track = tracks[currentTrack];

  return (
    <View style={styles.container}>
      <Text style={styles.title}>React Native Media Controls Demo</Text>

      <View style={styles.trackInfo}>
        <Text style={styles.trackTitle}>{track?.title}</Text>
        <Text style={styles.trackArtist}>{track?.artist}</Text>
        <Text style={styles.trackAlbum}>{track?.album}</Text>
      </View>

      <View style={styles.timeInfo}>
        <Text style={styles.timeText}>
          {formatTime(currentPosition)} /{' '}
          {formatTime(sound.current?.getDuration() ?? 0)}
        </Text>
        <Text style={styles.statusText}>
          Status: {isPlaying ? '▶️ Playing' : '⏸️ Paused'}
        </Text>
      </View>

      <View style={styles.controls}>
        <Button title="⏮️ Prev" onPress={prevTrack} />
        <Button
          title={isPlaying ? '⏸️ Pause' : '▶️ Play'}
          onPress={togglePlayPause}
        />
        <Button title="⏭️ Next" onPress={nextTrack} />
      </View>

      <View style={styles.controls}>
        <Button title="⏹️ Stop" onPress={stopPlayback} color="red" />
      </View>

      <View style={styles.instructions}>
        <Text style={styles.instructionText}>
          📱 Use the media controls (Notification or ControlCenter to play,
          pause, skip tracks.
        </Text>
        <Text style={styles.instructionText}>
          🎧 Test audio interruptions by playing music and then switching to
          another app.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 30,
    textAlign: 'center',
  },
  trackInfo: {
    alignItems: 'center',
    marginBottom: 20,
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 10,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    width: '80%',
  },
  trackTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 5,
  },
  trackArtist: {
    fontSize: 16,
    color: '#666',
    marginBottom: 3,
  },
  trackAlbum: {
    fontSize: 14,
    color: '#999',
  },
  timeInfo: {
    alignItems: 'center',
    marginBottom: 30,
  },
  timeText: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 5,
  },
  statusText: {
    fontSize: 16,
    color: '#333',
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    width: '100%',
    marginBottom: 20,
  },
  instructions: {
    marginTop: 30,
    padding: 15,
    backgroundColor: '#e3f2fd',
    borderRadius: 8,
  },
  instructionText: {
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 5,
    color: '#1976d2',
  },
});
