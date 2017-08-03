package common.java.model;

import java.io.Serializable;

/**
 * Created by huzuoliang on 2017/7/16.
 */
public class LongBitFormat implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String NAME;
    /** Bit offset */
    private final int OFFSET;
    /** Bit length */
    private final int LENGTH;
    /** Minimum value */
    private final long MIN;
    /** Maximum value */
    private final long MAX;
    /** Bit mask */
    private final long MASK;

    public LongBitFormat(String name, LongBitFormat previous, int length,
                         long min) {
        NAME = name;
        OFFSET = previous == null ? 0 : previous.OFFSET + previous.LENGTH;
        LENGTH = length;
        MIN = min;
        MAX = ((-1L) >>> (64 - LENGTH));//移动的位数，右移64-Leng位，相当于保留length位
        MASK = MAX << OFFSET;
    }

    /** Retrieve the value from the record. */
    public long retrieve(long record) {
        return (record & MASK) >>> OFFSET;
    }

    /** Combine the value to the record. */
    public long combine(long value, long record) {
        if (value < MIN) {
            throw new IllegalArgumentException(
                    "Illagal value: " + NAME + " = " + value + " < MIN = " + MIN);
        }
        if (value > MAX) {
            throw new IllegalArgumentException(
                    "Illagal value: " + NAME + " = " + value + " > MAX = " + MAX);
        }
        return (record & ~MASK) | (value << OFFSET);
    }

    public long getMin() {
        return MIN;
    }
}
