
import to_std
import sys

if(len(sys.argv) != 4):
	print "usage: [app directory] [build type (eg debug)] [output directory]"
	exit()

g = to_std.GradleToStd(sys.argv[1], sys.argv[2], sys.argv[3])
g.transform()
