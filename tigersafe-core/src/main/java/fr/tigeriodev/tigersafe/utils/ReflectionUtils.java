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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;

public final class ReflectionUtils {
    
    private static final Logger log = Logs.newLogger(ReflectionUtils.class);
    
    private ReflectionUtils() {}
    
    public static final Field getField(Class<?> clazz, String fieldName) {
        try {
            Field res = clazz.getDeclaredField(fieldName);
            res.setAccessible(true);
            return res;
        } catch (NoSuchFieldException ex) {
            log.newChildFromCurMeth()
                    .error(
                            () -> "\"" + fieldName + "\" field not found in "
                                    + clazz.getCanonicalName() + ": ",
                            ex
                    );
            return null;
        }
    }
    
    public static Method getMeth(Class<?> clazz, String methName, Class<?>... paramsType) {
        try {
            Method res = clazz.getDeclaredMethod(methName, paramsType);
            res.setAccessible(true);
            return res;
        } catch (NoSuchMethodException ex) {
            log.newChildFromCurMeth()
                    .error(
                            () -> methName + "(" + paramsType.length
                                    + " params) method not found in " + clazz.getCanonicalName()
                                    + ": ",
                            ex
                    );
            return null;
        }
    }
    
}
