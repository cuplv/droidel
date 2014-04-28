package edu.colorado.droidel.parser

class ManifestActivity(val packageName : String, private val name : String, val isMain : Boolean) {
  def getPackageQualifiedName() : String = {
    if (name.contains(packageName) || name.indexOf('.') != -1) name
    else s"$packageName.$name"
  }
}
