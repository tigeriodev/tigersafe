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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CiphersManager {
    
    private static final Map<String, Cipher> CIPHERS_BY_NAME = new HashMap<>();
    private static final Set<String> CIPHERS_NAME;
    private static final Set<Cipher> CIPHERS;
    
    static {
        addCipher(new Cipher("AES_CTR", new AESCTR()));
        addCipher(new Cipher("AES_GCM", new AESGCM()));
        addCipher(new Cipher("ChaCha20", new ChaCha20()));
        addCipher(new Cipher("ChaCha20-Poly1305", new ChaCha20Poly1305()));
        
        CIPHERS_NAME = Collections.unmodifiableSet(new HashSet<>(CIPHERS_BY_NAME.keySet()));
        CIPHERS = Collections.unmodifiableSet(new HashSet<>(CIPHERS_BY_NAME.values()));
        if (CIPHERS_NAME.size() != CIPHERS.size()) {
            throw new IllegalStateException(
                    "Ciphers are not unique: " + CIPHERS_NAME.size() + " unique names, "
                            + CIPHERS.size() + " unique implementations."
            );
        }
    }
    
    private CiphersManager() {}
    
    private static void addCipher(Cipher cipher) {
        if (CIPHERS_BY_NAME.put(cipher.name, cipher) != null) {
            throw new IllegalStateException("Cipher already added: " + cipher + ".");
        }
    }
    
    public static Cipher getCipherByName(String name) {
        Cipher res = CIPHERS_BY_NAME.get(name);
        if (res == null) {
            throw new IllegalArgumentException("Invalid cipher name: " + name + ".");
        }
        return res;
    }
    
    public static Set<String> getCiphersName() {
        return CIPHERS_NAME;
    }
    
    public static Set<Cipher> getCiphers() {
        return CIPHERS;
    }
    
    public static void waitAllWorkingChecks() {
        for (Cipher cipher : getCiphers()) {
            cipher.waitWorkingCheck();
        }
    }
    
}
