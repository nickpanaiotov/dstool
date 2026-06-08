package org.dstool.cmd;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import org.dstool.cli.BgHelp;
import org.dstool.cli.ExitException;
import org.dstool.cli.PinResolver;
import org.dstool.cli.TokenAccess;
import org.dstool.core.CadesSigner;
import org.dstool.core.SignatureFormat;
import org.dstool.token.FfmPkcs11Token;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "sign",
        description = "Подписва файл с CAdES подпис чрез PKCS#11 карта/токен (StampIT и съвместими).")
public final class SignCommand implements Callable<Integer> {

    @Mixin
    BgHelp help;

    @Parameters(index = "0", paramLabel = "<файл>", description = "Файл за подписване.")
    Path input;

    @Option(names = "--format", paramLabel = "<attached|detached>",
            description = "Тип подпис: attached = вграден (.p7m), detached = отделен (.p7s). По подразбиране: attached.")
    SignatureFormat format = SignatureFormat.ATTACHED;

    @Option(names = {"-o", "--output"}, paramLabel = "<файл>",
            description = "Изходен файл. По подразбиране: до входния файл със съответното разширение.")
    Path output;

    @Option(names = "--pkcs11-lib", paramLabel = "<път>",
            description = "Път до PKCS#11 драйвера (.dylib). Ако липсва, се търси автоматично.")
    Path pkcs11Lib;

    @Option(names = "--slot", paramLabel = "<n>",
            description = "Индекс на слота (вж. list-keys). Картите често държат "
                    + "квалифицирания подпис на отделен слот с отделен PIN.")
    Integer slot;

    @Option(names = "--key-index", paramLabel = "<n>",
            description = "Индекс на ключа за подписване (вж. list-keys). По подразбиране: 0.")
    int keyIndex = 0;

    @Option(names = "--digest", paramLabel = "<алгоритъм>",
            description = "Алгоритъм за дайджест: SHA256, SHA384 или SHA512. По подразбиране: SHA256.")
    DigestChoice digest = DigestChoice.SHA256;

    enum DigestChoice {
        SHA256(DigestAlgorithm.SHA256),
        SHA384(DigestAlgorithm.SHA384),
        SHA512(DigestAlgorithm.SHA512);

        private final DigestAlgorithm dss;

        DigestChoice(DigestAlgorithm dss) {
            this.dss = dss;
        }

        DigestAlgorithm toDss() {
            return dss;
        }
    }

    @Override
    public Integer call() throws Exception {
        if (input == null || !Files.isReadable(input)) {
            throw new ExitException(2, "Входният файл не съществува или не може да бъде прочетен: " + input);
        }
        Path library = TokenAccess.resolveLibrary(pkcs11Lib);
        int chosenSlot = TokenAccess.resolveSlot(TokenAccess.slots(library), slot);
        DSSDocument toSign = new FileDocument(input.toFile());

        char[] pin = PinResolver.resolve();
        try (FfmPkcs11Token token = new FfmPkcs11Token(library, pin, chosenSlot)) {
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            if (keys.isEmpty()) {
                throw new ExitException(1, "Не са намерени ключове в картата.");
            }
            if (keyIndex < 0 || keyIndex >= keys.size()) {
                throw new ExitException(2, "Невалиден индекс на ключ: " + keyIndex
                        + " (изберете между 0 и " + (keys.size() - 1) + ").");
            }
            DSSPrivateKeyEntry key = keys.get(keyIndex);

            DSSDocument signed = new CadesSigner().sign(toSign, token, key, format, digest.toDss());
            Path out = (output != null) ? output : defaultOutput(input, format);
            signed.save(out.toString());

            System.out.println("Готово: " + out.toAbsolutePath());
            return 0;
        } finally {
            Arrays.fill(pin, '\0');
        }
    }

    static Path defaultOutput(Path input, SignatureFormat format) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        return input.toAbsolutePath().resolveSibling(base + format.extension());
    }
}
