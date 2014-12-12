package com.test.supportfragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.content.Context;

import android.telephony.gsm.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import android.widget.Button;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder;
import android.util.Log;


public class MainActivity extends FragmentActivity {
    private MainFragment fragment;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Controller mController;    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
	fragment = (MainFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentMain);
	fragment.toString();

	mSurfaceView = (SurfaceView) findViewById(R.id.mySurfaceView);
	mSurfaceHolder = mSurfaceView.getHolder();
	mController = new Controller(this);
    }    

    @Override
    public void onDestroy() {
	mController.initCam();
	boolean b = mSurfaceHolder.isCreating();
	Log.d("onDestroy", "Surface holder creating? " + b);
    }   
}
