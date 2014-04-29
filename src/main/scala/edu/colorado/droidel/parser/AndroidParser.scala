package edu.colorado.droidel.parser

import java.io.File
import scala.xml.NodeSeq
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.XML

abstract class AndroidParser {
  protected def getAttr(n : Node, attrName : String) : NodeSeq =  n \ attrName
  
  // read attributes prefixed with the "android:" namespace
  protected def getAndroidPrefixedAttr(n : Node, attrName : String) : NodeSeq = getAttr(n, s"@{${n.getNamespace("android")}}$attrName")
  
  // same as above, but assert that there is only one such attr and return its text
  protected def getAndroidPrefixedAttrSingle(n : Node, attrName : String) : String = {
    val seq = getAndroidPrefixedAttr(n, attrName)
    assert(seq.size == 1)
    seq.head.text
  }
  
  protected def getAndroidPrefixedAttrOption(n : Node, attrName : String) : Option[String] = {   
    val seq = getAndroidPrefixedAttr(n, attrName)
    assert(seq.size <= 1) // don't want multiple attrs or it won't be clear what to do
    if (seq.size == 1) Some(seq.head.text) else None
  }
  
  // parse the special "android:enabled" tag controlling whether the OS can instantiate apps/activities
  protected def isEnabled(n : Node) = getAndroidPrefixedAttr(n, "enabled") match {
    case enabled if enabled.isEmpty => true // default value of enabled is true
    case enabled => enabled.head.text == "true"
  }  
       
  protected def packageNameToPath(packageName : String) : String = packageName.replace('.', File.separatorChar)    
}
