package common.java.model;

/**
 * Created by huzuoliang on 2017/7/16.
 */
public enum TrickStatus {
    ONE_HOT(null, 1, 0),
    HASH_TRICK(ONE_HOT.BITS, 1, 0),
    SAMPLE_ON_NEGATIVE(HASH_TRICK.BITS, 1, 0),
    PRINT_EVERY_NUMLINES(SAMPLE_ON_NEGATIVE.BITS, 25, -1),
    MAX_FEATURES(PRINT_EVERY_NUMLINES.BITS, 26, 0),
    EPOCHS(MAX_FEATURES.BITS, 10, 1);

    private final LongBitFormat BITS;

    TrickStatus(LongBitFormat previous, int length, long min) {
        BITS = new LongBitFormat(name(), previous, length, min);
    }

    public static boolean useOneHotCode(long status) {
        return ONE_HOT.BITS.retrieve(status) == 1;
    }

    public static boolean useHashTrick(long status) {
        return HASH_TRICK.BITS.retrieve(status) == 1;
    }

    public static boolean useSampleNegative(long status) {
        return SAMPLE_ON_NEGATIVE.BITS.retrieve(status) == 1;
    }

    public static int getPrintEveryLineNum(long status){
        return (int) PRINT_EVERY_NUMLINES.BITS.retrieve(status);
    }

    public static int getMaxFeatures(long status){
        return (int) MAX_FEATURES.BITS.retrieve(status);
    }

    public static int getEpochs(long status){
        return (int) EPOCHS.BITS.retrieve(status);
    }

    public static long toLong(int useOneHotCode, int useHashTrick,
                       int useSampleNegative,int printEveryLineNum,
                       int maxFeatures, int epochs) {
        long h = 0;
        h = ONE_HOT.BITS.combine(useOneHotCode, h);
        h = HASH_TRICK.BITS.combine(useHashTrick, h);
        h = SAMPLE_ON_NEGATIVE.BITS.combine(useSampleNegative, h);
        h = PRINT_EVERY_NUMLINES.BITS.combine(printEveryLineNum,h);
        h = MAX_FEATURES.BITS.combine(maxFeatures,h);
        h = EPOCHS.BITS.combine(epochs,h);
        return h;
    }
}
