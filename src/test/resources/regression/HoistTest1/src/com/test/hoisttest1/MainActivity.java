package com.test.hoisttest1;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;

import android.telephony.gsm.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

public class MainActivity extends Activity
{
    private Controller mController;    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
	mController = new Controller(this);
	mController.initCam();
    }
}
