package edu.colorado.droidel.parser

abstract class ManifestEntry(val packageName : String, private val name : String) {
  def getPackageQualifiedName() : String = {
    if (name.contains(packageName) || name.indexOf('.') != -1) name
    else s"$packageName.$name"
  }
  
  override def toString() : String = getPackageQualifiedName
}