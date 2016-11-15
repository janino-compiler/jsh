
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

package de.unkrig.jsh.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.codehaus.commons.nullanalysis.NotNullByDefault;
import org.codehaus.commons.nullanalysis.Nullable;

import de.unkrig.jsh.JshBase;
import de.unkrig.jsh.util.Cloneable2;

/**
 * Prints the name of files, or the names of the members of directories.
 */
public final
class Ls extends Cloneable2<Ls> {

    private enum TimeKind { LAST_MODIFIED_TIME, CREATION_TIME, LAST_ACCESS_TIME }

    private boolean    lonG;
    private boolean    directory;
    private TimeKind   timeToShow = TimeKind.LAST_MODIFIED_TIME;
    private DateFormat dateFormat = DateFormat.getDateTimeInstance();

    /**
     * {@code null} means "do not sort".
     */
    @Nullable private Comparator<File> sortingComparator = Ls.BY_PATH;

    /**
     * with -lt: sort by, and show, ctime (time of last modification of file status information); with -l: show ctime
     * and sort by name; otherwise: sort by ctime, newest first.
     */
    public Ls
    c() {
        Ls result = this.clone2();

        result.timeToShow = TimeKind.CREATION_TIME;

        if (!this.lonG || this.sortingComparator == Ls.BY_LAST_MODIFIED_TIME) {
            result.sortingComparator = Ls.BY_CREATION_TIME;
        }

        return result;
    }

    /**
     * List directories themselves, not their contents.
     */
    public Ls
    d() { Ls result = this.clone2(); result.directory = true; return result; }

    /**
     * With {@link #l()}, show times using the <var>dateFormat</var>.
     *
     * @param dateFormat E.g. a {@link SimpleDateFormat}
     */
    public Ls
    dateFormat(DateFormat dateFormat) {
        Ls result = this.clone2();
        result.dateFormat = dateFormat;
        return result;
    }

    /**
     * Use a long listing format.
     * <p>
     *   The default format for rendering dates is {@link JshBase#dateTimeFormat()}.
     * </p>
     */
    public Ls
    l() { Ls result = this.clone2(); result.lonG = true; return result; }

    /**
     * Sort by file size.
     */
    public Ls // SUPPRESS CHECKSTYLE MethodName
    S() { Ls result = this.clone2(); result.sortingComparator = Ls.BY_LENGTH; return result; }

    /**
     * Sort by <var>word</var> instead of name: "{@code none}" (-U), "{@code size}" (-S), "{@code time}" (-t), "{@code
     * version}" (-v), "{@code extension}" (-X).
     */
    public Ls
    sort(String word) {

        switch (word) {
        case "none":      return this.U();
        case "size":      return this.S();
        case "time":      return this.t();
        case "extension": return this.X();
        default:          throw new IllegalArgumentException(word);
        }
    }

    /**
     * Sort by modification time, newest first.
     */
    public Ls
    t() { Ls result = this.clone2(); result.sortingComparator = Ls.BY_LAST_MODIFIED_TIME; return result; }

    /**
     * With {@link #l()}{@code .}{@link #t()}: sort by, and show, access time;
     * with {@link #l()}: show access time and sort by name;
     * otherwise: sort by access time.
     */
    public Ls
    u() {
        Ls result = this.clone2();

        result.timeToShow = TimeKind.CREATION_TIME;

        if (!this.lonG || this.sortingComparator == Ls.BY_LAST_MODIFIED_TIME) {
            result.sortingComparator = Ls.BY_LAST_ACCESS_TIME;
        }

        return result;
    }

    /**
     * Do not sort; list entries in directory order,
     */
    public Ls // SUPPRESS CHECKSTYLE MethodName
    U() { Ls result = this.clone2(); result.sortingComparator = null; return result; }

    /**
     * Sort alphabetically by entry extension.
     */
    public Ls // SUPPRESS CHECKSTYLE MethodName
    X() { Ls result = this.clone2(); result.sortingComparator = Ls.BY_EXTENSION; return result; }

    /**
     * Lists the members of the current working directory.
     */
    public void
    $() { // SUPPRESS CHECKSTYLE MethodName
        for (String memberName : new File(".").list()) this.printMember(memberName, new File(memberName));
    }

    /**
     * Lists the <var>files</var> which are <em>not</em> directories, then for all <var>files</var> which <em>are</em>
     * directories:
     * <ul>
     *   <li>One empty line</li>
     *   <li>The path of the directory</li>
     *   <li>The names of the directory members (one per line)</li>
     * </ul>
     */
    public void
    $(File... files) { this.$(Arrays.asList(files)); } // SUPPRESS CHECKSTYLE MethodName

    /**
     * Prints information about the <var>files</var>.
     */
    public void
    $(Collection<File> files) { // SUPPRESS CHECKSTYLE MethodName

        // Zero args: Redirect to no-args version.
        if (files.isEmpty()) {
            this.$();
            return;
        }

        // Special case: ONE directory given; list members (in analogy with LS's no-args policy).
        if (!this.directory && files.size() == 1) {
            File f = files.iterator().next();
            if (f.isDirectory()) {
                this.printMembers(f);
                return;
            }
        }

        files = new ArrayList<>(files);
        Collections.sort((List<File>) files, this.sortingComparator);

        // Print the non-directory files first, then list the directories.
        List<File> directories = new ArrayList<File>();
        for (File file : files) {
            if (!this.directory && file.isDirectory()) {
                directories.add(file);
            } else {
                this.printMember(file.getPath(), file);
            }
        }

        for (File d : directories) {
            System.out.println();
            System.out.println(d + ":");
            this.printMembers(d);
        }
    }

