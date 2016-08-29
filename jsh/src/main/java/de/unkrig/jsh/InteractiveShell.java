
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.IScriptEvaluator;
import org.codehaus.commons.compiler.samples.DemoBase;
import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.Java.CompilationUnit.ImportDeclaration;
import org.codehaus.janino.Java.CompilationUnit.SingleStaticImportDeclaration;
import org.codehaus.janino.Java.CompilationUnit.SingleTypeImportDeclaration;
import org.codehaus.janino.Java.CompilationUnit.StaticImportOnDemandDeclaration;
import org.codehaus.janino.Java.CompilationUnit.TypeImportOnDemandDeclaration;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.Scanner.TokenType;
import org.codehaus.janino.ScriptEvaluator;
import org.codehaus.janino.Visitor.ImportVisitor;

import de.unkrig.commons.lang.StringUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.jsh.command.Cd;
import de.unkrig.jsh.command.Echo;
import de.unkrig.jsh.command.Ls;
import de.unkrig.jsh.command.Pwd;
import jline.console.ConsoleReader;
import jline.console.history.FileHistory;

/**
 * In interactive shell program, similar to the UNIX "sh", "csh", "bash" asf. programs, which uses strictly JAVA
 * syntax for executing commands.
 */
public final
class InteractiveShell extends DemoBase {

    private InteractiveShell() {}

    private static final File USER_HOME_DIR    = new File(System.getProperty("user.home", "."));
    private static final File JSH_HISTORY_FILE = new File(InteractiveShell.USER_HOME_DIR, ".jsh_history");
    private static final File JSHRC_FILE       = new File(InteractiveShell.USER_HOME_DIR, ".jshrc");

    /**
     * @param defaultImports             Effective for the RC script and the entered commands
     * @param optionalEncoding           Effective for the RC script
     * @throws InvocationTargetException The RC script or an entered command threw one of the
     *                                   <var>thrownExceptions</var>
     */
    public static void
    run(String[] defaultImports, Class<?>[] thrownExceptions, @Nullable String optionalEncoding)
    throws IOException, CompileException, InvocationTargetException {

        // Execute the RC file (typically "$HOME/jshrc").
        JSHRC: {
            IScriptEvaluator se = new ScriptEvaluator();

            se.setDefaultImports(defaultImports);
            se.setExtendedClass(Base.class);
            se.setThrownExceptions(thrownExceptions);

            // Scan, parse and compile the script file.
            InputStream is;
            try {
                is = new FileInputStream(InteractiveShell.JSHRC_FILE);
            } catch (FileNotFoundException fnfe) {
                break JSHRC;
            }

            try {

                se.cook(InteractiveShell.JSHRC_FILE.toString(), is, optionalEncoding);
                is.close();
            } finally {
                try { is.close(); } catch (Exception e) {}
            }

            se.evaluate(null); // Return value is always "null" because the return type is "void.class".
        }

        // Set up a "jline" console reader (command history etc.).
        final ConsoleReader cr = new ConsoleReader();

        // Configure a file-based command history.
        {
            final FileHistory fileHistory = new FileHistory(InteractiveShell.JSH_HISTORY_FILE);
            cr.setHistory(fileHistory);

            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void
                run() {
                    try {
                        fileHistory.flush();
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                }
            });
        }

        ProducerWhichThrows<String, IOException> lp = new ProducerWhichThrows<String, IOException>() {

            @Override @Nullable public String
            produce() throws IOException {
                String result = cr.readLine();
                cr.setPrompt("> ");
                return result;
            }
        };

        Parser parser = new Parser(new Scanner(null, InteractiveShell.lineProducerReader(lp)));

        // Use than JANINO implementation of IScriptEvaluator, because only that offers the "setMinimal()" feature.
        StatementEvaluator se = new StatementEvaluator();
        se.setDefaultImports(defaultImports);
        se.setExtendedClass(Base.class);
        se.setThrownExceptions(thrownExceptions);

        System.err.println("Welcome, stranger, and speak!");

