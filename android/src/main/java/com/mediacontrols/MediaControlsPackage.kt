package com.mediacontrols

import androidx.media3.common.util.UnstableApi
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import java.util.HashMap

class MediaControlsPackage : BaseReactPackage() {
    @UnstableApi
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == MediaControlsModule.NAME) {
      MediaControlsModule(reactContext)
    } else {
      null
    }
  }

    @UnstableApi
    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      val moduleInfos: MutableMap<String, ReactModuleInfo> = HashMap()
      moduleInfos[MediaControlsModule.NAME] = ReactModuleInfo(
        MediaControlsModule.NAME,
        MediaControlsModule.NAME,
        false,  // canOverrideExistingModule
        false,  // needsEagerInit
        false,  // isCxxModule
        true // isTurboModule
      )
      moduleInfos
    }
  }
}
