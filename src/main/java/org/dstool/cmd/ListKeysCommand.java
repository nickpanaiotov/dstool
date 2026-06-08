package org.dstool.cmd;

import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import org.dstool.cli.BgHelp;
import org.dstool.cli.PinResolver;
import org.dstool.cli.TokenAccess;
import org.dstool.token.FfmPkcs11Token;
import org.dstool.token.FfmPkcs11Token.SlotInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "list-keys",
        description = "Показва наличните ключове в токена (индекс, субект, сериен номер).")
public final class ListKeysCommand implements Callable<Integer> {

    @Mixin
    BgHelp help;

    @Option(names = "--pkcs11-lib", paramLabel = "<път>",
            description = "Път до PKCS#11 драйвера (.dylib). Ако липсва, се търси автоматично.")
    Path pkcs11Lib;

    @Option(names = "--slot", paramLabel = "<n>",
            description = "Индекс на слота. Ако липсва и слотовете са няколко, се показват за избор.")
    Integer slot;

    @Override
    public Integer call() {
        Path library = TokenAccess.resolveLibrary(pkcs11Lib);
        List<SlotInfo> slots = TokenAccess.slots(library);

        // With several slots and no explicit choice, show them (e.g. an authentication slot
        // vs. a separate qualified-signature slot) so the user can pick the right --slot.
        if (slot == null && slots.size() > 1) {
            System.out.println("Открити слотове (изберете подписващия с --slot <n>):");
            System.out.print(TokenAccess.formatSlots(slots));
            return 0;
        }
        int chosenSlot = TokenAccess.resolveSlot(slots, slot);

        char[] pin = PinResolver.resolve();
        try (FfmPkcs11Token token = new FfmPkcs11Token(library, pin, chosenSlot)) {
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            if (keys.isEmpty()) {
                System.out.println("Няма намерени ключове в токена.");
                return 0;
            }
            System.out.println("Намерени ключове:");
            for (int i = 0; i < keys.size(); i++) {
                var cert = keys.get(i).getCertificate().getCertificate();
                String subject = cert.getSubjectX500Principal().getName();
                String serial = cert.getSerialNumber().toString(16);
                // KeyUsage bit 1 = nonRepudiation (contentCommitment) — the eIDAS marker
                // of a qualified-signature key, as opposed to an authentication key.
                boolean[] ku = cert.getKeyUsage();
                String qualified = (ku != null && ku.length > 1 && ku[1])
                        ? "  [квалифициран подпис — non-repudiation]" : "";
                System.out.printf("  [%d] %s (сериен номер: %s)%s%n", i, subject, serial, qualified);
            }
            return 0;
        } finally {
            Arrays.fill(pin, '\0');
        }
    }
}
