package com.test.lifecycleandinterfacecallback;

import android.hardware.Camera;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.content.Context;
import android.util.Log;
import android.hardware.Camera.PictureCallback;

public class Controller implements ComponentCallbacks {

    private Camera mCamera;
    
    public Controller(Context ctx) {
      ctx.registerComponentCallbacks(this);
    }

    public void initCam() {
      if (mCamera == null) mCamera = Camera.open();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
      if (mCamera != null) {
        PictureCallback cb = new PictureCallback() {
	  @Override
	  public void onPictureTaken(byte[] data, Camera camera) {}
	};
	mCamera.takePicture(null, null, cb);
      }
    } 
    
    @Override
    public void onLowMemory() {}
}
