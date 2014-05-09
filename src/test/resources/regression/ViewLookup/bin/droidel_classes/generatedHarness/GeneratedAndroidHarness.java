package generatedHarness;

import stubs.*;

public final class GeneratedAndroidHarness {

  public static android.content.ComponentCallbacks extracted_2;
  public static android.hardware.Camera.PictureCallback extracted_1;

  public static void androidMain() {
    try {
      android.view.View loc_1 = stubs.GeneratedAndroidStubs.findViewById(-1);
      com.test.viewlookup.MainActivity loc_2 = new com.test.viewlookup.MainActivity();
      android.os.Bundle loc_3 = android.os.Bundle.EMPTY;
      android.content.res.Configuration loc_4 = new android.content.res.Configuration();
      android.hardware.Camera loc_5 = android.hardware.Camera.open();
      loc_2.onDestroy();
      loc_2.onCreate(loc_3);
      extracted_1.onPictureTaken(new byte[1], loc_5);
      extracted_2.onLowMemory();
      extracted_2.onConfigurationChanged(loc_4);
    }
    catch (Exception e) {
    }
  }
}
