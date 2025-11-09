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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TestsUtils {
    
    private TestsUtils() {}
    
    @FunctionalInterface
    public interface RunnableWithThrowable {
        
        void run() throws Throwable;
        
    }
    
    public static void testRandomFailing(Class<? extends Throwable> randomlyThrown, int maxTries,
            RunnableWithThrowable run) {
        int tryNum = 0;
        while (tryNum < maxTries) {
            try {
                run.run();
                return;
            } catch (Throwable thrown) {
                if (randomlyThrown.isInstance(thrown)) {
                    tryNum++;
                    if (tryNum >= maxTries) {
                        fail(
                                "Test failed " + maxTries + " times throwing "
                                        + randomlyThrown.getSimpleName() + ": ",
                                thrown
                        );
                    }
                } else {
                    fail("Unexpected thrown: ", thrown);
                }
            }
        }
    }
    
    public static File newTestFile(String fileName) throws IOException {
        File res = new File("../tests/temp/junit", fileName);
        Files.deleteIfExists(res.toPath());
        return res;
    }
    
}
