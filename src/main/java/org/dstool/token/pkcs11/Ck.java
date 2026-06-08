package org.dstool.token.pkcs11;

/**
 * PKCS#11 (Cryptoki v2.40) constants and the {@code CK_FUNCTION_LIST} layout used
 * by {@link Pkcs11Library}. Only the subset DSTool needs is modelled.
 *
 * <p>All Cryptoki scalar types ({@code CK_ULONG}, {@code CK_RV}, handles, ...) are
 * {@code unsigned long} on the LP64 ABI that both macOS arm64 and x86_64 use, so a
 * Java {@code long} maps each of them directly.</p>
 */
public final class Ck {

    private Ck() {
    }

    // --- return values ---
    public static final long CKR_OK = 0x00000000L;
    public static final long CKR_CANCEL = 0x00000001L;
    public static final long CKR_PIN_INCORRECT = 0x000000A0L;
    public static final long CKR_PIN_LOCKED = 0x000000A4L;
    public static final long CKR_USER_ALREADY_LOGGED_IN = 0x00000100L;
    public static final long CKR_BUFFER_TOO_SMALL = 0x00000150L;

    // sentinel returned in ulValueLen when an attribute is unavailable
    public static final long CK_UNAVAILABLE_INFORMATION = ~0L; // (CK_ULONG)-1

    // --- user types ---
    public static final long CKU_USER = 1L;

    // --- session flags ---
    public static final long CKF_RW_SESSION = 0x00000002L;
    public static final long CKF_SERIAL_SESSION = 0x00000004L;

    // --- object classes ---
    public static final long CKO_CERTIFICATE = 0x00000001L;
    public static final long CKO_PRIVATE_KEY = 0x00000003L;

    // --- key types ---
    public static final long CKK_RSA = 0x00000000L;
    public static final long CKK_EC = 0x00000003L;

    // --- attribute types ---
    public static final long CKA_CLASS = 0x00000000L;
    public static final long CKA_TOKEN = 0x00000001L;
    public static final long CKA_VALUE = 0x00000011L;
    public static final long CKA_KEY_TYPE = 0x00000100L;
    public static final long CKA_ID = 0x00000102L;
    public static final long CKA_SIGN = 0x00000108L;

    // --- mechanisms ---
    public static final long CKM_RSA_PKCS = 0x00000001L;
    public static final long CKM_ECDSA = 0x00001041L;

    // --- CK_FUNCTION_LIST byte offsets (LP64) ---
    // CK_VERSION (2 bytes) + 6 bytes padding => first function pointer at offset 8;
    // each subsequent pointer at 8 + 8 * index, indexes per the Cryptoki spec order.
    static final long OFF_C_Initialize = 8 + 8 * 0;
    static final long OFF_C_Finalize = 8 + 8 * 1;
    static final long OFF_C_GetSlotList = 8 + 8 * 4;
    static final long OFF_C_GetTokenInfo = 8 + 8 * 6;
    static final long OFF_C_OpenSession = 8 + 8 * 12;
    static final long OFF_C_CloseSession = 8 + 8 * 13;
    static final long OFF_C_Login = 8 + 8 * 18;
    static final long OFF_C_Logout = 8 + 8 * 19;
    static final long OFF_C_GetAttributeValue = 8 + 8 * 24;
    static final long OFF_C_FindObjectsInit = 8 + 8 * 26;
    static final long OFF_C_FindObjects = 8 + 8 * 27;
    static final long OFF_C_FindObjectsFinal = 8 + 8 * 28;
    static final long OFF_C_SignInit = 8 + 8 * 42;
    static final long OFF_C_Sign = 8 + 8 * 43;
    // enough to cover the highest offset we read
    static final long FUNCTION_LIST_BYTES = OFF_C_Sign + 8;

    // CK_ATTRIBUTE { CK_ULONG type; CK_VOID_PTR pValue; CK_ULONG ulValueLen; } = 24 bytes (LP64)
    static final long ATTRIBUTE_BYTES = 24;
    static final long ATTR_OFF_TYPE = 0;
    static final long ATTR_OFF_PVALUE = 8;
    static final long ATTR_OFF_LEN = 16;

    // CK_MECHANISM { CK_ULONG mechanism; CK_VOID_PTR pParameter; CK_ULONG ulParameterLen; } = 24 bytes
    static final long MECHANISM_BYTES = 24;

    // CK_TOKEN_INFO begins with CK_UTF8CHAR label[32] (space-padded). The full struct is
    // ~208 bytes; allocate generously since we only read the label.
    static final long TOKEN_INFO_BYTES = 256;
    static final int TOKEN_LABEL_LEN = 32;

    /** Human-friendly name for the common return values, for error messages. */
    public static String rvName(long rv) {
        if (rv == CKR_OK) return "CKR_OK";
        if (rv == CKR_CANCEL) return "CKR_CANCEL";
        if (rv == CKR_PIN_INCORRECT) return "CKR_PIN_INCORRECT";
        if (rv == CKR_PIN_LOCKED) return "CKR_PIN_LOCKED";
        if (rv == CKR_USER_ALREADY_LOGGED_IN) return "CKR_USER_ALREADY_LOGGED_IN";
        if (rv == CKR_BUFFER_TOO_SMALL) return "CKR_BUFFER_TOO_SMALL";
        return "CKR_0x" + Long.toHexString(rv);
    }
}
