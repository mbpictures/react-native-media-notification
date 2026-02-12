#import "MediaLibraryStore.h"

static NSString *const kMediaLibraryStorageKey = @"MediaLibraryStore.library";

NSString *const MediaLibraryUpdatedNotification = @"MediaLibraryUpdated";
NSString *const CarPlayItemSelectedNotification = @"CarPlayItemSelected";

@implementation MediaLibraryStore

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
    self.rootElement = root;
    [self persistLibrary];
    [[NSNotificationCenter defaultCenter] postNotificationName:MediaLibraryUpdatedNotification
                                                        object:nil];
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

@end
