package com.test.systemservice;

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
import android.accounts.AccountManager;
import android.accounts.Account;


public class MainActivity extends Activity {
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
	mSurfaceView = (SurfaceView) findViewById(R.id.mySurfaceView);
	mSurfaceHolder = mSurfaceView.getHolder();
	AccountManager mgr = (AccountManager) this.getSystemService(Context.ACCOUNT_SERVICE);

	
	Account[] accounts = mgr.getAccountsByType("sometype");
	AccountManager mgr2 = AccountManager.get(this);
	mgr2.getAccountsByType("sometype");
	
    }    

    @Override
    public void onDestroy() {
	boolean b = mSurfaceHolder.isCreating();
	Log.d("onDestroy", "Surface holder creating? " + b);
    }   
}
