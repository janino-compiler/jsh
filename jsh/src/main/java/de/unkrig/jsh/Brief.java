
/*
 * jsh - The Java Shell
 *
 * Copyright (c) 2016 Arno Unkrig. All rights reserved.
 * Copyright (c) 2015-2016 TIBCO Software Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.jsh;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.commons.nullanalysis.Nullable;

import de.unkrig.commons.lang.protocol.Consumer;

/**
 * Provides basic functionality for jsh (globbing, etc.), and is designed for brevity, i.e. short method signatures.
 */
public final
class Brief {

    private Brief() {}

    /**
     * Implements the "jsh standard way" of printing objects.
     * <ul>
     *   <li>A {@code null} subject results in <em>nothing</em> being printed.</li>
     *   <li>Arrays (including primitive arrays) result in one line being printed per element.</li>
     *   <li>Collections result in one line being printed per element.</li>
     *   <li>All other subjects converted with {@link Object#toString()}, and then printed in one line.</li>
     * </ul>
     */
    public static void
    print(@Nullable Object subject, PrintWriter pw) {
        Brief.print(subject, Brief.printWriterPrintln(pw));
    }

    private static Consumer<Object>
    printWriterPrintln(final PrintWriter pw) {
        return new Consumer<Object>() {
            @Override public void consume(@Nullable Object subject) { pw.println(subject); }
        };
    }

    /**
     * Implements the "jsh standard way" of printing objects.
     * <ul>
     *   <li>A {@code null} <var>subject</var> results in <em>nothing</em> being printed.</li>
     *   <li>
     *     An array <var>subject</var> (including a primitive array) results in one line being printed per element.
     *   </li>
     *   <li>
     *     A {@link Collection} <var><var>subject</var></var> results in one line being printed per collection element.
     *   </li>
     *   <li>
     *     A {@link Set} <var>subject</var> results in one line being printed per entry, in the form "{@code
     *     key=value}".
     *   </li>
     *   <li>
     *     Any other <var>subject</var> is converted with {@link Object#toString()}, and then printed in one line.
     *   </li>
     * </ul>
     */
    public static void
    print(@Nullable Object subject, PrintStream ps) { Brief.print(subject, Brief.printStreamPrintln(ps)); }

    private static Consumer<Object>
    printStreamPrintln(final PrintStream ps) {
        return new Consumer<Object>() {
            @Override public void consume(@Nullable Object subject) { ps.println(subject); }
        };
    }

    /**
     * Maps the <var>subject</var> to zero, one or more subjects, and lets the <var>destination</var> consume these.
     * <ul>
     *   <li>A {@code null} subject results in <em>zero</em> destination subjects.</li>
     *   <li>
     *     An array <var>subject</var> (including primitive arrays) results in one destination subject per element.
     *   </li>
     *   <li>A {@link Collection} <var>subject</var> results in one destination subject per collection element.</li>
     *   <li>
     *     A {@link Set} <var>subject</var> results in one destination subject per entry, in the form "{@code
     *     key=value}".
     *   </li>
     *   <li>Any other <var>subject</var> is simply passed through.</li>
     * </ul>
     */
    public static void
    print(@Nullable Object subject, Consumer<Object> destination) {

        if (subject == null) return;

        if (subject instanceof Object[]) {
            for (Object e : (Object[]) subject) destination.consume(e);
            return;
        }

        if (subject.getClass().isArray()) {

            // "o" is a PRIMITIVE array. (Arrays of object references have already been handled.)
            int length = Array.getLength(subject);
            for (int i = 0; i < length; i++) destination.consume(Array.get(subject, i));
            return;
        }

        if (subject instanceof Collection) {
            for (Object e : (Collection<?>) subject) destination.consume(e);
            return;
        }

        if (subject instanceof Map) {
            for (Entry<?, ?> e : ((Map<?, ?>) subject).entrySet()) {
                destination.consume(e.getKey() + "=" + e.getValue());
            }
            return;
        }

        destination.consume(subject);
    }

    /**
     * A {@code null} parent is equivalent with {@code new File(".")}, except that the elements of the result have
     * a {@code null} parent.
     *
     * @see #wildmatch(String, String)
     */
    public static Collection<File>
    glob(@Nullable File parent, String... globs) {
        Collection<File> result = new ArrayList<File>();
        for (String glob : globs) Brief.glob(parent, glob, result);
        return result;
    }

    private static void
    glob(@Nullable File parent, String glob, Collection<File> result) {

        final String prefix, suffix;
        {
            int idx = glob.indexOf('/');
            if (idx == -1) {
                prefix = glob;
                suffix = null;
            } else {
                prefix = glob.substring(0, idx);
                for (idx++; idx < glob.length() && glob.charAt(idx) == '/'; idx++);
                suffix = idx == glob.length() ? null : glob.substring(idx);
            }
        }

        File[] children = (parent == null ? new File(".") : parent).listFiles(new FilenameFilter() {

            @Override public boolean
            accept(@Nullable File dir, @Nullable String name) {
                assert name != null;
                return Brief.wildmatch(prefix, name);
            }
        });

        if (parent == null) {
            for (int i = 0; i < children.length; i++) {
                children[i] = new File(children[i].getName());
            }
        }

        if (suffix == null) {
            result.addAll(Arrays.asList(children));
        } else {
            for (File child : children) Brief.glob(child, suffix, result);
        }
    }

    /**
     * Implements "wildcard matching". "?" matches <em>any</em> character, "*" matches any sequence of characters.
     */
    public static boolean
    wildmatch(@Nullable String pattern, String text) {

        if (pattern == null) return true;

        int i;
        for (i = 0; i < pattern.length(); ++i) {
            char c = pattern.charAt(i);
            switch (c) {

            case '?':
                if (i == text.length()) return false;
                break;

            case '*':
                if (pattern.length() == i + 1) return true; // Optimization for trailing '*'.
                pattern = pattern.substring(i + 1);
                for (; i <= text.length(); ++i) {
                    if (Brief.wildmatch(pattern, text.substring(i))) return true;
                }
                return false;

            default:
                if (i == text.length()) return false;
                if (text.charAt(i) != c) return false;
                break;
            }
        }
        return text.length() == i;
    }

    /**
     * If {@code false}, then {@link #wildmatch(String, String)} will return {@code true} iff {@code
     * pattern.equals(text)}.
     */
    public static boolean
    containsWildcards(@Nullable String pattern) {
        return pattern != null && (pattern.indexOf('*') != -1 || pattern.indexOf('?') != -1);
    }
}
