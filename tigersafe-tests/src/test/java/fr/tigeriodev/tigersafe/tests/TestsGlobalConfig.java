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

import java.io.File;
import java.io.IOException;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.tests.utils.TestsUtils;

public final class TestsGlobalConfig {
    
    public static void resetForTest() throws IOException {
        File userGlobalConfigFile = TestsUtils.newTestFile("global-config.properties");
        initForTest(userGlobalConfigFile);
    }
    
    public static void initForTest(File userGlobalConfigFile) throws IOException {
        GlobalConfig.initFile(userGlobalConfigFile);
        GlobalConfig.setInstance(new GlobalConfig(userGlobalConfigFile), false, false);
    }
    
}
