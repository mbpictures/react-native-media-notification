#import "MediaLibraryStore.h"

static NSString *const kMediaLibraryStorageKey = @"MediaLibraryStore.library";

NSString *const MediaLibraryUpdatedNotification = @"MediaLibraryUpdated";
NSString *const CarPlayItemSelectedNotification = @"CarPlayItemSelected";
NSString *const MediaQueueUpdatedNotification = @"MediaQueueUpdated";
NSString *const CarPlayQueueItemSelectedNotification = @"CarPlayQueueItemSelected";

@interface MediaLibraryStore ()
// Guarded by @synchronized(self): written from the TurboModule queue, read by CarPlay on main.
@property (nonatomic, copy) NSArray<MediaElement *> *queueItems;
@property (nonatomic, copy, nullable) NSString *queueTitleValue;
@property (nonatomic, copy, nullable) NSString *currentItemId;
// Last resolved position, used to pick the right entry for tracks queued twice.
@property (nonatomic, assign) NSInteger queueIndexHint;
@end

@implementation MediaLibraryStore

- (instancetype)init {
    self = [super init];
    if (self) {
        _queueItems = @[];
    }
    return self;
}

+ (instancetype)sharedInstance {
    static MediaLibraryStore *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[MediaLibraryStore alloc] init];
        [instance loadPersistedLibrary];
    });
    return instance;
}

- (void)setLibrary:(MediaElement *)root {
    dispatch_async(dispatch_get_main_queue(), ^{
        self.rootElement = root;
        [self persistLibrary];
        [[NSNotificationCenter defaultCenter] postNotificationName:MediaLibraryUpdatedNotification
                                                            object:nil];
    });
}

- (void)persistLibrary {
    if (!self.rootElement) {
        [[NSUserDefaults standardUserDefaults] removeObjectForKey:kMediaLibraryStorageKey];
        return;
    }
    NSDictionary *dict = [self.rootElement toDictionary];
    [[NSUserDefaults standardUserDefaults] setObject:dict forKey:kMediaLibraryStorageKey];
}

- (void)loadPersistedLibrary {
    NSDictionary *dict = [[NSUserDefaults standardUserDefaults] dictionaryForKey:kMediaLibraryStorageKey];
    if (dict) {
        self.rootElement = [MediaElement fromDictionary:dict];
    }
}

- (nullable MediaElement *)findElementById:(NSString *)elementId {
    if (!self.rootElement) return nil;
    return [self findElementById:elementId inElement:self.rootElement];
}

- (nullable MediaElement *)findElementById:(NSString *)elementId inElement:(MediaElement *)element {
    if ([element.itemId isEqualToString:elementId]) {
        return element;
    }
    for (MediaElement *child in element.items) {
        MediaElement *found = [self findElementById:elementId inElement:child];
        if (found) return found;
    }
    return nil;
}

#pragma mark - Playback queue

- (void)setQueue:(NSArray<MediaElement *> *)queue
    currentIndex:(NSInteger)currentIndex
           title:(nullable NSString *)title {
    @synchronized (self) {
        self.queueItems = queue;
        self.queueTitleValue = title;
        self.queueIndexHint = queue.count == 0 ? 0 : MAX(0, MIN(currentIndex, (NSInteger)queue.count - 1));
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        [[NSNotificationCenter defaultCenter] postNotificationName:MediaQueueUpdatedNotification
                                                            object:nil];
    });
}

- (NSArray<MediaElement *> *)queue {
    @synchronized (self) {
        return self.queueItems;
    }
}

- (nullable NSString *)queueTitle {
    @synchronized (self) {
        return self.queueTitleValue;
    }
}

- (NSInteger)resolveQueueIndexForItemId:(nullable NSString *)itemId {
    BOOL trackChanged = NO;
    NSInteger index;
    @synchronized (self) {
        if (itemId && ![itemId isEqualToString:self.currentItemId]) {
            self.currentItemId = itemId;
            trackChanged = YES;
        }
        index = [self locateCurrentItem];
    }
    if (trackChanged) {
        // Lets a visible Up Next list move its playing indicator.
        dispatch_async(dispatch_get_main_queue(), ^{
            [[NSNotificationCenter defaultCenter] postNotificationName:MediaQueueUpdatedNotification
                                                                object:nil];
        });
    }
    return index;
}

- (NSInteger)currentQueueIndex {
    @synchronized (self) {
        return [self locateCurrentItem];
    }
}

// Caller holds the lock.
- (NSInteger)locateCurrentItem {
    NSString *itemId = self.currentItemId;
    NSArray<MediaElement *> *items = self.queueItems;
    if (!itemId || items.count == 0) {
        return NSNotFound;
    }
    NSInteger hint = self.queueIndexHint;
    if (hint < (NSInteger)items.count && [items[hint].itemId isEqualToString:itemId]) {
        return hint;
    }
    NSInteger best = NSNotFound;
    for (NSInteger i = 0; i < (NSInteger)items.count; i++) {
        if (![items[i].itemId isEqualToString:itemId]) continue;
        if (best == NSNotFound || labs(i - hint) < labs(best - hint)) {
            best = i;
        }
    }
    if (best != NSNotFound) {
        self.queueIndexHint = best;
    }
    return best;
}

@end
