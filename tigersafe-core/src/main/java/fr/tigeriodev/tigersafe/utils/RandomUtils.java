/*
 * Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package fr.tigeriodev.tigersafe.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class RandomUtils {
    
    private RandomUtils() {}
    
    public static char[] newRandomChars(int minLen, int maxLen, String alphabet)
            throws NoSuchAlgorithmException {
        int len = newRandomInt(minLen, maxLen);
        char[] res = new char[len];
        SecureRandom rand = SecureRandom.getInstanceStrong();
        int alphabetLen = alphabet.length();
        for (int i = 0; i < len; i++) {
            res[i] = alphabet.charAt(rand.nextInt(alphabetLen));
        }
        return res;
    }
    
    /**
     * NB: Should only be used with small (human) min and max values.
     * NB: Should prefer power of 2 for (max - min + 1).
     * @param min
     * @param max
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public static int newRandomInt(int min, int max) throws NoSuchAlgorithmException {
        if (min == max) {
            return min;
        }
        if (min > max) {
            throw new IllegalArgumentException("Min " + min + " > max " + max);
        }
        return min + SecureRandom.getInstanceStrong().nextInt(max - min + 1);
    }
    
    public static byte[] newRandomBytes(int minLen, int maxLen) throws NoSuchAlgorithmException {
        return newRandomBytesOfLen(newRandomInt(minLen, maxLen));
    }
    
    public static byte[] newRandomBytesOfLen(int len) throws NoSuchAlgorithmException {
        byte[] res = new byte[len];
        SecureRandom.getInstanceStrong().nextBytes(res);
        return res;
    }
    
    public static boolean newRandomBoolean() throws NoSuchAlgorithmException {
        return SecureRandom.getInstanceStrong().nextBoolean();
    }
    
}
