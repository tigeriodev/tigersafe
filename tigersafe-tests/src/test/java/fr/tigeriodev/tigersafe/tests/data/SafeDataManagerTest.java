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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.data.ExistingPasswordEntry;
import fr.tigeriodev.tigersafe.data.NewPasswordEntry;
import fr.tigeriodev.tigersafe.data.PasswordEntry;
import fr.tigeriodev.tigersafe.data.SafeData;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.data.SafeSerializationManager;
import fr.tigeriodev.tigersafe.data.PasswordEntry.Data;
import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.tests.TestsGlobalConfig;
import fr.tigeriodev.tigersafe.tests.utils.TestsUtils;

public class SafeDataManagerTest extends TestClass {
    
    @Test
    void test1() throws Exception {
        File safeFile = resetConfigAndSafeFile();
        SafeDataManager dm = new SafeDataManager(safeFile, "safePassword".toCharArray());
        assertEquals(0, dm.getPwEntries().length);
        assertFalse(dm.hasChanges());
        
        assertFalse(safeFile.isFile());
        dm.updateSafeFile();
        assertTrue(safeFile.isFile());
        assertEquals(0, dm.getPwEntries().length);
        
        dm.loadSafeFile();
        assertEquals(0, dm.getPwEntries().length);
        assertFalse(dm.hasChanges());
        
        NewPasswordEntry newPwEntryA = dm.addNewPwEntry();
        newPwEntryA.setName("name1", dm);
        newPwEntryA.setPassword("pw1".toCharArray());
        newPwEntryA.setSite("site1");
        newPwEntryA.setInfo("info1");
        newPwEntryA.setTOTP(TestsTOTP.newCommonTOTP1());
        
        assertArrayEquals(new PasswordEntry[] {
                newPwEntryA
        }, dm.getPwEntries());
        assertTrue(dm.hasChanges());
        
        dm.updateSafeFile();
        dm.loadSafeFile();
        assertFalse(dm.hasChanges());
        
        assertTrue(newPwEntryA.isDestroyed());
        
        PasswordEntry[] pwEntriesA = dm.getPwEntries();
        assertEquals(1, pwEntriesA.length);
        ExistingPasswordEntry pwEntry1A = (ExistingPasswordEntry) pwEntriesA[0];
        assertEquals("name1", pwEntry1A.getCurrentName());
        assertArrayEquals("pw1".toCharArray(), pwEntry1A.getCurrentPassword());
        assertEquals("site1", pwEntry1A.getCurrentSite());
        assertEquals("info1", pwEntry1A.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP1(), pwEntry1A.getCurrentTOTP());
        
        pwEntry1A.setName("newName1", dm);
        assertTrue(dm.hasChanges());
        pwEntry1A.setPassword("newPw1".toCharArray());
        pwEntry1A.setSite("newSite1");
        pwEntry1A.setInfo("newInfo1");
        pwEntry1A.setTOTP(TestsTOTP.newCommonTOTP2());
        assertTrue(dm.hasChanges());
        
        dm.updateSafeFile();
        dm.loadSafeFile();
        assertFalse(dm.hasChanges());
        
        assertTrue(pwEntry1A.isDestroyed());
        
        PasswordEntry[] pwEntriesB = dm.getPwEntries();
        assertEquals(1, pwEntriesB.length);
        ExistingPasswordEntry pwEntry1B = (ExistingPasswordEntry) pwEntriesB[0];
        assertEquals("newName1", pwEntry1B.getCurrentName());
        assertArrayEquals("newPw1".toCharArray(), pwEntry1B.getCurrentPassword());
        assertEquals("newSite1", pwEntry1B.getCurrentSite());
        assertEquals("newInfo1", pwEntry1B.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP2(), pwEntry1B.getCurrentTOTP());
        
        NewPasswordEntry newPwEntry2B = dm.addNewPwEntry();
        assertFalse(dm.hasChanges());
        newPwEntry2B.setName("name2", dm);
        assertFalse(dm.hasChanges());
        newPwEntry2B.setPassword("pw2".toCharArray());
        assertTrue(dm.hasChanges());
        newPwEntry2B.setSite("site2");
        newPwEntry2B.setInfo("info2");
        newPwEntry2B.setTOTP(TestsTOTP.newCommonTOTP1());
        
        assertArrayEquals(new PasswordEntry[] {
                newPwEntry2B, pwEntry1B
        }, dm.getPwEntries());
        assertTrue(dm.hasChanges());
        
        dm.updateSafeFile();
        dm.loadSafeFile();
        assertFalse(dm.hasChanges());
        
        assertTrue(pwEntry1B.isDestroyed());
        assertTrue(newPwEntry2B.isDestroyed());
        
        PasswordEntry[] pwEntriesC = dm.getPwEntries();
        assertEquals(2, pwEntriesC.length);
        ExistingPasswordEntry pwEntry2C = (ExistingPasswordEntry) pwEntriesC[0];
        assertEquals("name2", pwEntry2C.getCurrentName());
        assertArrayEquals("pw2".toCharArray(), pwEntry2C.getCurrentPassword());
        assertEquals("site2", pwEntry2C.getCurrentSite());
        assertEquals("info2", pwEntry2C.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP1(), pwEntry2C.getCurrentTOTP());
        
        ExistingPasswordEntry pwEntry1C = (ExistingPasswordEntry) pwEntriesC[1];
        assertEquals("newName1", pwEntry1C.getCurrentName());
        assertArrayEquals("newPw1".toCharArray(), pwEntry1C.getCurrentPassword());
        assertEquals("newSite1", pwEntry1C.getCurrentSite());
        assertEquals("newInfo1", pwEntry1C.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP2(), pwEntry1C.getCurrentTOTP());
        
        assertEquals(0, dm.getDeletedPwEntries().length);
        assertTrue(dm.isActivePwEntry(pwEntry1C));
        assertFalse(dm.isDeletedPwEntry(pwEntry1C));
        dm.deletePwEntry(pwEntry1C);
        assertFalse(dm.isActivePwEntry(pwEntry1C));
        assertTrue(dm.isDeletedPwEntry(pwEntry1C));
        assertTrue(dm.hasChanges());
        
        assertArrayEquals(new PasswordEntry[] {
                pwEntry1C
        }, dm.getDeletedPwEntries());
        assertArrayEquals(new PasswordEntry[] {
                pwEntry2C
        }, dm.getPwEntries());
        
        dm.restorePwEntry(pwEntry1C);
        assertTrue(dm.isActivePwEntry(pwEntry1C));
        assertFalse(dm.isDeletedPwEntry(pwEntry1C));
        assertFalse(dm.hasChanges());
        assertEquals(0, dm.getDeletedPwEntries().length);
        
        dm.deletePwEntry(pwEntry1C);
        assertFalse(dm.isActivePwEntry(pwEntry1C));
        assertTrue(dm.isDeletedPwEntry(pwEntry1C));
        assertTrue(dm.hasChanges());
        
        assertArrayEquals(new PasswordEntry[] {
                pwEntry1C
        }, dm.getDeletedPwEntries());
        assertArrayEquals(new PasswordEntry[] {
                pwEntry2C
        }, dm.getPwEntries());
        
        dm.updateSafeFile();
        dm.loadSafeFile();
        assertFalse(dm.hasChanges());
        assertEquals(0, dm.getDeletedPwEntries().length);
        
        assertTrue(pwEntry1C.isDestroyed());
        assertTrue(pwEntry2C.isDestroyed());
        
        PasswordEntry[] pwEntriesD = dm.getPwEntries();
        assertEquals(1, pwEntriesD.length);
        ExistingPasswordEntry pwEntry2D = (ExistingPasswordEntry) pwEntriesD[0];
        assertEquals("name2", pwEntry2D.getCurrentName());
        assertArrayEquals("pw2".toCharArray(), pwEntry2D.getCurrentPassword());
        assertEquals("site2", pwEntry2D.getCurrentSite());
        assertEquals("info2", pwEntry2D.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP1(), pwEntry2D.getCurrentTOTP());
        
        Data pwEntry3Data = new PasswordEntry.Data(
                "name3",
                "pw3".toCharArray(),
                Instant.ofEpochSecond(3L),
                "site3",
                "info3",
                TestsTOTP.newCommonTOTP2()
        );
        dm.importData(new SafeData(new Data[] {
                pwEntry3Data
        }));
        assertFalse(dm.hasChanges());
        
        PasswordEntry[] pwEntriesE = dm.getPwEntries();
        assertEquals(2, pwEntriesE.length);
        ExistingPasswordEntry pwEntry2E = (ExistingPasswordEntry) pwEntriesE[0];
        assertEquals("name2", pwEntry2E.getCurrentName());
        assertArrayEquals("pw2".toCharArray(), pwEntry2E.getCurrentPassword());
        assertEquals("site2", pwEntry2E.getCurrentSite());
        assertEquals("info2", pwEntry2E.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP1(), pwEntry2E.getCurrentTOTP());
        
        ExistingPasswordEntry pwEntry3E = (ExistingPasswordEntry) pwEntriesE[1];
        assertEquals("name3", pwEntry3E.getCurrentName());
        assertArrayEquals("pw3".toCharArray(), pwEntry3E.getCurrentPassword());
        assertEquals("site3", pwEntry3E.getCurrentSite());
        assertEquals("info3", pwEntry3E.getCurrentInfo());
        assertEquals(TestsTOTP.newCommonTOTP2(), pwEntry3E.getCurrentTOTP());
        
        ExistingPasswordEntry[] deletedPwEntriesE = dm.getDeletedPwEntries();
        
        File serialFile = TestsUtils.newTestFile("serialized-" + safeFile.getName());
        char[] serialPw = "serialPw".toCharArray();
        Cipher serialCipher = GlobalConfig.ConfigCipher.USER_DATA.getCipher();
        dm.exportDataTo(
                serialFile,
                serialCipher,
                serialPw,
                SafeSerializationManager.MAX_SERIAL_VER
        );
        
        SafeData deserializedExportedData =
                SafeSerializationManager.read(serialFile, serialCipher, serialPw);
        
        SafeDataManager dm2 = new SafeDataManager(
                TestsUtils.newTestFile("safe4.dat"),
                "safePassword2".toCharArray()
        );
        dm2.importData(deserializedExportedData);
        
        TestsPasswordEntry.assertArrEquals(pwEntriesE, dm2.getPwEntries());
        TestsPasswordEntry.assertArrEquals(deletedPwEntriesE, dm2.getDeletedPwEntries());
        
        dm.changeSafePw("newSafePassword".toCharArray());
        dm.changeSafeCiphers("ChaCha20", "ChaCha20-Poly1305");
        
        SafeDataManager dm3 = new SafeDataManager(safeFile, "newSafePassword".toCharArray());
        dm3.loadSafeFile();
        
        TestsPasswordEntry.assertArrEquals(pwEntriesE, dm3.getPwEntries());
        TestsPasswordEntry.assertArrEquals(deletedPwEntriesE, dm3.getDeletedPwEntries());
    }
    
    public static File resetConfigAndSafeFile() throws IOException {
        TestsGlobalConfig.resetForTest();
        CiphersManager.waitAllWorkingChecks();
        return TestsUtils.newTestFile("safe3.dat");
    }
    
}
