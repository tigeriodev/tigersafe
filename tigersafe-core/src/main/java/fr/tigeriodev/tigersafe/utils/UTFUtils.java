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
 * the following entities: Oracle and/or its affiliates, OpenJDK,
 * Arthur van Hoff, or any other entity.
 * Any references to these entities are for informational purposes only
 * and do not imply any association, sponsorship, or approval.
 */

package fr.tigeriodev.tigersafe.utils;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.Arrays;

/**
 * This class contains code from OpenJDK, slightly modified in order to
 * be able to write and read chars in a safer way, without impacting
 * performance.
 */
public final class UTFUtils {
    
    private static byte[] bytesBuf = null;
    private static char[] charsBuf = null;
    
    private UTFUtils() {}
    
    /**
     * Taken from {@link DataOutputStream#writeUTF(String, DataOutput)} and slightly modified by tigeriodev.
     * @param chars
     * @return
     * @throws UTFDataFormatException
     */
    public static int getUTFLen(char[] chars) throws UTFDataFormatException {
        final int len = chars.length;
        int utflen = len; // optimized for ASCII
        
        for (int i = 0; i < len; i++) {
            int c = chars[i];
            if (c >= 0x80 || c == 0)
                utflen += (c >= 0x800) ? 2 : 1;
        }
        
        if (utflen > 65535 || /* overflow */ utflen < len)
            throw new UTFDataFormatException("Too long chars: " + len + ".");
        
        return utflen;
    }
    
    /**
     * Taken from {@link DataOutputStream#writeUTF(String, DataOutput)} and slightly modified by tigeriodev.
     * @param chars
     * @param utflen of chars (unchecked)
     * @param dataOut
     * @throws IOException
     */
    public static void writeChars(char[] chars, int utflen, DataOutput dataOut) throws IOException {
        if (bytesBuf == null || bytesBuf.length < utflen) {
            if (bytesBuf != null) {
                MemUtils.clearByteArray(bytesBuf);
            }
            bytesBuf = new byte[Math.min(utflen << 1, 65535)];
        }
        
        int bytesNum = 0;
        int i = 0;
        for (i = 0; i < chars.length; i++) { // optimized for initial run of ASCII
            int c = chars[i];
            if (c >= 0x80 || c == 0)
                break;
            bytesBuf[bytesNum++] = (byte) c;
        }
        
        for (; i < chars.length; i++) {
            int c = chars[i];
            if (c < 0x80 && c != 0) {
                bytesBuf[bytesNum++] = (byte) c;
            } else if (c >= 0x800) {
                bytesBuf[bytesNum++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytesBuf[bytesNum++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytesBuf[bytesNum++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            } else {
                bytesBuf[bytesNum++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytesBuf[bytesNum++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
        }
        
        if (bytesNum != utflen) {
            throw new IllegalStateException("bytesNum " + bytesNum + " != utflen " + utflen + ".");
        }
        dataOut.write(bytesBuf, 0, bytesNum);
    }
    
    /**
     * Taken from {@link DataInputStream#readUTF(DataInput)} and slightly modified by tigeriodev.
     * @param utflen of chars to read
     * @param dataIn
     * @return
     * @throws IOException
     * @author Arthur van Hoff
     * @author tigeriodev
     */
    public static char[] readChars(int utflen, DataInput dataIn) throws IOException {
        if (bytesBuf == null || bytesBuf.length < utflen) {
            if (bytesBuf != null) {
                MemUtils.clearByteArray(bytesBuf);
            }
            bytesBuf = new byte[Math.min(utflen << 1, 65535)];
        }
        if (charsBuf == null || charsBuf.length < utflen) {
            if (charsBuf != null) {
                MemUtils.clearCharArray(charsBuf);
            }
            charsBuf = new char[Math.min(utflen << 1, 65535)];
        }
        
        int c, char2, char3;
        int count = 0;
        int charsNum = 0;
        
        dataIn.readFully(bytesBuf, 0, utflen);
        
        while (count < utflen) {
            c = (int) bytesBuf[count] & 0xff;
            if (c > 127)
                break;
            count++;
            charsBuf[charsNum++] = (char) c;
        }
        
        while (count < utflen) {
            c = (int) bytesBuf[count] & 0xff;
            switch (c >> 4) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> {
                    /* 0xxxxxxx*/
                    count++;
                    charsBuf[charsNum++] = (char) c;
                }
                case 12, 13 -> {
                    /* 110x xxxx   10xx xxxx*/
                    count += 2;
                    if (count > utflen)
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end"
                        );
                    char2 = (int) bytesBuf[count - 1];
                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException("malformed input around byte " + count);
                    charsBuf[charsNum++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                }
                case 14 -> {
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    if (count > utflen)
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end"
                        );
                    char2 = (int) bytesBuf[count - 2];
                    char3 = (int) bytesBuf[count - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                        throw new UTFDataFormatException(
                                "malformed input around byte " + (count - 1)
                        );
                    charsBuf[charsNum++] = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6)
                            | ((char3 & 0x3F) << 0));
                }
                default ->
                        /* 10xx xxxx,  1111 xxxx */
                        throw new UTFDataFormatException("malformed input around byte " + count);
            }
        }
        
        return Arrays.copyOf(charsBuf, charsNum); // copy because charsUTFCharsBuf can be cleared
    }
    
    public static void clearBuffers() {
        if (bytesBuf != null) {
            MemUtils.clearByteArray(bytesBuf);
            bytesBuf = null;
        }
        if (charsBuf != null) {
            MemUtils.clearCharArray(charsBuf);
            charsBuf = null;
        }
    }
    
}
