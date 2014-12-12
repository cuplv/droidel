package com.test.lifecycleandinterfacecallback;
import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.telephony.gsm.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import android.widget.Button;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder;
import android.util.Log;


public class MainActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Controller mController;    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
	mSurfaceView = (SurfaceView) findViewById(R.id.mySurfaceView);
	mController = new Controller(this);
	addPreferencesFromResource(-1);
	getPreferenceManager().toString();
    	getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					  String key) {
	mSurfaceHolder = mSurfaceView.getHolder();
    }
    
    // manifest-registered event handler
    public void myOnClick(View view) {
	mController.initCam();
	if (view.getId() == R.id.myBtn) {
	    // this cast will fail unless we know about the
	    // manifest-declared Button myBtn
	    Button b = (Button) view;
	    // this will be a null dispatch unless
	    // we know that our Button object (or at least
	    // *some* Button object) can flow 
	    // to the view parameter
	    b.setText("hi");	    
	}	
    }

    @Override
    public void onDestroy() {
	// this will be null dispatch if we don't know to call onSharedPreferenceChange
	// because mSurfaceViewHolder will never be initialized
	if (mSurfaceHolder != null) {
	    boolean b = mSurfaceHolder.isCreating();
	    Log.d("onDestroy", "Surface holder creating? " + b);
	}
    }   
}
