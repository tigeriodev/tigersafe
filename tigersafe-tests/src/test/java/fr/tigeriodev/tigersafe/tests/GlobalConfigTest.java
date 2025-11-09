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

package fr.tigeriodev.tigersafe.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.GlobalConfig.ConfigCipher;
import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.tests.utils.TestsStringUtils;
import fr.tigeriodev.tigersafe.tests.utils.TestsUtils;
import fr.tigeriodev.tigersafe.ui.UIConfig.KeyboardShortcut;

public class GlobalConfigTest extends TestClass {
    
    private File confFile;
    
    @Test
    void testSetInstance() throws IOException {
        resetConfigFile();
        GlobalConfig initInst = GlobalConfig.getInstance();
        
        GlobalConfig newInst1 = readConfigFile();
        assertTrue(newInst1 != initInst);
        
        GlobalConfig.setInstance(newInst1, false, false);
        assertTrue(GlobalConfig.getInstance() == newInst1);
        
        GlobalConfig newInst2 = new GlobalConfig(null);
        assertTrue(newInst2 != newInst1);
        
        assertThrows(
                IllegalStateException.class,
                () -> GlobalConfig.setInstance(newInst2, true, false)
        );
        assertTrue(GlobalConfig.getInstance() == newInst1);
        
        GlobalConfig.setInstance(newInst2, false, false);
        assertTrue(GlobalConfig.getInstance() == newInst2);
    }
    
    @Test
    void testGetDefaultProperties() throws IOException {
        Properties defProps = GlobalConfig.getDefaultProperties();
        
        Set<String> expectedPropsKey = new HashSet<>(
                Set.of(
                        GlobalConfig.LANGUAGE_KEY,
                        GlobalConfig.CUSTOM_LANGUAGE_FILE_KEY,
                        GlobalConfig.CUSTOM_STYLESHEET_KEY,
                        GlobalConfig.LAST_SAFE_FILE_KEY,
                        GlobalConfig.PW_GENERATION_CUSTOM_CHARS_KEY,
                        GlobalConfig.PW_GENERATION_MAX_LEN_KEY,
                        GlobalConfig.PW_GENERATION_MIN_LEN_KEY
                )
        );
        for (ConfigCipher confCipher : ConfigCipher.values()) {
            expectedPropsKey.add(confCipher.getConfigKey());
        }
        for (KeyboardShortcut keyboardShortcut : KeyboardShortcut.values()) {
            expectedPropsKey.add(keyboardShortcut.getConfigKey());
        }
        
        assertEquals(expectedPropsKey, defProps.keySet());
    }
    
