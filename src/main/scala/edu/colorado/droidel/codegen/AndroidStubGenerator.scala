package edu.colorado.droidel.codegen

import com.ibm.wala.types.MethodReference
import edu.colorado.droidel.util.Types._
import java.io.File
import com.ibm.wala.ssa.SSAInvokeInstruction
import com.ibm.wala.ssa.SymbolTable
import com.ibm.wala.classLoader.IMethod

trait AndroidStubGenerator {
  /** mapping from MethodReference to stub -> function deciding whether (and how) a given instruction should be stubbed */
  def generateStubs() : (Map[IMethod,(SSAInvokeInstruction, SymbolTable) => Option[Patch]],
                         /** list of files created during stub generation */
                         List[File])
}