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

import java.util.Set;

import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public class StringUtilsTest extends TestClass {
    
    @Test
    void testRemoveSeparatorsIn() {
        assertEquals("abc", StringUtils.removeSeparatorsIn("abc", Set.of(' ')));
        assertEquals("abc", StringUtils.removeSeparatorsIn(" abc ", Set.of(' ')));
        assertEquals("a bc", StringUtils.removeSeparatorsIn("a bc", Set.of(' ')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("a  bc", Set.of(' ')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("a  b c", Set.of(' ')));
        assertEquals("abc", StringUtils.removeSeparatorsIn(" a  b c ", Set.of(' ')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("   a  b    c  ", Set.of(' ')));
        
        assertEquals("abc,", StringUtils.removeSeparatorsIn("abc,", Set.of(' ', ',')));
        assertEquals(",abc", StringUtils.removeSeparatorsIn(",abc", Set.of(' ', ',')));
        assertEquals(",abc", StringUtils.removeSeparatorsIn(" ,abc ", Set.of(' ', ',')));
        assertEquals(",abc", StringUtils.removeSeparatorsIn(", a b c", Set.of(' ', ',')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("a  b c", Set.of(' ', ',')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("   a  b    c  ", Set.of(' ', ',')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("a,b,c", Set.of(' ', ',')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("a , b , c", Set.of(' ', ',')));
        assertEquals("abc", StringUtils.removeSeparatorsIn("   a , b ,   c  ,", Set.of(' ', ',')));
        
        assertEquals("!?*@", StringUtils.removeSeparatorsIn("!?*@", Set.of(',', ' ', '/', ';')));
        assertEquals("!?*@", StringUtils.removeSeparatorsIn("! ? * @", Set.of(',', ' ', '/', ';')));
        assertEquals(
                "!?*@",
                StringUtils.removeSeparatorsIn("!, ?, *, @", Set.of(',', ' ', '/', ';'))
        );
        assertEquals(
                "!?*@",
                StringUtils.removeSeparatorsIn(" !, ?, *, @ ", Set.of(',', ' ', '/', ';'))
        );
        assertEquals(
                "!?*@",
                StringUtils.removeSeparatorsIn(" !; ?; *; @ ", Set.of(',', ' ', '/', ';'))
        );
        assertEquals("!?*@", StringUtils.removeSeparatorsIn("!,?,*,@", Set.of(',', ' ', '/', ';')));
        assertEquals(
                "!?*@",
                StringUtils.removeSeparatorsIn("   !  , ?,  * ,   @  ", Set.of(',', ' ', '/', ';'))
        );
        
        assertEquals(
                "!?*@,/;",
                StringUtils.removeSeparatorsIn("!?*@,/;", Set.of(' ', ',', '/', ';'))
        );
        assertEquals(
                "!?*@,/;",
                StringUtils.removeSeparatorsIn("! ? * @ , / ;", Set.of(' ', ',', '/', ';'))
        );
        assertEquals(
                "!?*@,/;",
                StringUtils.removeSeparatorsIn("!  ?* @    ,/; ", Set.of(' ', ',', '/', ';'))
        );
    }
    
    @Test
    void testGetDuplicateCharIn() {
        assertEquals(null, StringUtils.getDuplicateCharIn("abc"));
        assertEquals('b', StringUtils.getDuplicateCharIn("abbc"));
        assertEquals(' ', StringUtils.getDuplicateCharIn("a  c"));
        assertEquals('a', StringUtils.getDuplicateCharIn("aabbcc"));
        assertEquals('a', StringUtils.getDuplicateCharIn("aaaaaaaaaabbcc"));
        assertEquals('a', StringUtils.getDuplicateCharIn("abcaghaij"));
        assertEquals('!', StringUtils.getDuplicateCharIn("abcdefghij!:;,*$)]|[(!"));
        assertEquals(null, StringUtils.getDuplicateCharIn("abcdefghij!:;,*$)]|[("));
        assertEquals(null, StringUtils.getDuplicateCharIn(TestsStringUtils.COMMON_CHARS));
        assertEquals(
                '!',
                StringUtils.getDuplicateCharIn(
                        TestsStringUtils.COMMON_CHARS + TestsStringUtils.COMMON_CHARS
                )
        );
    }
    
    @Test
    void testStripZerosAfterSep() {
        assertEquals("1", StringUtils.stripZerosAfterSep("1", '.'));
        assertEquals("0.1", StringUtils.stripZerosAfterSep("0.1", '.'));
        assertEquals("0.1", StringUtils.stripZerosAfterSep("0.10", '.'));
        assertEquals("0.1", StringUtils.stripZerosAfterSep("0.100", '.'));
        assertEquals("0.1002", StringUtils.stripZerosAfterSep("0.1002", '.'));
        assertEquals("0.1002", StringUtils.stripZerosAfterSep("0.10020", '.'));
        assertEquals("0.001", StringUtils.stripZerosAfterSep("0.001", '.'));
        assertEquals("0.001", StringUtils.stripZerosAfterSep("0.0010", '.'));
        assertEquals("0.001", StringUtils.stripZerosAfterSep("0.00100", '.'));
        assertEquals("0", StringUtils.stripZerosAfterSep("0.0", '.'));
        assertEquals("0", StringUtils.stripZerosAfterSep("0.000", '.'));
        
        assertEquals("63", StringUtils.stripZerosAfterSep("63", '.'));
        assertEquals("63.1", StringUtils.stripZerosAfterSep("63.1", '.'));
        assertEquals("63.1", StringUtils.stripZerosAfterSep("63.10", '.'));
        assertEquals("63.1", StringUtils.stripZerosAfterSep("63.100", '.'));
        assertEquals("63.1002", StringUtils.stripZerosAfterSep("63.1002", '.'));
        assertEquals("63.1002", StringUtils.stripZerosAfterSep("63.10020", '.'));
        assertEquals("63.001", StringUtils.stripZerosAfterSep("63.001", '.'));
        assertEquals("63.001", StringUtils.stripZerosAfterSep("63.0010", '.'));
        assertEquals("63.001", StringUtils.stripZerosAfterSep("63.00100", '.'));
        assertEquals("63", StringUtils.stripZerosAfterSep("63.0", '.'));
        assertEquals("63", StringUtils.stripZerosAfterSep("63.000", '.'));
        
        assertEquals("63", StringUtils.stripZerosAfterSep("63", ','));
        assertEquals("63,1", StringUtils.stripZerosAfterSep("63,1", ','));
        assertEquals("63,1", StringUtils.stripZerosAfterSep("63,10", ','));
        assertEquals("63,1", StringUtils.stripZerosAfterSep("63,100", ','));
        assertEquals("63,1002", StringUtils.stripZerosAfterSep("63,1002", ','));
        assertEquals("63,1002", StringUtils.stripZerosAfterSep("63,10020", ','));
        assertEquals("63,001", StringUtils.stripZerosAfterSep("63,001", ','));
        assertEquals("63,001", StringUtils.stripZerosAfterSep("63,0010", ','));
        assertEquals("63,001", StringUtils.stripZerosAfterSep("63,00100", ','));
        assertEquals("63", StringUtils.stripZerosAfterSep("63,0", ','));
        assertEquals("63", StringUtils.stripZerosAfterSep("63,000", ','));
    }
    
}
