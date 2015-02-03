package edu.colorado.droidel.instrumenter

import java.io.{BufferedWriter, File, OutputStreamWriter}

import com.ibm.wala.classLoader.{IField, IMethod}
import com.ibm.wala.shrikeBT.MethodEditor.{Output, Patch}
import com.ibm.wala.shrikeBT.analysis.Verifier
import com.ibm.wala.shrikeBT.shrikeCT.{ClassInstrumenter, OfflineInstrumenter}
import com.ibm.wala.shrikeBT._
import com.ibm.wala.shrikeCT.ClassWriter.Element
import com.ibm.wala.shrikeCT.{ClassConstants, ClassReader, ClassWriter}
import com.ibm.wala.types.FieldReference
import edu.colorado.walautil.ClassUtil


// TODO: this really needs to be refactored to take less parameters and enable using different pieces of functionality
// individually
class BytecodeInstrumenter {
  val DEBUG = false
  val instrumenter = new OfflineInstrumenter(true)
  
  type ClassName = String
  
  def doIt(inJar : File, instrumentationMap : Map[ClassName, Map[IMethod,Iterable[(Int, Iterable[FieldReference])]]],
           stubMap : Map[ClassName,Map[IMethod, Iterable[(Int, Patch)]]],
           insertMap : Map[ClassName,Map[IMethod, Iterable[(Int, Patch)]]],
           cbsToMakePublic : Map[ClassName, Set[IMethod]], outJarName : String) : File = {
    require(inJar.exists(), s"Can't find inJar $inJar")
    // tell the instrumenter what classes we are going to instrument
    instrumenter.addInputJar(inJar)    
    // don't write unmodified classes into the output JAR
    instrumenter.setPassUnmodifiedClasses(false)
    val outJar = new File(outJarName)    
    instrumenter.setOutputJar(outJar)
    instrumenter.beginTraversal()
    
    if (DEBUG) println(s"Instrumentation map is $instrumentationMap")   
        
    @annotation.tailrec
    def instrumentRec() : Unit = instrumenter.nextClass() match {
      case null => ()
      case curClass =>
        val className = s"${curClass.getReader().getName()}.class"
        val toInstrument = instrumentationMap.getOrElse(className, Map.empty)
        val toStub = stubMap.getOrElse(className, Map.empty)
        val toInsert = insertMap.getOrElse(className, Map.empty)
        val toMakePublic = cbsToMakePublic.getOrElse(className, Set.empty[IMethod])        
        if (toInstrument.nonEmpty || toStub.nonEmpty || toMakePublic.nonEmpty || toInsert.nonEmpty)
          doInstrumentation(curClass, toInstrument, toStub, toInsert, toMakePublic)
        instrumentRec
    }                
    
    instrumentRec()
    instrumenter.close()
    outJar
  }
  
  private def makeMethodsPublic(methodNums : Set[Int], oldClassWriter : ClassWriter) : ClassWriter = {
    val classReader = new ClassReader(oldClassWriter.makeBytes())
    val classWriter = new ClassWriter()
    val iter = new ClassReader.AttrIterator()
      
    classWriter.setMajorVersion(classReader.getMajorVersion())
    classWriter.setMinorVersion(classReader.getMinorVersion())
    classWriter.setRawCP(classReader.getCP(), false)
    classWriter.setAccessFlags(classReader.getAccessFlags())
    classWriter.setNameIndex(classReader.getNameIndex())
    classWriter.setSuperNameIndex(classReader.getSuperNameIndex())
    classWriter.setInterfaceNameIndices(classReader.getInterfaceNameIndices())
      
    def isPublic(access : Int) : Boolean = (access & ClassConstants.ACC_PUBLIC) != 0
    def isProtected(access : Int) : Boolean = (access & ClassConstants.ACC_PROTECTED) != 0
    def isPrivate(access : Int) : Boolean = (access & ClassConstants.ACC_PRIVATE) != 0
    def makePublic(access : Int) : Int = access match {
      case access if isPublic(access) => access
      case access if isProtected(access) => (access & ~ClassConstants.ACC_PROTECTED) | ClassConstants.ACC_PUBLIC
      case access if isPrivate(access) => (access & ~ClassConstants.ACC_PRIVATE) | ClassConstants.ACC_PUBLIC
      case _ => 
        if (DEBUG) println(s"Warning: unknown access level $access. Bad decompilation suspected")
        access | ClassConstants.ACC_PUBLIC
    }
      
    def collectAttrs(methodNum : Int) : Array[Element] = {
      val elems = new Array[Element](iter.getRemainingAttributesCount())
      (0 to elems.length - 1).foreach(i => {
        elems(i) = new ClassWriter.RawElement(classReader.getBytes(), iter.getRawOffset(), iter.getRawSize())
        iter.advance()
      })
      elems
    }
      
    (0 to classReader.getFieldCount() - 1).foreach(i => 
      classWriter.addRawField(new ClassWriter.RawElement(classReader.getBytes(), classReader.getFieldRawOffset(i), classReader.getFieldRawSize(i)))
    )
      
    (0 to classReader.getMethodCount() - 1).foreach(i =>
      if (methodNums.contains(i)) {
        classReader.initMethodAttributeIterator(i, iter)
        // make the access modifier for the method public if it is not already
        val access = classReader.getMethodAccessFlags(i)
        val newAccess = makePublic(access)
        classWriter.addMethod(newAccess, classReader.getMethodName(i), classReader.getMethodType(i), collectAttrs(i))
      } else 
        classWriter.addRawMethod(new ClassWriter.RawElement(classReader.getBytes(), classReader.getMethodRawOffset(i), classReader.getMethodRawSize(i)))
    )
      
    classReader.initClassAttributeIterator(iter)      
    while (iter.isValid()) {                
      classWriter.addClassAttribute(new ClassWriter.RawElement(classReader.getBytes(), iter.getRawOffset(), iter.getRawSize()))
      iter.advance()
    }
      
    classWriter
  }   
  
