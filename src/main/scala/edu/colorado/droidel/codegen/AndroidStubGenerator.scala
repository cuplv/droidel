package edu.colorado.droidel.codegen

import com.ibm.wala.types.MethodReference
import edu.colorado.droidel.util.Types._
import java.io.File
import com.ibm.wala.ssa.SSAInvokeInstruction
import com.ibm.wala.ssa.SymbolTable
import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.ssa.IR

trait AndroidStubGenerator {
  type TryCreatePatch = (SSAInvokeInstruction, IR) => Option[Patch]
  type StubMap = Map[IMethod, TryCreatePatch]  
  
  /** mapping from MethodReference to stub -> function deciding whether (and how) a given instruction should be stubbed,
   *  and alist of files created during stub generation 
   */          
  def generateStubs(stubMap : StubMap = Map.empty[IMethod, TryCreatePatch], generatedStubs : List[File] = Nil) : (StubMap, List[File])
                         
  // TODO: factor out some of the functionality for generating Java code for stubs/compiling into this trait or another one                                                  
}