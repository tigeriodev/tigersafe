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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.security.auth.DestroyFailedException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.data.PasswordEntry;
import fr.tigeriodev.tigersafe.data.PasswordEntry.Data;
import fr.tigeriodev.tigersafe.data.SafeData;
import fr.tigeriodev.tigersafe.data.SafeFileManager;
import fr.tigeriodev.tigersafe.data.TOTP;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.tests.TestsGlobalConfig;
import fr.tigeriodev.tigersafe.tests.utils.TestsStringUtils;
import fr.tigeriodev.tigersafe.tests.utils.TestsUtils;
import fr.tigeriodev.tigersafe.utils.NumberRange;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public class SafeFileManagerTest extends TestClass {
    
    @Nested
    class WriteReadPositiveInt {
        
        @Test
        void testCommon() throws NoSuchAlgorithmException, IOException {
            int min = 0;
            for (int num = min; num < min + 300; num++) {
                testWriteRead(num, min);
            }
        }
        
        @Test
        void testBig() throws NoSuchAlgorithmException, IOException {
            int min = 0;
            testWriteRead(10000, min);
            testWriteRead(100000, min);
            testWriteRead(1000000, min);
            testWriteRead(Integer.MAX_VALUE - 1, min);
            testWriteRead(Integer.MAX_VALUE, min);
        }
        
        @Test
        void testMin5() throws NoSuchAlgorithmException, IOException {
            int min = 5;
            for (int num = min; num < min + 300; num++) {
                testWriteRead(num, min);
            }
        }
        
        @Test
        void testNegative() {
            assertThrowsExactly(IllegalArgumentException.class, () -> testWriteRead(-1, 0));
            assertThrowsExactly(IllegalArgumentException.class, () -> testWriteRead(-5, 0));
            assertThrowsExactly(IllegalArgumentException.class, () -> testWriteRead(0, -1));
            assertThrowsExactly(IllegalArgumentException.class, () -> testWriteRead(0, -5));
        }
        
        void testWriteRead(int num, int min) throws NoSuchAlgorithmException, IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            SafeFileManager.writePositiveInt(num, min, dataOut);
            dataOut.flush();
            out.flush();
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            DataInputStream dataIn = new DataInputStream(in);
            int read = SafeFileManager.readPositiveInt(dataIn, min);
            testLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(
                            () -> "num = " + num + ", min = " + min + ", written = "
                                    + StringUtils.bytesToStr(out.toByteArray()) + ", read = " + read
                    );
            assertEquals(num, read);
        }
        
        @Test
        void testRandom() throws NoSuchAlgorithmException, IOException {
            TestsUtils.testRandomFailing(AssertionFailedError.class, 3, () -> {
                testRandom(0, 0);
                testRandom(1, 0);
                testRandom(5, 0);
                testRandom(25, 0);
                // For a given big number, there are not a lot of possibilities
            });
        }
        
        void testRandom(int num, int min) throws NoSuchAlgorithmException, IOException {
            List<byte[]> writtenVals = new ArrayList<>();
            Logger methLog = testLog.newChildFromCurMethIf(Level.DEBUG);
            for (int i = 0; i < 50; i++) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(out);
                SafeFileManager.writePositiveInt(num, min, dataOut);
                dataOut.flush();
                out.flush();
                byte[] newWritten = out.toByteArray();
                methLog.debug(
                        () -> "num = " + num + ", min = " + min + ", newWritten = "
                                + StringUtils.bytesToStr(newWritten)
                );
                for (byte[] prevWritten : writtenVals) {
                    if (Arrays.equals(prevWritten, newWritten)) {
                        fail(
                                "newWritten " + StringUtils.bytesToStr(newWritten)
                                        + " already written previously"
                        );
                    }
                }
                writtenVals.add(newWritten);
            }
        }
        
    }
    
    @Nested
    class WriteReadSmallNumber {
        
        @Test
        void testCommon() {
            testAllRanges((range) -> {
                for (int num = range.min; num <= range.getMax(); num++) {
                    testWriteRead(num, range);
                }
            });
        }
        
        @Test
        void testOutsideRange() {
            testAllRanges((range) -> {
                assertThrowsExactly(
                        IllegalArgumentException.class,
                        () -> testWriteRead(range.min - 1, range)
                );
                assertThrowsExactly(
                        IllegalArgumentException.class,
                        () -> testWriteRead(range.getMax() + 1, range)
                );
            });
        }
        
        @Test
        void testTooBigRange() {
            NumberRange range = new NumberRange(0, Byte.SIZE + 1);
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> testWriteRead(range.min, range)
            );
        }
        
        void testAllRanges(Consumer<NumberRange> test) {
            for (NumberRange range : new NumberRange[] {
                    SafeFileManager.INT_SIZE_RANGE,
                    SafeFileManager.SHORT_SIZE_RANGE,
                    SafeFileManager.IN_BLOCK_NOISE_LEN_RANGE,
                    SafeFileManager.OUT_BLOCK_NOISE_LEN_RANGE,
                    TOTP.ALGO_ORD_RANGE,
                    TOTP.DIGITS_NUM_RANGE,
                    TOTP.PERIOD_SECONDS_RANGE
            }) {
                testLog.debug(() -> "Start " + range);
                test.accept(range);
            }
        }
        
        void testWriteRead(int num, NumberRange range) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(out);
                SafeFileManager.writeSmallNumber(num, range, dataOut);
                dataOut.flush();
                out.flush();
                ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
                DataInputStream dataIn = new DataInputStream(in);
                int read = SafeFileManager.readSmallNumber(dataIn, range);
                testLog.newChildFromCurMethIf(Level.DEBUG)
                        .debug(
                                () -> "num = " + num + ", range = " + range + ", written = "
                                        + StringUtils.bytesToStr(out.toByteArray()) + ", read = "
                                        + read
                        );
                assertEquals(num, read);
            } catch (NoSuchAlgorithmException | IOException ex) {
                fail(ex);
            }
        }
        
        @Test
        void testRandom() throws NoSuchAlgorithmException, IOException {
            NumberRange range = new NumberRange(0, 1);
            TestsUtils.testRandomFailing(AssertionFailedError.class, 3, () -> {
                testRandom(range.min, range);
                testRandom(range.min + 1, range);
                testRandom(range.getMax(), range);
            });
        }
        
        void testRandom(int num, NumberRange range) throws NoSuchAlgorithmException, IOException {
            List<byte[]> writtenVals = new ArrayList<>();
            Logger methLog = testLog.newChildFromCurMethIf(Level.DEBUG);
            for (int i = 0; i < 2; i++) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(out);
                SafeFileManager.writeSmallNumber(num, range, dataOut);
                dataOut.flush();
                out.flush();
                byte[] newWritten = out.toByteArray();
                methLog.debug(
                        () -> "num = " + num + ", range = " + range + ", newWritten = "
                                + StringUtils.bytesToStr(newWritten)
                );
                for (byte[] prevWritten : writtenVals) {
                    if (Arrays.equals(prevWritten, newWritten)) {
                        fail(
                                "newWritten " + StringUtils.bytesToStr(newWritten)
                                        + " already written previously"
                        );
                    }
                }
                writtenVals.add(newWritten);
            }
        }
        
    }
    
    @Nested
    class WriteReadUnsignedShort {
        
        @Test
        void testCommon() throws NoSuchAlgorithmException, IOException {
            for (int num = 0; num < 300; num++) {
                testWriteRead(num);
            }
        }
        
        @Test
        void testBig() throws NoSuchAlgorithmException, IOException {
            testWriteRead(Short.MAX_VALUE - 2);
            testWriteRead(Short.MAX_VALUE - 1);
            testWriteRead(Short.MAX_VALUE);
            testWriteRead(Short.MAX_VALUE + 1);
            testWriteRead(Short.MAX_VALUE + 2);
            testWriteRead(Short.MAX_VALUE - Short.MIN_VALUE);
        }
        
        @Test
        void testNegative() {
            assertThrowsExactly(IllegalArgumentException.class, () -> testWriteRead(-1));
            assertThrowsExactly(IllegalArgumentException.class, () -> testWriteRead(-5));
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> testWriteRead(Short.MIN_VALUE)
            );
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> testWriteRead(-(Short.MAX_VALUE - Short.MIN_VALUE))
            );
        }
        
        @Test
        void testOverflow() {
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> testWriteRead(Short.MAX_VALUE - Short.MIN_VALUE + 1)
            );
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> testWriteRead(Short.MAX_VALUE - Short.MIN_VALUE + 2)
            );
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> testWriteRead(Short.MAX_VALUE - Short.MIN_VALUE + 50)
            );
        }
        
        void testWriteRead(int num) throws NoSuchAlgorithmException, IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            SafeFileManager.writeUnsignedShort(num, dataOut);
            dataOut.flush();
            out.flush();
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            DataInputStream dataIn = new DataInputStream(in);
            int read = SafeFileManager.readUnsignedShort(dataIn);
            testLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(
                            () -> "num = " + num + ", written = "
                                    + StringUtils.bytesToStr(out.toByteArray()) + ", read = " + read
                    );
            assertEquals(num, read);
        }
        
        @Test
        void testRandom() throws NoSuchAlgorithmException, IOException {
            TestsUtils.testRandomFailing(AssertionFailedError.class, 3, () -> {
                testRandom(0);
                testRandom(1);
                testRandom(5);
                testRandom(25);
                testRandom(Short.MAX_VALUE);
                // For a given big number, there are not a lot of possibilities
            });
        }
        
        void testRandom(int num) throws NoSuchAlgorithmException, IOException {
            List<byte[]> writtenVals = new ArrayList<>();
            Logger methLog = testLog.newChildFromCurMethIf(Level.DEBUG);
            for (int i = 0; i < 5; i++) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(out);
                SafeFileManager.writeUnsignedShort(num, dataOut);
                dataOut.flush();
                out.flush();
                byte[] newWritten = out.toByteArray();
                methLog.debug(
                        () -> "num = " + num + ", newWritten = "
                                + StringUtils.bytesToStr(newWritten)
                );
                for (byte[] prevWritten : writtenVals) {
                    if (Arrays.equals(prevWritten, newWritten)) {
                        fail(
                                "newWritten " + StringUtils.bytesToStr(newWritten)
                                        + " already written previously"
                        );
                    }
                }
                writtenVals.add(newWritten);
            }
        }
        
    }
    
    @Nested
    class WriteReadStr {
        
        @Test
        void test1() throws NoSuchAlgorithmException, IOException {
            testWriteRead(new String("abc"));
        }
        
        void testWriteRead(String str) throws NoSuchAlgorithmException, IOException {
            SafeFileManager.clearBuffers(); // ensure clean state
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            SafeFileManager.writeStr(str, dataOut);
            dataOut.flush();
            out.flush();
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            DataInputStream dataIn = new DataInputStream(in);
            String read = SafeFileManager.readStr(dataIn);
            testLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(
                            () -> "str = " + str + ", \nwritten = "
                                    + StringUtils.bytesToStr(out.toByteArray()) + ", \nread = "
                                    + read
                    );
            assertEquals(str, read);
            
            assertTrue(SafeFileManager.checkValidLen(str) == str);
        }
        
    }
    
    @Nested
    class WriteReadChars {
        
        @Test
        void testCommon() throws NoSuchAlgorithmException, IOException {
            testWriteRead("a".toCharArray());
            testWriteRead("ab".toCharArray());
            testWriteRead("abc".toCharArray());
            testWriteRead("abcdefghijklmnopqrstuvwxyz".toCharArray());
            testWriteRead(COMMON_CHARS.toCharArray());
            testWriteRead("first second third !".toCharArray());
        }
        
        @Test
        void testEmpty() throws NoSuchAlgorithmException, IOException {
            char[] chars = "".toCharArray();
            testWriteRead(chars);
            assertTrue(SafeFileManager.checkValidLen(chars) == chars);
        }
        
        @Test
        void testMaxLen() throws NoSuchAlgorithmException, IOException {
            char[] maxChars = new char[65535];
            Arrays.fill(maxChars, 'a');
            testWriteRead(maxChars);
            assertTrue(SafeFileManager.checkValidLen(maxChars) == maxChars);
        }
        
        @Test
        void testTooLong() {
            char[] maxChars = new char[65536];
            Arrays.fill(maxChars, 'a');
            assertThrowsExactly(UTFDataFormatException.class, () -> testWriteRead(maxChars));
            assertThrowsExactly(
                    IllegalArgumentException.class,
                    () -> SafeFileManager.checkValidLen(maxChars)
            );
        }
        
        void testWriteRead(char[] chars) throws NoSuchAlgorithmException, IOException {
            SafeFileManager.clearBuffers(); // ensure clean state
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(out);
            SafeFileManager.writeChars(chars, dataOut);
            dataOut.flush();
            out.flush();
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            DataInputStream dataIn = new DataInputStream(in);
            char[] read = SafeFileManager.readChars(dataIn);
            testLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(
                            () -> "chars = " + Arrays.toString(chars) + ", \nwritten = "
                                    + StringUtils.bytesToStr(out.toByteArray()) + ", \nread = "
                                    + Arrays.toString(read)
                    );
            assertArrayEquals(chars, read);
            
            assertTrue(SafeFileManager.checkValidLen(chars) == chars);
        }
        
    }
    
    @Nested
    class WriteReadFile {
        
        @Test
        void testSimple() throws FileNotFoundException, IOException, GeneralSecurityException,
                DestroyFailedException {
            testWriteRead(TestsPasswordEntry.Data.newSimpleArr(false));
        }
        
        @Test
        void testSimpleWithTOTP() throws FileNotFoundException, IOException,
                GeneralSecurityException, DestroyFailedException {
            testWriteRead(TestsPasswordEntry.Data.newSimpleArr(true));
        }
        
        @Test
        void testCommonCharsWithTOTP() throws FileNotFoundException, IOException,
                GeneralSecurityException, DestroyFailedException {
            PasswordEntry.Data[] pwEntriesData = new PasswordEntry.Data[] {
                    TestsPasswordEntry.Data.newCommonChars(true),
                    TestsPasswordEntry.Data.newCommonChars(false),
            };
            testWriteRead(pwEntriesData);
        }
        
        @Test
        void testBig() throws FileNotFoundException, IOException, GeneralSecurityException,
                DestroyFailedException {
            PasswordEntry.Data[] pwEntriesData = new PasswordEntry.Data[1000];
            for (int i = 0; i < pwEntriesData.length; i++) {
                pwEntriesData[i] = TestsPasswordEntry.Data.newCommonChars(true);
            }
            testWriteRead(pwEntriesData);
        }
        
        void testWriteRead(Data[] pwEntriesData)
                throws IOException, GeneralSecurityException, DestroyFailedException {
            File safeFile = resetConfigAndSafeFile();
            char[] safePw = TestsStringUtils.newRandomPw();
            char[] wrongPw = TestsStringUtils.newWrongPw(safePw);
            
            SafeFileManager.write(safeFile, safePw, new SafeData(pwEntriesData));
            SafeData readSafeData = SafeFileManager.read(safeFile, safePw);
            Data[] readPwEntriesData = readSafeData.getPwEntriesData();
            testLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(
                            () -> "safePw = " + Arrays.toString(safePw) + ", written = "
                                    + Arrays.toString(pwEntriesData) + ", \nread = "
                                    + Arrays.toString(readPwEntriesData)
                    );
            assertArrayEquals(
                    pwEntriesData,
                    readPwEntriesData,
                    () -> "safePw = " + Arrays.toString(safePw)
            );
            
            assertThrows(
                    Exception.class,
                    () -> SafeFileManager.read(safeFile, wrongPw),
                    () -> "safePw = " + Arrays.toString(safePw) + ", wrongPw = "
                            + Arrays.toString(wrongPw)
            );
        }
        
        @Test
        void testRandom() throws IOException, GeneralSecurityException, DestroyFailedException {
            char[] safePw = TestsStringUtils.newRandomPw();
            Data[] pwEntriesData = TestsPasswordEntry.Data.newSimpleArr(false);
            File safeFile1 = resetConfigAndSafeFile();
            File safeFile2 = TestsUtils.newTestFile("safe2.dat");
            
            SafeData safeData = new SafeData(pwEntriesData);
            SafeFileManager.write(safeFile1, safePw, safeData);
            SafeFileManager.write(safeFile2, safePw, safeData);
            
            int bufSize = 1024;
            byte[] buf1 = new byte[bufSize];
            byte[] buf2 = new byte[bufSize];
            try (
                    RandomAccessFile raf1 = new RandomAccessFile(safeFile1, "r");
                    RandomAccessFile raf2 = new RandomAccessFile(safeFile2, "r");
            ) {
                assertNotEquals(
                        raf1.length(),
                        raf2.length(),
                        () -> "safePw = " + Arrays.toString(safePw)
                );
                
                boolean reachedEOF = false;
                while (!reachedEOF) {
                    try {
                        raf1.readFully(buf1);
                    } catch (EOFException ex) {
                        reachedEOF = true;
                    }
                    try {
                        raf2.readFully(buf2);
                    } catch (EOFException ex) {
                        reachedEOF = true;
                    }
                    
                    assertNotEquals(
                            getSum(buf1),
                            getSum(buf2),
                            () -> "Similar buffers, buf1 = " + StringUtils.bytesToHexStr(buf1)
                                    + ", \n buf2 = " + StringUtils.bytesToHexStr(buf2)
                                    + ", safePw = " + Arrays.toString(safePw)
                    );
                }
            }
        }
        
        public static File resetConfigAndSafeFile() throws IOException {
            TestsGlobalConfig.resetForTest();
            CiphersManager.waitAllWorkingChecks();
            return TestsUtils.newTestFile("safe1.dat");
        }
        
        private static int getSum(byte[] buf) {
            int res = 0;
            for (byte b : buf) {
                res += b;
            }
            return res;
        }
        
    }
    
    @Test
    void testGetEndNoiseLen() {
        char[] safePw = new char[2];
        Map<Integer, Integer> occurrences = new HashMap<>();
        int commonCharsNum = COMMON_CHARS.length();
        for (int i = 0; i < commonCharsNum; i++) {
            safePw[0] = COMMON_CHARS.charAt(i);
            for (int j = 0; j < commonCharsNum; j++) {
                safePw[1] = COMMON_CHARS.charAt(j);
                int endNoiseLen = SafeFileManager.getEndNoiseLen(safePw);
                occurrences.put(endNoiseLen, occurrences.getOrDefault(endNoiseLen, 0) + 1);
            }
        }
        
        int diffNum = occurrences.keySet().size();
        
        assertEquals(1024, diffNum);
        
        int optimalOccurrences = (commonCharsNum * commonCharsNum) / diffNum;
        int maxOccurrences = optimalOccurrences + 2;
        int minOccurrences = optimalOccurrences - 2;
        assertFalse(
                occurrences.values()
                        .stream()
                        .anyMatch((val) -> val < minOccurrences || val > maxOccurrences),
                () -> {
                    return occurrences.entrySet()
                            .stream()
                            .sorted((ent1, ent2) -> ent2.getValue() - ent1.getValue())
                            .map((ent) -> ent.getKey() + ": " + ent.getValue())
                            .collect(Collectors.joining(", \n"));
                }
        );
    }
    
}
