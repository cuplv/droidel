package edu.colorado.droidel.codegen

import com.ibm.wala.classLoader.{IClass, IField, IMethod}
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.types.TypeReference
import edu.colorado.droidel.codegen.TypeInhabitor._
import edu.colorado.walautil.Types.MMap
import edu.colorado.walautil.{ClassUtil, Util}

import scala.collection.JavaConversions._

object TypeInhabitor {
  val DEBUG = false
}

/** class for solving the type inhabitation problem at the Java source level -- given a class hierarchy and a desired type T, generate 
 *  a valid Java expression whose type is T */
/** @param reuseInhabitants - should we create a fresh allocation site for each type, or re-use ones we've cached? */
class TypeInhabitor(reuseInhabitants : Boolean = true) {
  
  // type aliases to make some type signatures more clear
  type Expression = String
  type Statement = String
  type VarName = String
      
  // map from types to local or static variable names that we can re-use
  val inhabitantCache : MMap[IClass,Expression] = Util.makeMap[IClass,Expression]

  private var localVarCounter = 0
  private val NULL = "null"

  /** 
   * @return an expression of type @param typeReference and a list of allocations required to produce the expression
   * @param t - the type to be inhabited
   * @param allocs - optional list of allocations that will have been performed before this inhabitation
   * @param doAllocAndReturnVar - if true, the expression returned will always be a variable, and the list of allocs
   * returned will contain the assignment of variable to the expression of the appropriate type. if false, the expression
   * returned will be a function call or allocation expression instead of a variable. in addition, this expression will
   * *not* be cached in the inhabitant cache  
   * TODO: get rid of the silly boolean flag and adopt a single usage model
   * TODO: lift allocs to an instance field instead?
   **/
  def inhabit(t : TypeReference, cha : IClassHierarchy, allocs : List[Statement] = Nil, 
      doAllocAndReturnVar : Boolean = true) : (Expression, List[Statement]) = inhabitInternal(t, cha, allocs, doAllocAndReturnVar) match {
    case (Some(inhabitant), allocs) => (inhabitant, allocs)
    case (None, _) => sys.error(s"Couldn't inhabit type $t")
  }
  
  def inhabitFunctionCall(m : IMethod, receiver : Option[VarName], cha : IClassHierarchy, allocs : List[Statement]) : (Expression, List[Statement]) = {
    val (args, newAllocs) = inhabitArgs(m, cha, allocs)
    val invoke = s".${m.getName()}$args"
    receiver match {
      case Some(receiver) =>
        assert(receiver != NULL, s"Trying to inhabit call to $m with null")
        (s"$receiver$invoke", newAllocs)
      case None =>
        assert(m.isStatic())
        (s"${ClassUtil.deWalaifyClassName(m.getDeclaringClass())}$invoke", newAllocs)
    }    
  }
  
  def mkAssign(lhsType : IClass, rhsExpr : Expression) : (Statement, String) = {
    def mkFreshLocal() : VarName = { localVarCounter += 1; s"loc_$localVarCounter" }
    val freshLocal = mkFreshLocal
    (mkAssign(ClassUtil.deWalaifyClassName(lhsType), freshLocal, rhsExpr), freshLocal)
  }
  
  private def mkAssign(lhsType : String, lhs : VarName, rhs : Expression) : Statement = s"$lhsType $lhs = $rhs"    
 
  private def inhabitInternal(t : TypeReference, cha : IClassHierarchy, allocs : List[Statement], 
      doAllocAndReturnVar : Boolean) : (Option[Expression],List[Statement]) =
    if (t.isPrimitiveType()) (Some(inhabitPrimitiveType(t)), allocs)
    else if (t.isArrayType()) (Some(inhabitArrayType(t)), allocs)
    else cha.lookupClass(t) match {
      case null => (Some(NULL), allocs) // can't resolve type, so clearly can't inhabit it
      case c => inhabitReferenceType(c, cha, allocs, doAllocAndReturnVar)
    }

  private def inhabitStaticFieldRead(f : IField) : Expression = {
    require(f.isStatic())
    s"${ClassUtil.deWalaifyClassName(f.getDeclaringClass())}.${f.getName().toString()}" 
  }
  
