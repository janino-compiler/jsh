
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

package de.unkrig.jsh.command;

import java.io.OutputStream;
import java.io.PrintStream;

import org.codehaus.commons.nullanalysis.Nullable;

import de.unkrig.jsh.util.Cloneable2;

/**
 * Prints its arguments.
 */
public final
class Echo extends Cloneable2<Echo> {

    private PrintStream ps = System.out;
    private boolean     n;

    /**
     * Implements the "{@code -n}" option of the command.
     */
    public Echo
    n() {
        Echo result = this.clone2();
        result.n = true;
        return result;
    }

    /**
     * Redirects the output to a different {@link OutputStream} (initially, STDOUT is the destination stream).
     */
    public Echo
    stream(OutputStream os) {
        Echo result = this.clone2();
        result.ps = os instanceof PrintStream ? (PrintStream) os : new PrintStream(os);
        return result;
    }

    /**
     * Prints the <var>args</var>, separated with spaces. {@code null} <var>args</var> are silently ignored.
     * Eventually prints a line break, unless the {@link #n()} option is configured.
     */
    public void
    $(@Nullable Object... args) { // SUPPRESS CHECKSTYLE MethodName

        if (args != null) {

            boolean first = true;
            for (Object arg : args) {

                // Ignore "null" arg.
                if (arg == null) continue;

                if (first) {
                    first = false;
                } else {
                    this.ps.print(' ');
                }

                this.ps.print(arg);
            }
        }

        if (!this.n) this.ps.println();

        this.ps.flush();
    }
}
