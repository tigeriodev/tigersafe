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
import java.util.Arrays;

import javax.crypto.spec.ChaCha20ParameterSpec;

public final class ChaCha20 extends JavaCipherImpl {
    
    ChaCha20() {
        super(
                "ChaCha20",
                "ChaCha20",
                32 /* 256 bits */,
                16 /* 96 (nonce) + 32 (counter) = 128 bits cf. https://datatracker.ietf.org/doc/html/rfc7539#section-2.3 */
        );
    }
    
    @Override
    public AlgorithmParameterSpec newParameterSpec(byte[] iv) {
        byte[] nonce = Arrays.copyOf(iv, 12);
        int counter = ((iv[12] & 0xff) << 24)
                + ((iv[13] & 0xff) << 16)
                + ((iv[14] & 0xff) << 8)
                + ((iv[15] & 0xff) << 0);
        return new ChaCha20ParameterSpec(nonce, counter);
    }
    
    @Override
    public int getEncryptedLen(int plainLen) {
        return plainLen;
    }
    
}
