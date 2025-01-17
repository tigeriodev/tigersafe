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
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;

public final class MemUtils {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(MemUtils.class);
    private static final Logger log = Logs.newLogger(MemUtils.class);
    
    private static final Field strValueF = ReflectionUtils.getField(String.class, "value");
    private static final Field strHashF = ReflectionUtils.getField(String.class, "hash");
    private static final Field secretKeyF = ReflectionUtils.getField(SecretKeySpec.class, "key");
    
    private static Field pbkdf2PasswdF = null;
    private static Field pbkdf2SaltF = null;
    private static Field pbkdf2KeyF = null;
    
    // Prevent compiler optimization
    private static int[] lastClearedIntArray;
    private static float[] lastClearedFloatArray;
    private static byte[] lastClearedByteArray;
    private static char[] lastClearedCharArray;
    
    private MemUtils() {}
    
    public static void clearByteArray(byte[] arr) {
        if (arr.length == 0) {
            return;
        }
        Arrays.fill(arr, (byte) 0);
        
        // Prevent compiler optimization
        if (
            arr[0] + arr[arr.length - 1] != (byte) 0
                    || (lastClearedByteArray != null
                            && lastClearedByteArray.length > 0
                            && (lastClearedByteArray[0] & arr[0]) < 0)
        ) {
            throw new RuntimeException("Unexpected memory clearing issue.");
        }
        lastClearedByteArray = arr;
    }
    
    public static void clearIntArray(int[] arr) {
        if (arr.length == 0) {
            return;
        }
        Arrays.fill(arr, 0);
        
        // Prevent compiler optimization
        if (
            arr[0] + arr[arr.length - 1] != 0
                    || (lastClearedIntArray != null
                            && lastClearedIntArray.length > 0
                            && (lastClearedIntArray[0] & arr[0]) < 0)
        ) {
            throw new RuntimeException("Unexpected memory clearing issue.");
        }
        lastClearedIntArray = arr;
    }
    
    public static void clearFloatArray(float[] arr) {
        if (arr.length == 0) {
            return;
        }
        Arrays.fill(arr, 0f);
        
        // Prevent compiler optimization
        if (
            arr[0] + arr[arr.length - 1] != 0f
                    || (lastClearedFloatArray != null
                            && lastClearedFloatArray.length > 0
                            && (lastClearedFloatArray[0] * arr[0]) < 0)
        ) {
            throw new RuntimeException("Unexpected memory clearing issue.");
        }
        lastClearedFloatArray = arr;
    }
    
    public static void clearCharArray(char[] arr) {
        if (arr.length == 0) {
            return;
        }
        char c = (char) 0;
        Arrays.fill(arr, c);
        
        // Prevent compiler optimization
        if (
            arr[0] + arr[arr.length - 1] != c + c
                    || (lastClearedCharArray != null
                            && lastClearedCharArray.length > 0
                            && (lastClearedCharArray[0] & arr[0]) < 0)
        ) {
            throw new RuntimeException("Unexpected memory clearing issue.");
        }
        lastClearedCharArray = arr;
    }
    
    public static void clearCharMatrix(char[][] matrix) {
        if (matrix.length == 0) {
            return;
        }
        
        for (int i = 0; i < matrix.length; i++) {
            MemUtils.clearCharArray(matrix[i]);
        }
        Arrays.fill(matrix, null);
    }
    
