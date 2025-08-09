#import "MediaControls.h"
#import <MediaPlayer/MediaPlayer.h>
#import <AVFoundation/AVFoundation.h>

@interface MediaControls ()
@property (nonatomic, strong) MPNowPlayingInfoCenter *nowPlayingCenter;
@property (nonatomic, strong) MPRemoteCommandCenter *commandCenter;
@property (nonatomic, assign) BOOL audioInterruptionEnabled;
@property (nonatomic, assign) BOOL hasListeners;
@end

@implementation MediaControls

RCT_EXPORT_MODULE()

- (instancetype)init {
    self = [super init];
    if (self) {
        _nowPlayingCenter = [MPNowPlayingInfoCenter defaultCenter];
        _commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
        _audioInterruptionEnabled = NO;
        _hasListeners = NO;
        [self setupRemoteCommands];
    }
    return self;
}

- (void)setupRemoteCommands {
    // Play command
    [_commandCenter.playCommand addTarget:self action:@selector(handlePlayCommand:)];
    _commandCenter.playCommand.enabled = YES;

    // Pause command
    [_commandCenter.pauseCommand addTarget:self action:@selector(handlePauseCommand:)];
    _commandCenter.pauseCommand.enabled = YES;

    // Stop command
    [_commandCenter.stopCommand addTarget:self action:@selector(handleStopCommand:)];
    _commandCenter.stopCommand.enabled = YES;

    // Next track command
    [_commandCenter.nextTrackCommand addTarget:self action:@selector(handleNextTrackCommand:)];
    _commandCenter.nextTrackCommand.enabled = YES;

    // Previous track command
    [_commandCenter.previousTrackCommand addTarget:self action:@selector(handlePreviousTrackCommand:)];
    _commandCenter.previousTrackCommand.enabled = YES;

    // Seek forward command
    [_commandCenter.seekForwardCommand addTarget:self action:@selector(handleSeekForwardCommand:)];
    _commandCenter.seekForwardCommand.enabled = YES;
    _commandCenter.seekForwardCommand.preferredIntervals = @[@15];

    // Seek backward command
    [_commandCenter.seekBackwardCommand addTarget:self action:@selector(handleSeekBackwardCommand:)];
    _commandCenter.seekBackwardCommand.enabled = YES;
    _commandCenter.seekBackwardCommand.preferredIntervals = @[@15];

    // Change playback position command
    [_commandCenter.changePlaybackPositionCommand addTarget:self action:@selector(handleChangePlaybackPositionCommand:)];
    _commandCenter.changePlaybackPositionCommand.enabled = YES;
}

- (void)startObserving {
    _hasListeners = YES;
    if (_audioInterruptionEnabled) {
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(audioSessionInterrupted:)
                                                     name:AVAudioSessionInterruptionNotification
                                                   object:nil];
    }
}

- (void)stopObserving {
    _hasListeners = NO;
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"play", @"pause", @"stop", @"skipToNext", @"skipToPrevious", @"seekForward", @"seekBackward", @"seek"];
}

#pragma mark - React Native Methods

RCT_EXPORT_METHOD(updateMetadata:(NSDictionary *)metadata
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    @try {
        NSMutableDictionary *nowPlayingInfo = [NSMutableDictionary dictionary];

        if (metadata[@"title"]) {
            nowPlayingInfo[MPMediaItemPropertyTitle] = metadata[@"title"];
        }

        if (metadata[@"artist"]) {
            nowPlayingInfo[MPMediaItemPropertyArtist] = metadata[@"artist"];
        }

        if (metadata[@"album"]) {
            nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = metadata[@"album"];
        }

        if (metadata[@"duration"]) {
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = metadata[@"duration"];
        }

        if (metadata[@"position"]) {
            nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = metadata[@"position"];
        }

        if (metadata[@"isPlaying"]) {
            BOOL isPlaying = [metadata[@"isPlaying"] boolValue];
            nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = @(isPlaying ? 1.0 : 0.0);
        }

        // Load artwork if provided
        if (metadata[@"artwork"]) {
            NSString *artworkURL = metadata[@"artwork"];
            [self loadArtworkFromURL:artworkURL completion:^(UIImage *image) {
                if (image) {
                    MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc] initWithBoundsSize:image.size requestHandler:^UIImage * _Nonnull(CGSize size) {
                        return image;
                    }];
                    nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork;
                }
                _nowPlayingCenter.nowPlayingInfo = nowPlayingInfo;
            }];
        } else {
            _nowPlayingCenter.nowPlayingInfo = nowPlayingInfo;
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
        _nowPlayingCenter.nowPlayingInfo = nil;
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

        if (enabled && _hasListeners) {
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

#pragma mark - Remote Command Handlers

- (MPRemoteCommandHandlerStatus)handlePlayCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"play" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handlePauseCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"pause" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleStopCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"stop" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleNextTrackCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"skipToNext" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handlePreviousTrackCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"skipToPrevious" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleSeekForwardCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"seekForward" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleSeekBackwardCommand:(MPRemoteCommandEvent *)event {
    [self sendEventWithName:@"seekBackward" body:nil];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)handleChangePlaybackPositionCommand:(MPChangePlaybackPositionCommandEvent *)event {
    NSDictionary *params = @{@"position": @(event.positionTime)};
    [self sendEventWithName:@"seek" body:params];
    return MPRemoteCommandHandlerStatusSuccess;
}

#pragma mark - Audio Interruption Handler

- (void)audioSessionInterrupted:(NSNotification *)notification {
    if (!_audioInterruptionEnabled) return;

    NSNumber *interruptionType = notification.userInfo[AVAudioSessionInterruptionTypeKey];

    if (interruptionType.integerValue == AVAudioSessionInterruptionTypeBegan) {
        [self sendEventWithName:@"pause" body:nil];
    } else if (interruptionType.integerValue == AVAudioSessionInterruptionTypeEnded) {
        NSNumber *interruptionOptions = notification.userInfo[AVAudioSessionInterruptionOptionKey];
        if (interruptionOptions.integerValue == AVAudioSessionInterruptionOptionShouldResume) {
            [self sendEventWithName:@"play" body:nil];
        }
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
