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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.Base32Encoding;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public class Base32EncodingTest extends TestClass {
    
    @Nested
    class EncodeDecode {
        
        @Test
        void test1() {
            testEncodeDecode("".getBytes(), "".toCharArray());
            testEncodeDecode("f".getBytes(), "MY======".toCharArray());
            testEncodeDecode("fo".getBytes(), "MZXQ====".toCharArray());
            testEncodeDecode("foo".getBytes(), "MZXW6===".toCharArray());
            testEncodeDecode("foob".getBytes(), "MZXW6YQ=".toCharArray());
            testEncodeDecode("fooba".getBytes(), "MZXW6YTB".toCharArray());
            testEncodeDecode("foobar".getBytes(), "MZXW6YTBOI======".toCharArray());
        }
        
        @Test
        void test2() {
            testEncodeDecode("abc".getBytes());
            testEncodeDecode(
                    "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdef"
                            .getBytes()
            );
        }
        
        @Test
        void test3() {
            byte[] bytes = new byte[0];
            for (int i = 0; i < 1024; i++) {
                bytes = Arrays.copyOf(bytes, bytes.length + 1);
                bytes[bytes.length - 1] = (byte) i;
                testEncodeDecode(bytes);
            }
        }
        
        void testEncodeDecode(byte[] bytes) {
            testEncodeDecode(bytes, null);
        }
        
        void testEncodeDecode(byte[] bytes, char[] expectedEncoded) {
            char[] encoded = Base32Encoding.encode(bytes);
            if (expectedEncoded != null) {
                assertArrayEquals(
                        expectedEncoded,
                        encoded,
                        () -> "bytes = " + StringUtils.bytesToStr(bytes) + ", encoded = "
                                + Arrays.toString(encoded)
                );
            }
            byte[] decoded = Base32Encoding.decode(encoded);
            assertArrayEquals(
                    bytes,
                    decoded,
                    () -> "bytes = " + StringUtils.bytesToStr(bytes) + ", encoded = "
                            + Arrays.toString(encoded) + ", decoded = "
                            + StringUtils.bytesToStr(decoded)
            );
        }
        
    }
    
    @Nested
    class Decode {
        
        @Test
        void testLowerCase() {
            testDecode("".toCharArray(), "".getBytes());
            testDecode("my======".toCharArray(), "f".getBytes());
            testDecode("mzxq====".toCharArray(), "fo".getBytes());
            testDecode("mzxw6===".toCharArray(), "foo".getBytes());
            testDecode("mzxw6yq=".toCharArray(), "foob".getBytes());
            testDecode("mzxw6ytb".toCharArray(), "fooba".getBytes());
            testDecode("mzxw6ytboi======".toCharArray(), "foobar".getBytes());
        }
        
        @Test
        void testMixedCase() {
            testDecode("".toCharArray(), "".getBytes());
            testDecode("mY======".toCharArray(), "f".getBytes());
            testDecode("mZxQ====".toCharArray(), "fo".getBytes());
            testDecode("MZXw6===".toCharArray(), "foo".getBytes());
            testDecode("mZxW6yQ=".toCharArray(), "foob".getBytes());
            testDecode("mZXw6ytb".toCharArray(), "fooba".getBytes());
            testDecode("mzxW6ytboI======".toCharArray(), "foobar".getBytes());
        }
        
        void testDecode(char[] encoded, byte[] expectedDecoded) {
            byte[] decoded = Base32Encoding.decode(encoded);
            assertArrayEquals(
                    expectedDecoded,
                    decoded,
                    () -> "encoded = " + Arrays.toString(encoded) + ", decoded = "
                            + StringUtils.bytesToStr(decoded)
            );
        }
        
    }
    
}