  private val currentlyInhabiting = Util.makeSet[IClass]
  
  private def printInhabitFailMsg(clazz : IClass) : Unit =
    println(s"Could not figure out how to inhabit type $clazz; returning null")
  
  private def inhabitReferenceType(clazz : IClass, cha : IClassHierarchy, allocs : List[Statement], 
      doAllocAndReturnVar : Boolean = true) : (Option[Expression], List[Statement]) = {
    if (reuseInhabitants && inhabitantCache.contains(clazz)) (Some(inhabitantCache(clazz)), allocs)    
    else {
      // TODO: backtrack in the case of recursive inhabitation rather than giving up
      val (inhabitant, newAllocs) = if (!currentlyInhabiting.add(clazz)) (NULL, allocs) else clazz match {
        case clazz if !clazz.isPublic() =>
          if (DEBUG) printInhabitFailMsg(clazz)
          (inhabitCast(NULL, clazz), allocs) // TODO: return None and backtrack here instead?
        case clazz if ClassUtil.isInnerOrEnum(clazz) => 
          // TODO: support inhabiting inner classes and enums
          if (DEBUG) printInhabitFailMsg(clazz)
          (inhabitCast(NULL, clazz), allocs) // TODO: return None and backtrack here instead?
        case clazz if clazz.isInterface() || clazz.isAbstract() => 
          inhabitAbstractOrInterfaceType(clazz, cha, allocs) match {
            case (Some(inhabitant), allocs) => (inhabitant, allocs)
            case (None, _ ) => 
              printInhabitFailMsg(clazz)
              (inhabitCast(NULL, clazz), allocs)
          } 
        case _ =>
          val typ = clazz.getReference()     
          // look for a public static field of the type
          // TODO: handle subtyping
          val publicStaticFlds = clazz.getAllStaticFields().collect({
            case f if f.isPublic() && f.getFieldTypeReference() == clazz.getReference() => f 
          })
          
          if (!publicStaticFlds.isEmpty) {        
            // TODO: consider picking all fields?
            // just picking one of the fields for now
            (inhabitStaticFieldRead(publicStaticFlds.head), allocs)        
          } else {        
            // find a public constructor for the type
            val constructors = clazz.getDeclaredMethods().filter(m =>
              m.isPublic() && m.isInit() && !ClassUtil.getNonReceiverParameterTypes(m).contains(typ)
            )     
            if (!constructors.isEmpty) {
              // for now, just try constructor with least number of args and give up if it doesn't work
              // TODO: backtrack upon failure
              val easiestConstructor = constructors.minBy(c => c.getNumberOfParameters())
              inhabitAllocation(easiestConstructor, cha, allocs)
            } else {                    
              // can't find a public constructor, try a static factory
              val staticFactories = clazz.getAllMethods().filter(m =>
                // TODO: check for covariant return types
                m.isPublic() && m.isStatic() && m.getReturnType() == typ && !ClassUtil.getParameterTypes(m).contains(typ)
              )
              if (staticFactories.isEmpty) { // TODO: backtrack on failure
                if (DEBUG) printInhabitFailMsg(clazz)
                (inhabitCast(NULL, clazz), allocs)
              } else {
                val easiestFactory = staticFactories.minBy(f => f.getNumberOfParameters())
                inhabitFunctionCall(easiestFactory, None, cha, allocs)
              }
            }
          }
      }
      
      currentlyInhabiting.remove(clazz)
      if (doAllocAndReturnVar && clazz.isPublic() &&
          // hack! protected enums are weird and can cause problems. try to avoid this
          !(ClassUtil.isInnerOrEnum(clazz) && inhabitant == NULL)) { 
        // emit an assignment to the inhabitant and return the LHS of the assignment as the inhabitant
        val (assign, freshLocal) = mkAssign(clazz, inhabitant)  
        inhabitantCache.put(clazz, freshLocal) // cache the LHS 
        (Some(freshLocal), assign :: newAllocs)
      } else (Some(inhabitant), newAllocs) // don't emit an assignment and don't cache
    }   
  }
  
