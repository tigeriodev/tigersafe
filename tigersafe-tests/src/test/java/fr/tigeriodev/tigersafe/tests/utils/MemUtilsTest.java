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
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.MemUtils;

public class MemUtilsTest extends TestClass {
    
    @Nested
    class TryClearPrimitives {
        
        static class TestObj {
            
            boolean bool = true;
            long l = 1;
            int i = 1;
            short s = 1;
            byte b = 1;
            double d = 1;
            float f = 1;
            char c = '1';
            
            boolean[] boolarr = new boolean[] {
                    true, true, true
            };
            long[] larr = new long[] {
                    1, 2, 3
            };
            int[] iarr = new int[] {
                    1, 2, 3
            };
            short[] sarr = new short[] {
                    1, 2, 3
            };
            byte[] barr = new byte[] {
                    1, 2, 3
            };
            double[] darr = new double[] {
                    1, 2, 3
            };
            float[] farr = new float[] {
                    1, 2, 3
            };
            char[] carr = new char[] {
                    '1', '2', '3'
            };
            
            TestObj innerObj = null;
            TestObj[] innerObjArr = null;
            
        }
        
        @Test
        void testNotDeeply1() {
            final TestObj testObj = new TestObj();
            
            MemUtils.tryClearPrimitives(testObj, false);
            
            assertCleared(testObj);
        }
        
        @Test
        void testNotDeeply2() {
            final TestObj testObj = new TestObj();
            final TestObj testInnerObj = new TestObj();
            final TestObj[] testInnerObjArr = new TestObj[] {
                    new TestObj(), new TestObj()
            };
            testObj.innerObj = testInnerObj;
            testObj.innerObjArr = testInnerObjArr;
            
            MemUtils.tryClearPrimitives(testObj, false);
            
            assertCleared(testObj);
        }
        
        @Test
        void testDeeply1() {
            final TestObj testObj = new TestObj();
            final TestObj testInnerObj = new TestObj();
            testObj.innerObj = testInnerObj;
            
            MemUtils.tryClearPrimitives(testObj, true);
            
            assertCleared(testObj);
            assertCleared(testInnerObj);
        }
        
        @Test
        void testDeeply2() {
            final TestObj testObj = new TestObj();
            final TestObj testInnerObj = new TestObj();
            final TestObj[] testInnerObjArr = new TestObj[] {
                    new TestObj(), new TestObj()
            };
            testObj.innerObj = testInnerObj;
            testObj.innerObjArr = testInnerObjArr;
            
            MemUtils.tryClearPrimitives(testObj, true);
            
            assertCleared(testObj);
            assertCleared(testInnerObj);
            assertCleared(testInnerObjArr[0]);
            assertCleared(testInnerObjArr[1]);
        }
        
        void assertCleared(TestObj obj) {
            assertEquals(false, obj.bool);
            assertEquals(0L, obj.l);
            assertEquals(0, obj.i);
            assertEquals((short) 0, obj.s);
            assertEquals((byte) 0, obj.b);
            assertEquals(0d, obj.d);
            assertEquals(0f, obj.f);
            assertEquals((char) 0, obj.c);
            
            assertArrayEquals(new boolean[] {
                    false, false, false
            }, obj.boolarr);
            assertArrayEquals(new long[] {
                    0, 0, 0
            }, obj.larr);
            assertArrayEquals(new int[] {
                    0, 0, 0
            }, obj.iarr);
            assertArrayEquals(new short[] {
                    0, 0, 0
            }, obj.sarr);
            assertArrayEquals(new byte[] {
                    0, 0, 0
            }, obj.barr);
            assertArrayEquals(new double[] {
                    0, 0, 0
            }, obj.darr);
            assertArrayEquals(new float[] {
                    0, 0, 0
            }, obj.farr);
            assertArrayEquals(new char[] {
                    0, 0, 0
            }, obj.carr);
        }
        
    }
    
}
