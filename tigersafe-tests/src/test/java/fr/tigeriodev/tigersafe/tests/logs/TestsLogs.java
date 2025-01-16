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

package fr.tigeriodev.tigersafe.tests.logs;

import java.util.ArrayList;
import java.util.List;

import fr.tigeriodev.tigersafe.logs.ConsoleLogger;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logs;

public class TestsLogs {
    
    private static boolean warnOrHigherPrinted = false;
    private static List<LogMessagePattern> mutedPatterns = new ArrayList<>();
    
    public static void init() {
        Logs.setLoggerFactory(
                (displayName, initLevel) -> new ConsoleLogger(displayName, initLevel) {
                    
                    @Override
                    protected void print(Level level, String msg, Throwable thrown) {
                        for (LogMessagePattern mutedPattern : mutedPatterns) {
                            if (
                                mutedPattern.loggerName.equals(displayName)
                                        && mutedPattern.level == level
                                        && msg.contains(mutedPattern.msgContains)
                            ) {
                                return;
                            }
                        }
                        if (Level.WARN.isLowerThan(level)) {
                            warnOrHigherPrinted = true;
                        }
                        super.print(level, msg, thrown);
                    };
                    
                }
        );
    }
    
    public static void runMuting(LogMessagePattern msgPattern, Runnable run) {
        mutedPatterns.add(msgPattern);
        run.run();
        mutedPatterns.remove(msgPattern);
    }
    
    /**
     * NB: This method is not perfect for multithreading, but still works.
     * @return true if has encountered warn or higher level logs since last call to this method.
     */
    public static boolean resetWarnOrHigherPrinted() {
        boolean hasEncountered = warnOrHigherPrinted;
        warnOrHigherPrinted = false;
        return hasEncountered;
    }
    
}
