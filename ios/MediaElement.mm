#import "MediaElement.h"

@implementation MediaElement

+ (nullable instancetype)fromDictionary:(NSDictionary *)dict {
    if (!dict || ![dict isKindOfClass:[NSDictionary class]]) {
        return nil;
    }

    MediaElement *element = [[MediaElement alloc] init];
    element.itemId = dict[@"id"] ?: @"root";
    element.title = dict[@"title"];
    element.artist = dict[@"artist"];
    element.album = dict[@"album"];
    element.artwork = dict[@"artwork"];
    element.mediaType = dict[@"mediaType"];

    if (dict[@"duration"] != nil) {
        element.duration = dict[@"duration"];
    }

    if (dict[@"playable"] != nil) {
        element.playable = [dict[@"playable"] boolValue];
    }

    if (dict[@"browsable"] != nil) {
        element.browsable = [dict[@"browsable"] boolValue];
    }

    NSArray *itemsArray = dict[@"items"];
    if (itemsArray && [itemsArray isKindOfClass:[NSArray class]]) {
        NSMutableArray<MediaElement *> *children = [NSMutableArray array];
        for (NSDictionary *childDict in itemsArray) {
            MediaElement *child = [MediaElement fromDictionary:childDict];
            if (child) {
                [children addObject:child];
            }
        }
        element.items = children;
    }

    return element;
}

@end
