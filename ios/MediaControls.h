#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import "MediaControlsSpec.h"

@interface MediaControls : RCTEventEmitter <NativeMediaControlsSpec>
#else
@interface MediaControls : RCTEventEmitter <RCTBridgeModule>
#endif

@end
