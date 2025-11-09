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

package fr.tigeriodev.tigersafe.tests.ciphers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CipherImpl;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.ciphers.NotWorkingCipherException;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.tests.TestClass;
import fr.tigeriodev.tigersafe.tests.logs.LogMessagePattern;
import fr.tigeriodev.tigersafe.tests.logs.TestsLogs;

public class CiphersManagerTest extends TestClass {
    
    @Test
    void testAllCiphers() throws InterruptedException {
        Set<Cipher> ciphers = CiphersManager.getCiphers();
        assertEquals(4, ciphers.size());
        
        for (Cipher cipher : ciphers) {
            assertTrue(cipher == CiphersManager.getCipherByName(cipher.getName()));
            cipher.checkWorkingAsync();
        }
        
        testLog.debug(() -> "Start wait...");
        CiphersManager.waitAllWorkingChecks();
        testLog.debug(() -> "End wait...");
        
        for (Cipher cipher : ciphers) {
            assertTrue(cipher.isWorking());
            assertTrue(cipher.isChecked());
            assertTrue(cipher.isWorkingOrUnchecked());
        }
    }
    
    @Test
    void testNotWorkingCipher()
            throws NoSuchAlgorithmException, GeneralSecurityException, InterruptedException {
        Cipher notWorkingCipher = new Cipher("notWorkingCipher", new CipherImpl() {
            
            @Override
            public SecretKey newKey() throws NoSuchAlgorithmException {
                throw new UnsupportedOperationException("Unimplemented method 'newKey'");
            }
            
            @Override
            public int getKeySize() {
                throw new UnsupportedOperationException("Unimplemented method 'getKeySize'");
            }
            
            @Override
            public byte[] keyToBytes(SecretKey key) {
                throw new UnsupportedOperationException("Unimplemented method 'keyToBytes'");
            }
            
            @Override
            public SecretKey bytesToKey(byte[] keyBytes) {
                throw new UnsupportedOperationException("Unimplemented method 'bytesToKey'");
            }
            
            @Override
            public int getIvSize() {
                throw new UnsupportedOperationException("Unimplemented method 'getIvSize'");
            }
            
            @Override
            public byte[] newIv() throws NoSuchAlgorithmException {
                throw new UnsupportedOperationException("Unimplemented method 'newIv'");
            }
            
            @Override
            public int getDerivationSaltSize() {
                throw new UnsupportedOperationException(
                        "Unimplemented method 'getDerivationSaltSize'"
                );
            }
            
            @Override
            public byte[] newDerivationSalt() throws NoSuchAlgorithmException {
                throw new UnsupportedOperationException("Unimplemented method 'newDerivationSalt'");
            }
            
            @Override
            public SecretKey getDerivatedKeyFrom(char[] password, byte[] salt)
                    throws NoSuchAlgorithmException, InvalidKeySpecException {
                throw new UnsupportedOperationException(
                        "Unimplemented method 'getDerivatedKeyFrom'"
                );
            }
            
            @Override
            public byte[] encryptBytes(byte[] plainBytes, SecretKey key, byte[] iv)
                    throws GeneralSecurityException {
                throw new UnsupportedOperationException("Unimplemented method 'encryptBytes'");
            }
            
            @Override
            public byte[] decryptBytes(byte[] encryptedBytes, SecretKey key, byte[] iv)
                    throws GeneralSecurityException {
                throw new UnsupportedOperationException("Unimplemented method 'decryptBytes'");
            }
            
            @Override
            public OutputStream newEncryptionStream(SecretKey key, byte[] iv, OutputStream out)
                    throws GeneralSecurityException {
                throw new UnsupportedOperationException(
                        "Unimplemented method 'newEncryptionStream'"
                );
            }
            
            @Override
            public InputStream newDecryptionStream(InputStream in, SecretKey key, byte[] iv)
                    throws GeneralSecurityException {
                throw new UnsupportedOperationException(
                        "Unimplemented method 'newDecryptionStream'"
                );
            }
            
            @Override
            public int getEncryptedLen(int plainLen) {
                throw new UnsupportedOperationException("Unimplemented method 'getEncryptedLen'");
            }
            
        });
        
        assertFalse(notWorkingCipher.isChecked());
        assertFalse(notWorkingCipher.isWorking());
        assertTrue(notWorkingCipher.isWorkingOrUnchecked());
        
        byte[] testBytes = new byte[3];
        assertThrows(NotWorkingCipherException.class, () -> {
            notWorkingCipher
                    .encryptBytes(testBytes, notWorkingCipher.newKey(), notWorkingCipher.newIv());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            notWorkingCipher.decryptBytes(testBytes, null, null);
        });
        
        TestsLogs.runMuting(
                new LogMessagePattern(
                        Cipher.class,
                        Level.WARN,
                        "notWorkingCipher cipher is not working"
                ),
                () -> {
                    notWorkingCipher.checkWorkingAsync();
                    
                    testLog.debug(() -> "Start wait...");
                    notWorkingCipher.waitWorkingCheck();
                    testLog.debug(() -> "End wait...");
                }
        );
        
        assertTrue(notWorkingCipher.isChecked());
        assertFalse(notWorkingCipher.isWorking());
        assertFalse(notWorkingCipher.isWorkingOrUnchecked());
        
        assertThrows(NotWorkingCipherException.class, () -> {
            notWorkingCipher
                    .encryptBytes(testBytes, notWorkingCipher.newKey(), notWorkingCipher.newIv());
        });
        assertThrows(NotWorkingCipherException.class, () -> {
            notWorkingCipher.decryptBytes(testBytes, null, null);
        });
    }
    
}
