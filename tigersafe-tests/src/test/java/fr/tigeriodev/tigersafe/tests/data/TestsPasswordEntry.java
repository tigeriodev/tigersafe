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

import static fr.tigeriodev.tigersafe.tests.utils.TestsStringUtils.COMMON_CHARS;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

import fr.tigeriodev.tigersafe.data.PasswordEntry;

public class TestsPasswordEntry {
    
    private TestsPasswordEntry() {}
    
    public static class Data {
        
        public static PasswordEntry.Data newCommonChars(boolean withTOTP) {
            return new PasswordEntry.Data(
                    new String(COMMON_CHARS.toCharArray()),
                    COMMON_CHARS.toCharArray(),
                    Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
                    new String(COMMON_CHARS.toCharArray()),
                    new String(COMMON_CHARS.toCharArray()),
                    withTOTP ? TestsTOTP.newCommonTOTP1() : null
            );
        }
        
        public static PasswordEntry.Data[] newSimpleArr(boolean withTOTP) {
            return new PasswordEntry.Data[] {
                    new PasswordEntry.Data(
                            "name1",
                            "password1".toCharArray(),
                            Instant.ofEpochSecond(1L),
                            "site1",
                            "info1",
                            withTOTP ? TestsTOTP.newCommonTOTP1() : null
                    ),
                    new PasswordEntry.Data(
                            "name2",
                            "password2".toCharArray(),
                            Instant.ofEpochSecond(2L),
                            "site2",
                            "info2",
                            null
                    ),
                    new PasswordEntry.Data(
                            "name3",
                            "password3".toCharArray(),
                            Instant.ofEpochSecond(3L),
                            "site3",
                            "info3",
                            withTOTP ? TestsTOTP.newCommonTOTP2() : null
                    ),
            };
        }
        
    }
    
    public static void assertArrEquals(PasswordEntry[] arr1, PasswordEntry[] arr2) {
        if (arr1 == arr2) {
            return;
        }
        if ((arr1 == null && arr2 != null) || (arr2 == null && arr1 != null)) {
            fail("Arrays are different: arr1 = " + arr1 + " != arr2 = " + arr2);
        }
        if (arr1.length != arr2.length) {
            fail("Arrays have different length: arr1: " + arr1.length + " != arr2: " + arr2.length);
        }
        
        for (int i = 0; i < arr1.length; i++) {
            if (!equals(arr1[i], arr2[i])) {
                fail(
                        "Arrays have different element at " + i + ": arr1[" + i + "] = " + arr1[i]
                                + " != arr2[" + i + "] = " + arr2[i]
                );
            }
        }
    }
    
    public static boolean equals(PasswordEntry ent1, PasswordEntry ent2) {
        if (ent1 == ent2) {
            return true;
        }
        if ((ent1 == null && ent2 != null) || (ent2 == null && ent1 != null)) {
            return false;
        }
        if (ent1.getClass() != ent2.getClass()) {
            return false;
        }
        
        if (!Objects.equals(ent1.getCurrentName(), ent2.getCurrentName())) {
            return false;
        }
        if (!Arrays.equals(ent1.getCurrentPassword(), ent2.getCurrentPassword())) {
            return false;
        }
        if (
            !Objects.equals(
                    ent1.getCurrentLastPasswordChangeTime(),
                    ent2.getCurrentLastPasswordChangeTime()
            )
        ) {
            return false;
        }
        if (!Objects.equals(ent1.getCurrentSite(), ent2.getCurrentSite())) {
            return false;
        }
        if (!Objects.equals(ent1.getCurrentInfo(), ent2.getCurrentInfo())) {
            return false;
        }
        if (!Objects.equals(ent1.getCurrentTOTP(), ent2.getCurrentTOTP())) {
            return false;
        }
        return true;
    }
    
}
