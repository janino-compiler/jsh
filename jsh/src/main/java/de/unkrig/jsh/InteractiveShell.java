
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
import org.codehaus.janino.Java.ReferenceType;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.Scanner.TokenType;
import org.codehaus.janino.ScriptEvaluator;
import org.codehaus.janino.Visitor.ImportVisitor;

import de.unkrig.commons.lang.StringUtil;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.util.ArrayUtil;
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
     * Executes the ".jshrc" file, then reads statements from the console and executes them.
     * <p>
     *   Errors that occur as the ".jshrc" file is processed are not caught; errors that occur as statements are read
     *   <em>from the console</em>, scanned, parsed, compiled an executed are reported on the console in a
     *   user-friendly fashion, and otherwise ignored.
     * </p>
     * <p>
     *   In addition to any "normal" Java statement, the following constructs are supported:
     * </p>
     * <ul>
     *   <li>
     *     A PACKAGE declaration (JLS7 7.4) changes the name of the class that is generated for all statements that are
     *     entered afterwards, until another PACKAGE declaration is entered.
     *   </li>
     *   <li>
     *     A THROWS statement (like "{@code throws java.lang.Exception;}") allows all statements that are entered
     *     afterwards to throw that exception. (Notice that the Java language knows no "throws statement"; only the
     *     {@link InteractiveShell} supports that concept.) There is no way to remove a previously declared exception.
     *     The exception class name must be fully qualified.
     *   </li>
     *   <li>
     *     An IMPORT declaration (JLS7 7.5) applies to all statements that will are entered afterwards. There is no way
     *     to remove a previously declared IMPORT.
     *   </li>
     * </ul>
     *
     * @param defaultImports             These add to the sytem import "java.lang"; effective for the RC script and the
     *                                   entered commands
     * @param thrownExceptions           The exceptions that the RC script and the entered commands may throw
     * @param optionalEncoding           Effective for the RC script
     * @throws InvocationTargetException The RC script or an entered command threw one of the
     *                                   <var>thrownExceptions</var>
     * @throws CompileException          An error occurred when the RC script was read, scanned, parsed and compiled
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

        // Wrap the JLINE "ConsoleReader" as a Producer<String>.
        ProducerWhichThrows<String, IOException> lp = new ProducerWhichThrows<String, IOException>() {

            @Override @Nullable public String
            produce() throws IOException {
                String result = cr.readLine();
                cr.setPrompt("> ");
                return result;
            }
        };

        StatementEvaluator se = new StatementEvaluator();
        se.setDefaultImports(defaultImports);
        se.setExtendedClass(Base.class);
        se.setThrownExceptions(thrownExceptions);

        System.err.println("Welcome, stranger, and speak!");

        for (;;) {

            // Set up a new parser for each statement, because on parse errors we want to discard the tokens
            // that are remaining on the command line.
            final Parser parser = new Parser(new Scanner(null, InteractiveShell.lineProducerReader(lp)));

            // Scan, parse, compile and load one statement.
            cr.setPrompt("$ ");
            try {
                if (parser.peek().type == TokenType.END_OF_INPUT) {
                    break;
                } else
                if (parser.peek("package")) {
                    se.setClassName(parser.parsePackageDeclaration().packageName + ".SC");
                } else
                if (parser.peekRead("throws")) {
                    ReferenceType[] tes = parser.parseReferenceTypeList();
                    parser.read(";");

                    for (ReferenceType te : tes) {
                        Class<?> tec = InteractiveShell.class.getClassLoader().loadClass(
                            StringUtil.join(Arrays.asList(te.identifiers), ".")
                        );
                        thrownExceptions = ArrayUtil.append(thrownExceptions, tec);
                    }

                    se.setThrownExceptions(thrownExceptions);
                } else
                if (parser.peek("import")) {

                    // "StatementEvaluator.cook()" would ALSO parse import declarations, but then they'd only be
                    // effective for ONE statement. Thus we parse them here and add them to the "default imports"
                    // of the StatementEvaluator.
                    ImportDeclaration id = parser.parseImportDeclaration();

                    String imporT = id.accept(new ImportVisitor<String, RuntimeException>() {

                        @Override @Nullable public String
                        visitSingleTypeImportDeclaration(SingleTypeImportDeclaration stid) {
                            return InteractiveShell.join(stid.identifiers, ".");
                        }

                        @Override @Nullable public String
                        visitTypeImportOnDemandDeclaration(TypeImportOnDemandDeclaration tiodd) {
                            return InteractiveShell.join(tiodd.identifiers, ".") + ".*";
                        }

                        @Override @Nullable public String
                        visitSingleStaticImportDeclaration(SingleStaticImportDeclaration ssid) {
                            return "static " + InteractiveShell.join(ssid.identifiers, ".");
                        }

                        @Override @Nullable public String
                        visitStaticImportOnDemandDeclaration(StaticImportOnDemandDeclaration siodd) {
                            return "static " + InteractiveShell.join(siodd.identifiers, ".") + ".*";
                        }
                    });

                    defaultImports = ArrayUtil.append(defaultImports, imporT);
                    se.setDefaultImports(defaultImports);
                } else {

                    // Parse and compile one statement.
                    se.cook(parser);

                    // Evaluate the compiled statement.
                    se.execute();
                }
            } catch (CompileException ce) {
                System.out.flush();

                // For CompileExceptions, report only the MESSAGE, not the exception type.
                System.err.println(ce.getLocalizedMessage());
            } catch (Exception e) {
                System.out.flush();

                // An InvocationTargetException from "sw.execute()", or any other exception (e.g. IOException).
                e.printStackTrace();
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

            @Override public int
            read(@Nullable char[] cbuf, int off, int len) throws IOException {
                if (len <= 0) return 0;

                String l = this.line;
                if (l == null) return -1;

                while (this.offset >= l.length()) {
                    l = lineProducer.produce();
                    if (l == null) {
                        this.line = null;
                        return -1;
                    }

                    this.line   = l + InteractiveShell.LINE_SEPARATOR;
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

    /**
     * The base class for the classes generated from the entered commands; has a set of "command fields" (like {@code
     * ls}, "shorthand commands" (like {@code exit()}) and "utility methods" (like {@link #glob(String...)}.
     */
    public abstract static
    class Base {

        // "Command fields".

        // SUPPRESS CHECKSTYLE ConstantName|JavadocVariable:4
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

        public static void pwd() { Pwd.$(); }

        // Utility methods.

        /**
         * @return The current working directory
         */
        public static File
        getcwd() { return new File(System.getProperty("user.dir")); }

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
         * @return A modifiable, empty {@link Set}; shorthand for "{@code new HashSet()}"
         */
        @SuppressWarnings("rawtypes") public static Set
        set() { return new HashSet(); }
    }

    /**
     * Converts all <var>elements</var> to string and concatenates these, separated by the <var>glue</var>.
     */
    private static String
    join(Object[] elements, String glue) {
        if (elements.length == 0) return "";
        if (elements.length == 1) return String.valueOf(elements[0]);

        StringBuilder sb = new StringBuilder().append(elements[0]).append(glue).append(elements[1]);
        for (int i = 2; i < elements.length; i++) sb.append(glue).append(elements[i]);
        return sb.toString();
    }
}
