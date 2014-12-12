import java.io.File

import edu.colorado.droidel.constants.DroidelConstants
import edu.colorado.droidel.driver.{AbsurdityIdentifier, AndroidAppTransformer, AndroidCGBuilder}
import edu.colorado.walautil.{Timer, Util}

import scala.collection.JavaConversions._

object Regression {

  def main(args: Array[String]) = {
    if (args.length == 0) sys.error("Usage: sbt test:run <android_jar>")
    else {
      val androidJar = new File(args(0))
      assert(androidJar.exists(), s"Couldn't find Android JAR ${androidJar.getAbsolutePath()}")
      val testPrefix = s"src${File.separator}test${File.separator}resources${File.separator}regression${File.separator}"

      val tests =
        Iterable("HoistTest1", "HoistTest2", "ProtectedCallback", "LifecycleAndInterfaceCallback", "ViewLookup",
                 "SupportFragment", "SystemService")

      // our tests should have all the library dependencies included, so we don't need JPhantom
      val useJPhantom = false
      tests.foreach(test => {
      	val testPath = s"$testPrefix$test"
      
      	val droidelOutBinDir = new File(s"${testPath}/${DroidelConstants.DROIDEL_BIN_SUFFIX}")
      	// clear Droidel output if it already exists
      	if (droidelOutBinDir.exists()) Util.deleteAllFiles(droidelOutBinDir)
      
      	// generate stubs and a specialized harness for the app
      	val transformer = new AndroidAppTransformer(testPath, androidJar,
                                                    droidelHome = ".",
      	                                            instrumentLibs = false,
                                                    useJPhantom = useJPhantom,
        // we don't want to keep the generated source files for the stubs/harness
                                                    cleanupGeneratedFiles = true)
      	transformer.transformApp() // do it
      
      	// make sure Droidel did something
      	assert(droidelOutBinDir.exists(), s"No Droidel output found in ${droidelOutBinDir.getAbsolutePath()}")
      
      	// now, build a call graph and points-to analysis with the generated stubs/harness 
      	val analysisScope = transformer.makeAnalysisScope(useHarness = true)

      	val timer = new Timer()
      	timer.start()
      	println("Building call graph")
      	val walaRes =
          new AndroidCGBuilder(analysisScope, transformer.harnessClassName, transformer.harnessMethodName)
          .makeAndroidCallGraph
      	timer.printTimeTaken("Building call graph")
        val packagePrefix = test.toLowerCase
        assert(walaRes.cg.exists(n => n.getMethod.getDeclaringClass.getName.toString.toLowerCase.contains(packagePrefix)),
               "No application classes reachable in call graph!")
      	
      	// walk over the call call graph / points-to analysis and check that they are free of absurdities
      	println("Checking for absurdities")
      	val absurdities = new AbsurdityIdentifier(transformer.harnessClassName).getAbsurdities(walaRes)
      	timer.printTimeTaken("Checking for absurdities") 
      	assert(absurdities.isEmpty, s"After harness generation, expected no absurdities for test $test")

      	// clean up after ourselves
      	Util.deleteAllFiles(droidelOutBinDir)
      	if (useJPhantom) {
      	  val jphantomBinDir = new File(s"${testPath}/${DroidelConstants.JPHANTOMIZED_BIN_SUFFIX}")
      	  if (jphantomBinDir.exists()) Util.deleteAllFiles(jphantomBinDir)
      	}
      })
    }
  }
  
}
