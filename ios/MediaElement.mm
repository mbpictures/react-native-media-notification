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

- (NSDictionary *)toDictionary {
    NSMutableDictionary *dict = [NSMutableDictionary dictionary];
    dict[@"id"] = self.itemId;
    if (self.title) dict[@"title"] = self.title;
    if (self.artist) dict[@"artist"] = self.artist;
    if (self.album) dict[@"album"] = self.album;
    if (self.artwork) dict[@"artwork"] = self.artwork;
    if (self.mediaType) dict[@"mediaType"] = self.mediaType;
    if (self.duration) dict[@"duration"] = self.duration;
    dict[@"playable"] = @(self.playable);
    dict[@"browsable"] = @(self.browsable);

    if (self.items) {
        NSMutableArray *children = [NSMutableArray array];
        for (MediaElement *child in self.items) {
            [children addObject:[child toDictionary]];
        }
        dict[@"items"] = children;
    }

    return dict;
}

@end