        for (;;) {

            // Scan, parse, compile and load one statement.
            cr.setPrompt("$ ");
            try {
                if (parser.peek().type == TokenType.END_OF_INPUT) break;

                if (parser.peek("import")) {
                    ImportDeclaration id = parser.parseImportDeclaration();
                    String imporT = id.accept(new ImportVisitor<String, RuntimeException>() {

                        @Override @Nullable public String
                        visitSingleTypeImportDeclaration(SingleTypeImportDeclaration stid) {
                            return StringUtil.join(Arrays.asList(stid.identifiers), ".");
                        }

                        @Override @Nullable public String
                        visitTypeImportOnDemandDeclaration(TypeImportOnDemandDeclaration tiodd) {
                            return StringUtil.join(Arrays.asList(tiodd.identifiers), ".") + ".*";
                        }

                        @Override @Nullable public String
                        visitSingleStaticImportDeclaration(SingleStaticImportDeclaration ssid) {
                            return "static " + StringUtil.join(Arrays.asList(ssid.identifiers), ".");
                        }

                        @Override @Nullable public String
                        visitStaticImportOnDemandDeclaration(StaticImportOnDemandDeclaration siodd) {
                            return "static " + StringUtil.join(Arrays.asList(siodd.identifiers), ".") + ".*";
                        }
                    });

                    defaultImports = Arrays.copyOf(defaultImports, defaultImports.length + 1);
                    defaultImports[defaultImports.length - 1] = imporT;
                    se.setDefaultImports(defaultImports);
                } else {
                    se.cook(parser);
                }
            } catch (CompileException ce) {
                System.err.println(ce.getLocalizedMessage());
                continue;
            }

            // Evaluate script with actual parameter values.
            try {
                se.execute();
            } catch (Exception e) {
                System.out.flush();
                System.err.println(e.getLocalizedMessage());
                continue;
            }

            System.out.flush();
        }
    }

    /**
     * Wraps a producer of strings (lines) as a {@link Reader}.
     */
    private static Reader
    lineProducerReader(final ProducerWhichThrows<String, ? extends IOException> lineProducer) {

        return new Reader() {

            @Nullable String line = "";
            int              offset;

            @Override
            public int read(@Nullable char[] cbuf, int off, int len) throws IOException {
                if (len <= 0) return 0;

                String l = this.line;
                if (l == null) return -1;

                while (this.offset >= l.length()) {
                    l = lineProducer.produce();
                    if (l == null) {
                        this.line = null;
                        return -1;
                    }

                    this.line = l + InteractiveShell.LINE_SEPARATOR;
                    this.offset = 0;
                }

                int n = Math.min(l.length() - this.offset, len);
                l.getChars(this.offset, this.offset + n, cbuf, off);
                this.offset += n;
                return n;
            }

            @Override public void
            close() {}
        };
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public static abstract
    class Base {

        // "Command fields".

        public static final Cd   cd   = new Cd();
        public static final Echo echo = new Echo();
        public static final Ls   ls   = new Ls();
        public static final Pwd  pwd  = new Pwd();

        // Shorthand commands.

        public static void cd()               { Base.cd.$(); }
        public static void cd(File dir)       { Base.cd.$(dir); }
        public static void cd(String dirName) { Base.cd.$(dirName); }

        public static void echo(Object... args) { Base.echo.$(args); }

        public static void err(@Nullable Object subject) { Brief.print(subject, System.err); }

        public static void exit()           { System.exit(0); }
        public static void exit(int status) { System.exit(status); }

        public static void ls(File... files) { Base.ls.$(files); }

        public static void out(@Nullable Object subject) { Brief.print(subject, System.out); }

        // Utility methods.

        @Nullable public static Collection<? extends File>
        glob(@Nullable String... globs) { return globs == null ? null : Brief.glob(null, globs); }

        /**
         * Creates and returns a modifiable, empty {@link List}. Shorthand for "{@code new ArrayList()}".
         */
        @SuppressWarnings("rawtypes") public static List
        list() { return new ArrayList(); }

        /**
         * Creates and returns a modifiable, empty {@link Map}. Shorthand for "{@code new HashMap()}".
         */
        @SuppressWarnings("rawtypes") public static Map
        map() { return new HashMap(); }

        /**
         * Creates and returns a modifiable, empty {@link Set}. Shorthand for "{@code new HashSet()}".
         */
        @SuppressWarnings("rawtypes") public static Set
        set() { return new HashSet(); }
    }
}
