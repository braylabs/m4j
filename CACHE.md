# Setup Cache global storage with M4J

M4J can utilize the InterSystems (Cache Extreme)[http://docs.intersystems.com/ens20121/csp/docbook/DocBook.UI.Page.cls?KEY=BLXT]
drivers for extremely fast (near native) storage and retrieval of global values.  Cache Extreme is a JNI/C++ driver that interacts
with the Cache kernel directly and therefore must be running on the same machine.

GT.M has some similar capabilities, but a proxy/adapter has not been created yet for M4J.

## Prerequisite
- You must have InterSystems Cache installed (2014.* and 2015.* have been used so far)
- Currently, Windows is the only platform that works, OSX (et. al.) cause core dumps in the cacheextreme drivers)

## Step 1: Install cache extreme driver into you local maven repository
    mvn install:install-file -Dfile={path-to-cache-install}/lib/cacheextreme.jar -DgroupId=com.intersystems -DartifactId=cacheextreme -Dversion=1.0 -Dpackaging=jar
    
## Step 2: Launch M4J with CACHE global storage
    java -jar target/m4j-0.1.jar --globals=CACHE

## Step 3: Test it
- There are still some quirks, but in general, if you create a new global `S ^FOO=1`, quit and then start m4j again, the global should still be present
and you should be able to see it in the cache console (`csession`) as well.    
    
## Troubleshooting
- Cache must be installed and running on same machine
- Must have the `%Service_CallIn` enabled w/o authentication (Under System > Security Management > Services) in the Cache Management Portal
- If you get an error like `Illegal instruction: 4` or a core-dump that looks like the one below, then you are experiencing an error in the Cache drivers that I've not found a work-around for.
```
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGBUS (0xa) at pc=0x00000001266a0c3c, pid=6346, tid=4867
#
# JRE version: Java(TM) SE Runtime Environment (8.0_25-b17) (build 1.8.0_25-b17)
# Java VM: Java HotSpot(TM) 64-Bit Server VM (25.25-b02 mixed mode bsd-amd64 compressed oops)
# Problematic frame:
# C  [liblcbclientnt.dylib+0xbc3c]  UnicodeToCacheUni+0xac
#
# Failed to write core dump. Core dumps have been disabled. To enable core dumping, try "ulimit -c unlimited" before starting Java again
#
# An error report file with more information is saved as:
# /Users/brian/dev/m4j/hs_err_pid6346.log
#
# If you would like to submit a bug report, please visit:
#   http://bugreport.sun.com/bugreport/crash.jsp
# The crash happened outside the Java Virtual Machine in native code.
# See problematic frame for where to report the bug.
#
Abort trap: 6
```

    