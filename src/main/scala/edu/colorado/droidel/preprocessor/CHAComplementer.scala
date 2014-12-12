package edu.colorado.droidel.preprocessor

import java.io.File

import edu.colorado.walautil.JavaUtil

import scala.sys.process._

/** Use JPhantom (https://github.com/gbalats/jphantom) [Balatsouras and Smaragdakis OOPSLA '13]
 *  to complement class hierarchy by filling in missing classes. This is very important because
 *  WALA's class hierarchy analysis silently discards classes whose superclass cannot be found */
class CHAComplementer(appJar : File, droidelHome : String, libJars : List[File], jPhantomOutDir : File) {
  
  def complement() : Boolean = {
    val JPHANTOM_JAR = s"$droidelHome/lib/jphantom/build/jar/jphantom.jar"
    assert(new File(JPHANTOM_JAR).exists(), s"Can't find JPhantom; expecting the JAR at $JPHANTOM_JAR")
    // path to script that runs JPhantom
    val JPHANTOM = s"java -jar $JPHANTOM_JAR"
    
    val jPhantomIn = "jphantom_in.jar"

    // merge app JAR on top of Android JAR, placing output in jPhantomIn      
    JavaUtil.mergeJars(appJar :: libJars, jPhantomIn, duplicateWarning = false)
        
    if (!jPhantomOutDir.exists()) jPhantomOutDir.mkdir() 
    val jPhantomOutJar = "jphantom_out.jar"
    val jPhantomOutJarPath = s"${jPhantomOutDir.getAbsolutePath()}/$jPhantomOutJar"
    // TODO: using command line for now, but would like to import the JPhantom code and call it directly instead
    // problem is that I get an IncompatibleClassChangeException if I try this. some other dependencies (dex2jar)
    // use a different version of ASM than JPhantom does, and trying to set up the classpath so it loads
    // the correct version of ASM for each library is too tricky
    
    // run JPhantom on the merged JAR, placing the output in the appropriate directory
    println("Running JPhantom")
    val cmd = s"$JPHANTOM ${jPhantomIn} -o $jPhantomOutJarPath"

   val phantomizedJar = new File(jPhantomOutJarPath)
   val res = try {
      val output = cmd.!! // run JPhantom
      println(output)
      if (phantomizedJar.exists()) {
        // unpack JPhantom's output JAR in the specified output directory
        Process(Seq("jar", "xf", jPhantomOutJar), new File(jPhantomOutDir.getAbsolutePath())).!!
        // delete the output JAR; we don't need it now that it was unpacked
        phantomizedJar.delete()
        true
      } else {
        println("JPhantom completed, but phantomized JAR does not exist. Continuing without it")
        false
      }
    } catch {
      case e : Throwable => 
        println(s"JPhantom failed: $e. Continuing")
        if (phantomizedJar.exists()) phantomizedJar.delete()
        // JPhantom didn't work. fail gracefully by extracting the input JAR in the JPhantom directory and moving on
        JavaUtil.extractJar(new File(jPhantomIn), jPhantomOutDir.getAbsolutePath())
        true
    }    
   
    // delete the input JAR to JPhantom that we created
    new File(jPhantomIn).delete()
    res
  }
  
}
