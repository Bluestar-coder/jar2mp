package com.z0fsec.jar2mp.util;

public class ClassFileUtils {

    private static final int[] MAJOR_VERSION_TO_JAVA = new int[]{
            0, 0, 0, 0, 0, 0, 0, 0, // 0-7 unused
            0, 0, 0, 0, 0, 0, 0, 0, // 8-15 unused
            0, 0, 0, 0, 0, 0, 0, 0, // 16-23 unused
            0, 0, 0, 0, 0, 0, 0, 0, // 24-31 unused
            0, 0, 0, 0, 0, 0, 0, 0, // 32-39 unused
            0, 0, 0, 0, 0, 0, 0, 0, // 40-47 unused
            1,  // 48 = Java 1.4
            2,  // 49 = Java 5
            3,  // 50 = Java 6
            4,  // 51 = Java 7
            5,  // 52 = Java 8
            6,  // 53 = Java 9
            7,  // 54 = Java 10
            8,  // 55 = Java 11
            9,  // 56 = Java 12
            10, // 57 = Java 13
            11, // 58 = Java 14
            12, // 59 = Java 15
            13, // 60 = Java 16
            14, // 61 = Java 17
            15, // 62 = Java 18
            16, // 63 = Java 19
            17, // 64 = Java 20
            18, // 65 = Java 21
    };

    public static int majorVersionToJava(int majorVersion) {
        if (majorVersion >= 0 && majorVersion < MAJOR_VERSION_TO_JAVA.length) {
            return MAJOR_VERSION_TO_JAVA[majorVersion];
        }
        // Java 22+ follows pattern: majorVersion - 44
        if (majorVersion > 65) {
            return majorVersion - 44;
        }
        return 8; // default
    }

    public static int readU1(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    public static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static int readU4(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
                ((data[offset + 1] & 0xFF) << 16) |
                ((data[offset + 2] & 0xFF) << 8) |
                (data[offset + 3] & 0xFF);
    }
}
