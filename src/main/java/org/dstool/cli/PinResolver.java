package org.dstool.cli;

import java.io.Console;

/**
 * Resolves the card PIN without ever placing it on the command line (so it never
 * lands in shell history or {@code ps} output).
 *
 * <ol>
 *   <li>Interactive: a no-echo console prompt.</li>
 *   <li>Non-interactive (CI, piped): the {@code DSTOOL_PIN} environment variable.</li>
 * </ol>
 */
public final class PinResolver {

    public static final String ENV_PIN = "DSTOOL_PIN";

    private PinResolver() {
    }

    /** Returns the PIN as a {@code char[]}; the caller must zero it after use. */
    public static char[] resolve() {
        Console console = System.console();
        if (console != null) {
            char[] pin = console.readPassword("Въведете PIN на картата: ");
            if (pin == null || pin.length == 0) {
                throw new ExitException(2, "Не е въведен PIN.");
            }
            return pin;
        }
        String env = System.getenv(ENV_PIN);
        if (env != null && !env.isEmpty()) {
            return env.toCharArray();
        }
        throw new ExitException(2, "Няма достъпна конзола за въвеждане на PIN. "
                + "Задайте променливата " + ENV_PIN + " за неинтерактивна употреба.");
    }
}
