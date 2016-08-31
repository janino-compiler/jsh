
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
import java.lang.reflect.Method;
import java.util.Collections;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.Java;
import org.codehaus.janino.Java.Statement;
import org.codehaus.janino.Mod;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.ScriptEvaluator;

/**
 * A variant of {@link ScriptEvaluator} which does not parse until end-of-input, but only one {@link
 * Parser#parseStatement() statement}.
 *
 * @see Parser#parseStatement()
 */
final
class StatementEvaluator extends ClassBodyEvaluator {

    private static final String METHOD_NAME = "sc";

    @Nullable private Method result; // null=uncooked

    @Nullable private Class<?>[] optionalThrownExceptions;

    public void
    setThrownExceptions(Class<?>[] thrownExceptions) { this.optionalThrownExceptions = thrownExceptions.clone(); }

    /**
     * Override {@link ClassBodyEvaluator#cook(Scanner)} so that the evaluator does parse a class body, but
     * a stateement.
     */
    @Override public void
    cook(@Nullable Scanner scanner) throws CompileException, IOException {
        assert scanner != null;
        this.cook(new Parser(scanner));
    }

    /**
     * Parses any IMPORT declarations, and then exactly <em>one</em> statement, then generates a class with a single
     * method that contains that statement.
     */
    public void
    cook(Parser parser) throws CompileException, IOException {

        // Create a compilation unit and parse any import declarations.
        Java.CompilationUnit compilationUnit = this.makeCompilationUnit(parser);

        // Parse the statement.
        Statement statement = parser.parseStatement();

        // Add one class declaration to the compilation unit.
        final Java.AbstractClassDeclaration
        cd = this.addPackageMemberClassDeclaration(parser.location(), compilationUnit);

        // Add one single-statement method to the class declaration.
        cd.addDeclaredMethod(
            this.makeMethodDeclaration(parser.location(), this.optionalThrownExceptions, statement)
        );

        // Compile and load the compilation unit.
        Class<?> c = this.compileToClass(compilationUnit);

        // Find the statement method by name.
        try {
            this.result = c.getDeclaredMethod(StatementEvaluator.METHOD_NAME);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException((
                "SNO: Loaded class does not declare method \""
                + StatementEvaluator.METHOD_NAME
                + "\""
            ), ex);
        }
    }

    /**
     * To the given {@link Java.AbstractClassDeclaration}, adds
     * <ul>
     *   <li>
     *     A public method declaration with the given return type, name, parameter names and values and thrown
     *     exceptions
     *   </li>
     *   <li>A block</li>
     * </ul>
     */
    protected Java.MethodDeclarator
    makeMethodDeclaration(
        Location             location,
        @Nullable Class<?>[] optionalThrownExceptions,
        Java.BlockStatement  statement
    ) {

        Java.FunctionDeclarator.FormalParameters fps = new Java.FunctionDeclarator.FormalParameters(
            location,
            new Java.FunctionDeclarator.FormalParameter[0],
            false
        );

        return new Java.MethodDeclarator(
            location,                                                // location
            null,                                                    // optionalDocComment
            new Java.Modifiers(                                      // modifiers
                (short) (Mod.PUBLIC | Mod.STATIC),
                new Java.Annotation[0]
            ),
            null,                                                    // optionalTypeParameters
            this.classToType(location, void.class),                  // type
            StatementEvaluator.METHOD_NAME,                          // name
            fps,                                                     // formalParameters
            this.classesToTypes(location, optionalThrownExceptions), // thrownExceptions
            Collections.singletonList(statement)                     // optionalStatements
        );
    }

    void
    execute() throws Exception { this.assertCooked().invoke(null); }

    private Method
    assertCooked() {

        if (this.result != null) return this.result;

        throw new IllegalStateException("Must only be called after \"cook()\"");
    }
}
