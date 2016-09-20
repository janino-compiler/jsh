# jsh - The "Java Shell"

## Introduction

On popular operating systems (UNIX, MS WINDOWS, MAC OS, ...) exists a plethora of "shells", i.e. programs that read a sequence of "commands" (either from a "script file" or from the console) and execute them: Bourne Shell, BASH, C Shell, Korn Shell, only to mention a few.

JSH is yet another shell program that stands out from these wrt the following aspects:
* The command syntax is Java. (Not *similar* to Java, but *really* Java.)
* The commands (ls, echo, ...) are not shell-external programs, but classes in the running JVM.
* The code entered is compiled into Java bytecode, which is loaded into the running JVM, which makes execution tremendously fast.
* 100% Java - runs on any platform for which a JRE >= 1.7 is available.

These properties open up a whole range of new use cases which you would normally *not* implement with a shell script:
* Processing of massive a amounts of data (traditionally, one would use processing tools like PERL or AWK for that).
* Number crunching (traditionally, one would use specialized environments for that, like MATLAB).

## Usage

JSH is available on MAVEN CENTRAL. Just get the POM, then run

```sh
mvn exec:java -Dexec.mainClass=de.unkrig.jsh.Main
```

As it is worth a thousand words, here's an example:

```java
Welcome, stranger, and speak!
$ System.out.println("HELLO WORLD");
HELLO WORLD
$ for (int i = 0; i < 3; i++) {
>     System.out.println("HELLO WORLD");
> }
HELLO WORLD
HELLO WORLD
HELLO WORLD
$ for (String s : new String[] { "a", "b", "c" }) {
>     System.out.println(s);
> }
a
b
c
$
$ ls();
.bashrc
.bash_history
.bash_profile
.jshrc
$
exit();
```

## Contributing

If you find the concept useful, feel free to use JSH, give feedback, and contribute! The best way to contact me is via a GITHUB issue.
