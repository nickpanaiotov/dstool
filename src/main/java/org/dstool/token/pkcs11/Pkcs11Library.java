package org.dstool.token.pkcs11;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Thin, pure-Java (Project Panama / FFM) binding to a PKCS#11 middleware library.
 * No JNI — which is exactly what lets it work inside a GraalVM native image.
 *
 * <p>The middleware {@code .dylib} is opened at <em>runtime</em> by explicit path
 * via {@link SymbolLookup#libraryLookup(Path, Arena)} (never {@code defaultLookup},
 * which is unsupported in native images). Function pointers are read from the
 * {@code CK_FUNCTION_LIST} returned by {@code C_GetFunctionList}, which every
 * conformant Cryptoki module exports.</p>
 *
 * <p>Not thread-safe: a PKCS#11 session must be used from a single thread (the
 * library is held in a confined {@link Arena}, created and closed on that thread).</p>
 */
public final class Pkcs11Library implements AutoCloseable {

    private final Arena arena; // owns the loaded library for the connection's lifetime
    private final Linker linker = Linker.nativeLinker();

    private final MethodHandle cInitialize;
    private final MethodHandle cFinalize;
    private final MethodHandle cGetSlotList;
    private final MethodHandle cGetTokenInfo;
    private final MethodHandle cOpenSession;
    private final MethodHandle cCloseSession;
    private final MethodHandle cLogin;
    private final MethodHandle cLogout;
    private final MethodHandle cGetAttributeValue;
    private final MethodHandle cFindObjectsInit;
    private final MethodHandle cFindObjects;
    private final MethodHandle cFindObjectsFinal;
    private final MethodHandle cSignInit;
    private final MethodHandle cSign;

