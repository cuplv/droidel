package generatedHarness;

import stubs.*;

public final class GeneratedAndroidHarness {

  public static android.hardware.Camera.PictureCallback extracted_2;
  public static android.content.ComponentCallbacks extracted_1;

  public static void androidMain() {
    try {
      android.view.View loc_1 = stubs.GeneratedAndroidStubs.findViewById(-1);
      com.test.protectedCallback.MainActivity loc_2 = new com.test.protectedCallback.MainActivity();
      android.os.Bundle loc_3 = android.os.Bundle.EMPTY;
      android.hardware.Camera loc_4 = android.hardware.Camera.open();
      android.content.res.Configuration loc_5 = new android.content.res.Configuration();
      loc_2.onDestroy();
      loc_2.onPause();
      loc_2.onCreate(loc_3);
      extracted_1.onLowMemory();
      extracted_1.onConfigurationChanged(loc_5);
      extracted_2.onPictureTaken(new byte[1], loc_4);
    }
    catch (Exception e) {
    }
  }
}
