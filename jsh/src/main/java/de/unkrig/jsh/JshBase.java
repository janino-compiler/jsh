
/*
 * jsh - The Java Shell
 *
 * Copyright (c) 2016, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.jsh;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.commons.nullanalysis.Nullable;

import de.unkrig.jsh.command.Cd;
import de.unkrig.jsh.command.Echo;
import de.unkrig.jsh.command.Ls;
import de.unkrig.jsh.command.Pwd;

/**
 * The base class for the classes generated from the entered commands; has a set of "command fields" (like {@code
 * ls}, "shorthand commands" (like {@code exit()}) and "utility methods" (like {@link #glob(String...)}.
 */
public abstract
class JshBase {

    // "Command fields".

    public static final Cd   cd   = new Cd(); // SUPPRESS CHECKSTYLE ConstantName|JavadocVariable:4
    public static final Echo echo = new Echo();
    public static final Ls   ls   = new Ls();
    public static final Pwd  pwd  = new Pwd();

    // Shorthand commands.

    public static void cd()               { JshBase.cd.$(); }
    public static void cd(File dir)       { JshBase.cd.$(dir); }
    public static void cd(String dirName) { JshBase.cd.$(dirName); }

    public static void echo(Object... args) { JshBase.echo.$(args); }

    public static void err(@Nullable Object subject) { Brief.print(subject, System.err); }

    public static void exit()           { System.exit(0); }
    public static void exit(int status) { System.exit(status); }

    public static void ls(File... files) { JshBase.ls.$(files); }

    public static void out(@Nullable Object subject) { Brief.print(subject, System.out); }

    public static void pwd() { Pwd.$(); }

    public static int  run(String... command) throws IOException, InterruptedException { return Command.run(command); }

    // Utility methods.

    /**
     * @return The current working directory
     */
    public static File
    getcwd() { return new File(System.getProperty("user.dir")); }

    /**
     * Shorthand for "{@code new File(}<var>pathname</var>{@code )}".
     */
    public static File
    file(String pathname) { return new File(pathname); }

    /**
     * Shorthand for {@link Command#from(File)}.
     */
    public static Command
    from(File inputFile) { return Command.from(inputFile); }

    @Nullable public static Collection<? extends File>
    glob(@Nullable String... globs) { return globs == null ? null : Brief.glob(null, globs); }

    /**
     * @return A modifiable, empty {@link List}; shorthand for "{@code new ArrayList()}"
     */
    @SuppressWarnings("rawtypes") public static List
    list() { return new ArrayList(); }

    /**
     * @return A modifiable, empty {@link Map}; shorthand for "{@code new HashMap()}"
     */
    @SuppressWarnings("rawtypes") public static Map
    map() { return new HashMap(); }

    /**
     * Shorthand for {@link Command#pipe(String...)}.
     */
    public static Command
    pipe(String... command) { return Command.pipe(command); }

    /**
     * @return A modifiable, empty {@link Set}; shorthand for "{@code new HashSet()}"
     */
    @SuppressWarnings("rawtypes") public static Set
    set() { return new HashSet(); }
}
