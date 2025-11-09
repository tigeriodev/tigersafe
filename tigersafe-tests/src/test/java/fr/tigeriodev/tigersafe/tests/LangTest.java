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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.Lang;

public class LangTest extends TestClass {
    
    @Nested
    class DefaultLangFiles {
        
        static void getResourceDirFiles(String resrcDirPath, Consumer<List<Path>> exec)
                throws IOException, URISyntaxException {
            URL dirURL = Lang.class.getResource("/" + resrcDirPath);
            if (dirURL.getProtocol().equals("jar")) {
                try (
                        FileSystem fs =
                                FileSystems.newFileSystem(dirURL.toURI(), Collections.emptyMap())
                ) {
                    Path dirPath = fs.getPath("/" + resrcDirPath);
                    exec.accept(Files.list(dirPath).collect(Collectors.toList())); // code must be executed while the fs is open
                }
            } else {
                Path dirPath = Paths.get(dirURL.toURI());
                exec.accept(Files.list(dirPath).collect(Collectors.toList()));
            }
        }
        
        @Test
        void testSameKeysAndAvailable() throws IOException, URISyntaxException {
            getResourceDirFiles("languages", (defFilesPath) -> {
                assertEquals(
                        Lang.AVAILABLE_LANGUAGES.size(),
                        defFilesPath.size(),
                        () -> "Lang.AVAILABLE_LANGUAGES size " + Lang.AVAILABLE_LANGUAGES.size()
                                + " != default language files number " + defFilesPath.size() + "."
                );
                Path refFilePath = defFilesPath.get(0);
                checkAvailableLang(refFilePath);
                String[] refKeys = getKeys(refFilePath);
                
                assertTrue(refKeys.length > 10); // detect eventual reading issue
                assertEquals(2, defFilesPath.size());
                
                Iterator<Path> it = defFilesPath.iterator();
                it.next(); // ignore ref file path
                while (it.hasNext()) {
                    Path filePath = it.next();
                    checkAvailableLang(filePath);
                    assertArrayEquals(
                            refKeys,
                            getKeys(filePath),
                            () -> "ref file = " + refFilePath.getFileName() + ", mismatch file = "
                                    + filePath.getFileName()
                    );
                }
            });
        }
        
        private static String[] getKeys(Path langFilePath) {
            List<String> keysList = new ArrayList<>();
            keysList.add("padding"); // to match indexes and file lines number
            try (BufferedReader br = Files.newBufferedReader(langFilePath);) {
                String line;
                while ((line = br.readLine()) != null) {
                    int keyDelimiterInd = line.indexOf(':');
                    if (keyDelimiterInd < 0) {
                        fail(
                                "Missing delimiter ':' at line " + (keysList.size()) + " in "
                                        + langFilePath.getFileName()
                        );
                    }
                    keysList.add(line.substring(0, keyDelimiterInd));
                }
            } catch (IOException ex) {
                fail(ex);
            }
            return keysList.toArray(new String[0]);
        }
        
        private static void checkAvailableLang(Path defLangFile) {
            String fileName = defLangFile.getFileName().toString();
            String localeStr =
                    fileName.substring("language_".length(), fileName.indexOf(".properties"));
            Locale locale = Locale.forLanguageTag(localeStr);
            assertTrue(
                    Lang.isAvailable(locale),
                    () -> "The default language file " + defLangFile
                            + " is not available in Lang.AVAILABLE_LANGUAGES."
            );
        }
        
    }
    
}