    private Pkcs11Library(Arena arena, MemorySegment functionList) {
        this.arena = arena;
        MemorySegment fl = functionList.reinterpret(Ck.FUNCTION_LIST_BYTES);

        FunctionDescriptor rvAddr = FunctionDescriptor.of(JAVA_LONG, ADDRESS);
        this.cInitialize = bind(fl, Ck.OFF_C_Initialize, rvAddr);
        this.cFinalize = bind(fl, Ck.OFF_C_Finalize, rvAddr);
        this.cGetSlotList = bind(fl, Ck.OFF_C_GetSlotList,
                FunctionDescriptor.of(JAVA_LONG, JAVA_BYTE, ADDRESS, ADDRESS));
        this.cGetTokenInfo = bind(fl, Ck.OFF_C_GetTokenInfo,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS));
        this.cOpenSession = bind(fl, Ck.OFF_C_OpenSession,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS));
        this.cCloseSession = bind(fl, Ck.OFF_C_CloseSession, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
        this.cLogin = bind(fl, Ck.OFF_C_Login,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG));
        this.cLogout = bind(fl, Ck.OFF_C_Logout, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
        this.cGetAttributeValue = bind(fl, Ck.OFF_C_GetAttributeValue,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG));
        this.cFindObjectsInit = bind(fl, Ck.OFF_C_FindObjectsInit,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG));
        this.cFindObjects = bind(fl, Ck.OFF_C_FindObjects,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS));
        this.cFindObjectsFinal = bind(fl, Ck.OFF_C_FindObjectsFinal, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
        this.cSignInit = bind(fl, Ck.OFF_C_SignInit,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG));
        this.cSign = bind(fl, Ck.OFF_C_Sign,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));
    }

    /** Loads the middleware at {@code libPath} and resolves the Cryptoki function table. */
    public static Pkcs11Library load(Path libPath) {
        // Confined (single-thread) arena: DSTool is single-threaded, and a confined arena
        // avoids native-image's experimental -H:+SharedArenaSupport requirement.
        Arena arena = Arena.ofConfined();
        try {
            SymbolLookup lookup = SymbolLookup.libraryLookup(libPath, arena);
            MemorySegment getFnList = lookup.find("C_GetFunctionList")
                    .orElseThrow(() -> new Pkcs11Exception(
                            "Библиотеката не изнася C_GetFunctionList: " + libPath));
            MethodHandle h = Linker.nativeLinker()
                    .downcallHandle(getFnList, FunctionDescriptor.of(JAVA_LONG, ADDRESS));

            MemorySegment ppList = arena.allocate(ADDRESS);
            long rv = (long) h.invoke(ppList);
            if (rv != Ck.CKR_OK) {
                throw new Pkcs11Exception("C_GetFunctionList", rv);
            }
            MemorySegment functionList = ppList.get(ADDRESS, 0);
            return new Pkcs11Library(arena, functionList);
        } catch (Pkcs11Exception e) {
            arena.close();
            throw e;
        } catch (Throwable t) {
            arena.close();
            throw new Pkcs11Exception("Зареждане на PKCS#11 библиотека неуспешно: " + t.getMessage());
        }
    }

    private MethodHandle bind(MemorySegment functionList, long offset, FunctionDescriptor desc) {
        MemorySegment fnPtr = functionList.get(ADDRESS, offset);
        return linker.downcallHandle(fnPtr, desc);
    }

    // --- Cryptoki operations ------------------------------------------------

    public void initialize() {
        check("C_Initialize", rv(cInitialize, MemorySegment.NULL));
    }

    /** Slot ids that currently have a token present. */
    public long[] slotsWithToken() {
        try (Arena t = Arena.ofConfined()) {
            MemorySegment pCount = t.allocate(JAVA_LONG);
            check("C_GetSlotList", rv(cGetSlotList, (byte) 1, MemorySegment.NULL, pCount));
            long n = pCount.get(JAVA_LONG, 0);
            if (n == 0) {
                return new long[0];
            }
            MemorySegment pSlots = t.allocate(JAVA_LONG, n);
            check("C_GetSlotList", rv(cGetSlotList, (byte) 1, pSlots, pCount));
            n = pCount.get(JAVA_LONG, 0);
            long[] slots = new long[(int) n];
            for (int i = 0; i < n; i++) {
                slots[i] = pSlots.getAtIndex(JAVA_LONG, i);
            }
            return slots;
        }
    }

    /** The token label for a slot (from {@code CK_TOKEN_INFO}); no login required. */
    public String tokenLabel(long slotId) {
        try (Arena t = Arena.ofConfined()) {
            MemorySegment info = t.allocate(Ck.TOKEN_INFO_BYTES);
            long rv = rv(cGetTokenInfo, slotId, info);
            if (rv != Ck.CKR_OK) {
                return "(слот " + slotId + ")";
            }
            byte[] label = new byte[Ck.TOKEN_LABEL_LEN];
            MemorySegment.copy(info, JAVA_BYTE, 0, label, 0, Ck.TOKEN_LABEL_LEN);
            return new String(label, java.nio.charset.StandardCharsets.UTF_8).trim();
        }
    }

    public long openReadOnlySession(long slotId) {
        try (Arena t = Arena.ofConfined()) {
            MemorySegment phSession = t.allocate(JAVA_LONG);
            check("C_OpenSession", rv(cOpenSession, slotId, Ck.CKF_SERIAL_SESSION,
                    MemorySegment.NULL, MemorySegment.NULL, phSession));
            return phSession.get(JAVA_LONG, 0);
        }
    }

    public void login(long session, char[] pin) {
        byte[] pinBytes = utf8(pin);
        try (Arena t = Arena.ofConfined()) {
            MemorySegment pPin = t.allocate(pinBytes.length);
            MemorySegment.copy(pinBytes, 0, pPin, JAVA_BYTE, 0, pinBytes.length);
            long rv = rv(cLogin, session, Ck.CKU_USER, pPin, (long) pinBytes.length);
            pPin.fill((byte) 0);
            if (rv != Ck.CKR_OK && rv != Ck.CKR_USER_ALREADY_LOGGED_IN) {
                throw new Pkcs11Exception("C_Login", rv);
            }
        } finally {
            java.util.Arrays.fill(pinBytes, (byte) 0);
        }
    }

    public void logout(long session) {
        rv(cLogout, session); // best-effort; ignore result on teardown
    }

    public void closeSession(long session) {
        rv(cCloseSession, session); // best-effort
    }

    /** Handles of every object on {@code session} whose CKA_CLASS equals {@code objectClass}. */
    public long[] findByClass(long session, long objectClass) {
        try (Arena t = Arena.ofConfined()) {
            MemorySegment classVal = t.allocate(JAVA_LONG);
            classVal.set(JAVA_LONG, 0, objectClass);
            MemorySegment tmpl = t.allocate(Ck.ATTRIBUTE_BYTES);
            tmpl.set(JAVA_LONG, Ck.ATTR_OFF_TYPE, Ck.CKA_CLASS);
            tmpl.set(ADDRESS, Ck.ATTR_OFF_PVALUE, classVal);
            tmpl.set(JAVA_LONG, Ck.ATTR_OFF_LEN, 8);

            check("C_FindObjectsInit", rv(cFindObjectsInit, session, tmpl, 1L));
            List<Long> handles = new ArrayList<>();
            MemorySegment phObjects = t.allocate(JAVA_LONG, 32);
            MemorySegment pCount = t.allocate(JAVA_LONG);
            while (true) {
                check("C_FindObjects", rv(cFindObjects, session, phObjects, 32L, pCount));
                long c = pCount.get(JAVA_LONG, 0);
                for (int i = 0; i < c; i++) {
                    handles.add(phObjects.getAtIndex(JAVA_LONG, i));
                }
                if (c < 32) {
                    break;
                }
            }
            rv(cFindObjectsFinal, session);
            long[] out = new long[handles.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = handles.get(i);
            }
            return out;
        }
    }

    /** Raw value of attribute {@code attrType} on {@code object}, or {@code null} if absent. */
    public byte[] getAttribute(long session, long object, long attrType) {
        try (Arena t = Arena.ofConfined()) {
            MemorySegment attr = t.allocate(Ck.ATTRIBUTE_BYTES);
            attr.set(JAVA_LONG, Ck.ATTR_OFF_TYPE, attrType);
            attr.set(ADDRESS, Ck.ATTR_OFF_PVALUE, MemorySegment.NULL);
            attr.set(JAVA_LONG, Ck.ATTR_OFF_LEN, 0);

            long rv = rv(cGetAttributeValue, session, object, attr, 1L);
            if (rv != Ck.CKR_OK) {
                return null;
            }
            long len = attr.get(JAVA_LONG, Ck.ATTR_OFF_LEN);
            if (len == Ck.CK_UNAVAILABLE_INFORMATION || len == 0) {
                return new byte[0];
            }
            MemorySegment buf = t.allocate(len);
            attr.set(ADDRESS, Ck.ATTR_OFF_PVALUE, buf);
            attr.set(JAVA_LONG, Ck.ATTR_OFF_LEN, len);
            check("C_GetAttributeValue", rv(cGetAttributeValue, session, object, attr, 1L));

            byte[] out = new byte[(int) len];
            MemorySegment.copy(buf, JAVA_BYTE, 0, out, 0, (int) len);
            return out;
        }
    }

    /** Attribute read as a CK_ULONG (native endianness), or {@code null} if absent. */
    public Long getAttributeULong(long session, long object, long attrType) {
        byte[] raw = getAttribute(session, object, attrType);
        if (raw == null || raw.length < 8) {
            return null;
        }
        try (Arena t = Arena.ofConfined()) {
            MemorySegment seg = t.allocate(JAVA_LONG);
            MemorySegment.copy(raw, 0, seg, JAVA_BYTE, 0, 8);
            return seg.get(JAVA_LONG, 0);
        }
    }

    /** Single-part signature ({@code C_SignInit} + {@code C_Sign}, two-pass length idiom). */
    public byte[] sign(long session, long mechanism, long keyHandle, byte[] data) {
        try (Arena t = Arena.ofConfined()) {
            MemorySegment mech = t.allocate(Ck.MECHANISM_BYTES);
            mech.set(JAVA_LONG, 0, mechanism);
            mech.set(ADDRESS, 8, MemorySegment.NULL);
            mech.set(JAVA_LONG, 16, 0L);
            check("C_SignInit", rv(cSignInit, session, mech, keyHandle));

            MemorySegment pData = t.allocate(Math.max(1, data.length));
            MemorySegment.copy(data, 0, pData, JAVA_BYTE, 0, data.length);
            MemorySegment pLen = t.allocate(JAVA_LONG);

            check("C_Sign", rv(cSign, session, pData, (long) data.length, MemorySegment.NULL, pLen));
            long sigLen = pLen.get(JAVA_LONG, 0);
            MemorySegment pSig = t.allocate(Math.max(1, sigLen));
            check("C_Sign", rv(cSign, session, pData, (long) data.length, pSig, pLen));

            long actual = pLen.get(JAVA_LONG, 0);
            byte[] out = new byte[(int) actual];
            MemorySegment.copy(pSig, JAVA_BYTE, 0, out, 0, (int) actual);
            return out;
        }
    }

    @Override
    public void close() {
        try {
            rv(cFinalize, MemorySegment.NULL);
        } catch (RuntimeException ignored) {
            // best-effort
        }
        arena.close();
    }

    // --- helpers ------------------------------------------------------------

    private long rv(MethodHandle handle, Object... args) {
        try {
            return (Long) handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new Pkcs11Exception("Извикване на PKCS#11 функция неуспешно: " + t.getMessage());
        }
    }

    private static void check(String op, long rv) {
        if (rv != Ck.CKR_OK) {
            throw new Pkcs11Exception(op, rv);
        }
    }

    private static byte[] utf8(char[] pin) {
        java.nio.ByteBuffer bb = java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(pin));
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        return out;
    }
}
