/*
 * Copyright (c) 1994, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)
 * 
 * This file contains portions of code from OpenJDK, licensed under the
 * terms of the GNU General Public License version 2 only and copyrighted
 * by Oracle and/or its affiliates, modified by tigeriodev on 16/01/2025.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * DISCLAIMER
 * 
 * This software is not affiliated with, endorsed by, or approved by
 * the following entities: Oracle and/or its affiliates, OpenJDK, or any
 * other entity.
 * Any references to these entities are for informational purposes only
 * and do not imply any association, sponsorship, or approval.
 */

package fr.tigeriodev.tigersafe.utils;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

/**
 * This class contains code from OpenJDK, slightly modified in order to
 * be able to write and read bytes in a safer way, without impacting
 * performance.
 */
public class DestroyableByteArrayOutputStream extends ByteArrayOutputStream implements Destroyable {
    
    /**
     * Taken from {@link ByteArrayOutputStream}.
     * 
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    
    /**
     * Taken from {@link ByteArrayOutputStream#write(byte[], int, int)} and slightly modified by tigeriodev.
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        ensureSize(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }
    
    /**
     * Taken from {@link ByteArrayOutputStream#write(int)} and slightly modified by tigeriodev.
     */
    @Override
    public synchronized void write(int b) {
        ensureSize(count + 1);
        buf[count++] = (byte) b;
    }
    
    /**
     * Inspired by {@link ByteArrayOutputStream#ensureCapacity(int)}
     */
    private void ensureSize(int minSize) {
        if (minSize - buf.length > 0) {
            int initSize = buf.length;
            int newSize = initSize << 1;
            if (newSize - minSize < 0) {
                newSize = minSize;
            }
            if (newSize - MAX_ARRAY_SIZE > 0) {
                if (minSize < 0) { // overflow
                    throw new OutOfMemoryError();
                }
                newSize = minSize > MAX_ARRAY_SIZE ? minSize : MAX_ARRAY_SIZE;
            }
            
            byte[] initBuf = buf;
            buf = Arrays.copyOf(initBuf, newSize);
            MemUtils.clearByteArray(initBuf);
        }
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        MemUtils.clearByteArray(buf);
        count = 0;
    }
    
    @Override
    public boolean isDestroyed() {
        return buf.length == 0 && count == 0;
    }
    
}
