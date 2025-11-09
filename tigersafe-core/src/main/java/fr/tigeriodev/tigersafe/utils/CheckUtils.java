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

import java.util.Collection;
import java.util.Objects;

public final class CheckUtils {
    
    private CheckUtils() {}
    
    public static final <T> T notNull(final T o) {
        return Objects.requireNonNull(o);
    }
    
    public static final String notEmpty(final String str) {
        if (str.isEmpty()) {
            throw new IllegalArgumentException("String is empty.");
        }
        return str;
    }
    
    public static final boolean isNotEmpty(final String str) {
        return str != null && !str.isEmpty();
    }
    
    public static final <T, C extends Collection<T>> C notEmpty(final C c) {
        if (c.isEmpty()) {
            throw new IllegalArgumentException("Collection is empty.");
        }
        return c;
    }
    
    public static final <T, C extends Collection<T>> boolean isNotEmpty(final C c) {
        return c != null && !c.isEmpty();
    }
    
    public static final <T> T[] notEmpty(final T[] arr) {
        if (arr.length == 0) {
            throw new IllegalArgumentException("Array is empty.");
        }
        return arr;
    }
    
    public static final char[] notEmpty(final char[] arr) {
        if (arr.length == 0) {
            throw new IllegalArgumentException("char[] is empty.");
        }
        return arr;
    }
    
    public static final int positive(final int i) {
        if (i < 0) {
            throw new IllegalArgumentException("Int " + i + " < 0.");
        }
        return i;
    }
    
    public static final int strictlyPositive(final int i) {
        if (i <= 0) {
            throw new IllegalArgumentException("Int " + i + " <= 0.");
        }
        return i;
    }
    
    public static final int inRange(final int num, final NumberRange range) {
        if (!range.contains(num)) {
            throw new IllegalArgumentException("Int " + num + " is not in range: " + range + ".");
        }
        return num;
    }
    
    public static final int inRange(final int num, final int min, final int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        if (num < min || num > max) {
            throw new IllegalArgumentException(
                    "Int " + num + " is not in range: [" + min + "; " + max + "]."
            );
        }
        return num;
    }
    
    public static final boolean isNotIllegal(final Runnable check) {
        try {
            check.run();
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
    
}
