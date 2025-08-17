#import "MediaControls.h"
#import <MediaPlayer/MediaPlayer.h>
#import <AVFoundation/AVFoundation.h>

@interface MediaControls ()
@property (nonatomic, assign) BOOL audioInterruptionEnabled;
@property (nonatomic, assign) BOOL hasListeners;
@property (nonatomic, assign) BOOL audioInterrupted;
@property (nonatomic, assign) BOOL explictlyPaused;
@end

@implementation MediaControls

RCT_EXPORT_MODULE()

- (instancetype)init {
    self = [super init];
    if (self) {
        _audioInterruptionEnabled = NO;
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(audioHardwareRouteChanged:) name:AVAudioSessionRouteChangeNotification object:nil];
        [[UIApplication sharedApplication] beginReceivingRemoteControlEvents];
    }
    return self;
}

- (void)stopObserving {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"play", @"pause", @"stop", @"skipToNext", @"skipToPrevious", @"seekForward", @"seekBackward", @"seek"];
}

#pragma mark - React Native Methods

RCT_EXPORT_METHOD(setControlEnabled:(NSString*)name enabled:(BOOL)enabled) {
  MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
  if ([name  isEqual: @"play"]) {
    [commandCenter.playCommand addTarget:self action:@selector(handlePlayCommand:)];
    commandCenter.playCommand.enabled = enabled;
  }

  if ([name  isEqual: @"pause"]) {
    [commandCenter.pauseCommand addTarget:self action:@selector(handlePauseCommand:)];
    commandCenter.pauseCommand.enabled = enabled;
  }

  if ([name  isEqual: @"stop"]) {
    [commandCenter.stopCommand addTarget:self action:@selector(handleStopCommand:)];
    commandCenter.stopCommand.enabled = enabled;
  }

  if ([name  isEqual: @"skipToNext"]) {
    [commandCenter.nextTrackCommand addTarget:self action:@selector(handleNextTrackCommand:)];
    commandCenter.nextTrackCommand.enabled = enabled;
  }

  if ([name  isEqual: @"skipToPrevious"]) {
    [commandCenter.previousTrackCommand addTarget:self action:@selector(handlePreviousTrackCommand:)];
    commandCenter.previousTrackCommand.enabled = enabled;
  }

  if ([name  isEqual: @"seekForward"]) {
    [commandCenter.seekForwardCommand addTarget:self action:@selector(handleSeekForwardCommand:)];
    commandCenter.seekForwardCommand.enabled = enabled;
  }

  if ([name  isEqual: @"seekBackward"]) {
    [commandCenter.seekBackwardCommand addTarget:self action:@selector(handleSeekBackwardCommand:)];
    commandCenter.seekBackwardCommand.enabled = enabled;
  }

  if ([name  isEqual: @"seek"]) {
    [commandCenter.changePlaybackPositionCommand addTarget:self action:@selector(handleChangePlaybackPositionCommand:)];
    commandCenter.changePlaybackPositionCommand.enabled = enabled;
  }
}

