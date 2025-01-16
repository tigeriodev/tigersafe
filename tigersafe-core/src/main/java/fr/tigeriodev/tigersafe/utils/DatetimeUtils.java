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

package fr.tigeriodev.tigersafe.utils;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class DatetimeUtils {
    
    private DatetimeUtils() {}
    
    public static final Instant nowWithoutNanos() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
    
    public static final Period getPeriodBetween(Instant start, Instant end) {
        ZoneId zone = ZoneId.systemDefault();
        return Period.between(start.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate());
    }
    
}
