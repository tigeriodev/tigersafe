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

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.tests.logs.TestsLogs;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class TestClass {
    
    static {
        TestsLogs.init();
    }
    
    private static final byte MAX_PRINTED_PARENT_CLASSES = 4;
    
    protected final Logger log;
    protected Logger testLog;
    
    public TestClass() {
        log = Logs.newLogger(getClass());
    }
    
    @BeforeEach
    void beforeTest(TestInfo testInfo) {
        testLog = log.newChild(getTestName(testInfo) + "()");
        testLog.info(() -> "start");
    }
    
    @AfterEach
    void afterTest(TestInfo testInfo) {
        testLog.info(() -> "end");
        assertFalse(
                TestsLogs.resetWarnOrHigherPrinted(),
                () -> "A warning or higher log has been printed."
        );
    }
    
    String getTestName(TestInfo testInfo) {
        StringBuilder classNamePrefix = new StringBuilder();
        Class<?> testClass = testInfo.getTestClass().orElseThrow(IllegalStateException::new);
        byte i = 0;
        while (i < MAX_PRINTED_PARENT_CLASSES && testClass != null && testClass.isMemberClass()) {
            classNamePrefix.insert(0, testClass.getSimpleName() + ".");
            i++;
            testClass = testClass.getDeclaringClass();
        }
        return classNamePrefix
                + testInfo.getTestMethod().orElseThrow(IllegalStateException::new).getName();
    }
    
}
