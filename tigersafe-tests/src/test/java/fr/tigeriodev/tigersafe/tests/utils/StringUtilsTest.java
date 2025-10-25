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
    
}
