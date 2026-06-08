package org.dstool.cli;

import org.dstool.core.Pkcs11Locator;
import org.dstool.token.FfmPkcs11Token;
import org.dstool.token.FfmPkcs11Token.SlotInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared CLI logic for resolving the PKCS#11 library path and slot. */
public final class TokenAccess {

    private TokenAccess() {
    }

    /** Slots with a token present; fails (exit 1) if none. */
    public static List<SlotInfo> slots(Path library) {
        List<SlotInfo> slots = FfmPkcs11Token.listSlots(library);
        if (slots.isEmpty()) {
            throw new ExitException(1, "Не е открит токен в четеца (няма слот с карта).");
        }
        return slots;
    }

    /** Indented "[i] label" listing of slots, for help/error messages. */
    public static String formatSlots(List<SlotInfo> slots) {
        StringBuilder sb = new StringBuilder();
        for (SlotInfo s : slots) {
            sb.append(String.format("  [%d] %s%n", s.index(), s.label()));
        }
        return sb.toString();
    }

    /**
     * Picks the slot to use: the explicit {@code --slot} if given (validated), the only slot
     * if there is just one, otherwise an error listing the slots so the user can choose.
     */
    public static int resolveSlot(List<SlotInfo> slots, Integer requested) {
        if (requested != null) {
            if (requested < 0 || requested >= slots.size()) {
                throw new ExitException(2, "Невалиден слот " + requested
                        + ". Налични слотове:\n" + formatSlots(slots));
            }
            return requested;
        }
        if (slots.size() == 1) {
            return 0;
        }
        throw new ExitException(2, "Открити са няколко слота — изберете с --slot <n>:\n"
                + formatSlots(slots));
    }

    /**
     * Resolves the PKCS#11 library: the explicit {@code --pkcs11-lib} path if given,
     * otherwise the first auto-detected candidate.
     *
     * @throws ExitException with code 3 if no readable library can be found
     */
    public static Path resolveLibrary(Path explicit) {
        if (explicit != null) {
            if (!Files.isReadable(explicit)) {
                throw new ExitException(3, "PKCS#11 библиотеката не е намерена или не може да бъде прочетена: " + explicit);
            }
            return explicit;
        }
        return Pkcs11Locator.autodetect().orElseThrow(() -> new ExitException(3,
                "Не е намерена PKCS#11 библиотека. Задайте пътя с --pkcs11-lib.\nПроверени пътища:\n  "
                        + String.join("\n  ", Pkcs11Locator.candidates())));
    }
}
