
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;

import de.unkrig.commons.io.IoUtil;

/**
 * A super-small framework for building UNIX pipes of commands.
 * <p>
 *   Example:
 * </p>
 * <pre>{@code
 *     pipe("ls").through("cat").to("sed", "-e", "s/e//g");
 * }</pre>
 */
public abstract
class Command {

    /**
     * Executes the <var>command</var>, without redirecting its input or output.
     */
    public static void
    run(String... command) throws InterruptedException, IOException {
        new ProcessBuilder(command).inheritIO().start().waitFor();
    }

    /**
     * Creates and returns a {@link Command} object which will execute the given <var>command</var> and catch its
     * output.
     *
     * @see Command
     */
    public static Command
    pipe(final String... command) {

        return new Command() {

            @Override protected Process
            run() throws IOException {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectError(Redirect.INHERIT);
                return pb.start();
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

        Process     process1     = this.run();
        InputStream fromProcess1 = process1.getInputStream();

        Process      process2;
        OutputStream toProcess2;
        {
            ProcessBuilder pb2 = new ProcessBuilder(command);
            pb2.redirectOutput(Redirect.INHERIT);
            pb2.redirectError(Redirect.INHERIT);
            process2   = pb2.start();
            toProcess2 = process2.getOutputStream();
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

        process1.waitFor();
        process2.waitFor();
    }

    /**
     * Creates and returns a {@link Command} object which will execute <em>this</em> {@link Command}, catch its
     * output, feed it through the given <var>command</var> and catch its output.
     *
     * @see Command
     */
    public Command
    through(final String... command) throws InterruptedException, IOException {

        return new Command() {

            @Override protected Process
            run() throws IOException {
                final Process     process1     = Command.this.run();
                final InputStream fromProcess1 = process1.getInputStream();

                Process            process2;
                final OutputStream toProcess2;
                {
                    ProcessBuilder pb2 = new ProcessBuilder(command);
                    pb2.redirectError(Redirect.INHERIT);

                    process2 = pb2.start();

                    toProcess2 = process2.getOutputStream();
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
                            process1.waitFor();
                        } catch (IOException ioe) {

                            // Ignore "broken pipe" error condition.
                            ;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                t.setDaemon(true);
                t.start();

                return process2;
            }
        };
    }

    /**
     * Creates and returns a {@link Process} that executes this command. The output of that process is <em>not</em>
     * redirected, so the caller can catch it through {@link Process#getInputStream()}.
     */
    protected abstract Process
    run() throws IOException;
}
