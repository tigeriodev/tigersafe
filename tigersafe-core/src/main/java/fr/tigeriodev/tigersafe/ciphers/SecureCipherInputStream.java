/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package fr.tigeriodev.tigersafe.ciphers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;

/**
 * This class is inspired by {@link CipherInputStream} from OpenJDK,
 * made to have a safer and more optimized way of reading encrypted streams.
 */
public class SecureCipherInputStream extends InputStream {
    
    private final InputStream in;
    private final javax.crypto.Cipher cipher;
    
    private byte[] buf;
    private int availStartInd = 0;
    private int availEndExclInd = 0;
    
    private boolean hasReachedEnd = false;
    private boolean isClosed = false;
    
    public SecureCipherInputStream(InputStream in, javax.crypto.Cipher cipher) {
        this(in, cipher, 512);
    }
    
    public SecureCipherInputStream(InputStream in, javax.crypto.Cipher cipher, int initBufSize) {
        this.in = CheckUtils.notNull(in);
        this.cipher = CheckUtils.notNull(cipher);
        this.buf = new byte[initBufSize];
    }
    
    /**
     * Must be called before any cipher update/doFinal call.
     * NB: This method is needed because of AEAD ciphers which do not decrypt any byte before reaching the end (for the authentication tag).
     * @param nextEncryptedLen the length of the next encrypted portion provided to the cipher (with update/doFinal).
     */
    private void prepareBuf(int nextEncryptedLen) {
        int minBufSize = cipher.getOutputSize(nextEncryptedLen);
        if (buf.length < minBufSize) {
            byte[] initBuf = buf;
            buf = Arrays.copyOf(buf, minBufSize);
            MemUtils.clearByteArray(initBuf);
        }
        availStartInd = 0;
        availEndExclInd = 0;
    }
    
    /**
     * Ensures that at least 1 byte is available for reading if the end of the stream has not been reached.
     * This method blocks until input data is available, the end of the stream is detected, or an exception is thrown.
     * @throws IOException
     */
    private void readMoreIfNoneAvailable() throws IOException {
        if (hasReachedEnd) {
            return;
        }
        if (availStartInd < availEndExclInd) {
            return;
        }
        do {
            int readNum = in.read(buf);
            
            if (readNum > 0) {
                prepareBuf(readNum);
                try {
                    availEndExclInd = cipher.update(buf, 0, readNum, buf, availStartInd); // for AEAD, availEndExclInd = 0 until end of stream reached
                } catch (IllegalStateException ex) {
                    throw ex;
                } catch (ShortBufferException ex) {
                    throw new IOException(ex);
                }
            } else if (readNum == -1) {
                hasReachedEnd = true;
                prepareBuf(0);
                try {
                    availEndExclInd = cipher.doFinal(buf, 0);
                    return;
                } catch (
                        IllegalBlockSizeException | BadPaddingException | ShortBufferException ex
                ) {
                    throw new IOException(ex);
                }
            }
        } while (availEndExclInd == 0);
    }
    
    @Override
    public int read() throws IOException {
        readMoreIfNoneAvailable();
        if (available() <= 0) {
            return -1;
        }
        int res = buf[availStartInd++] & 0xff;
        destroyIfFullyRead();
        return res;
    };
    
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        readMoreIfNoneAvailable();
        int availNum = available();
        if (availNum <= 0) {
            return -1;
        }
        
        if (len < availNum) {
            availNum = len;
        }
        System.arraycopy(buf, availStartInd, b, off, availNum);
        availStartInd += availNum;
        destroyIfFullyRead();
        return availNum;
    }
    
    @Override
    public long skip(long n) throws IOException {
        // This method is not optimized because its specification is too uncertain and should therefore not be used.
        if (n <= 0) {
            return 0;
        }
        long availNum = available();
        if (availNum <= 0L) {
            return 0;
        }
        if (n < availNum) {
            availNum = n;
        }
        availStartInd += availNum;
        destroyIfFullyRead();
        return availNum;
    }
    
    @Override
    public int available() throws IOException {
        return availEndExclInd - availStartInd;
    }
    
    @Override
    public boolean markSupported() {
        return false;
    }
    
    @Override
    public void close() throws IOException {
        if (isClosed) {
            return;
        }
        try {
            isClosed = true;
            in.close();
            
            if (!hasReachedEnd) {
                prepareBuf(0);
                try {
                    cipher.doFinal(buf, 0);
                } catch (
                        BadPaddingException | IllegalBlockSizeException | ShortBufferException ex
                ) {
                    // The stream has not been and will not be fully read, exceptions are pointless
                }
            }
        } finally {
            destroy();
        }
    }
    
    private void destroyIfFullyRead() {
        if (hasReachedEnd && availEndExclInd <= availStartInd) {
            destroy();
        }
    }
    
    public void destroy() {
        MemUtils.clearByteArray(buf);
        buf = new byte[0];
        availStartInd = 0;
        availEndExclInd = 0;
    }
    
}
