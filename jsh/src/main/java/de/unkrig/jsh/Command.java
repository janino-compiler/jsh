
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
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;

import de.unkrig.commons.io.IoUtil;

/**
 * A super-small framework for building UNIX pipes of commands.
 * <p>
 *   Examples:
 * </p>
 * <pre>{@code
 *     pipe("ls").through("cat").to("sed", "-e", "s/e//g");
 *     from(new File("foo.txt")).through("sort").to("sed", "-e", "s/e//g");
 *     from(new File("foo.txt")).through("sort").to(new File("foo2.txt"));
 * }</pre>
 */
public abstract
class Command {

    /**
     * Executes the <var>command</var>, without redirecting its input, output and error.
     */
    public static void
    run(String... command) throws InterruptedException, IOException {
        Command.run(Redirect.INHERIT, command);
    }

    /**
     * Executes the <var>command</var>, without redirecting its input or output.
     *
     * @param redirectError Where to redirect the error output
     */
    public static void
    run(Redirect redirectError, String... command) throws InterruptedException, IOException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectInput(Redirect.INHERIT);
        pb.redirectOutput(Redirect.INHERIT);
        pb.redirectError(redirectError);

        pb.start().waitFor();
    }

    /**
     * Creates and returns a {@link Command} object which reads from the <var>inputFile</var>
     *
     * @see Command
     */
    public static Command
    from(final File inputFile) {

        return new Command() {
            @Override protected InputStream run() throws IOException { return new FileInputStream(inputFile); }
        };
    }

    /**
     * Creates and returns a {@link Command} object which will execute the given <var>command</var> and catch its
     * output.
     *
     * @see Command
     */
    public static Command
    pipe(final String... command) {
        return Command.pipe(Redirect.INHERIT, command);
    }

    /**
     * Creates and returns a {@link Command} object which will execute the given <var>command</var> and catch its
     * output.
     *
     * @param redirectError Where to redirect the error output
     * @see Command
     */
    public static Command
    pipe(final Redirect redirectError, final String... command) {

        return new Command() {

            @Override protected InputStream
            run() throws IOException {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectError(redirectError);
                return Command.outputOf(pb.start());
            }
        };
    }

    /**
     * Executes <em>this</em> {@link Command}, catches its output and feeds it into the <var>command</var>.
     *
     * @see Command
     */
    public void
    to(String... command) throws InterruptedException, IOException {
        this.to(Redirect.INHERIT, command);
    }

    /**
     * Executes <em>this</em> {@link Command}, catches its output and feeds it into the <var>command</var>.
     *
     * @param redirectError Where to redirect the error output
     * @see Command
     */
    public void
    to(Redirect redirectError, String... command) throws InterruptedException, IOException {

        InputStream fromProcess1 = this.run();

        OutputStream toProcess2;
        {
            ProcessBuilder pb2 = new ProcessBuilder(command);
            pb2.redirectOutput(Redirect.INHERIT);
            pb2.redirectError(redirectError);
            toProcess2 = Command.inputOf(pb2.start());
        }

        try {
            IoUtil.copy(
                fromProcess1, // inputStream
                true,         // closeInputStream
                toProcess2,   // outputStream
                true          // closeOutputStream
            );
        } catch (IOException ioe) {

            // Ignore "broken pipe" error condition.
            ;
        }
    }

    /**
     * Executes <em>this</em> {@link Command}, catches its output and stores it in the <var>outputFile</var>.
     *
     * @see Command
     */
    public void
    to(File outputFile) throws IOException {

        FileOutputStream os = new FileOutputStream(outputFile);

        IoUtil.copy(
            this.run(), // inputStream
            true,       // closeInputStream
            os,         // outputStream
            true        // closeOutputStream
        );
    }

    /**
     * Creates and returns a {@link Command} object which will execute <em>this</em> {@link Command}, catch its
     * output, feed it through the given <var>command</var> and catch its output.
     *
     * @see Command
     */
    public Command
    through(final String... command) throws InterruptedException, IOException {
        return this.through(Redirect.INHERIT, command);
    }

    /**
     * Creates and returns a {@link Command} object which will execute <em>this</em> {@link Command}, catch its
     * output, feed it through the given <var>command</var> and catch its output.
     *
     * @param redirectError Where to redirect the error output
     * @see Command
     */
    public Command
    through(final Redirect redirectError, final String... command)
    throws InterruptedException, IOException {

        return new Command() {

            @Override protected InputStream
            run() throws IOException {
                final InputStream fromProcess1 = Command.this.run();

                final OutputStream toProcess2;
                InputStream        fromProcess2;
                {
                    ProcessBuilder pb2 = new ProcessBuilder(command);
                    pb2.redirectError(redirectError);

                    Process process2 = pb2.start();

                    toProcess2   = Command.inputOf(process2);
                    fromProcess2 = Command.outputOf(process2);
                }

                Thread t = new Thread() {

                    @Override public void
                    run() {
                        try {
                            IoUtil.copy(
                                fromProcess1, // inputStream
                                true,         // closeInputStream
                                toProcess2,   // outputStream
                                true          // closeOutputStream
                            );
                        } catch (IOException ioe) {

                            // Ignore "broken pipe" error condition.
                            ;
                        }
                    }
                };
                t.setDaemon(true);
                t.start();

                return fromProcess2;
            }
        };
    }

    private static InputStream
    outputOf(final Process process) {
        return new FilterInputStream(process.getInputStream()) {

            @Override public void
            close() throws IOException {

                super.close();

                try {
                    process.waitFor();
                } catch (InterruptedException ie) {
                    throw new IOException(ie);
                }
            }
        };
    }

    private static InputStream
    errorOf(final Process process) {
        return new FilterInputStream(process.getErrorStream()) {

            @Override public void
            close() throws IOException {

                super.close();

                try {
                    process.waitFor();
                } catch (InterruptedException ie) {
                    throw new IOException(ie);
                }
            }
        };
    }

    private static OutputStream
    inputOf(final Process process) {
        return new FilterOutputStream(process.getOutputStream()) {

            @Override public void
            close() throws IOException {

                super.close();

                try {
                    process.waitFor();
                } catch (InterruptedException ie) {
                    throw new IOException(ie);
                }
            }
        };
    }

    /**
     * Creates and returns a {@link Process} that executes this command. The output of that process is <em>not</em>
     * redirected, so the caller can catch it through {@link Process#getInputStream()}.
     */
    protected abstract InputStream
    run() throws IOException;
}
