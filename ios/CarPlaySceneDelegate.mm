#import "CarPlaySceneDelegate.h"
#import "MediaLibraryStore.h"
#import "MediaElement.h"

static const NSInteger kMaxTabs = 4;

API_AVAILABLE(ios(14.0))
@interface CarPlaySceneDelegate ()

@property (nonatomic, strong) CPInterfaceController *interfaceController;

@end

API_AVAILABLE(ios(14.0))
@implementation CarPlaySceneDelegate

static CarPlaySceneDelegate *_sharedInstance = nil;

#pragma mark - Public API

+ (void)connectWithInterfaceController:(CPInterfaceController *)interfaceController {
    _sharedInstance = [[CarPlaySceneDelegate alloc] init];
    _sharedInstance.interfaceController = interfaceController;

    [_sharedInstance buildAndSetRootTemplate];

    [[NSNotificationCenter defaultCenter] addObserver:_sharedInstance
                                             selector:@selector(onLibraryUpdated:)
                                                 name:MediaLibraryUpdatedNotification
                                               object:nil];
}

+ (void)disconnect {
    if (_sharedInstance) {
        [[NSNotificationCenter defaultCenter] removeObserver:_sharedInstance
                                                        name:MediaLibraryUpdatedNotification
                                                      object:nil];
        _sharedInstance.interfaceController = nil;
        _sharedInstance = nil;
    }
}

#pragma mark - Library Update

- (void)onLibraryUpdated:(NSNotification *)notification {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self buildAndSetRootTemplate];
    });
}

#pragma mark - Template Building

- (void)buildAndSetRootTemplate {
    MediaElement *root = [MediaLibraryStore sharedInstance].rootElement;
    if (!root || !root.items || root.items.count == 0) {
        return;
    }

    NSMutableArray<CPTemplate *> *tabs = [NSMutableArray array];
    NSInteger tabCount = MIN((NSInteger)root.items.count, kMaxTabs);

    for (NSInteger i = 0; i < tabCount; i++) {
        MediaElement *tabElement = root.items[i];
        CPListTemplate *listTemplate = [self buildListTemplateForElement:tabElement];
        [tabs addObject:listTemplate];
    }

    CPTabBarTemplate *tabBar = [[CPTabBarTemplate alloc] initWithTemplates:tabs];

    [self.interfaceController setRootTemplate:tabBar animated:YES completion:nil];
}

- (CPListTemplate *)buildListTemplateForElement:(MediaElement *)element {
    NSMutableArray<CPListItem *> *listItems = [NSMutableArray array];

    for (MediaElement *child in element.items) {
        CPListItem *item = [[CPListItem alloc] initWithText:child.title ?: @""
                                                 detailText:child.artist];
        item.accessoryType = child.browsable ? CPListItemAccessoryTypeDisclosureIndicator : CPListItemAccessoryTypeNone;

        __weak CarPlaySceneDelegate *weakSelf = self;
        NSString *childId = child.itemId;
        BOOL childPlayable = child.playable;
        BOOL childBrowsable = child.browsable;

        item.handler = ^(id<CPSelectableListItem> _Nonnull selectedItem, dispatch_block_t _Nonnull completionHandler) {
            CarPlaySceneDelegate *strongSelf = weakSelf;
            if (!strongSelf) {
                completionHandler();
                return;
            }

            if (childPlayable) {
                // Emit setMediaItems event via notification
                [[NSNotificationCenter defaultCenter] postNotificationName:CarPlayItemSelectedNotification
                                                                    object:nil
                                                                  userInfo:@{@"mediaItems": @[childId ?: @""]}];
                // Show the Now Playing screen
                CPNowPlayingTemplate *nowPlaying = [CPNowPlayingTemplate sharedTemplate];
                [strongSelf.interfaceController pushTemplate:nowPlaying animated:YES completion:nil];
            } else if (childBrowsable) {
                MediaElement *childElement = [[MediaLibraryStore sharedInstance] findElementById:childId];
                if (childElement && childElement.items.count > 0) {
                    CPListTemplate *subTemplate = [strongSelf buildListTemplateForElement:childElement];
                    [strongSelf.interfaceController pushTemplate:subTemplate animated:YES completion:nil];
                }
            }

            completionHandler();
        };

        // Load artwork asynchronously
        if (child.artwork) {
            [self loadArtworkFromURL:child.artwork completion:^(UIImage *image) {
                if (image) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        [item setImage:image];
                    });
                }
            }];
        }

        [listItems addObject:item];
    }

    CPListSection *section = [[CPListSection alloc] initWithItems:listItems];
    CPListTemplate *listTemplate = [[CPListTemplate alloc] initWithTitle:element.title ?: @""
                                                               sections:@[section]];

    // Set tab image based on media type
    listTemplate.tabImage = [self tabImageForMediaType:element.mediaType];

    return listTemplate;
}

#pragma mark - Helpers

- (UIImage *)tabImageForMediaType:(NSString *)mediaType {
    if ([mediaType isEqualToString:@"podcast"]) {
        return [UIImage systemImageNamed:@"mic"];
    } else if ([mediaType isEqualToString:@"radio"]) {
        return [UIImage systemImageNamed:@"radio"];
    } else if ([mediaType isEqualToString:@"album"]) {
        return [UIImage systemImageNamed:@"square.stack"];
    } else if ([mediaType isEqualToString:@"artist"]) {
        return [UIImage systemImageNamed:@"person"];
    } else if ([mediaType isEqualToString:@"genre"]) {
        return [UIImage systemImageNamed:@"guitars"];
    } else if ([mediaType isEqualToString:@"playlist"]) {
        return [UIImage systemImageNamed:@"music.note.list"];
    }
    return [UIImage systemImageNamed:@"music.note"];
}

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

@end
