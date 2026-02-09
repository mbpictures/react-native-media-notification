#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <CarPlay/CarPlay.h>

API_AVAILABLE(ios(14.0))
@interface CarPlaySceneDelegate : NSObject

+ (void)connectWithInterfaceController:(CPInterfaceController *)interfaceController;
+ (void)disconnect;

@end
