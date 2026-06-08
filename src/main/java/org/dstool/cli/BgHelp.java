package org.dstool.cli;

import picocli.CommandLine.Option;

/**
 * Bulgarian {@code -h/--help} option, mixed into every command so the whole CLI
 * surface (including the standard help line) is localized.
 */
public final class BgHelp {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Показва тази помощ и изход.")
    boolean helpRequested;
}