  private def doInstrumentation(ci : ClassInstrumenter, toInstrument : Map[IMethod,Iterable[(Int, Iterable[FieldReference])]], 
                                toStub : Map[IMethod,Iterable[(Int, Patch)]],
                                toInsert : Map[IMethod,Iterable[(Int, Patch)]],
                                toMakePublic : Set[IMethod]) : Unit = {
    if (DEBUG) println(s"Instrumenting class ${ci.getReader().getName()}")
    def getMethod(methodData : MethodData, methods : Iterable[IMethod]) : Option[IMethod] = methods.find(m =>       
      methodData.getName() == m.getName().toString() && 
      methodData.getSignature() == m.getDescriptor().toString())
        
    def hasMethod(methodData : MethodData, methods : Iterable[IMethod]) : Boolean = getMethod(methodData, methods).isDefined
    
    def getInstrumentationFromMap[T](methodData : MethodData, map : Map[IMethod,Iterable[T]]) : Iterable[T] = {
      getMethod(methodData, map.keys) match {
        case Some(method) => map(method)
        case None => List.empty[T]
      }
    }
      
    val makePublicNums = (0 to ci.getReader().getMethodCount() - 1).foldLeft (Set.empty[Int]) ((makePublicNums, i) => {
      ci.visitMethod(i) match { // for each method in the bytecode class
        case null => makePublicNums
        case methodData =>
          val allocsTodo = getInstrumentationFromMap(methodData, toInstrument)
          val stubsTodo = getInstrumentationFromMap(methodData, toStub)
          val insertTodo = getInstrumentationFromMap(methodData, toInsert)
          val methodName = methodData.getName()
          if (allocsTodo.nonEmpty || stubsTodo.nonEmpty || toInsert.nonEmpty) {
            if (DEBUG) println(s"Instrumenting method $methodName")
            new Verifier(methodData).verify() // sanity check: verify the input bytecode          
              
            if (DEBUG) {
              val writer = new BufferedWriter(new OutputStreamWriter(System.out))
              new Disassembler(methodData).disassembleTo(writer)
              writer.flush()
            }          
              
            val methodEditor = new MethodEditor(methodData)
            methodEditor.beginPass()

            // transform each allocation x = new T() to { x := new T(); staticFieldName := x }
            // at the bytecode level, this is a transformation from new to { new T; dup; putstatic T staticFieldName }
            allocsTodo.foreach(pair => pair._2.foreach(staticField => 
              methodEditor.insertAfter(pair._1, new MethodEditor.Patch() {
                override def emitTo(o : Output) : Unit = {
                  if (DEBUG) println("Instrumenting extraction")
                  o.emit(DupInstruction.make(0)) // copy the allocation on the stack so we can do a put
                  val fieldName = staticField.getName().toString()                                        
                  val fieldDeclaringClass = ClassUtil.typeRefToBytecodeType(staticField.getDeclaringClass())
                  val fieldType = ClassUtil.typeRefToBytecodeType(staticField.getFieldType())
                  val isStatic = true
                  o.emit(PutInstruction.make(fieldType, fieldDeclaringClass, fieldName, isStatic))
                }         
              })
            ))
            
            // transform calls to findViewById/findFragmentById with a constant first argument to view-specialized stubs
            stubsTodo.foreach(pair => methodEditor.replaceWith(pair._1, pair._2))
            // prepend assignments to dependency-injected fields
            insertTodo.foreach(pair => methodEditor.insertAfter(pair._1, pair._2))

            methodEditor.applyPatches() // commit the changes
              
            if (DEBUG) {
              println("After transform:")          
              val writer = new BufferedWriter(new OutputStreamWriter(System.out))
              new Disassembler(methodData).disassembleTo(writer)
              writer.flush()
            }
                
            //assert(ci.isChanged(), s"Instrumentation of $methodName didn't change the method")
            if (DEBUG) println(s"Transformed method $methodName")
          }
          // done doing instrumentation (if we did it at all), now check to see if method needs to be made public
          if (hasMethod(methodData, toMakePublic)) {
            if (DEBUG) println(s"Changing access of $methodName to public")
            // if we don't do this, Shrike will refuse to emit the method in the case that all we're doing is making it public            
            methodData.setHasChanged()
            makePublicNums + i
          } else makePublicNums
      }
    })
      
    // did we instrument the method, or do we need to change it's access level?
    if (ci.isChanged()) {
    // yes. change method access levels if required and write out the transformed classes
      val writer = {
        val writer = ci.emitClass()
        if (!makePublicNums.isEmpty) makeMethodsPublic(makePublicNums, writer) else writer         
      }
      instrumenter.outputModifiedClass(ci, writer)
    }
  }
}
