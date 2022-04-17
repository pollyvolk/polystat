/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2022 Polystat.org
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

import com.jcabi.log.Logger;
import com.jcabi.manifests.Manifests;
import com.jcabi.xml.XML;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import org.cactoos.Func;
import org.cactoos.io.OutputTo;
import org.cactoos.io.Stdin;
import org.cactoos.io.TeeInput;
import org.cactoos.list.ListOf;
import org.cactoos.scalar.LengthOf;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Main entrance.
 *
 * @since 1.0
 * @todo #1:1h Let's use some library for command line arguments parsing.
 *  The current implementation in this class is super primitive and must
 *  be replaced by something decent.
 * @todo #1:1h For some reason, the Logger.info() doesn't print anything
 *  to the console when the JAR is run via command line. It does print
 *  during testing, but doesn't show anything when being run as
 *  a JAR-with-dependencies. I didn't manage to find the reason, that's
 *  why added these "printf" instructions. Let's fix it, make sure
 *  Logger works in the JAR-with-dependencies, and remove this.stdout
 *  from this class at all.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@CommandLine.Command(
    name = "polystat",
    helpCommand = true,
    description = "Read our README in GitHub",
    mixinStandardHelpOptions = true,
    versionProvider = Polystat.Version.class
)
public final class Polystat implements Callable<Integer> {

    /**
     * Analyzers.
     */
    private static final Analysis[] ALL = {
        new AnFaR(),
        new AnOdin(),
    };


    @ArgGroup(exclusive = true)
    private IncludeExclude ie;

    private static class IncludeExclude {
        @CommandLine.Option(names = "--exclude", split=",", required = true) 
        Collection<String> exclude;

        @CommandLine.Option(names = "--include", split=",", required = true) 
        Collection<String> include;
    }

    /**
     * Source directory. If not specified, defaults to reading code from standard input.
     */
    @CommandLine.Option(
        names = "--files",
        description = "The directory with EO files."
    )
    private Path source;

    /**
     * Output directoty. If not specified, defaults to a temporary directory.
     */
    @CommandLine.Option(
        names = "--tmp",
        description = "The directory with .XML files and maybe other temp."
    )
    private Path temp;

    /**
     * Output directoty.
     */
    @CommandLine.Option(
        names = "--sarif",
        description = "Print JSON output in SARIF 2.0 format"
    )
    private boolean sarif;

    /**
     * Overrides the options of {@code this} with theinitialized options of other.
     * @param other Configs which will override this.
     */
    void overrideBy(final Polystat other) {
        this.sarif = other.sarif;
        if (other.temp != null) {
            this.temp = other.temp;
        }
        if (other.source != null) {
            this.source = other.source;
        }
        if (other.ie != null) {
            this.ie = other.ie;
        }
    }

    /**
     * Main entrance for Java command line.
     * @param args The args
     */
    @SuppressWarnings("PMD.DoNotCallSystemExit")
    public static void main(final String... cmdlineArgs) {
        final String[] configArgs =new ListOf<String>(new Config(Paths.get(".polystat"))).toArray(new String[0]);
        final Polystat config = new Polystat();
        final Polystat cmdline = new Polystat();
        new CommandLine(config).parseArgs(configArgs);
        new CommandLine(cmdline).parseArgs(cmdlineArgs);
        config.overrideBy(cmdline);
        try {
            System.exit(config.call());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Override
    public Integer call() throws Exception {
        final Path tempdir;
        if (this.temp == null) {
            tempdir = Files.createTempDirectory("polystat-temp");
        } else {
            tempdir = this.temp;
        }
        final Path sources;
        if (this.source == null) {
            sources = readCodeFromStdin();
        } else {
            sources = this.source;
        }
        final Iterable<Result> errors =
            this.scan(sources, tempdir);
        final Supplier<String> out;
        if (this.sarif) {
            out = new AsSarif(errors);
        } else {
            out = new AsConsole(errors);
        }

        Logger.info(this, "%s\n", out.get());
        return 0;
    }

    /**
     * Scan.
     * @param src Path with sources
     * @param tmp Path with temp files
     * @return Errors
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Iterable<Result> scan(final Path src, final Path tmp) {
        final Func<String, XML> xmir = new Program(src, tmp);
        final Collection<Result> errors = new ArrayList<>(Polystat.ALL.length);
        for (final Analysis analysis : Polystat.ALL) {
            try {
                errors.addAll(new ListOf<>(analysis.errors(xmir, "\\Phi.test")));
            // @checkstyle IllegalCatchCheck (1 line)
            } catch (final Exception ex) {
                errors.add(
                    new Result.Failed(
                        analysis.getClass(),
                        ex,
                        analysis.getClass().getName()
                    )
                );
            }
        }
        final Collection<Result> filteredResults; 
        if (this.ie == null) {
            // no filtering is necessary
            filteredResults = errors;
        } else if (this.ie.exclude == null) {
            // leave only those results that
            // came from rules in "include" list
            filteredResults = errors.stream().filter(
                e -> ie.include.stream()
                        .filter(rule -> e.ruleId().equals(rule))
                        .findAny()
                        .isPresent()
            ).collect(Collectors.toList());
        } else {
            // exclude the results that 
            // came from the rules from the "exclude" list
            filteredResults = errors.stream().filter(
                e -> ie.exclude.stream()
                        .filter(rule -> !e.ruleId().equals(rule))
                        .findAny()
                        .isPresent()
            ).collect(Collectors.toList());
        }
        return filteredResults;
    }

    /**
     * Reads the EO code from standard input,
     * creates a temporary directory and
     * writes the code to a new file in this directory called "test.eo".
     * @return Path object of "{tmpdir}/test.eo" files.
     * @throws Exception When IO fails.
     */
    private static Path readCodeFromStdin() throws Exception {
        final Path tmpdir = Files.createTempDirectory("polystat_stdin");
        final String name = "test.eo";
        final Path fullpath = tmpdir.resolve(Paths.get(name));
        final Path tmpfile = Files.createFile(fullpath);
        new LengthOf(
            new TeeInput(
                new Stdin(),
                new OutputTo(tmpfile)
            )
        ).value();
        return tmpdir;
    }

    /**
     * Version.
     * @since 1.0
     */
    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{
                Manifests.read("Polystat-Version"),
                Manifests.read("EO-Version"),
            };
        }
    }
}
