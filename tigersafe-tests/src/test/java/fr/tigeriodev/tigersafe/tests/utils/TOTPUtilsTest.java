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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import fr.tigeriodev.tigersafe.utils.TOTPUtils;

public class TOTPUtilsTest extends TestClass {
    
    @Test
    void testGenerateTOTP() {
        byte[] sha1Key = StringUtils
                .hexStrToBytes("31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30");
        byte[] sha256Key = StringUtils.hexStrToBytes(
                "31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                        + " 31 32 33 34 35 36 37 38 39 30 31 32"
        );
        byte[] sha512Key = StringUtils.hexStrToBytes(
                "31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                        + " 31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                        + " 31 32 33 34 35 36 37 38 39 30 31 32 33 34 35 36 37 38 39 30"
                        + " 31 32 33 34"
        );
        long t0 = 0L;
        long periodSeconds = 30L;
        int codeDigits = 8;
        String sha1Crypto = "HmacSHA1";
        String sha256Crypto = "HmacSHA256";
        String sha512Crypto = "HmacSHA512";
        
        assertEquals(
                "94287082",
                TOTPUtils.generateTOTP(sha1Key, (59L - t0) / periodSeconds, codeDigits, sha1Crypto)
        );
        assertEquals(
                "46119246",
                TOTPUtils.generateTOTP(
                        sha256Key,
                        (59L - t0) / periodSeconds,
                        codeDigits,
                        sha256Crypto
                )
        );
        assertEquals(
                "90693936",
                TOTPUtils.generateTOTP(
                        sha512Key,
                        (59L - t0) / periodSeconds,
                        codeDigits,
                        sha512Crypto
                )
        );
        
        assertEquals(
                "07081804",
                TOTPUtils.generateTOTP(
                        sha1Key,
                        (1111111109L - t0) / periodSeconds,
                        codeDigits,
                        sha1Crypto
                )
        );
        assertEquals(
                "68084774",
                TOTPUtils.generateTOTP(
                        sha256Key,
                        (1111111109L - t0) / periodSeconds,
                        codeDigits,
                        sha256Crypto
                )
        );
        assertEquals(
                "25091201",
                TOTPUtils.generateTOTP(
                        sha512Key,
                        (1111111109L - t0) / periodSeconds,
                        codeDigits,
                        sha512Crypto
                )
        );
        
        assertEquals(
                "14050471",
                TOTPUtils.generateTOTP(
                        sha1Key,
                        (1111111111L - t0) / periodSeconds,
                        codeDigits,
                        sha1Crypto
                )
        );
        assertEquals(
                "67062674",
                TOTPUtils.generateTOTP(
                        sha256Key,
                        (1111111111L - t0) / periodSeconds,
                        codeDigits,
                        sha256Crypto
                )
        );
        assertEquals(
                "99943326",
                TOTPUtils.generateTOTP(
                        sha512Key,
                        (1111111111L - t0) / periodSeconds,
                        codeDigits,
                        sha512Crypto
                )
        );
        
        assertEquals(
                "89005924",
                TOTPUtils.generateTOTP(
                        sha1Key,
                        (1234567890L - t0) / periodSeconds,
                        codeDigits,
                        sha1Crypto
                )
        );
        assertEquals(
                "91819424",
                TOTPUtils.generateTOTP(
                        sha256Key,
                        (1234567890L - t0) / periodSeconds,
                        codeDigits,
                        sha256Crypto
                )
        );
        assertEquals(
                "93441116",
                TOTPUtils.generateTOTP(
                        sha512Key,
                        (1234567890L - t0) / periodSeconds,
                        codeDigits,
                        sha512Crypto
                )
        );
        
        assertEquals(
                "69279037",
                TOTPUtils.generateTOTP(
                        sha1Key,
                        (2000000000L - t0) / periodSeconds,
                        codeDigits,
                        sha1Crypto
                )
        );
        assertEquals(
                "90698825",
                TOTPUtils.generateTOTP(
                        sha256Key,
                        (2000000000L - t0) / periodSeconds,
                        codeDigits,
                        sha256Crypto
                )
        );
        assertEquals(
                "38618901",
                TOTPUtils.generateTOTP(
                        sha512Key,
                        (2000000000L - t0) / periodSeconds,
                        codeDigits,
                        sha512Crypto
                )
        );
        
        assertEquals(
                "65353130",
                TOTPUtils.generateTOTP(
                        sha1Key,
                        (20000000000L - t0) / periodSeconds,
                        codeDigits,
                        sha1Crypto
                )
        );
        assertEquals(
                "77737706",
                TOTPUtils.generateTOTP(
                        sha256Key,
                        (20000000000L - t0) / periodSeconds,
                        codeDigits,
                        sha256Crypto
                )
        );
        assertEquals(
                "47863826",
                TOTPUtils.generateTOTP(
                        sha512Key,
                        (20000000000L - t0) / periodSeconds,
                        codeDigits,
                        sha512Crypto
                )
        );
    }
    
}
