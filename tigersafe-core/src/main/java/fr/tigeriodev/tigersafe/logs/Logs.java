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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Logs {
    
    public static final boolean ALLOW_UNSAFE_LOGGERS =
            Boolean.getBoolean("tigersafe.unsafeLoggers");
    private static final Logger DISABLED_LOGGER = new Logger(Level.ERROR) {
        
        @Override
        public boolean isLoggable(Level level) {
            return false;
        };
        
        @Override
        protected void print(Level level, String msg, Throwable thrown) {
            // Nothing
        }
        
        @Override
        public Logger newChildFromCurMethIf(Level maxUsedLevel) {
            return DISABLED_LOGGER;
        };
        
        @Override
        public Logger newChildFromCurMeth() {
            return DISABLED_LOGGER;
        };
        
        @Override
        public Logger newChild(String displayName, Level minLevel) {
            return DISABLED_LOGGER;
        };
        
    };
    
    private static LoggerFactory loggerFactory;
    private static final Level defLevel;
    private static final List<PatternLevel> specificLevels;
    
    private static final class PatternLevel {
        
        final String pattern;
        final Level level;
        
        PatternLevel(String pattern, Level level) {
            this.pattern = pattern;
            this.level = level;
        }
        
    }
    
    static {
        String logsConfigPath = System.getProperty("tigersafe.logs");
        if (logsConfigPath != null && !logsConfigPath.isEmpty()) {
            try (BufferedReader br = Files.newBufferedReader(Paths.get(logsConfigPath))) {
                String line = br.readLine();
                if (line != null) {
                    defLevel = Objects.requireNonNull(Level.fromId(line));
                    specificLevels = new ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(":");
                        specificLevels.add(new PatternLevel(parts[0], Level.fromId(parts[1])));
                    }
                } else {
                    throw new IllegalArgumentException("Empty logs config.");
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            defLevel = Level.WARN;
            specificLevels = null;
        }
    }
    
    public static void setLoggerFactory(LoggerFactory factory) {
        if (loggerFactory != null) {
            throw new IllegalStateException("The LoggerFactory has already been set.");
        }
        loggerFactory = factory;
    }
    
    public static Logger newUnsafeLogger(Class<?> clazz) {
        if (ALLOW_UNSAFE_LOGGERS) {
            return newLogger(clazz);
        } else {
            return DISABLED_LOGGER;
        }
    }
    
    public static Logger newLogger(Class<?> clazz) {
        return newLogger(clazz, defLevel);
    }
    
    public static Logger newLogger(Class<?> clazz, Level minLevel) {
        return loggerFactory
                .newLogger(clazz.getSimpleName(), getInitLevel(clazz.getCanonicalName(), minLevel));
    }
    
    private static Level getInitLevel(String loggerName, Level codeMinLevel) {
        if (specificLevels != null) {
            for (PatternLevel patternLevel : specificLevels) {
                if (loggerName.contains(patternLevel.pattern)) {
                    return patternLevel.level;
                }
            }
        }
        return codeMinLevel;
    }
    
}
