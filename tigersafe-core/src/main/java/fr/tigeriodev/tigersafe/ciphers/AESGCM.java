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

package fr.tigeriodev.tigersafe.ciphers;

import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.spec.GCMParameterSpec;

public final class AESGCM extends JavaCipherImpl {
    
    public static final int GCM_TAG_SIZE_BITS = 128;
    public static final int GCM_TAG_SIZE = GCM_TAG_SIZE_BITS / Byte.SIZE;
    
    AESGCM() {
        super(
                "AES",
                "AES/GCM/NoPadding",
                32 /* 256 bits */,
                12 /* 96 bits cf. https://crypto.stackexchange.com/questions/41601/aes-gcm-recommended-iv-size-why-12-bytes */
        );
    }
    
    @Override
    public AlgorithmParameterSpec newParameterSpec(byte[] iv) {
        return new GCMParameterSpec(GCM_TAG_SIZE_BITS, iv);
    }
    
    @Override
    public int getEncryptedLen(int plainLen) {
        return plainLen + GCM_TAG_SIZE;
    }
    
}
