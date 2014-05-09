package stubs;

import android.view.*;
import android.widget.*;

public final class GeneratedAndroidStubs {
  private static android.widget.LinearLayout __fake1;
  private static android.view.SurfaceView mySurfaceView;

  static {
    try {
      android.content.Context loc_1 = null;
      android.content.ContextWrapper loc_2 = new android.content.ContextWrapper(loc_1);
      android.content.Context loc_3 = loc_2;
      __fake1 = new android.widget.LinearLayout(loc_3);
      mySurfaceView = new android.view.SurfaceView(loc_3);
    }
    catch (Exception e) {
    }
  }

  public static android.view.View findViewById(int id) {
    switch (id) {
      case 2131034112: return mySurfaceView;
      default: return null;
    }
  }
  public static android.view.SurfaceView getView2131034112() {
    return mySurfaceView;
  }
}
