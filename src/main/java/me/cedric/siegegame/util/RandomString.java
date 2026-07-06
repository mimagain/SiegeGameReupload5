package me.cedric.siegegame.util;

import java.util.Random;

public class RandomString {

    
    public static final int DEFAULT_LENGTH = 8;

    
    private static final char[] SYMBOL;

    
    private static final int KEY_BITS;

    
    static {
        StringBuilder symbol = new StringBuilder();
        for (char character = '0'; character <= '9'; character++) {
            symbol.append(character);
        }
        for (char character = 'a'; character <= 'z'; character++) {
            symbol.append(character);
        }
        for (char character = 'A'; character <= 'Z'; character++) {
            symbol.append(character);
        }
        SYMBOL = symbol.toString().toCharArray();
        int bits = Integer.SIZE - Integer.numberOfLeadingZeros(SYMBOL.length);
        KEY_BITS = bits - (Integer.bitCount(SYMBOL.length) == bits ? 0 : 1);
    }

    
    private final Random random;

    
    private final int length;

    
    public RandomString() {
        this(DEFAULT_LENGTH);
    }

    
    public RandomString(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("A random string's length cannot be zero or negative");
        }
        this.length = length;
        random = new Random();
    }

    
    public static String make() {
        return make(DEFAULT_LENGTH);
    }

    
    public static String make(int length) {
        return new RandomString(length).nextString();
    }

    
    public static String hashOf(int value) {
        char[] buffer = new char[(Integer.SIZE / KEY_BITS) + ((Integer.SIZE % KEY_BITS) == 0 ? 0 : 1)];
        for (int index = 0; index < buffer.length; index++) {
            buffer[index] = SYMBOL[(value >>> index * KEY_BITS) & (-1 >>> (Integer.SIZE - KEY_BITS))];
        }
        return new String(buffer);
    }

    
    public String nextString() {
        char[] buffer = new char[length];
        for (int index = 0; index < length; index++) {
            buffer[index] = SYMBOL[random.nextInt(SYMBOL.length)];
        }
        return new String(buffer);
    }
}



