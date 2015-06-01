name := "Droidel"

version := "0.1-SNAPSHOT"

organization := "University of Colorado Boulder"

scalaVersion := "2.10.2"

offline := true

resolvers += "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
        "University of Colorado Boulder" %% "walautil" % "0.1-SNAPSHOT",
	"com.ibm.wala" % "com.ibm.wala.shrike" % "1.3.7",
	"com.ibm.wala" % "com.ibm.wala.util" % "1.3.7",
	"com.ibm.wala" % "com.ibm.wala.core" % "1.3.7",
	"com.squareup" % "javawriter" % "2.2.1")