  private def inhabitAllocation(m : IMethod, cha : IClassHierarchy, allocs : List[Statement]) : (Expression, List[Statement]) = {
    val (args, newAllocs) = inhabitArgs(m, cha, allocs)   
    (s"new ${ClassUtil.deWalaifyClassName(m.getDeclaringClass())}$args", newAllocs)
  }       
  
  private def inhabitArgs(m : IMethod, cha : IClassHierarchy, allocs : List[Statement]) : (String, List[Statement]) = {
    // for non-static method, first param is always receiver, which should be synthesized elsewhere
    val firstParam = if (m.isStatic()) 0 else 1
    // foldRight because each arg is prepended to list, so we want to start from the last arg and work backward
    val (paramBindings, newAllocs) = (firstParam to m.getNumberOfParameters() - 1).foldRight (List.empty[String], allocs) ((paramIndex, l) => { 
      val (inhabitant, newAllocs) = inhabit(m.getParameterType(paramIndex), cha, l._2)
      (inhabitant :: l._1, newAllocs)
    })
    (s"(${Util.toCSVStr(paramBindings)})", newAllocs)
  }

  // TODO: there are lots of options here. we could return null, pick one subclass, pick all subclasses, etc.
  // for now, just pick one one subclass and instantiate it
  private def findConcreteSubclass(clazz : IClass, cha : IClassHierarchy) : Option[IClass] = {
    require(clazz.isAbstract() || clazz.isInterface())
    val choices = if (clazz.isInterface()) cha.getImplementors(clazz.getReference()) else cha.computeSubClasses(clazz.getReference()) 
    choices match {
      case null => None
      case subs =>
        val concreteSubs = subs.filter(sub => !sub.isAbstract() && !sub.isInterface() && sub.isPublic() && !currentlyInhabiting.contains(sub))
        if (concreteSubs.isEmpty) None
        else Some(concreteSubs.head)
    }
  }
  
  private def inhabitAbstractOrInterfaceType(interface : IClass, cha : IClassHierarchy, allocs : List[Statement]) : (Option[Expression], List[Statement]) = 
    findConcreteSubclass(interface, cha) match {
      case Some(clazz) => inhabitReferenceType(clazz, cha, allocs)
      case None =>         
        if (DEBUG) printInhabitFailMsg(interface)
        // can't find anything concrete. have to return null or else we won't be able to generate a harness that compiles
        // TODO: return None and backtrack here?
        (Some(inhabitCast(NULL, interface)), allocs)
    }

  // create a cast expression (@param typ) @param exp
  def inhabitCast(exp : Expression, typ : IClass) : Expression =
    if (typ.isPublic) s"(${ClassUtil.deWalaifyClassName(typ)}) $exp"
    else exp
  
  // inhabit a primitive type with its default value
  private def inhabitPrimitiveType(t : TypeReference) : Expression = t match {
    case TypeReference.Int => "0"
    case TypeReference.Boolean => "false"
    case TypeReference.Float => "0f"
    case TypeReference.Long => "0l"
    case TypeReference.Double => "0.0"
    case TypeReference.Char => "\u0000" 
    case TypeReference.Byte => "0"
    case TypeReference.Short => "0"    
    case t => sys.error(s"Getting default assignment for type $t")
  }
  
  private def expandPrimitiveTypeName(t : TypeReference) : String = t match {
    case TypeReference.Int => "int"
    case TypeReference.Boolean => "boolean"
    case TypeReference.Float => "float"
    case TypeReference.Long => "long"
    case TypeReference.Double => "double"
    case TypeReference.Char => "char"
    case TypeReference.Byte => "byte" 
    case TypeReference.Short => "short"
    case t => sys.error(s"Getting expanded name for primitve type $t")
  }
  
  private def inhabitArrayType(t : TypeReference) : Expression = {
    require(t.isArrayType())
    assert(t.getDimensionality() <= 1, "Unhandled: >1D arrays")
    val innerType = t.getArrayElementType() match {
      case t if t.isPrimitiveType() => expandPrimitiveTypeName(t)
      case t => ClassUtil.deWalaifyClassName(t.getArrayElementType())
    }
    s"new $innerType[1]"
  }    
  
}
