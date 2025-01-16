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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.security.auth.DestroyFailedException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.data.PasswordEntry;
import fr.tigeriodev.tigersafe.data.SafeData;
import fr.tigeriodev.tigersafe.data.SafeSerializationManager;
import fr.tigeriodev.tigersafe.data.PasswordEntry.Data;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.tests.ciphers.TestsCiphersManager;
import fr.tigeriodev.tigersafe.tests.utils.TestsStringUtils;
import fr.tigeriodev.tigersafe.tests.utils.TestsUtils;

public class SafeSerializationManagerTest extends TestClass {
    
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
            Cipher cipher = TestsCiphersManager.getWorkingAuthCipher();
            char[] serialPw = TestsStringUtils.newRandomPw();
            char[] wrongPw = TestsStringUtils.newWrongPw(serialPw);
            
            for (
                    short serialVer = 1;
                    serialVer <= SafeSerializationManager.MAX_SERIAL_VER;
                    serialVer++
            ) {
                File serialFile = TestsUtils.newTestFile("serial1.dat");
                SafeSerializationManager.write(
                        serialFile,
                        cipher,
                        serialPw,
                        serialVer,
                        new SafeData(pwEntriesData)
                );
                Data[] readPwEntriesData =
                        SafeSerializationManager.read(serialFile, cipher, serialPw)
                                .getPwEntriesData();
                final short fserialVer = serialVer;
                testLog.newChildFromCurMethIf(Level.DEBUG)
                        .debug(
                                () -> "serialVer = " + fserialVer + ", serialPw = "
                                        + Arrays.toString(serialPw) + ", written = "
                                        + Arrays.toString(pwEntriesData) + ", \nread = "
                                        + Arrays.toString(readPwEntriesData)
                        );
                assertArrayEquals(
                        pwEntriesData,
                        readPwEntriesData,
                        () -> "serialVer = " + fserialVer + ", serialPw = "
                                + Arrays.toString(serialPw)
                );
                
                assertThrows(
                        Exception.class,
                        () -> SafeSerializationManager.read(serialFile, cipher, wrongPw),
                        () -> "serialPw = " + Arrays.toString(serialPw) + ", wrongPw = "
                                + Arrays.toString(wrongPw)
                );
            }
        }
        
    }
    
}
