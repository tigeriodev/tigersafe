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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.utils.DatetimeUtils;

public class DatetimeUtilsTest extends TestClass {
    
    @Nested
    class GetPeriodBetween {
        
        @Test
        void test1() {
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime start = ZonedDateTime.of(2024, 1, 1, 23, 0, 0, 0, zone);
            assertEquals(
                    0,
                    DatetimeUtils
                            .getPeriodBetween(start.toInstant(), start.plusDays(30).toInstant())
                            .toTotalMonths()
            );
            assertEquals(
                    1,
                    DatetimeUtils
                            .getPeriodBetween(
                                    start.toInstant(),
                                    start.plusDays(30).plusHours(1).toInstant()
                            )
                            .toTotalMonths()
            );
            assertEquals(
                    1,
                    DatetimeUtils
                            .getPeriodBetween(
                                    start.toInstant(),
                                    start.plusDays(58).plusHours(1).toInstant()
                            )
                            .toTotalMonths()
            );
            assertEquals(
                    2,
                    DatetimeUtils
                            .getPeriodBetween(
                                    start.toInstant(),
                                    start.plusDays(59).plusHours(1).toInstant()
                            )
                            .toTotalMonths()
            );
            
            IntStream.of(0, 1, 2, 5, 10, 12, 13, 14, 15, 24, 25).forEach((addedMonths) -> {
                assertEquals(
                        addedMonths,
                        DatetimeUtils
                                .getPeriodBetween(
                                        start.toInstant(),
                                        start.plusMonths(addedMonths).toInstant()
                                )
                                .toTotalMonths()
                );
            });
        }
        
        @Test
        void testTimeZone() {
            TimeZone initTimeZone = TimeZone.getDefault();
            
            TimeZone utc0 = TimeZone.getTimeZone("Europe/Dublin"); // UTC+0
            TimeZone utc1 = TimeZone.getTimeZone("Europe/Paris"); // UTC+1
            
            ZonedDateTime startUTC0 = ZonedDateTime.of(2024, 1, 1, 23, 0, 0, 0, utc0.toZoneId());
            Instant start = startUTC0.toInstant();
            Instant end = startUTC0.plusDays(30).plusHours(1).toInstant();
            
            TimeZone.setDefault(utc0);
            assertEquals(
                    1,
                    DatetimeUtils.getPeriodBetween(
                            start, // 01/01/24 23:00
                            end // 01/02/24 00:00
                    ).toTotalMonths()
            );
            
            TimeZone.setDefault(utc1);
            assertEquals(
                    0,
                    DatetimeUtils.getPeriodBetween(
                            start, // 02/01/24 00:00
                            end // 01/02/24 01:00
                    ).toTotalMonths()
            );
            
            TimeZone.setDefault(initTimeZone);
        }
        
    }
    
    @Test
    void testNowWithoutNanos() {
        Instant nowWithoutNanos = DatetimeUtils.nowWithoutNanos();
        assertEquals(0, nowWithoutNanos.getNano());
        assertEquals(0, nowWithoutNanos.until(Instant.now(), ChronoUnit.SECONDS));
    }
    
}
