#import <Foundation/Foundation.h>
#import "MediaElement.h"

extern NSString *const MediaLibraryUpdatedNotification;
extern NSString *const CarPlayItemSelectedNotification;
extern NSString *const MediaQueueUpdatedNotification;
// userInfo: @"queueIndex" (NSNumber), @"mediaId" (NSString)
extern NSString *const CarPlayQueueItemSelectedNotification;
extern NSString *const MediaCustomButtonsUpdatedNotification;
extern NSString *const CarPlayCustomButtonPressedNotification;

NS_ASSUME_NONNULL_BEGIN


@interface MediaCustomButton : NSObject

@property (nonatomic, strong) NSString *eventId;
@property (nonatomic, strong, nullable) NSString *icon;
@property (nonatomic, strong, nullable) NSString *displayName;

+ (nullable instancetype)fromDictionary:(NSDictionary *)dict;

@end

@interface MediaLibraryStore : NSObject

@property (nonatomic, strong, nullable) MediaElement *rootElement;
@property (nonatomic, copy) NSArray<MediaCustomButton *> *customButtons;

+ (instancetype)sharedInstance;
- (void)setLibrary:(MediaElement *)root;
- (nullable MediaElement *)findElementById:(NSString *)elementId;

#pragma mark - Playback queue

- (void)setQueue:(NSArray<MediaElement *> *)queue
    currentIndex:(NSInteger)currentIndex
           title:(nullable NSString *)title;
- (NSArray<MediaElement *> *)queue;
- (nullable NSString *)queueTitle;
/// Records the playing track and returns its queue index, or NSNotFound if it isn't queued.
- (NSInteger)resolveQueueIndexForItemId:(nullable NSString *)itemId;
/// Queue index of the playing track, or NSNotFound.
- (NSInteger)currentQueueIndex;

@end

NS_ASSUME_NONNULL_END
