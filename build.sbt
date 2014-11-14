name := "Droidel"

version := "0.1-SNAPSHOT"

organization := "University of Colorado Boulder"

scalaVersion := "2.10.2"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


libraryDependencies ++= Seq(
	"com.ibm.wala" % "com.ibm.wala.shrike" % "1.3.7-SNAPSHOT",
	"com.ibm.wala" % "com.ibm.wala.util" % "1.3.7-SNAPSHOT",
	"com.ibm.wala" % "com.ibm.wala.core" % "1.3.7-SNAPSHOT",
	"com.squareup" % "javawriter" % "2.2.1")
