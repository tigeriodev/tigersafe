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

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.MutableString;

public class MutableStringTest extends TestClass {
    
    @Test
    void test1() {
        testAllMutableStrings((mutStr) -> {
            assertArrayEquals("".toCharArray(), mutStr.getVal());
            
            mutStr.addChar('a');
            assertArrayEquals("a".toCharArray(), mutStr.getVal());
            
            mutStr.addChar('b');
            assertArrayEquals("ab".toCharArray(), mutStr.getVal());
            
            mutStr.addChars(" cd");
            assertArrayEquals("ab cd".toCharArray(), mutStr.getVal());
            
            mutStr.addChars("e");
            assertArrayEquals("ab cde".toCharArray(), mutStr.getVal());
            
            mutStr.addChars(" fg".toCharArray());
            assertArrayEquals("ab cde fg".toCharArray(), mutStr.getVal());
            
            mutStr.addChars("h".toCharArray());
            assertArrayEquals("ab cde fgh".toCharArray(), mutStr.getVal());
            
            mutStr.remLastChar();
            assertArrayEquals("ab cde fg".toCharArray(), mutStr.getVal());
            
            mutStr.remChars(3, 7);
            assertArrayEquals("ab fg".toCharArray(), mutStr.getVal());
            
            mutStr.insertChars(3, "cde ".toCharArray());
            assertArrayEquals("ab cde fg".toCharArray(), mutStr.getVal());
            
            mutStr.remChars(0, 9);
            assertArrayEquals("".toCharArray(), mutStr.getVal());
            
            mutStr.insertChars(0, "ab cde fg".toCharArray());
            assertArrayEquals("ab cde fg".toCharArray(), mutStr.getVal());
            
            mutStr.clear();
            assertArrayEquals("".toCharArray(), mutStr.getVal());
            
            mutStr.addChars("ab cde fg");
            assertArrayEquals("ab cde fg".toCharArray(), mutStr.getVal());
            
            mutStr.resize(6);
            assertArrayEquals("ab cde".toCharArray(), mutStr.getVal());
            
            mutStr.setChars("set chars");
            assertArrayEquals("set chars".toCharArray(), mutStr.getVal());
            
            mutStr.setChars("");
            assertArrayEquals("".toCharArray(), mutStr.getVal());
            
            mutStr.setChars("set chars");
            assertArrayEquals("set chars".toCharArray(), mutStr.getVal());
            
            int maxLenMinus1 = SafeDataManager.EXPECTED_PW_MAX_LEN - 1;
            mutStr.setChars("m".repeat(maxLenMinus1));
            assertArrayEquals("m".repeat(maxLenMinus1).toCharArray(), mutStr.getVal());
            
            mutStr.addChar('M');
            assertArrayEquals(("m".repeat(maxLenMinus1) + "M").toCharArray(), mutStr.getVal());
            
            mutStr.addChar('M');
            assertArrayEquals(("m".repeat(maxLenMinus1) + "MM").toCharArray(), mutStr.getVal());
            
            mutStr.remLastChar();
            assertArrayEquals(("m".repeat(maxLenMinus1) + "M").toCharArray(), mutStr.getVal());
            
            mutStr.remLastChar();
            assertArrayEquals("m".repeat(maxLenMinus1).toCharArray(), mutStr.getVal());
            
            int maxLen = SafeDataManager.EXPECTED_PW_MAX_LEN;
            mutStr.setChars("m".repeat(maxLen));
            assertArrayEquals("m".repeat(maxLen).toCharArray(), mutStr.getVal());
            
            mutStr.addChar('M');
            assertArrayEquals(("m".repeat(maxLen) + "M").toCharArray(), mutStr.getVal());
            
            mutStr.clear();
            assertArrayEquals("".toCharArray(), mutStr.getVal());
        });
    }
    
    void testAllMutableStrings(Consumer<MutableString> test) {
        for (MutableString mutStr : new MutableString[] {
                SafeDataManager.newSafePwHolder(),
                new MutableString.Simple(),
                new MutableString.Advanced(0, SafeDataManager.EXPECTED_PW_MAX_LEN >>> 1)
        }) {
            testLog.debug(() -> "Start " + mutStr);
            test.accept(mutStr);
        }
    }
    
}