    public static boolean tryClearString(String str) {
        if (str == null || str.isEmpty()) { // Prevents clearing interned ""
            return true;
        }
        
        Logger unsafeMethLog = unsafeLog.newChildFromCurMethIf(Level.DEBUG);
        unsafeMethLog.debug(() -> "str = " + StringUtils.quote(str));
        
        Exception exception = null;
        try {
            clearByteArray((byte[]) strValueF.get(str));
        } catch (
                ClassCastException | IllegalArgumentException | IllegalAccessException
                | SecurityException ex
        ) {
            exception = ex;
        }
        try {
            strHashF.setInt(str, 0);
        } catch (IllegalArgumentException | IllegalAccessException | SecurityException ex) {
            exception = ex;
        }
        
        if (exception != null) {
            log.newChildFromCurMeth().error(() -> "Failed to clear a String: ", exception);
            tryClearPrimitives(str, false);
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * 
     * @param obj
     * @param deeply true to recursively consider primitives of Object fields
     */
    public static boolean tryClearPrimitives(Object obj, boolean deeply) {
        boolean isSuccessful = true;
        Class<?> clazz = obj.getClass();
        while (clazz != null && !Object.class.equals(clazz)) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                
                try {
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (val == null) {
                        continue;
                    }
                    Class<? extends Object> valClass = val.getClass();
                    if (valClass == char[].class) {
                        clearCharArray((char[]) val);
                    } else if (valClass == byte[].class) {
                        clearByteArray((byte[]) val);
                    } else if (valClass == Integer.class) {
                        field.setInt(obj, 0);
                    } else if (valClass == Long.class) {
                        field.setLong(obj, 0L);
                    } else if (valClass == Byte.class) {
                        field.setByte(obj, (byte) 0);
                    } else if (valClass == Short.class) {
                        field.setShort(obj, (short) 0);
                    } else if (valClass == Float.class) {
                        field.setFloat(obj, 0f);
                    } else if (valClass == Double.class) {
                        field.setDouble(obj, 0d);
                    } else if (valClass == Character.class) {
                        field.setChar(obj, (char) 0);
                    } else if (valClass == Boolean.class) {
                        field.setBoolean(obj, false);
                    } else if (valClass == int[].class) {
                        Arrays.fill((int[]) val, 0);
                    } else if (valClass == long[].class) {
                        Arrays.fill((long[]) val, 0L);
                    } else if (valClass == short[].class) {
                        Arrays.fill((short[]) val, (short) 0);
                    } else if (valClass == float[].class) {
                        Arrays.fill((float[]) val, 0f);
                    } else if (valClass == double[].class) {
                        Arrays.fill((double[]) val, 0d);
                    } else if (valClass == boolean[].class) {
                        Arrays.fill((boolean[]) val, false);
                    } else if (valClass.isArray()) {
                        Object[] valAsArray = (Object[]) val;
                        for (int i = 0; i < valAsArray.length; i++) {
                            if (!tryClearPrimitives(valAsArray[i], deeply)) {
                                isSuccessful = false;
                                break;
                            }
                        }
                    } else if (deeply) {
                        isSuccessful = tryClearPrimitives(val, deeply) && isSuccessful;
                    }
                } catch (
                        IllegalArgumentException | IllegalAccessException | SecurityException
                        | ExceptionInInitializerError ex
                ) {
                    ex.printStackTrace();
                    isSuccessful = false;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return isSuccessful;
    }
    
    public static boolean tryDestroyPBKDF2Key(SecretKey key) {
        Class<? extends SecretKey> keyClass = key.getClass();
        if (!"com.sun.crypto.provider.PBKDF2KeyImpl".equals(keyClass.getCanonicalName())) {
            log.newChildFromCurMethIf(Level.WARN)
                    .warn(
                            () -> "Unexpected PBKDF2 SecretKey implemention: "
                                    + keyClass.getCanonicalName()
                    );
            return tryDestroyKey(key);
        }
        
        if (pbkdf2PasswdF == null) {
            pbkdf2PasswdF = ReflectionUtils.getField(keyClass, "passwd");
            pbkdf2SaltF = ReflectionUtils.getField(keyClass, "salt");
            pbkdf2KeyF = ReflectionUtils.getField(keyClass, "key");
        }
        
        Exception exception = null;
        
        try {
            clearCharArray((char[]) pbkdf2PasswdF.get(key));
        } catch (
                ClassCastException | NullPointerException | IllegalArgumentException
                | IllegalAccessException | SecurityException ex
        ) { // NullPointerException for clearCharArray(null)
            exception = ex;
        }
        try {
            clearByteArray((byte[]) pbkdf2SaltF.get(key));
        } catch (
                ClassCastException | NullPointerException | IllegalArgumentException
                | IllegalAccessException | SecurityException ex
        ) { // NullPointerException for clearByteArray(null)
            exception = ex;
        }
        try {
            clearByteArray((byte[]) pbkdf2KeyF.get(key));
        } catch (
                ClassCastException | NullPointerException | IllegalArgumentException
                | IllegalAccessException | SecurityException ex
        ) { // NullPointerException for clearByteArray(null)
            exception = ex;
        }
        
        if (exception != null || !MemUtils.isKeyDestroyed(key)) {
            log.newChildFromCurMeth()
                    .error(
                            () -> "Failed to destroy PBKDF2 SecretKey implementation for: "
                                    + StringUtils.getSafeObjName(key) + ".",
                            exception
                    );
            tryDestroyKey(key);
            return false;
        } else {
            return true;
        }
    }
    
    public static boolean tryDestroyKey(SecretKey key) {
        try {
            key.destroy();
            return true;
        } catch (DestroyFailedException destroyEx) {
            if (key instanceof SecretKeySpec) {
                try {
                    clearByteArray((byte[]) secretKeyF.get(key));
                } catch (
                        ClassCastException | IllegalArgumentException | IllegalAccessException
                        | SecurityException ex
                ) {
                    log.newChildFromCurMethIf(Level.WARN)
                            .warn(
                                    () -> "Unexpectedly failed to destroy SecretKeySpec: "
                                            + StringUtils.getSafeObjName(key),
                                    ex
                            );
                }
            }
            
            if (MemUtils.isKeyDestroyed(key)) {
                return true;
            }
            if (!tryClearPrimitives(key, false)) {
                log.newChildFromCurMeth()
                        .error(
                                () -> "Failed to destroy SecretKey: "
                                        + StringUtils.getSafeObjName(key),
                                destroyEx
                        );
            }
            
            return MemUtils.isKeyDestroyed(key);
        }
    }
    
    public static boolean isKeyDestroyed(SecretKey key) {
        if (key == null) {
            return true;
        }
        byte[] keyBytes = key.getEncoded(); // usually a clone
        boolean res = true;
        
        if (keyBytes.length > 0) {
            byte firstByte = keyBytes[0];
            for (int i = 1; i < keyBytes.length; i++) {
                if (keyBytes[i] != firstByte) {
                    res = false;
                    break;
                }
            }
        }
        
        clearByteArray(keyBytes);
        return res;
    }
    
    public static boolean tryDestroy(Destroyable obj) {
        try {
            unsafeLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(() -> "obj = " + StringUtils.getSafeObjName(obj));
            obj.destroy();
            return true;
        } catch (DestroyFailedException ex) {
            log.newChildFromCurMeth()
                    .error(() -> "Failed to destroy: " + StringUtils.getSafeObjName(obj), ex);
            return false;
        }
    }
    
    public static void clearHeap(int margin) {
        lastClearedCharArray = null;
        lastClearedIntArray = null;
        lastClearedFloatArray = null;
        
        Runtime runtime = Runtime.getRuntime();
        
        // Free unused memory
        runtime.runFinalization();
        runtime.gc();
        runtime.gc();
        runtime.runFinalization();
        runtime.gc();
        runtime.gc();
        runtime.runFinalization();
        Logger methLog = log.newChildFromCurMethIf(Level.DEBUG);
        methLog.debug(() -> "after GC: " + getMemDebug());
        
        int freeMem; // memory that the JVM has eventually used
        try {
            freeMem = Math.toIntExact(runtime.freeMemory());
        } catch (ArithmeticException overflowEx) {
            freeMem = Integer.MAX_VALUE; // accept not clearing all memory when it is too big.
        }
        
        int marginCoef = 1;
        int eraserSize = freeMem - margin;
        
        byte[] eraser = null;
        while (eraserSize > 100) {
            try {
                eraser = new byte[eraserSize];
                final int feraserLen = eraser.length;
                methLog.debug(() -> "cleared " + formatBytesNum(feraserLen));
                break;
            } catch (OutOfMemoryError memErr) {
                final int feraserSize = eraserSize;
                methLog.warn(() -> "OutOfMemory with eraserSize = " + feraserSize);
                if (marginCoef < 3) {
                    eraserSize -= margin;
                    marginCoef++;
                } else {
                    eraserSize >>= 1;
                }
            }
        }
        
        if (eraser == null) {
            throw new RuntimeException("The heap memory could not be cleared.");
        }
        
        // Prevent compiler optimization
        if (eraser.length > 2) {
            eraser[1] = eraser[0];
        }
        if (
            eraser.length != eraserSize
                    || (freeMem > 8
                            && eraser[7] + eraser[8] + eraser[eraser.length - 1] != (byte) 0)
                    || (lastClearedByteArray != null
                            && lastClearedByteArray.length > 0
                            && (lastClearedByteArray[0] & eraser[0]) < 0)
        ) {
            throw new RuntimeException("Unexpected memory clearing issue.");
        }
        
        lastClearedByteArray = null;
    }
    
    public static String getMemDebug() {
        Runtime runtime = Runtime.getRuntime();
        long heapSize = runtime.totalMemory();
        long heapMaxSize = runtime.maxMemory();
        long heapFreeSize = runtime.freeMemory();
        return "heap free size: " + formatBytesNum(heapFreeSize) + " / " + formatBytesNum(heapSize)
                + " (heap max size = " + formatBytesNum(heapMaxSize) + ")";
    }
    
    private static String formatBytesNum(long num) {
        return String.format(Locale.ENGLISH, "%,d", num).replace(",", " ");
    }
    
}
