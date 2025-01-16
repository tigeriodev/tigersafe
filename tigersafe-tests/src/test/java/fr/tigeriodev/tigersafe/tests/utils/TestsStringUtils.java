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

package fr.tigeriodev.tigersafe.tests.utils;

import static org.junit.jupiter.api.Assertions.fail;

import java.security.NoSuchAlgorithmException;

import fr.tigeriodev.tigersafe.utils.RandomUtils;

public class TestsStringUtils {
    
    public static final String COMMON_CHARS =
            "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
    
    public static char[] newRandomPw() {
        try {
            return RandomUtils.newRandomChars(10, 20, COMMON_CHARS);
        } catch (NoSuchAlgorithmException ex) {
            fail("Could not generate a random test password: ", ex);
            return null;
        }
    }
    
    public static char[] newWrongPw(char[] validPw) {
        char[] wrongPw = validPw.clone();
        wrongPw[validPw.length - 1] = (char) (wrongPw[validPw.length - 1] + 1);
        return wrongPw;
    }
    
}
