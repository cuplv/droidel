package com.test.protectedCallback;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;

import android.telephony.gsm.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import android.widget.Button;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder;
import android.util.Log;


public class MainActivity extends Activity {
    private Controller mController;    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
	mController = new Controller(this);
    }

    @Override
    public void onPause() {
	Log.d("TAG", "Hi!");
    }

    @Override
    protected void onDestroy() {
	// this will be null dispatch if we can't call the
	// protected method onCreate
	// because mController will never be inialized
	mController.initCam();
    }   
}
