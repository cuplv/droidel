package edu.colorado.droidel.constants

object AndroidConstants {  
  // interesting classes
  val CONTEXT_TYPE = "android.content.Context"
  val ACTIVITY_TYPE = "android.app.Activity"
  val SERVICE_TYPE = "android.app.Service"
  val BROADCAST_RECEIVER_TYPE = "android.content.BroadcastReceiver"
  val CONTENT_PROVIDER_TYPE = "android.content.ContentProvider";
  val APPLICATION_TYPE = "android.app.Application";        
  val VIEW_TYPE = "android.view.View"    
    
  val APP_FRAGMENT_TYPE = "android.app.Fragment"
  // apparently much more commonly used because it has better backward compatibility
  val FRAGMENT_TYPE = "android.support.v4.app.Fragment"      
  val FRAGMENT_MANAGER_TYPE = "android.support.v4.app.FragmentManager"    

  // interesting methods
  val FIND_VIEW_BY_ID = "findViewById"
  val FIND_FRAGMENT_BY_ID = "findFragmentById" 
}
