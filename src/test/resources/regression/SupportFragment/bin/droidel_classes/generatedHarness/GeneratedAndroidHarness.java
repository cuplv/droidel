package generatedHarness;

import stubs.*;

public final class GeneratedAndroidHarness {

  public static android.hardware.Camera.PictureCallback extracted_2;
  public static android.content.ComponentCallbacks extracted_1;

  public static void androidMain() {
    try {
      android.view.View loc_1 = stubs.GeneratedAndroidStubs.findViewById(-1);
      com.test.supportfragment.MainActivity loc_2 = new com.test.supportfragment.MainActivity();
      com.test.supportfragment.MainFragment loc_3 = new com.test.supportfragment.MainFragment();
      android.os.Bundle loc_4 = android.os.Bundle.EMPTY;
      android.content.Context loc_5 = null;
      android.content.ContextWrapper loc_6 = new android.content.ContextWrapper(loc_5);
      android.content.Context loc_7 = loc_6;
      android.support.v4.view.PagerTitleStrip loc_8 = new android.support.v4.view.PagerTitleStrip(loc_7);
      android.view.ViewGroup loc_9 = loc_8;
      com.android.internal.policy.impl.PhoneLayoutInflater loc_10 = new com.android.internal.policy.impl.PhoneLayoutInflater(loc_7);
      android.view.LayoutInflater loc_11 = loc_10;
      android.hardware.Camera loc_12 = android.hardware.Camera.open();
      android.content.res.Configuration loc_13 = new android.content.res.Configuration();
      loc_3.onClick(loc_1);
      loc_3.onCreateView(loc_11, loc_9, loc_4);
      loc_2.onDestroy();
      loc_2.onCreate(loc_4);
      loc_3.onClick(loc_1);
      extracted_1.onLowMemory();
      extracted_1.onConfigurationChanged(loc_13);
      extracted_2.onPictureTaken(new byte[1], loc_12);
    }
    catch (Exception e) {
    }
  }
}
