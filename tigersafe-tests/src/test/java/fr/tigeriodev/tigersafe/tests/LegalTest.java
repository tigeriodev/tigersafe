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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class LegalTest extends TestClass {
    
    private static final String[] rootPaths = new String[] {
            "tigersafe-core/src", "tigersafe-ui/src", "tigersafe-tests/src"
    };
    private static final String[] specialHeaderFilesName = new String[] {
            "SecureCipherInputStream.java",
            "TOTPUtils.java",
            "SingleLineTextLayout.java",
            "DestroyableByteArrayOutputStream.java",
            "UTFUtils.java",
    };
    
    static {
        Arrays.sort(specialHeaderFilesName);
    }
    
    @Test
    void testHeaders() throws IOException {
        String projectHeader = """
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
 */""";
        
        assertFalse(isOutdated(projectHeader), () -> "Outdated copyright in project header");
        File projectRootFile = new File("..");
        Set<String> specialHeadersFound = new HashSet<>();
        Set<String> specialHeadersFoundFilesName = new HashSet<>();
        for (String rootPath : rootPaths) {
            checkHeaders(
                    new File(projectRootFile, rootPath),
                    projectHeader,
                    specialHeadersFound,
                    specialHeadersFoundFilesName
            );
        }
        
        String[] specialHeadersFoundFilesNameArr =
                specialHeadersFoundFilesName.toArray(new String[0]);
        Arrays.sort(specialHeadersFoundFilesNameArr);
        assertArrayEquals(
                specialHeaderFilesName,
                specialHeadersFoundFilesNameArr,
                () -> "Missing or unexpected special headers in some files, specialHeadersFoundFilesName = "
                        + Arrays.toString(specialHeadersFoundFilesNameArr)
        );
        
        for (String specialHeader : specialHeadersFound) {
            assertFalse(
                    isOutdated(specialHeader),
                    () -> "Outdated copyright in this special header: " + specialHeader
            );
        }
        
        checkNoticeHeaders(projectHeader, specialHeadersFound);
    }
    
    void checkHeaders(File file, String projectHeader, Set<String> specialHeadersFound,
            Set<String> specialHeadersFoundFilesName) throws IOException {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                checkHeaders(
                        child,
                        projectHeader,
                        specialHeadersFound,
                        specialHeadersFoundFilesName
                );
            }
        } else {
            String fileName = file.getName();
            if (fileName.endsWith(".java")) {
                boolean foundSpecialHeader = false;
                boolean foundProjectHeader = false;
                boolean foundCodeStart = false;
                boolean isModuleInfo = fileName.equals("module-info.java");
                try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
                    List<String> curHeaderLines = new ArrayList<>();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        if (
                            (!isModuleInfo && line.startsWith("package"))
                                    || (isModuleInfo && line.startsWith("module"))
                        ) {
                            foundCodeStart = true;
                            break;
                        }
                        
                        if (line.contains("*/")) {
                            assertFalse(
                                    curHeaderLines.isEmpty(),
                                    () -> "Found header end without header start in "
                                            + file.getAbsolutePath()
                            );
                            curHeaderLines.add(line);
                            String curHeader = String.join("\n", curHeaderLines);
                            if (projectHeader.equals(curHeader)) {
                                assertFalse(
                                        foundProjectHeader,
                                        () -> "Found project header several times in "
                                                + file.getAbsolutePath()
                                );
                                foundProjectHeader = true;
                            } else {
                                foundSpecialHeader = true;
                                specialHeadersFound.add(curHeader);
                                specialHeadersFoundFilesName.add(fileName);
                            }
                            curHeaderLines.clear();
                        } else if (!curHeaderLines.isEmpty() || line.contains("/*")) {
                            curHeaderLines.add(line);
                            assertFalse(
                                    isOutdated(line),
                                    () -> "Outdated copyright in " + file.getAbsolutePath()
                            );
                        }
                    }
                    assertTrue(
                            foundCodeStart,
                            () -> "Could not find code start in " + file.getAbsolutePath()
                    );
                    assertTrue(
                            curHeaderLines.isEmpty(),
                            () -> "Could not find a header end in " + file.getAbsolutePath()
                    );
                }
                assertTrue(
                        foundProjectHeader || foundSpecialHeader,
                        () -> "Missing header in " + file.getAbsolutePath()
                );
                assertFalse(
                        foundProjectHeader && foundSpecialHeader,
                        () -> "Cannot mix project header with other header in "
                                + file.getAbsolutePath()
                );
                
                boolean isExpectedSpecialHeader = false;
                for (String specialHeaderFileName : specialHeaderFilesName) {
                    if (fileName.equals(specialHeaderFileName)) {
                        isExpectedSpecialHeader = true;
                        break;
                    }
                }
                assertTrue(
                        isExpectedSpecialHeader == foundSpecialHeader,
                        () -> "Unexpected header in " + file.getAbsolutePath()
                );
            }
        }
    }
    
    static boolean isOutdated(String copyrightText) {
        return copyrightText.contains("Copyright")
                && copyrightText.contains("tigeriodev")
                && !copyrightText.contains(Integer.toString(Year.now().getValue()));
    }
    
    void checkNoticeHeaders(String projectHeader, Set<String> expectedSpecialHeaders)
            throws IOException {
        File file = new File("../NOTICE");
        
        Set<String> specialHeadersFound = new HashSet<>();
        boolean foundProjectHeader = false;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            List<String> curHeaderLines = new ArrayList<>();
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("*/")) {
                    assertFalse(
                            curHeaderLines.isEmpty(),
                            () -> "Found header end without header start in "
                                    + file.getAbsolutePath()
                    );
                    curHeaderLines.add(line);
                    String curHeader = String.join("\n", curHeaderLines);
                    if (projectHeader.equals(curHeader)) {
                        assertFalse(
                                foundProjectHeader,
                                () -> "Found project header several times in "
                                        + file.getAbsolutePath()
                        );
                        foundProjectHeader = true;
                    } else {
                        assertTrue(
                                specialHeadersFound.add(curHeader),
                                () -> "Found this special header several times in "
                                        + file.getAbsolutePath() + ": " + curHeader
                        );
                    }
                    curHeaderLines.clear();
                } else if (!curHeaderLines.isEmpty() || line.contains("/*")) {
                    curHeaderLines.add(line);
                }
            }
        }
        assertTrue(foundProjectHeader, () -> "Missing project header in " + file.getAbsolutePath());
        assertEquals(
                expectedSpecialHeaders,
                specialHeadersFound,
                () -> "Missing or unexpected special headers in " + file.getAbsolutePath()
        );
    }
    
    @Test
    void testLegalFilesInResources() throws IOException {
        checkSameLines(
                new File("../LICENSE"),
                new File("../tigersafe-ui/src/main/resources/LICENSE")
        );
        checkSameLines(
                new File("../NOTICE"),
                new File("../tigersafe-ui/src/main/resources/NOTICE")
        );
    }
    
    static void checkSameLines(File file1, File file2) throws IOException {
        try (
                BufferedReader reader1 = Files.newBufferedReader(file1.toPath());
                BufferedReader reader2 = Files.newBufferedReader(file2.toPath())
        ) {
            int lineInd = 1;
            String line1 = reader1.readLine();
            String line2 = reader2.readLine();
            while (line1 != null && line2 != null) {
                final int flineInd = lineInd;
                assertEquals(
                        line1,
                        line2,
                        () -> "Different line " + flineInd + " in " + file1.getAbsolutePath()
                                + " and " + file2.getAbsolutePath()
                );
                
                lineInd++;
                line1 = reader1.readLine();
                line2 = reader2.readLine();
            }
            
            assertEquals(
                    line1,
                    line2,
                    () -> "Different number of lines in " + file1.getAbsolutePath() + " and "
                            + file2.getAbsolutePath()
            );
        }
    }
    
    @Test
    void testProjectCopyrightNotice() throws IOException {
        String projectCopyrightNotice = """
TigerSafe
Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License version 2 only, as
published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
version 2 for more details (a copy is included in the LICENSE file that
accompanied this code).

You should have received a copy of the GNU General Public License version
2 along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.""";
        
        assertFalse(
                isOutdated(projectCopyrightNotice),
                () -> "Outdated copyright in project copyright notice"
        );
        
        String[] projectCopNotLines = projectCopyrightNotice.split("\n");
        
        File projectNoticeFile = new File("../NOTICE");
        assertTrue(
                startsWithLinesBlock(projectNoticeFile, projectCopNotLines),
                () -> "Project notice (" + projectNoticeFile.getAbsolutePath()
                        + ") does not start with the project copyright notice."
        );
        
        File winInstallFile = new File("../install/windows/install.ps1");
        assertTrue(
                containsLinesBlock(winInstallFile, projectCopNotLines),
                () -> "Windows install (" + winInstallFile.getAbsolutePath()
                        + ") does not contain the project copyright notice."
        );
        
        File unixInstallFile = new File("../install/unix/install.sh");
        assertTrue(
                containsLinesBlock(unixInstallFile, projectCopNotLines),
                () -> "Unix install (" + unixInstallFile.getAbsolutePath()
                        + ") does not contain the project copyright notice."
        );
    }
    
    static boolean startsWithLinesBlock(File file, String[] lines) throws IOException {
        if (lines.length < 1) {
            throw new IllegalArgumentException();
        }
        
        int lineInd = 0;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(lines[lineInd])) {
                    lineInd++;
                    if (lineInd >= lines.length) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }
        return lineInd >= lines.length;
    }
    
    static boolean containsLinesBlock(File file, String[] lines) throws IOException {
        if (lines.length < 1) {
            throw new IllegalArgumentException();
        }
        
        int lastValidLineInd = -1;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(lines[lastValidLineInd + 1])) {
                    lastValidLineInd++;
                    if (lastValidLineInd >= lines.length - 1) {
                        return true;
                    }
                } else {
                    lastValidLineInd = -1;
                }
            }
        }
        return lastValidLineInd >= lines.length - 1;
    }
    
}
