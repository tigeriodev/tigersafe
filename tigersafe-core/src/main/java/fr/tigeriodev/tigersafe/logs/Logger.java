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

import java.util.concurrent.Callable;

import fr.tigeriodev.tigersafe.utils.CheckUtils;

public abstract class Logger {
    
    protected Level minLevel;
    
    public Logger(Level minLevel) {
        setMinLevel(minLevel);
    }
    
    public void setMinLevel(Level newVal) {
        minLevel = CheckUtils.notNull(newVal);
    }
    
    public Level getMinLevel() {
        return minLevel;
    }
    
    public boolean isDebugLoggable() {
        return isLoggable(Level.DEBUG);
    }
    
    public boolean isInfoLoggable() {
        return isLoggable(Level.INFO);
    }
    
    public boolean isWarnLoggable() {
        return isLoggable(Level.WARN);
    }
    
    public boolean isErrorLoggable() {
        return isLoggable(Level.ERROR);
    }
    
    public boolean isLoggable(Level level) {
        return minLevel.isLowerThan(level);
    }
    
    public void debug(Callable<String> msgCallable) {
        log(Level.DEBUG, msgCallable);
    }
    
    public void debug(Callable<String> msgCallable, Throwable thrown) {
        log(Level.DEBUG, msgCallable, thrown);
    }
    
    public void info(Callable<String> msgCallable) {
        log(Level.INFO, msgCallable);
    }
    
    public void info(Callable<String> msgCallable, Throwable thrown) {
        log(Level.INFO, msgCallable, thrown);
    }
    
    public void warn(Callable<String> msgCallable) {
        log(Level.WARN, msgCallable);
    }
    
    public void warn(Callable<String> msgCallable, Throwable thrown) {
        log(Level.WARN, msgCallable, thrown);
    }
    
    public void error(Callable<String> msgCallable) {
        log(Level.ERROR, msgCallable);
    }
    
    public void error(Callable<String> msgCallable, Throwable thrown) {
        log(Level.ERROR, msgCallable, thrown);
    }
    
    public void log(Level level, Callable<String> msgCallable) {
        log(level, msgCallable, null);
    }
    
    public void log(Level level, Callable<String> msgCallable, Throwable thrown) {
        if (isLoggable(level)) {
            try {
                print(level, msgCallable.call(), thrown);
            } catch (Exception ex) {
                print(Level.ERROR, "An error occurred while logging: ", ex);
            }
        }
    }
    
    public void log(Level level, String msg) {
        log(level, msg, null);
    }
    
    public void log(Level level, String msg, Throwable thrown) {
        if (isLoggable(level)) {
            print(level, msg, thrown);
        }
    }
    
    protected abstract void print(Level level, String msg, Throwable thrown);
    
    public Logger newChild(Class<?> clazz) {
        return newChild(clazz.getSimpleName());
    }
    
    public Logger newChild(Class<?> clazz, Level minLevel) {
        return newChild(clazz.getSimpleName(), minLevel);
    }
    
    public Logger newChildFromInstance(Object instance) {
        return newChild("#" + instance.hashCode());
    }
    
    public Logger newChildFromCurMethIf(Level maxUsedLevel) {
        if (!isLoggable(maxUsedLevel)) {
            return this; // will never print, no need to create new logger
        } // else will print for at least maxUsedLevel, and maybe a lower level too
        String curMethName =
                StackWalker.getInstance().walk(f -> f.skip(1).findFirst()).get().getMethodName();
        return newChild(curMethName + "()");
    }
    
    public Logger newChildFromCurMeth() {
        String curMethName =
                StackWalker.getInstance().walk(f -> f.skip(1).findFirst()).get().getMethodName();
        return newChild(curMethName + "()");
    }
    
    public Logger newChild(String displayName) {
        return newChild(displayName, getMinLevel());
    }
    
    public Logger newChild(String displayName, Level minLevel) {
        return new ChildLogger(this, displayName, minLevel);
    }
    
}
