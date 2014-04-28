package edu.colorado.droidel.parser

class AndroidManifest(val packageName : String, val targetAPIVersion : String, val activities : Iterable[ManifestActivity]) {}
