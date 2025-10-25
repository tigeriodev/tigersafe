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

package fr.tigeriodev.tigersafe.tests.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.data.TOTP;
import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public class TOTPTest extends TestClass {
    
    @Test
    void testCommon() {
        TOTP totp = TestsTOTP.newCommonTOTP1();
        
        Instant initTime = Instant.ofEpochSecond(417L);
        totp.updateCurTime(initTime);
        assertEquals("605961", totp.getCurCode());
        assertEquals("369327", totp.getNextCode());
        
        assertTrue(totp.updateCurTime(initTime.plusSeconds(30)));
        assertEquals("369327", totp.getCurCode());
        assertEquals("809365", totp.getNextCode());
        
        assertTrue(totp.updateCurTime(totp.getNextIntervalStartTime()));
        assertEquals("809365", totp.getCurCode());
        assertEquals("610045", totp.getNextCode());
    }
    
    @Test
    void testNewCode() {
        byte[][] keys = new byte[][] {
                StringUtils.hexStrToBytes(
                        "31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                ),
                StringUtils.hexStrToBytes(
                        "31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                                + " 31 32 33 34 35 36 37 38 39 30 31 32"
                ),
                StringUtils.hexStrToBytes(
                        "31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                                + " 31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                                + " 31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                                + " 31 32 33 34"
                )
        };
        
        for (byte[] key : keys) {
            for (TOTP.Algorithm algo : TOTP.Algorithm.values()) {
                for (
                        int digitsNum = TOTP.DIGITS_NUM_RANGE.min;
                        digitsNum <= TOTP.DIGITS_NUM_RANGE.getMax();
                        digitsNum++
                ) {
                    TOTP totp = new TOTP(key, "", "", algo, digitsNum, 30);
                    String newCode1 = totp.newCode(25L);
                    assertNotEquals(
                            newCode1,
                            totp.newCode(26L),
                            () -> "Same codes for " + new String(totp.getURI())
                    );
                    assertEquals(
                            digitsNum,
                            newCode1.length(),
                            () -> "Invalid code length for " + new String(totp.getURI())
                    );
                }
            }
        }
    }
    
    @Nested
    class FromURI {
        
        @Test
        void testOnlyKey() {
            char[] fullURI =
                    "otpauth://totp/?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI("ABCWZ3PPEHPK3VXL".toCharArray());
            assertArrayEquals(fullURI, totp.getURI());
        }
        
        @Test
        void testOnlySpacedKey() {
            char[] fullURI =
                    "otpauth://totp/?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            String[] urisToTest = new String[] {
                    "ABCW Z3PP EHPK 3VXL",
                    "ABCWZ   3PPE   HPK3   VXL",
                    "A B C W Z 3 P P E H P K 3 V X L",
                    "ABCWZ   3P     PE  HPK3 VXL",
                    "  ABCWZ   3P     PE  HPK3 VXL   "
            };
            for (String uri : urisToTest) {
                TOTP totp = TOTP.fromURI(uri.toCharArray());
                assertArrayEquals(fullURI, totp.getURI(), () -> "uri = " + uri);
            }
        }
        
        @Test
        void testSimple() {
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertArrayEquals(fullURI, totp.getURI());
            
            assertEquals(totp.label, "user@host.com");
            assertEquals(totp.issuer, "");
            assertEquals(totp.algo, TOTP.Algorithm.SHA1);
            assertEquals(totp.digitsNum, 6);
            assertEquals(totp.periodSeconds, 30);
            
            String[] urisToTest = new String[] {
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL",
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1",
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&digits=6",
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&period=30",
                    "otpauth://totp/user@host.com?period=30&secret=ABCWZ3PPEHPK3VXL"
            };
            for (String uri : urisToTest) {
                TOTP totp2 = TOTP.fromURI(uri.toCharArray());
                assertEquals(totp, totp2, () -> "uri = " + uri);
                assertArrayEquals(fullURI, totp2.getURI(), () -> "uri = " + uri);
            }
        }
        
        @Test
        void testAlgo() {
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA256&digits=6&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.algo, TOTP.Algorithm.SHA256);
            assertArrayEquals(fullURI, totp.getURI());
            
            TOTP totp2 = TOTP.fromURI(
                    "otpauth://totp/user@host.com?algorithm=SHA256&secret=ABCWZ3PPEHPK3VXL"
                            .toCharArray()
            );
            assertEquals(totp, totp2);
            assertArrayEquals(fullURI, totp2.getURI());
        }
        
        @Test
        void testDigits() {
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=7&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.digitsNum, 7);
            assertArrayEquals(fullURI, totp.getURI());
            
            TOTP totp2 = TOTP.fromURI(
                    "otpauth://totp/user@host.com?digits=7&secret=ABCWZ3PPEHPK3VXL".toCharArray()
            );
            assertEquals(totp, totp2);
            assertArrayEquals(fullURI, totp2.getURI());
        }
        
        @Test
        void testMaxDigits() {
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> TOTP.fromURI(
                            "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=9&period=30"
                                    .toCharArray()
                    )
            );
            
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=8&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.digitsNum, TOTP.DIGITS_NUM_RANGE.getMax());
            assertArrayEquals(fullURI, totp.getURI());
        }
        
        @Test
        void testMinDigits() {
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> TOTP.fromURI(
                            "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=4&period=30"
                                    .toCharArray()
                    )
            );
            
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=5&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.digitsNum, TOTP.DIGITS_NUM_RANGE.min);
            assertArrayEquals(fullURI, totp.getURI());
        }
        
        @Test
        void testPeriod() {
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=60"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.periodSeconds, 60);
            assertArrayEquals(fullURI, totp.getURI());
            
            TOTP totp2 = TOTP.fromURI(
                    "otpauth://totp/user@host.com?period=60&secret=ABCWZ3PPEHPK3VXL".toCharArray()
            );
            assertEquals(totp, totp2);
            assertArrayEquals(fullURI, totp2.getURI());
        }
        
        @Test
        void testEmptyLabel() {
            char[] fullURI =
                    "otpauth://totp/?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertArrayEquals(fullURI, totp.getURI());
        }
        
        @Test
        void testIssuer() {
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&issuer=test_issuer&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.issuer, "test_issuer");
            assertArrayEquals(fullURI, totp.getURI());
        }
        
        @Test
        void testFullyCustom() {
            char[] fullURI =
                    "otpauth://totp/user@host.com?secret=ABCWZ3PPEHPK3VXL&issuer=test_issuer&algorithm=SHA256&digits=8&period=60"
                            .toCharArray();
            TOTP totp = TOTP.fromURI(fullURI);
            assertEquals(totp.label, "user@host.com");
            assertEquals(totp.issuer, "test_issuer");
            assertEquals(totp.algo, TOTP.Algorithm.SHA256);
            assertEquals(totp.digitsNum, 8);
            assertEquals(totp.periodSeconds, 60);
            assertArrayEquals(fullURI, totp.getURI());
            
            TOTP totp2 = TOTP.fromURI(
                    "otpauth://totp/user@host.com?issuer=test_issuer&period=60&digits=8&secret=ABCWZ3PPEHPK3VXL&algorithm=SHA256"
                            .toCharArray()
            );
            assertEquals(totp, totp2);
            assertArrayEquals(fullURI, totp2.getURI());
        }
        
        @Test
        void testUnique() {
            char[] fullURI1 =
                    "otpauth://totp/?secret=ABCWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            char[] fullURI2 =
                    "otpauth://totp/?secret=DEFWZ3PPEHPK3VXL&algorithm=SHA1&digits=6&period=30"
                            .toCharArray();
            TOTP totp1 = TOTP.fromURI(fullURI1);
            assertArrayEquals(fullURI1, totp1.getURI());
            TOTP totp2 = TOTP.fromURI(fullURI2);
            assertArrayEquals(fullURI2, totp2.getURI());
            
            assertNotEquals(totp1, totp2);
            assertFalse(Arrays.equals(totp1.getKeyBytes(), totp2.getKeyBytes()));
        }
        
    }
    
}
