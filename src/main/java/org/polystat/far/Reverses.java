/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 Polystat.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.polystat.far;

import com.jcabi.xml.ClasspathSources;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.jcabi.xml.XSL;
import com.jcabi.xml.XSLDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedList;
import org.cactoos.Func;
import org.cactoos.func.UncheckedFunc;
import org.cactoos.io.OutputTo;
import org.cactoos.io.ResourceOf;
import org.cactoos.list.ListOf;
import org.cactoos.text.TextOf;
import org.eolang.parser.Spy;
import org.eolang.parser.Xsline;

/**
 * Finding bugs via reverses.
 *
 * @since 1.0
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class Reverses {

    /**
     * The XMIR of the code.
     */
    private final UncheckedFunc<String, XML> xmir;

    /**
     * Ctor.
     * @param src The XMIR
     */
    public Reverses(final Func<String, XML> src) {
        this.xmir = new UncheckedFunc<>(src);
    }

    /**
     * Find all errors.
     * @param locator The locator of the object to analyze
     * @return List of errors
     * @throws IOException If fails
     */
    public Collection<String> errors(final String locator) throws IOException {
        final Collection<String> bugs = new LinkedList<>();
        final XML obj = this.xmir.apply(locator);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Xsline(obj, new OutputTo(baos), new Spy.Verbose(), new ListOf<>())
            .with(Reverses.xsl("expected.xsl").with("expected", "\\perp"))
            .with(Reverses.xsl("reverses.xsl"))
            .with(
                Reverses.xsl("calculate.xsl"),
                xml -> !xml.nodes("//r").isEmpty()
            )
            .with(Reverses.xsl("remove-input-perps.xsl"))
            .with(Reverses.xsl("remove-all-any.xsl"))
            .pass();
        final XML out = new XMLDocument(
            baos.toString(StandardCharsets.UTF_8.name())
        );
        if (out.nodes("//opt").size() > 2) {
            bugs.add("There are some possible errors");
        }
        return bugs;
    }

    /**
     * Make XSL.
     * @param name Name of it
     * @return A new XSL
     * @throws IOException If fails
     */
    private static XSL xsl(final String name) throws IOException {
        return new XSLDocument(
            new TextOf(
                new ResourceOf(
                    String.format("org/polystat/far/%s", name)
                )
            ).asString()
        ).with(new ClasspathSources());
    }

}