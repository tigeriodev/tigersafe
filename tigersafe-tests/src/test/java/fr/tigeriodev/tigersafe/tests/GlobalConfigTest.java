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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.GlobalConfig.ConfigCipher;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.tests.utils.TestsStringUtils;
import fr.tigeriodev.tigersafe.tests.utils.TestsUtils;

public class GlobalConfigTest extends TestClass {
    
    private File confFile;
    
    @Test
    void testSetWriteReadLanguage() throws IOException {
        resetConfigFile();
        
        Locale initLang = Lang.AVAILABLE_LANGUAGES[0];
        Locale newLang = Lang.AVAILABLE_LANGUAGES[1];
        assertNotEquals(initLang, newLang);
        
        GlobalConfig initConf = readConfigFile();
        assertEquals(initLang, initConf.getLanguage());
        initConf.setLanguage(newLang);
        assertEquals(newLang, initConf.getLanguage());
        initConf.updateUserFile();
        
        GlobalConfig newConf = readConfigFile();
        assertEquals(newLang, newConf.getLanguage());
        assertEquals(initConf.getCustomLanguageFile(), newConf.getCustomLanguageFile());
        assertEquals(initConf.getCustomStylesheetFile(), newConf.getCustomStylesheetFile());
        assertEquals(initConf.getLastSafeFile(), newConf.getLastSafeFile());
        assertEquals(initConf.getPwGenerationCustomChars(), newConf.getPwGenerationCustomChars());
        assertEquals(initConf.getPwGenerationMaxLen(), newConf.getPwGenerationMaxLen());
        assertEquals(initConf.getPwGenerationMinLen(), newConf.getPwGenerationMinLen());
        for (ConfigCipher confCipher : ConfigCipher.values()) {
            assertEquals(initConf.getCipher(confCipher), newConf.getCipher(confCipher));
        }
        
        newConf.setLanguage(initLang);
        assertEquals(initLang, newConf.getLanguage());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newLang, refreshedConf.getLanguage());
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
        assertEquals(initConf.getLanguage(), newConf.getLanguage());
        assertEquals(initConf.getCustomLanguageFile(), newConf.getCustomLanguageFile());
        assertEquals(initConf.getCustomStylesheetFile(), newConf.getCustomStylesheetFile());
        assertEquals(initConf.getLastSafeFile(), newConf.getLastSafeFile());
        assertEquals(initConf.getPwGenerationCustomChars(), newConf.getPwGenerationCustomChars());
        assertEquals(initConf.getPwGenerationMaxLen(), newConf.getPwGenerationMaxLen());
        assertEquals(initConf.getPwGenerationMinLen(), newConf.getPwGenerationMinLen());
        for (GlobalConfig.ConfigCipher confCipher : GlobalConfig.ConfigCipher.values()) {
            if (!modifiedConfCipher.equals(confCipher)) {
                assertEquals(initConf.getCipher(confCipher), newConf.getCipher(confCipher));
            }
        }
        
        newConf.setCipher(modifiedConfCipher, initCipherName);
        assertEquals(initCipher, newConf.getCipher(modifiedConfCipher));
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newCipher, refreshedConf.getCipher(modifiedConfCipher));
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
        assertEquals(initConf.getLanguage(), newConf.getLanguage());
        assertEquals(initConf.getCustomLanguageFile(), newConf.getCustomLanguageFile());
        assertEquals(initConf.getCustomStylesheetFile(), newConf.getCustomStylesheetFile());
        assertEquals(initConf.getLastSafeFile(), newConf.getLastSafeFile());
        assertEquals(initConf.getPwGenerationMaxLen(), newConf.getPwGenerationMaxLen());
        assertEquals(initConf.getPwGenerationMinLen(), newConf.getPwGenerationMinLen());
        for (ConfigCipher confCipher : ConfigCipher.values()) {
            assertEquals(initConf.getCipher(confCipher), newConf.getCipher(confCipher));
        }
        
        newConf.setPwGenerationCustomChars(initCustomChars);
        assertEquals(initCustomChars, newConf.getPwGenerationCustomChars());
        // do not updateUserFile
        
        GlobalConfig refreshedConf = readConfigFile();
        assertEquals(newCustomChars, refreshedConf.getPwGenerationCustomChars());
    }
    
    private void resetConfigFile() {
        confFile = TestsUtils.newTestFile("global-config-test.properties");
    }
    
    private GlobalConfig readConfigFile() throws IOException {
        GlobalConfig.initForTest(confFile);
        return GlobalConfig.getInstance();
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
