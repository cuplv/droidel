package edu.colorado.droidel.parser

import edu.colorado.walautil.Util

abstract class LayoutElement(val typ : String, val declFile : String, val name : String, val id : Option[Int]) {
  protected def opt2str[T](opt : Option[T]) : String = (if (opt.isDefined) " " else "") + opt.getOrElse("")
  override def toString : String = "{" + typ + " " + name + opt2str(id) + "}"
  // purposely not including name in hashCode or equals because we sometimes generate that manually
  override def hashCode : Int = Util.makeHash(List(typ, declFile, id))
  override def equals(other : Any) : Boolean = other match {
    case l : LayoutElement => typ == l.typ && declFile == l.declFile && id == l.id
    case _ => false
  } 
}


// abstraction of Android View class and its subclasses
class LayoutView(typ : String, declFile : String, name : String, id : Option[Int],
                 val text : Option[String], val onClick : Option[String]) extends LayoutElement(typ, declFile, name, id){
  override def toString : String = "{" + typ + " " + name + opt2str(id) + opt2str(text) + opt2str(onClick) + " " + declFile + "}"
  override def hashCode : Int = Util.makeHash(List(typ, declFile, id, text, onClick))
  override def equals(other : Any) : Boolean = other match {
    // purposely not including name because we sometimes generate that manually
    case l : LayoutView => text == l.text && onClick == l.onClick && super.equals(l)
    case _ => false
  } 
}

// abstraction of Android Fragment class and its subclasses
class LayoutFragment(typ : String, declFile : String, name : String, id : Option[Int]) extends LayoutElement(typ, declFile, name, id) {}

class LayoutInclude(declFile : String, id : Option[Int]) extends LayoutElement("", declFile, "", id) {
  override def toString : String = s"include $declFile"

}