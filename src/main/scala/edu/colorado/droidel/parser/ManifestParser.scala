package edu.colorado.droidel.parser

import scala.xml.Node
import java.io.File
import scala.xml.XML
import ManifestParser._

object ManifestParser {
  private val DEBUG = false
}

class ManifestParser extends AndroidParser {
  def parseAndroidManifest(appDir : File) : AndroidManifest = {
    val manifestFile = new File(s"${appDir.getAbsolutePath()}/AndroidManifest.xml")
    assert(manifestFile.exists(), s"Couldn't find android manifest file ${manifestFile.getAbsolutePath()}")
      
    val xml = XML.loadFile(manifestFile)
    val packageName = xml.attributes("package").head.text
        
    val targetSdkVersion = {
      val sdkUsage = xml \ "uses-sdk"
      // defaults to 1 if not declared -- see http://developer.android.com/guide/topics/manifest/uses-sdk-element.html
      if (sdkUsage.isEmpty) "1"
      else 
        // TODO: get minSDKVersion -- this is what targetSDK defaults to if not declared
        getAndroidPrefixedAttrSingle(sdkUsage.head, "targetSdkVersion")      
    }

    val MAIN_ACTION = "android.intent.action.MAIN"
    val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"
    // parse applications
    val apps = xml \ "application"
    assert(apps.size == 1, s"Expecting only one application, but found ${apps.size}")
    val app = apps.head
    assert(isEnabled(app), "Handle disabled apps")
    
    def parseClassName(name : String) : String =
      // "." is shorthand for the package name (see http://developer.android.com/guide/topics/manifest/activity-element.html)
      if (name.startsWith(".")) packageName + name 
      else name
    
    val applications = getAndroidPrefixedAttrOption(app, "name") match {
      case Some(attr) => List(new ManifestApplication(packageName, parseClassName(attr)))
      case None => Nil
    }
      
    // parse enabled activities
    // TODO: parse other activity stuff? parsing configChanges seems important, at the very least
    val activities = (app \ "activity").foldLeft (List.empty[ManifestActivity]) ((l, a) => 
      if (!isEnabled(a)) l // ignore disabled Activity's 
      else {
        // parse intent-filter to determine which is the main activity
        // TODO: need to understand difference between MAIN/LAUNCHER/DEFAULT better. For now, just assuming Activity is main iff it has DEFAULT and LAUNCHER
        val intentFilters = (a \ "intent-filter")
        val actions = (intentFilters \ "action").map(n => getAndroidPrefixedAttrSingle(n, "name"))
        val categories = (intentFilters \ "category").map(n => getAndroidPrefixedAttrSingle(n, "name"))
        val isMain = actions.contains(MAIN_ACTION) && categories.contains(LAUNCHER_CATEGORY)        
        new ManifestActivity(packageName, parseClassName(getAndroidPrefixedAttrSingle(a, "name")), isMain) :: l
      }
    )        
    
    // check that there's only one main Activity
    if (DEBUG) {
      val numMainActs = activities.filter(act => act.isMain).size    
      assert(numMainActs == 1, s"Expected exactly one main Activity, but found $numMainActs")
    }
      
    // TODO: parse Service's, ContentProvider's, e.t.c
    /*val receivers = (app \ "receiver")
    val services = (app \ "service")
    val providers = (app \ "provider")*/
    new AndroidManifest(packageName, targetSdkVersion, activities, applications)
  }
}


