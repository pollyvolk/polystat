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

package org.polystat;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cactoos.Func;
import org.cactoos.io.InputOf;
import org.cactoos.io.OutputTo;
import org.eolang.parser.Syntax;

/**
 * A collection of all EO files, which are accessible as XMIR elements.
 *
 * @since 1.0
 * @checkstyle AbbreviationAsWordInNameCheck (5 lines)
 */
public final class XMIR implements Func<String, XML> {

    /**
     * The directory with EO files.
     */
    private final Path sources;

    /**
     * The directory with .XML files and maybe other temp.
     */
    private final Path temp;

    /**
     * Ctor.
     * @param src The dir with .eo sources
     * @param tmp Temp dir with .xml files
     */
    public XMIR(final Path src, final Path tmp) {
        this.sources = src;
        this.temp = tmp;
    }

    @Override
    public XML apply(final String locator) throws Exception {
        final String[] parts = locator.split("\\.");
        final Path xml = this.temp.resolve("test.xml");
        if (!Files.exists(xml)) {
            new Syntax(
                "test",
                new InputOf(this.sources.resolve("test.eo")),
                new OutputTo(xml)
            ).parse();
        }
        XML obj = new XMLDocument(xml).nodes("/program/objects").get(0);
        for (int idx = 1; idx < parts.length; ++idx) {
            final List<XML> objs = obj.nodes(
                String.format("o[@name='%s']", parts[idx])
            );
            obj = objs.get(0);
        }
        return obj;
    }
}
