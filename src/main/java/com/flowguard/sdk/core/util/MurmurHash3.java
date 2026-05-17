package com.flowguard.sdk.core.util;

import java.nio.charset.StandardCharsets;

public final class MurmurHash3 {

    private MurmurHash3() {}

    /**
     * Generates 32-bit hash from a string using MurmurHash3.
     */
    public static int hash32(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return hash32(bytes, bytes.length, 0);
    }

    /**
     * Generates 32-bit hash from a byte array.
     */
    public static int hash32(byte[] data, int length, int seed) {
        int h1 = seed;
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        int roundedLength = (length & 0xfffffffc);
        for (int i = 0; i < roundedLength; i += 4) {
            int k1 = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | (data[i + 3] << 24);

            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        switch (length & 0x03) {
            case 3:
                k1 ^= (data[roundedLength + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[roundedLength + 1] & 0xff) << 8;
            case 1:
                k1 ^= (data[roundedLength] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }

        h1 ^= length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);

        return h1;
    }
}
