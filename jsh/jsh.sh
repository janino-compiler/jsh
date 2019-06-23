#!/bin/bash

MR="c:/Users/Arno/.m2/repository";
#java="c:/Program Files/Java/jdk-11-ea+26/bin/java.exe";
java="c:/Program Files/Java/jdk1.8.0_45/bin/java.exe";

"$java" -cp \
target/classes\
\;\
../commons-compiler/target/classes\
\;\
../janino/target/classes\
\;\
$MR/de/unkrig/de-unkrig-commons/1.1.11/de-unkrig-commons-1.1.11.jar\
\;\
$MR/jline/jline/2.14/jline-2.14.jar\
 de.unkrig.jsh.Main $@;