    @Test
    void testSetWriteReadLanguage() throws IOException {
        resetConfigFile();
        
        Locale initLang = Locale.ENGLISH;
        Locale newLang = Locale.FRENCH;
        assertNotEquals(initLang, newLang);
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initLang, initConf.getLanguage());
        initConf.setLanguage(newLang);
        assertEquals(newLang, initConf.getLanguage());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newLang, newConf.getLanguage());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setLanguage(initLang);
        assertEquals(initLang, newConf.getLanguage());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newLang, refreshedConf.getLanguage());
        assertSamePropsValue(initConf, refreshedConf);
    }
    
    @Test
    void testSetWriteReadCustomLanguageFile() throws IOException {
        resetConfigFile();
        
        final File initFile = null;
        final File newFile =
                TestsUtils.newTestFile("newCustomLangFile.properties").getAbsoluteFile();
        newFile.createNewFile();
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initFile, initConf.getCustomLanguageFile());
        initConf.setCustomLanguageFile(newFile);
        assertEquals(newFile, initConf.getCustomLanguageFile());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newFile, newConf.getCustomLanguageFile());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setCustomLanguageFile(initFile);
        assertEquals(initFile, newConf.getCustomLanguageFile());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newFile, refreshedConf.getCustomLanguageFile());
        assertSamePropsValue(initConf, refreshedConf);
        
        assertTrue(newFile.delete());
    }
    
    @Test
    void testSetWriteReadCustomStylesheetFile() throws IOException {
        resetConfigFile();
        
        final File initFile = null;
        final String initURL = null;
        final File newFile = TestsUtils.newTestFile("newCustomStylesheet.css").getAbsoluteFile();
        final String newURL = newFile.toURI().toURL().toExternalForm();
        newFile.createNewFile();
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initFile, initConf.getCustomStylesheetFile());
        assertEquals(initURL, initConf.getCustomStylesheetURL());
        initConf.setCustomStylesheet(newFile);
        assertEquals(newFile, initConf.getCustomStylesheetFile());
        assertEquals(newURL, initConf.getCustomStylesheetURL());
        
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newFile, newConf.getCustomStylesheetFile());
        assertEquals(newURL, newConf.getCustomStylesheetURL());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setCustomStylesheet(initFile);
        assertEquals(initFile, newConf.getCustomStylesheetFile());
        assertEquals(initURL, newConf.getCustomStylesheetURL());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newFile, refreshedConf.getCustomStylesheetFile());
        assertEquals(newURL, refreshedConf.getCustomStylesheetURL());
        assertSamePropsValue(initConf, refreshedConf);
        
        assertTrue(newFile.delete());
    }
    
    @Test
    void testSetWriteReadLastSafeFile() throws IOException {
        resetConfigFile();
        
        final File initFile = null;
        final File newFile = new File("newLastSafeFile.dat").getAbsoluteFile();
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initFile, initConf.getLastSafeFile());
        initConf.setLastSafeFile(newFile);
        assertEquals(newFile, initConf.getLastSafeFile());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newFile, newConf.getLastSafeFile());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setLastSafeFile(initFile);
        assertEquals(initFile, newConf.getLastSafeFile());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newFile, refreshedConf.getLastSafeFile());
        assertSamePropsValue(initConf, refreshedConf);
    }
    
    @Test
    void testSetWriteReadCipher() throws IOException {
        resetConfigFile();
        
        ConfigCipher modifiedConfCipher = ConfigCipher.INTERNAL_DATA;
        String initCipherName = "AES_CTR";
        Cipher initCipher = CiphersManager.getCipherByName(initCipherName);
        String newCipherName = "ChaCha20";
        Cipher newCipher = CiphersManager.getCipherByName(newCipherName);
        assertNotEquals(initCipher, newCipher);
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initCipher, initConf.getCipher(modifiedConfCipher));
        initConf.setCipher(modifiedConfCipher, newCipherName);
        assertEquals(newCipher, initConf.getCipher(modifiedConfCipher));
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newCipher, newConf.getCipher(modifiedConfCipher));
        assertSamePropsValue(initConf, newConf);
        
        newConf.setCipher(modifiedConfCipher, initCipherName);
        assertEquals(initCipher, newConf.getCipher(modifiedConfCipher));
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newCipher, refreshedConf.getCipher(modifiedConfCipher));
        assertSamePropsValue(initConf, refreshedConf);
    }
    
    @Test
    void testSetWriteReadPwGenerationCustomChars() throws IOException {
        resetConfigFile();
        
        Set<String> initCustomChars =
                Set.of("!#$%&()*+,-./:;<=>?@[]^_{|}~", "!#$%&*+-?@_", "!#*?@_");
        Set<String> newCustomChars = Set.of("<=>?@[]^_{|}~", "!#*?@_");
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initCustomChars, initConf.getPwGenerationCustomChars());
        initConf.setPwGenerationCustomChars(newCustomChars);
        assertEquals(newCustomChars, initConf.getPwGenerationCustomChars());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newCustomChars, newConf.getPwGenerationCustomChars());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setPwGenerationCustomChars(initCustomChars);
        assertEquals(initCustomChars, newConf.getPwGenerationCustomChars());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newCustomChars, refreshedConf.getPwGenerationCustomChars());
        assertSamePropsValue(initConf, refreshedConf);
    }
    
    @Test
    void testSetWriteReadPwGenerationMinLen() throws IOException {
        resetConfigFile();
        
        final int initMinLen = 20;
        final int newMinLen = 30;
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initMinLen, initConf.getPwGenerationMinLen());
        initConf.setPwGenerationMinLen(newMinLen);
        assertEquals(newMinLen, initConf.getPwGenerationMinLen());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newMinLen, newConf.getPwGenerationMinLen());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setPwGenerationMinLen(initMinLen);
        assertEquals(initMinLen, newConf.getPwGenerationMinLen());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newMinLen, refreshedConf.getPwGenerationMinLen());
        assertSamePropsValue(initConf, refreshedConf);
    }
    
    @Test
    void testSetWriteReadPwGenerationMaxLen() throws IOException {
        resetConfigFile();
        
        final int initMaxLen = 40;
        final int newMaxLen = 30;
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initMaxLen, initConf.getPwGenerationMaxLen());
        initConf.setPwGenerationMaxLen(newMaxLen);
        assertEquals(newMaxLen, initConf.getPwGenerationMaxLen());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newMaxLen, newConf.getPwGenerationMaxLen());
        assertSamePropsValue(initConf, newConf);
        
        newConf.setPwGenerationMaxLen(initMaxLen);
        assertEquals(initMaxLen, newConf.getPwGenerationMaxLen());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newMaxLen, refreshedConf.getPwGenerationMaxLen());
        assertSamePropsValue(initConf, refreshedConf);
    }
    
    private void resetConfigFile() throws IOException {
        confFile = TestsUtils.newTestFile("global-config-test.properties");
    }
    
    private GlobalConfig readConfigFile() throws IOException {
        GlobalConfig.initFile(confFile);
        return new GlobalConfig(confFile);
    }
    
    private static void assertSamePropsValue(GlobalConfig expectedConf, GlobalConfig actualConf) {
        assertEquals(expectedConf.getLanguage(), actualConf.getLanguage());
        assertEquals(expectedConf.getCustomLanguageFile(), actualConf.getCustomLanguageFile());
        assertEquals(expectedConf.getCustomStylesheetURL(), actualConf.getCustomStylesheetURL());
        assertEquals(expectedConf.getCustomStylesheetFile(), actualConf.getCustomStylesheetFile());
        assertEquals(expectedConf.getLastSafeFile(), actualConf.getLastSafeFile());
        assertEquals(
                expectedConf.getPwGenerationCustomChars(),
                actualConf.getPwGenerationCustomChars()
        );
        assertEquals(expectedConf.getPwGenerationMinLen(), actualConf.getPwGenerationMinLen());
        assertEquals(expectedConf.getPwGenerationMaxLen(), actualConf.getPwGenerationMaxLen());
        for (GlobalConfig.ConfigCipher confCipher : GlobalConfig.ConfigCipher.values()) {
            assertEquals(expectedConf.getCipher(confCipher), actualConf.getCipher(confCipher));
        }
    }
    
    @Nested
    class EncodeDecodeStrCol {
        
        @Test
        void testCommon() {
            Set<String> strCol = new LinkedHashSet<>(
                    List.of(
                            "a",
                            "abc",
                            "a".repeat(1023),
                            "a".repeat(56),
                            TestsStringUtils.COMMON_CHARS
                    )
            );
            testEncodeDecodeStrCol(strCol);
        }
        
        @Test
        void testAllLengths() {
            Set<String> strCol = new LinkedHashSet<>();
            StringBuilder sb = new StringBuilder("");
            for (int len = 0; len <= 1023; len++) {
                strCol.add(sb.toString());
                sb.append("a");
            }
            testEncodeDecodeStrCol(strCol);
        }
        
        void testEncodeDecodeStrCol(Collection<String> strCol) {
            String encoded = GlobalConfig.encodeStrCol(strCol);
            testLog.debug(() -> "encoded = " + encoded);
            List<String> decoded = new ArrayList<>();
            GlobalConfig.decodeStrCol(encoded, decoded);
            testLog.debug(() -> "decoded = " + Arrays.toString(decoded.toArray()));
            
            assertEquals(strCol.size(), decoded.size());
            int i = 0;
            for (String str : strCol) {
                assertEquals(str, decoded.get(i++));
            }
        }
        
    }
    
}
