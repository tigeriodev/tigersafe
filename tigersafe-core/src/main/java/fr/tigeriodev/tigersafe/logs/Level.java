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

public enum Level {
    
    DEBUG("D", "\033[90m"),
    INFO("I", null),
    WARN("W", "\033[33m"),
    ERROR("E", "\033[31m");
    
    public final String id;
    public final String ansiColor;
    
    public static Level fromId(String id) {
        for (Level level : values()) {
            if (level.id.equals(id)) {
                return level;
            }
        }
        return null;
    }
    
    private Level(String id, String ansiColor) {
        this.id = id;
        if (ansiColor != null && ansiColor.isEmpty()) {
            throw new IllegalArgumentException(
                    "ansiColor cannot be an empty String, should be null."
            );
        }
        this.ansiColor = ansiColor;
    }
    
    public String getColoredLines(String lines) {
        if (ansiColor == null) {
            return lines;
        }
        if (!lines.contains("\n")) {
            return getColoredLine(lines);
        }
        
        StringBuilder sb = new StringBuilder();
        for (String line : lines.split("\n")) {
            sb.append(getColoredLine(line)).append("\n");
        }
        sb.setLength(sb.length() - 1); // rem last newLine
        return sb.toString();
    }
    
    public String getColoredLine(String line) {
        if (ansiColor != null) {
            return ansiColor + line + "\033[0m";
        } else {
            return line;
        }
    }
    
    public boolean isLowerThan(Level other) {
        return this.ordinal() <= other.ordinal();
    }
    
}
