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
package org.polystat.odin;

import com.jcabi.xml.XML;
import java.util.List;
import java.util.stream.Collectors;
import org.cactoos.list.ListOf;
import org.polystat.Analysis;
import org.polystat.Xmir;
import org.polystat.odin.interop.java.EOOdinAnalyzer;
import org.polystat.odin.interop.java.OdinAnalysisErrorInterop;

/**
 * The implementation of analysis via odin (object dependency inspector).
 *
 * @see <a href="https://github.com/polystat/odin">Github</a>
 * @since 0.3
 */
public final class OdinAnalysis implements Analysis {

    /**
     * Odin analyzer that performs analysis.
     */
    private final EOOdinAnalyzer<String> analyzer;

    /**
     * XMIR representation of the entire source code.
     */
    private final Xmir xmir;

    /**
     * Ctor.
     * @param xmir XMIR representation of the entire source code.
     */
    public OdinAnalysis(final Xmir xmir) {
        this.xmir = xmir;
        this.analyzer = new EOOdinAnalyzer.EOOdinXmirAnalyzer();
    }

    @Override
    public Iterable<String> errors(final String locator) throws Exception {
        final XML xml = this.xmir.repr(locator);
        final String str = this.getObjectsHierarchy(xml);
        return this.analyzer.analyze(str).stream()
            .map(OdinAnalysisErrorInterop::message)
            .collect(Collectors.toList());
    }

    /**
     * Resolves object hierarchy for the give object represented in XMIR and
     * returns a well-formed XML.
     * @param xml XMIR of object to get hierarchy for
     * @return Well-formed XML containing objects that form a hierarchy in XMIR
     * @throws Exception on errors
     */
    private String getObjectsHierarchy(final XML xml) throws Exception {
        return String.format(
            "%s%n%s%n%s",
            "<objects>",
            this.resolveObjectHierarchy(xml),
            "</objects>"
        );
    }

    /**
     * Recursively resolves the decoratees for the given decorator object
     * represented as XMIR.
     * @param xml XMIR that represents an object
     * @return Concatenated XML string containing the whole object hierarchy
     * @throws Exception on errors
     */
    private String resolveObjectHierarchy(final XML xml) throws Exception {
        String result = xml.toString();
        for (final String decoratee : xml.xpath("o[@name='@']/@base")) {
            final List<String> split = new ListOf<>(decoratee.split("\\."));
            final String name = split.get(split.size() - 1);
            result = String.format(
                "%s%s",
                this.xmir.repr(String.format("\\Phi.%s", name)),
                result
            );
        }
        return result;
    }
}