RCT_EXPORT_METHOD(updateMetadata:(JS::NativeMediaControls::MediaTrackMetadata &)metadata
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    MPNowPlayingInfoCenter *_nowPlayingCenter = [MPNowPlayingInfoCenter defaultCenter];

    @try {
        NSMutableDictionary *nowPlayingInfo = [NSMutableDictionary dictionary];

        if (metadata.title().length > 0) {
            nowPlayingInfo[MPMediaItemPropertyTitle] = metadata.title();
        }

        if (metadata.artist().length > 0) {
            nowPlayingInfo[MPMediaItemPropertyArtist] = metadata.artist();
        }

        if (metadata.album().length > 0) {
            nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = metadata.album();
        }

        if (metadata.duration().has_value()) {
            double duration = metadata.duration().value();
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = [NSNumber numberWithDouble:duration];
        }

        if (metadata.position().has_value()) {
            double position = metadata.position().value();
            nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = [NSNumber numberWithDouble:position];
        }

        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = metadata.isPlaying() ? [NSNumber numberWithDouble:1] : [NSNumber numberWithDouble:0];

        _nowPlayingCenter.nowPlayingInfo = nowPlayingInfo;

        if (@available(iOS 11.0, *)) {
            if (!self.audioInterrupted) {
                self.explictlyPaused = false;
            }

            if (metadata.isPlaying()) {
                _nowPlayingCenter.playbackState = MPNowPlayingPlaybackStatePlaying;
            } else {
                _nowPlayingCenter.playbackState = MPNowPlayingPlaybackStatePaused;

                if (!self.audioInterrupted) {
                    self.explictlyPaused = true;
                }
            }
        }


        // Load artwork if provided
        if (metadata.artwork().length > 0) {
            NSString *artworkURL = metadata.artwork();
            [self loadArtworkFromURL:artworkURL completion:^(UIImage *image) {
                if (!image) {
                    return;
                }
                dispatch_async(dispatch_get_main_queue(), ^{
                    MPNowPlayingInfoCenter *center = [MPNowPlayingInfoCenter defaultCenter];
                    MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc] initWithBoundsSize:image.size requestHandler:^UIImage * _Nonnull(CGSize size) {
                        return image;
                    }];
                    NSMutableDictionary *mediaDict = (center.nowPlayingInfo != nil) ? [[NSMutableDictionary alloc] initWithDictionary: center.nowPlayingInfo] : [NSMutableDictionary dictionary];
                    [mediaDict setValue:artwork forKey:MPMediaItemPropertyArtwork];
                    center.nowPlayingInfo = mediaDict;
                });
            }];
        }

        resolve(nil);
    }
    @catch (NSException *exception) {
        reject(@"UPDATE_METADATA_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(stopMediaNotification:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    @try {
        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
        resolve(nil);
    }
    @catch (NSException *exception) {
        reject(@"STOP_NOTIFICATION_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(enableAudioInterruption:(BOOL)enabled
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    @try {
        _audioInterruptionEnabled = enabled;

        if (enabled) {
            [[NSNotificationCenter defaultCenter] addObserver:self
                                                     selector:@selector(audioSessionInterrupted:)
                                                         name:AVAudioSessionInterruptionNotification
                                                       object:nil];
        } else {
            [[NSNotificationCenter defaultCenter] removeObserver:self
                                                            name:AVAudioSessionInterruptionNotification
                                                          object:nil];
        }

        resolve(nil);
    }
    @catch (NSException *exception) {
        reject(@"AUDIO_INTERRUPTION_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(enableBackgroundMode:(BOOL) enabled){
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory: AVAudioSessionCategoryPlayback error: nil];
    [session setActive: enabled error: nil];
}

- (MPRemoteCommandHandlerStatus)handlePlayCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"play" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handlePauseCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"pause" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleStopCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"stop" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleNextTrackCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"skipToNext" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handlePreviousTrackCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"skipToPrevious" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleSeekForwardCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"seekForward" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleSeekBackwardCommand:(MPRemoteCommandEvent *)event {
    [self emitEvent:@"seekBackward" position:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleChangePlaybackPositionCommand:(MPChangePlaybackPositionCommandEvent *)event {
    [self emitEvent:@"seek" position:[NSNumber numberWithDouble:event.positionTime]];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (void)emitEvent:(NSString*) name position:(nullable NSNumber*) position {
  NSMutableDictionary *params = [NSMutableDictionary dictionary];
  params[@"command"] = name;
  if (position) {
    params[@"seekPosition"] = position;
  }
  [self emitOnEvent:params];
}

#pragma mark - Audio Interruption Handler

- (void)audioSessionInterrupted:(NSNotification *)notification {
    if (!_audioInterruptionEnabled) return;

    NSInteger interruptionType = [notification.userInfo[AVAudioSessionInterruptionTypeKey] integerValue];

    if (interruptionType == AVAudioSessionInterruptionTypeBegan) {
        self.audioInterrupted = true;
        if (!self.explictlyPaused) {
            [self emitEvent:@"pause" position:nil];
        }
    } else if (interruptionType == AVAudioSessionInterruptionTypeEnded) {
        self.audioInterrupted = false;
        if (!self.explictlyPaused) {
            [self emitEvent:@"play" position:nil];
        }
    }
}

- (void)audioHardwareRouteChanged:(NSNotification *)notification {
    NSInteger routeChangeReason = [notification.userInfo[AVAudioSessionRouteChangeReasonKey] integerValue];
    if (routeChangeReason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) {
        //headphones unplugged or bluetooth device disconnected, iOS will pause audio
        [self emitEvent:@"pause" position:nil];
    }
}

#pragma mark - Helper Methods

- (void)loadArtworkFromURL:(NSString *)urlString completion:(void (^)(UIImage *))completion {
    NSURL *url = [NSURL URLWithString:urlString];
    if (!url) {
        completion(nil);
        return;
    }

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSData *imageData = [NSData dataWithContentsOfURL:url];
        UIImage *image = imageData ? [UIImage imageWithData:imageData] : nil;

        dispatch_async(dispatch_get_main_queue(), ^{
            completion(image);
        });
    });
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeMediaControlsSpecJSI>(params);
}

@end
