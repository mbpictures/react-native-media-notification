#import <Foundation/Foundation.h>
#import "MediaElement.h"

extern NSString *const MediaLibraryUpdatedNotification;
extern NSString *const CarPlayItemSelectedNotification;

@interface MediaLibraryStore : NSObject

@property (nonatomic, strong, nullable) MediaElement *rootElement;

+ (instancetype)sharedInstance;
- (void)setLibrary:(MediaElement *)root;
- (nullable MediaElement *)findElementById:(NSString *)elementId;

@end
