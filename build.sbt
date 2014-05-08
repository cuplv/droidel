name := "Droidel"

version := "0.1-SNAPSHOT"

organization := "University of Colorado Boulder"

scalaVersion := "2.10.2"

resolvers += "Local Maven Repository" at "file:///" + Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
	"com.ibm.wala" % "com.ibm.wala.shrike" % "1.3.4-SNAPSHOT",
	"com.ibm.wala" % "com.ibm.wala.util" % "1.3.4-SNAPSHOT",
	"com.ibm.wala" % "com.ibm.wala.core" % "1.3.4-SNAPSHOT",
	"com.squareup" % "javawriter" % "2.2.1")

// uncomment these and run 'sbt eclipse' to generate eclipse project
//EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

//EclipseKeys.withSource := true