    private void
    printMembers(File directory) {
        File[] members = directory.listFiles();
        Arrays.sort(members, this.sortingComparator);
        for (File member : members) this.printMember(member.getName(), member);
    }

    private void
    printMember(String name, File member) {
        if (this.lonG) {

            FileTime time = Ls.getFileTime(member, this.timeToShow);

            System.out.printf(
                "%c%c%c%c %-12s %9d %s %s%n",
                member.isDirectory() ? 'd' : '-',
                member.canRead()     ? 'r' : '-',
                member.canWrite()    ? 'w' : '-',
                member.canExecute()  ? 'x' : '-',
                this.getOwnerName(member),
                member.length(),
                time == null ? "???" : Ls.threadSafeFormat(this.dateFormat, new Date(time.toMillis())),
                name
            );
        } else {
            System.out.println(name);
        }
    }

    /**
     * Formats the <var>date</var> with the <var>dateFormat</var>. Equivalent with {@link DateFormat#format(Date)},
     * and is also thread-safe (while {@link DateFormat#format(Date)} is <em>not</var>).
     */
    private static String
    threadSafeFormat(DateFormat dateFormat, Date date) {

        // Cloning is probably the fastest way to format in a thread-safe manner.
        return ((DateFormat) dateFormat.clone()).format(date);
    }

    /**
     * @return {@code null} iff an {@link IOException} occurs
     */
    @Nullable private String
    getOwnerName(File file) {

        try {
            return Files.readAttributes(file.toPath(), PosixFileAttributes.class).owner().getName();
        } catch (IOException ioe) {
            return null;
        } catch (UnsupportedOperationException e) {
            ;
        }

        try {
            return Files.getFileAttributeView(file.toPath(), FileOwnerAttributeView.class).getOwner().getName();
        } catch (IOException ioe) {
            return null;
        } catch (UnsupportedOperationException e) {
            ;
        }

        return null;
    }

    @Nullable private static FileTime
    getFileTime(@Nullable File file, TimeKind timeKind) {

        BasicFileAttributes basicFileAttributes = Ls.getBasicFileAttributes(file);
        if (basicFileAttributes == null) return null;

        switch (timeKind) {
        case CREATION_TIME:      return basicFileAttributes.creationTime();
        case LAST_ACCESS_TIME:   return basicFileAttributes.lastAccessTime();
        case LAST_MODIFIED_TIME: return basicFileAttributes.lastModifiedTime();
        default:                 throw new AssertionError(timeKind);
        }
    }

    @Nullable private static BasicFileAttributes
    getBasicFileAttributes(@Nullable File file) {

        if (file == null) return null;

        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static
    class FilesByTimeComparator implements Comparator<File> {

        private final TimeKind timeKind;

        public
        FilesByTimeComparator(TimeKind timeKind) { this.timeKind = timeKind; }

        @NotNullByDefault(false) @Override public int
        compare(File f1, File f2) {
            return Ls.compare(Ls.getFileTime(f1, this.timeKind), Ls.getFileTime(f1, this.timeKind));
        }
    }

    private static final Comparator<File>
    BY_PATH               = Ls.naturalOrderComparator(),
    BY_LAST_MODIFIED_TIME = new FilesByTimeComparator(TimeKind.LAST_MODIFIED_TIME),
    BY_CREATION_TIME      = new FilesByTimeComparator(TimeKind.CREATION_TIME),
    BY_LAST_ACCESS_TIME   = new FilesByTimeComparator(TimeKind.LAST_ACCESS_TIME),
    BY_LENGTH             = new Comparator<File>() {
        @NotNullByDefault(false) @Override public int
        compare(File f1, File f2) { return Long.compare(f1.length(), f2.length()); }
    },
    BY_EXTENSION = new Comparator<File>() {
        @NotNullByDefault(false) @Override public int
        compare(File f1, File f2) {
            String extension1 = Ls.extension(f1.getName());
            String extension2 = Ls.extension(f2.getName());
            return Ls.compare(extension1, extension2, f1.getPath(), f2.getPath());
        }
    };

    /**
     * @return -1 iff only <var>o1</var> is {@code null};
     *         1 iff only <var>o2</var> is {@code null};
     *         0 iff both are {@code null};
     *         otherwise {@code o1.compareTo(o2)}
     */
    private static <T extends Comparable<T>> int
    compare(@Nullable T o1, @Nullable T o2) {
        if (o1 == null) return o2 == null ? 0 : -1;
        if (o2 == null) return 1;
        return o1.compareTo(o2);
    }

    /**
     * Compares by <em>two</em> criteria. Iff the primary criteria are equal, then the secondary criteria take effect.
     */
    private static <T1 extends Comparable<T1>, T2 extends Comparable<T2>> int
    compare(@Nullable T1 primary1, @Nullable T1 primary2, @Nullable T2 secondary1, @Nullable T2 secondary2) {
        int result = Ls.compare(primary1, primary2);
        return result != 0 ? result : Ls.compare(secondary1, secondary2);
    }

    /**
     * @return The "file name extension", or {@code null} iff the name does not contain a period
     */
    @Nullable private static String
    extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? null : name.substring(dot + 1);
    }

    /**
     * @return A comparator that compares with the {@link #compare(Comparable, Comparable)} method
     */
    private static <T extends Comparable<T>> Comparator<T>
    naturalOrderComparator() {
        return new Comparator<T>() {
            @NotNullByDefault(false) @Override public int compare(T o1, T o2) { return Ls.compare(o1, o2); }
        };
    }

}
