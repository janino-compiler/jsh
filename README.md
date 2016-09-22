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

Get the latest version of the runnable JAR file from [here](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=de.unkrig&a=jsh&v=LATEST&c=jar-with-dependencies), and run it:

> `$ java -jar` _jar-file_

Now start typing Java code and see how it executes. Here's an example session:

```java
Welcome, stranger, and speak!
$ System.out.println("HELLO WORLD");
HELLO WORLD
$ for (int i = 0; i < 3; i++) {
>     out("HELLO WORLD"); // Short for "System.out.println()".
> }
HELLO WORLD
HELLO WORLD
HELLO WORLD
$ for (String s : new String[] { "a", "b", "c" }) {
>     out(s);
> }
a
b
c
$ ls(); // Shorthand for "ls.$()".
.bashrc
.bash_history
.bash_profile
.jshrc
$ ls.l().$(); // "l()" activates "long" listing, resembling "ls -l".
-rwx paula\Arno        6437 20.09.2016 13:42:27 .bashrc
-rwx paula\Arno       50409 20.09.2016 13:42:28 .bash_history
-rwx paula\Arno        1494 22.01.2013 22:05:48 .bash_profile
-rwx paula\Arno          56 16.08.2015 20:32:00 .jshrc
$
exit();
```

For documentation of the available commands (like `ls();`), download the JAVADOC

> https://oss.sonatype.org/content/groups/public/de/unkrig/jsh/_latest-version_/jsh-_latest-version_-javadoc.jar

, and check the documentation of the "JshBase" class.

## Licensing

JSH is available under [the new BSD license](https://raw.githubusercontent.com/janino-compiler/jsh/master/jsh/LICENSE).

## Contributing

If you find the concept useful, feel free to use JSH, give feedback, and contribute! The best way to contact me is via a GITHUB issue.

[1]: https://oss.sonatype.org/content/groups/public/de/unkrig/jsh/0.1.1/jsh-0.1.1-javadoc.jar!/de/unkrig/jsh/JshBase.html
[2]: jar:https://oss.sonatype.org/content/groups/public/de/unkrig/jsh/0.1.1/jsh-0.1.1-javadoc.jar!/de/unkrig/jsh/JshBase.html
