package edu.colorado.droidel.preprocessor

import java.util.jar.JarFile
import edu.colorado.droidel.util.JavaUtil
import java.io.File
import scala.sys.process._

/** Use JPhantom (https://github.com/gbalats/jphantom) [Balatsouras and Smaragdakis OOPSLA '13]
 *  to complement class hierarchy by filling in missing classes. This is very important because
 *  WALA's class hierarchy analysis silently discards classes whose superclass cannot be found */
class CHAComplementer(appJar : File, libJars : List[File], jPhantomOutDir : File) { 
  
  def complement() : Unit = {
    val JPHANTOM_JAR = "jphantom/build/jar/jphantom.jar"
    assert(new File(JPHANTOM_JAR).exists(), s"Can't find JPhantom; expecting the JAR at $JPHANTOM_JAR")
    // path to script that runs JPhantom
    val JPHANTOM = s"java -jar $JPHANTOM_JAR"
    
    val jPhantomIn = "jphantom_in.jar"

    // merge app JAR on top of Android JAR, placing output in jPhantomIn      
    JavaUtil.mergeJars(appJar :: libJars, jPhantomIn, duplicateWarning = false)
    
    if (!jPhantomOutDir.exists()) jPhantomOutDir.mkdir() 
    val jPhantomOutJar = "jphantom_out.jar"
    val jPhantomOutJarPath = s"${jPhantomOutDir.getAbsolutePath()}/$jPhantomOutJar"
    // TODO: using command line for now, but could load the JPhantom JAR dynamically instead
    // run JPhantom on the merged JAR, placing the output in the appropriate directory
    println("Running JPhantom")
    val cmd = s"$JPHANTOM ${jPhantomIn} -o $jPhantomOutJarPath"
    val output = cmd.!! // run JPhantom
    println(output)
    
    val phantomizedJar = new File(jPhantomOutJarPath)
    assert(phantomizedJar.exists(), s"Problem while running JPhantom; check output: $output")
    // unpack JPhantom's output JAR in the specified output directory
    Process(Seq("jar", "xvf", jPhantomOutJar), new File(jPhantomOutDir.getAbsolutePath())).!!
    // delete the output JAR; we don't need it now that it was unpacked
    phantomizedJar.delete()    
    // delete the input JAR to JPhantom that we created
    new File(jPhantomIn).delete()
  }
  
}
