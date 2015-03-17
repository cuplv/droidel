package droidelhelpers;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Context;
import android.view.View;

public interface DroidelStubs {
    
    public Application getApplication(String className);

    public Activity getActivity(String className);

    public Service getService(String className);

    public BroadcastReceiver getBroadcastReceiver(String className);

    public ContentProvider getContentProvider(String className);

    public android.app.Fragment getAppFragment(String className);

    public android.support.v4.app.Fragment getSupportFragment(String className);

    public View inflateViewById(int id, Context ctx);

    public void callXMLRegisteredCallback(Context ctx, View v);

}
