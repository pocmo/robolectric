package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.Q;

import android.media.AudioSystem;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow for {@link AudioSystem}. */
@Implements(value = AudioSystem.class)
public class ShadowAudioSystem {

  @Implementation(minSdk = Q)
  protected static int native_get_FCC_8() {
    // Return the value hard-coded in native code:
    // https://cs.android.com/android/platform/superproject/+/master:system/media/audio/include/system/audio-base.h?q=symbol:FCC_8
    return 8;
  }
}
