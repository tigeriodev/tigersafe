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

package fr.tigeriodev.tigersafe.logs;

import fr.tigeriodev.tigersafe.utils.StringUtils;

public class ConsoleLogger extends Logger {
    
    // TODO could probably optimize with StringBuilder
    private static final String FORMAT =
            "%1$tH:%1$tM:%1$tS.%1$tL | %2$s | %3$9.9s | %4$20.20s | %5$s %6$s%n";
    
    private final String displayName;
    
    public ConsoleLogger(String displayName, Level initLevel) {
        super(initLevel);
        this.displayName = displayName;
    }
    
    @Override
    protected void print(Level level, String msg, Throwable thrown) {
        String thrownStackTrace = thrown != null
                ? System.lineSeparator() + level.getColoredLines(StringUtils.getStackTrace(thrown))
                : "";
        
        // Use only System.out to avoid conflicts when printing errors and not errors logs (text can be mixed even with 1 thread)
        System.out.format(
                FORMAT,
                System.currentTimeMillis(),
                level.getColoredLine(level.id),
                Thread.currentThread().getName(),
                displayName,
                level.getColoredLines(msg),
                thrownStackTrace
        );
    }
    
}
