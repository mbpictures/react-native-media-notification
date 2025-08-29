import { useEffect, useState } from 'react';
import { View, Button, Text, StyleSheet } from 'react-native';
import * as MediaControls from 'react-native-media-notification';
import * as MusicHandler from './MusicHandler';

export default function App() {
  const [isPlaying, setIsPlaying] = useState(MusicHandler.playing);
  const [currentPosition, setCurrentPosition] = useState(0);
  const [currentTrack, setCurrentTrack] = useState(
    MusicHandler.currentTrackIndex
  );
  const [duration, setDuration] = useState(MusicHandler.duration);

  useEffect(() => {
    MediaControls.setMediaLibrary({
      id: 'root',
      title: 'Media Library',
      browsable: true,
      playable: false,
      items: MusicHandler.tracks.map((track, index) => ({
        id: `track-${index}`,
        title: track.title,
        artist: track.artist,
        album: track.album,
        artwork: track.artwork,
        duration: 0,
        playable: true,
        mediaItem: 'music',
      })),
    });
    const playingSub = MusicHandler.emitter.addListener(
      'playing',
      setIsPlaying
    );
    const durationSub = MusicHandler.emitter.addListener(
      'duration',
      setDuration
    );
    const trackChangedSub = MusicHandler.emitter.addListener(
      'trackChanged',
      setCurrentTrack
    );

    return () => {
      MediaControls.shutdown();
      playingSub.remove();
      durationSub.remove();
      trackChangedSub.remove();
    };
  }, []);

  useEffect(() => {
    MusicHandler.loadTrack(currentTrack, setDuration);

    const interval = setInterval(() => {
      MusicHandler.sound?.getCurrentTime((position) => {
        setCurrentPosition(position);
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [currentTrack]);

  const handleFinished = () => {
    const nextTrackIndex = (currentTrack + 1) % MusicHandler.tracks.length;
    MusicHandler.loadTrack(nextTrackIndex);
  };

  const togglePlayPause = async () => {
    MusicHandler.setPlaying(!isPlaying, handleFinished);
  };

  const stopPlayback = async () => {
    try {
      setIsPlaying(false);
      setCurrentPosition(0);
      await MediaControls.stopMediaNotification();
      MusicHandler.sound?.stop();
    } catch (error) {
      console.error('Error while stopping: ', error);
    }
  };

  const nextTrack = () => {
    const nextTrackIndex = (currentTrack + 1) % MusicHandler.tracks.length;
    MusicHandler.loadTrack(nextTrackIndex);
  };

  const prevTrack = () => {
    const prevTrackIndex =
      currentTrack === 0 ? MusicHandler.tracks.length - 1 : currentTrack - 1;
    MusicHandler.loadTrack(prevTrackIndex);
  };

  const formatTime = (s: number) => {
    const minutes = Math.floor(s / 60);
    const seconds = Math.floor(s % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const track = MusicHandler.tracks[currentTrack];

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
          {formatTime(currentPosition)} / {formatTime(duration)}
        </Text>
        <Text style={styles.statusText}>
          Status: {isPlaying ? '▶️ Playing' : '⏸️ Paused'}
        </Text>
      </View>

      <View style={styles.controls}>
        <Button title="⏮️ Prev" onPress={prevTrack} />
        <Button
          title={isPlaying ? '⏸️ Pause' : '▶️ Play'}
          onPress={() => togglePlayPause()}
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
