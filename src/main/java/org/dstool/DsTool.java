package org.dstool;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.dstool.cli.BgHelp;
import org.dstool.cli.ExitException;
import org.dstool.cmd.ListKeysCommand;
import org.dstool.cmd.SignCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.InputStream;
import java.security.Security;
import java.util.Properties;

/**
 * DSTool — the missing macOS CAdES signer for StampIT / Bulgarian QES smart cards.
 *
 * <p>Root command; dispatches to {@code sign} and {@code list-keys}.</p>
 */
@Command(
        name = "dstool",
        versionProvider = DsTool.VersionProvider.class,
        description = "Подписване на документи с CAdES чрез StampIT карта/токен на macOS.",
        subcommands = {SignCommand.class, ListKeysCommand.class})
public final class DsTool implements Runnable {

    @Spec
    CommandSpec spec;

    @Mixin
    BgHelp help;

    @Option(names = {"-V", "--version"}, versionHelp = true, description = "Показва версията и изход.")
    boolean versionRequested;

    public static void main(String[] args) {
        // Keep the CLI output clean: DSS is chatty at INFO/WARN via slf4j-simple (e.g. it
        // warns that the issuing CA isn't on the card — expected for BASELINE-B). Real
        // failures are thrown as exceptions and shown as "Грешка:". Override with
        // -Dorg.slf4j.simpleLogger.defaultLogLevel=info for verbose diagnostics.
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        CommandLine cmd = new CommandLine(new DsTool())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    int code = (ex instanceof ExitException ee) ? ee.code() : 1;
                    commandLine.getErr().println("Грешка: " + ex.getMessage());
                    return code;
                });
        System.exit(cmd.execute(args));
    }

    @Override
    public void run() {
        // No subcommand given: show usage.
        spec.commandLine().usage(System.out);
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            Properties p = new Properties();
            try (InputStream in = DsTool.class.getResourceAsStream("/version.properties")) {
                if (in != null) {
                    p.load(in);
                }
            }
            return new String[]{"DSTool " + p.getProperty("version", "dev")};
        }
    }
}
