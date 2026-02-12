#import <Foundation/Foundation.h>

@interface MediaElement : NSObject

@property (nonatomic, strong) NSString *itemId;
@property (nonatomic, strong, nullable) NSString *title;
@property (nonatomic, strong, nullable) NSString *artist;
@property (nonatomic, strong, nullable) NSString *album;
@property (nonatomic, strong, nullable) NSString *artwork;
@property (nonatomic, strong, nullable) NSNumber *duration;
@property (nonatomic, assign) BOOL playable;
@property (nonatomic, assign) BOOL browsable;
@property (nonatomic, strong, nullable) NSString *mediaType;
@property (nonatomic, strong, nullable) NSArray<MediaElement *> *items;

+ (nullable instancetype)fromDictionary:(NSDictionary *)dict;
- (NSDictionary *)toDictionary;

@end
