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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;

public final class CiphersManager {
    
    private static final Logger log = Logs.newLogger(CiphersManager.class);
    private static final Map<String, Cipher> CIPHERS_BY_NAME = new HashMap<>();
    private static final Set<String> CIPHERS_NAME;
    private static final Set<Cipher> CIPHERS;
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Set<String> pendingWorkingCheckCiphersName = new HashSet<>();
    
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
    
    public static void waitWorkingCheck(String cipherName) {
        if (!pendingWorkingCheckCiphersName.contains(cipherName)) {
            return;
        }
        Logger methLog = log.newChildFromCurMethIf(Level.DEBUG);
        synchronized (pendingWorkingCheckCiphersName) {
            try {
                while (pendingWorkingCheckCiphersName.contains(cipherName)) {
                    methLog.debug(() -> cipherName + " cipher is pending check, start waiting...");
                    pendingWorkingCheckCiphersName.wait();
                    methLog.debug(() -> "wait end for " + cipherName + " cipher");
                }
            } catch (InterruptedException ex) {
                methLog.debug(() -> "interruption for " + cipherName + " cipher", ex);
            }
        }
    }
    
    public static void waitAllWorkingChecks() {
        if (pendingWorkingCheckCiphersName.isEmpty()) {
            return;
        }
        Logger methLog = log.newChildFromCurMethIf(Level.DEBUG);
        synchronized (pendingWorkingCheckCiphersName) {
            try {
                while (!pendingWorkingCheckCiphersName.isEmpty()) {
                    methLog.debug(() -> "there is a cipher pending check, start waiting...");
                    pendingWorkingCheckCiphersName.wait();
                    methLog.debug(() -> "wait end");
                }
            } catch (InterruptedException ex) {
                methLog.debug(() -> "interruption", ex);
            }
        }
    }
    
    public static CompletableFuture<Boolean> checkWorkingAsync(CipherImpl cipherImpl,
            String cipherName) {
        log.debug(() -> "Start checking " + cipherName + " cipher...");
        pendingWorkingCheckCiphersName.add(cipherName);
        return CompletableFuture.supplyAsync(() -> {
            try {
                CipherImpl.checkWorking(cipherImpl);
                log.debug(() -> cipherName + " cipher is working");
                return true;
            } catch (Exception ex) {
                log.warn(() -> cipherName + " cipher is not working: ", ex);
                return false;
            } finally {
                pendingWorkingCheckCiphersName.remove(cipherName);
                synchronized (pendingWorkingCheckCiphersName) {
                    pendingWorkingCheckCiphersName.notifyAll();
                }
            }
        }, executor);
    }
    
    public static void stop() {
        log.debug(() -> "stopped");
        List<Runnable> notScheduled = executor.shutdownNow();
        if (!notScheduled.isEmpty()) {
            log.warn(() -> notScheduled.size() + " not scheduled tasks because of shutdown");
        }
    }
    
}
