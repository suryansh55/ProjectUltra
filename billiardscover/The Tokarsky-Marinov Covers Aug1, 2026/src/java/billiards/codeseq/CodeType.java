package billiards.codeseq;

public enum CodeType {
    OSO,  // open stable odd
    OSNO, // open stable not odd
    ONS,  // open not stable
    CS,   // closed stable
    CNS;  // closed not stable

    public static boolean isStable(final CodeType codeType) {
        switch (codeType) {
        case OSO:
            return true;
        case OSNO:
            return true;
        case ONS:
            return false;
        case CS:
            return true;
        case CNS:
            return false;
        default:
            // Java does not check for exhaustiveness when switching on an enum
            throw new RuntimeException("unknown code type: " + codeType);
        }
    }
}
