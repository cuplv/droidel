package generatedHarness;

import stubs.*;

public final class GeneratedAndroidHarness {

  public static android.hardware.Camera.PictureCallback extracted_2;
  public static android.content.ComponentCallbacks extracted_1;

  public static void androidMain() {
    try {
      android.view.View loc_1 = stubs.GeneratedAndroidStubs.findViewById(-1);
      com.test.lifecycleandinterfacecallback.MainActivity loc_2 = new com.test.lifecycleandinterfacecallback.MainActivity();
      android.os.Bundle loc_3 = android.os.Bundle.EMPTY;
      java.lang.String loc_4 = new java.lang.String();
      android.content.SharedPreferences loc_5 = null;
      android.hardware.Camera loc_6 = android.hardware.Camera.open();
      android.content.res.Configuration loc_7 = new android.content.res.Configuration();
      loc_2.myOnClick(loc_1);
      loc_2.onSharedPreferenceChanged(loc_5, loc_4);
      loc_2.onDestroy();
      loc_2.onCreate(loc_3);
      loc_2.onSharedPreferenceChanged(loc_5, loc_4);
      extracted_1.onLowMemory();
      extracted_1.onConfigurationChanged(loc_7);
      extracted_2.onPictureTaken(new byte[1], loc_6);
    }
    catch (Exception e) {
    }
  }
}
