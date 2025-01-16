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

public final class NumberRange {
    
    public final int min;
    
    /**
     * Power of 2 of the number of possibilities of this range. 
     */
    public final int pow;
    
    public NumberRange(int min, int pow) {
        this.min = min;
        this.pow = CheckUtils.positive(pow);
    }
    
    public int getMax() {
        return min + (1 << pow) - 1;
    }
    
    public boolean contains(int num) {
        return min <= num && num <= getMax();
    }
    
    @Override
    public String toString() {
        return "NumberRange [" + min + "; " + getMax() + "]";
    }
    
}
