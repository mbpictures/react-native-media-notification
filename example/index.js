import { AppRegistry } from 'react-native';
import {
  foregroundEventHandler,
  setBackgroundMessageHandler,
} from 'react-native-media-notification';
import App from './src/App';
import { name as appName } from './app.json';
import { backgroundMusicHandler } from './src/MusicHandler';

AppRegistry.registerComponent(appName, () => App);
setBackgroundMessageHandler(backgroundMusicHandler, AppRegistry);
foregroundEventHandler(backgroundMusicHandler);
