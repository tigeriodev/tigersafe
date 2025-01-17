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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKey;

import fr.tigeriodev.tigersafe.utils.RandomUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public interface CipherImpl {
    
    SecretKey newKey() throws NoSuchAlgorithmException;
    
    /**
     * @return size in bytes
     */
    int getKeySize();
    
    byte[] keyToBytes(SecretKey key);
    
    SecretKey bytesToKey(byte[] keyBytes);
    
    /**
     * @return size in bytes
     */
    int getIvSize();
    
    byte[] newIv() throws NoSuchAlgorithmException;
    
    /**
     * @return size in bytes
     */
    int getDerivationSaltSize();
    
    byte[] newDerivationSalt() throws NoSuchAlgorithmException;
    
    SecretKey getDerivatedKeyFrom(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException;
    
    byte[] encryptBytes(byte[] plainBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException;
    
    byte[] decryptBytes(byte[] encryptedBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException;
    
    OutputStream newEncryptionStream(SecretKey key, byte[] iv, OutputStream out)
            throws GeneralSecurityException;
    
    InputStream newDecryptionStream(InputStream in, SecretKey key, byte[] iv)
            throws GeneralSecurityException;
    
    int getEncryptedLen(int plainLen);
    
    public static void checkWorking(CipherImpl cipherImpl) throws Exception {
        final String commonChars =
                "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        byte[][] testsBytes = new byte[][] {
                "abcdef123456abcdef".getBytes(StandardCharsets.UTF_8),
                RandomUtils.newRandomBytes(5, 30)
        };
        
        SecretKey key1 = cipherImpl.newKey();
        char[] pw1Chars = RandomUtils.newRandomChars(10, 30, commonChars);
        byte[] derivSalt1 = cipherImpl.newDerivationSalt();
        SecretKey derivKey1 = cipherImpl.getDerivatedKeyFrom(pw1Chars, derivSalt1);
        
        byte[] iv1 = cipherImpl.newIv();
        
        SecretKey[] keys = new SecretKey[] {
                key1, derivKey1
        };
        byte[][] testsEncryptedBytes = new byte[keys.length * testsBytes.length][];
        
        int testInd = 0;
        for (SecretKey key : keys) {
            for (byte[] testBytes : testsBytes) {
                byte[] encryptedBytes = cipherImpl.encryptBytes(testBytes, key, iv1);
                byte[] decryptedBytes = cipherImpl.decryptBytes(encryptedBytes, key, iv1);
                
                if (encryptedBytes.length != cipherImpl.getEncryptedLen(testBytes.length)) {
                    throw new GeneralSecurityException(
                            "testBytes " + StringUtils.bytesToStr(testBytes)
                                    + " has unexpected encrypted length: " + encryptedBytes.length
                    );
                }
                
                if (Arrays.equals(testBytes, encryptedBytes)) {
                    throw new GeneralSecurityException(
                            "testBytes " + StringUtils.bytesToStr(testBytes)
                                    + " not encrypted: \n key = "
                                    + StringUtils.bytesToStr(key.getEncoded()) + "\n iv = "
                                    + StringUtils.bytesToStr(iv1) + "\n encrypted = "
                                    + StringUtils.bytesToStr(encryptedBytes) + "."
                    );
                }
                
                if (!Arrays.equals(testBytes, decryptedBytes)) {
                    throw new GeneralSecurityException(
                            "testBytes " + StringUtils.bytesToStr(testBytes)
                                    + " not correctly encrypted/decrypted: \n key = "
                                    + StringUtils.bytesToStr(key.getEncoded()) + "\n iv = "
                                    + StringUtils.bytesToStr(iv1) + "\n encrypted = "
                                    + StringUtils.bytesToStr(encryptedBytes) + "\n decrypted = "
                                    + StringUtils.bytesToStr(decryptedBytes) + "."
                    );
                }
                
                testsEncryptedBytes[testInd++] = encryptedBytes;
            }
        }
        
        for (int i = 0; i < testsEncryptedBytes.length; i++) {
            for (int j = 0; j < testsEncryptedBytes.length; j++) {
                if (j == i) {
                    continue;
                }
                if (Arrays.equals(testsEncryptedBytes[i], testsEncryptedBytes[j])) {
                    throw new GeneralSecurityException(
                            "Encryption is independent of key and/or plain bytes."
                    );
                }
            }
        }
        
        SecretKey key2 = cipherImpl.newKey();
        if (Arrays.equals(key1.getEncoded(), key2.getEncoded())) {
            throw new GeneralSecurityException("Keys are not random.");
        }
        
        byte[] iv2 = cipherImpl.newIv();
        if (Arrays.equals(iv1, iv2)) {
            throw new GeneralSecurityException("IVs are not random.");
        }
        
        byte[] derivSalt2 = cipherImpl.newDerivationSalt();
        if (Arrays.equals(derivSalt1, derivSalt2)) {
            throw new GeneralSecurityException("Derivation salts are not random.");
        }
        
        SecretKey derivKey2 = cipherImpl.getDerivatedKeyFrom(pw1Chars, derivSalt2);
        if (Arrays.equals(derivKey1.getEncoded(), derivKey2.getEncoded())) {
            throw new GeneralSecurityException("Derivated keys are independent of salt.");
        }
        
        char[] pw2Chars = pw1Chars.clone();
        int midInd = pw2Chars.length >>> 1;
        pw2Chars[midInd] = (char) (pw2Chars[midInd] + 1);
        SecretKey derivKey3 = cipherImpl.getDerivatedKeyFrom(pw2Chars, derivSalt1);
        if (Arrays.equals(derivKey1.getEncoded(), derivKey3.getEncoded())) {
            throw new GeneralSecurityException("Derivated keys are independent of password.");
        }
        
        for (SecretKey key : new SecretKey[] {
                key1, key2
        }) {
            byte[] keyBytes = key.getEncoded();
            if (keyBytes.length != cipherImpl.getKeySize()) {
                throw new GeneralSecurityException(
                        "key " + StringUtils.bytesToStr(keyBytes) + " has unexpected length: "
                                + keyBytes.length + "."
                );
            }
        }
        
        for (SecretKey derivKey : new SecretKey[] {
                derivKey1, derivKey2, derivKey3
        }) {
            byte[] keyBytes = derivKey.getEncoded();
            if (keyBytes.length != cipherImpl.getKeySize()) {
                throw new GeneralSecurityException(
                        "derivKey " + StringUtils.bytesToStr(keyBytes) + " has unexpected length: "
                                + keyBytes.length + "."
                );
            }
        }
        
        for (byte[] derivSalt : new byte[][] {
                derivSalt1, derivSalt2
        }) {
            if (derivSalt.length != cipherImpl.getDerivationSaltSize()) {
                throw new GeneralSecurityException(
                        "derivSalt " + StringUtils.bytesToStr(derivSalt)
                                + " has unexpected length: " + derivSalt.length + "."
                );
            }
        }
        
        for (byte[] iv : new byte[][] {
                iv1, iv2
        }) {
            if (iv.length != cipherImpl.getIvSize()) {
                throw new GeneralSecurityException(
                        "iv " + StringUtils.bytesToStr(iv) + " has unexpected length: " + iv.length
                                + "."
                );
            }
        }
        
        // Stream tests
        
        byte[][][] streamTestsBytes = new byte[][][] {
                testsBytes, new byte[][] {
                        "456789hijklm456789".getBytes(StandardCharsets.UTF_8),
                        RandomUtils.newRandomBytes(5, 30)
                }
        };
        byte[][] streamTestsEncryptedBytes = new byte[keys.length * streamTestsBytes.length][];
        int streamTestInd = 0;
        for (SecretKey key : keys) {
            for (byte[][] streamTestBytesArr : streamTestsBytes) {
                ByteArrayOutputStream encryptedBytesOut = new ByteArrayOutputStream();
                try (
                        OutputStream cipherOut =
                                cipherImpl.newEncryptionStream(key, iv1, encryptedBytesOut)
                ) {
                    for (byte[] testBytes : streamTestBytesArr) {
                        cipherOut.write(testBytes);
                    }
                }
                
                byte[] encryptedBytes = encryptedBytesOut.toByteArray();
                int totalTestBytesLen = 0;
                for (byte[] testBytes : streamTestBytesArr) {
                    totalTestBytesLen += testBytes.length;
                }
                if (encryptedBytes.length != cipherImpl.getEncryptedLen(totalTestBytesLen)) {
                    throw new GeneralSecurityException(
                            "streamTestBytesArr (" + totalTestBytesLen
                                    + " bytes in total) have unexpected encrypted length: "
                                    + encryptedBytes.length
                    );
                }
                
                ByteArrayInputStream encryptedBytesIn = new ByteArrayInputStream(encryptedBytes);
                try (
                        InputStream cipherIn =
                                cipherImpl.newDecryptionStream(encryptedBytesIn, key, iv1)
                ) {
                    for (byte[] testBytes : streamTestBytesArr) {
                        byte[] decryptedTestBytes = new byte[testBytes.length];
                        cipherIn.read(decryptedTestBytes);
                        if (!Arrays.equals(testBytes, decryptedTestBytes)) {
                            throw new GeneralSecurityException(
                                    "testBytes " + StringUtils.bytesToStr(
                                            testBytes
                                    ) + " not correctly encrypted/decrypted in stream: \n key = "
                                            + StringUtils.bytesToStr(key.getEncoded()) + "\n iv = "
                                            + StringUtils.bytesToStr(iv1) + "\n encrypted stream = "
                                            + StringUtils.bytesToStr(encryptedBytes)
                                            + "\n decrypted test = "
                                            + StringUtils.bytesToStr(decryptedTestBytes) + "."
                            );
                        }
                    }
                }
                
                for (byte[] testBytes : streamTestBytesArr) {
                    for (int i = 0; i < encryptedBytes.length - testBytes.length + 1; i++) {
                        for (int j = 0; j < testBytes.length; j++) {
                            if (encryptedBytes[i + j] != testBytes[j]) {
                                break;
                            }
                            if (j == testBytes.length - 1) {
                                throw new GeneralSecurityException(
                                        "testBytes " + StringUtils.bytesToStr(testBytes)
                                                + " not encrypted in stream: \n key = "
                                                + StringUtils.bytesToStr(key.getEncoded())
                                                + "\n iv = " + StringUtils.bytesToStr(iv1)
                                                + "\n encrypted stream = "
                                                + StringUtils.bytesToStr(encryptedBytes)
                                                + "\n testBytes found at " + i + "."
                                );
                            }
                        }
                    }
                }
                
                streamTestsEncryptedBytes[streamTestInd++] = encryptedBytes;
            }
        }
        
        for (int i = 0; i < streamTestsEncryptedBytes.length; i++) {
            for (int j = 0; j < streamTestsEncryptedBytes.length; j++) {
                if (j == i) {
                    continue;
                }
                if (Arrays.equals(streamTestsEncryptedBytes[i], streamTestsEncryptedBytes[j])) {
                    throw new GeneralSecurityException(
                            "Stream encryption is independent of key and/or plain bytes."
                    );
                }
            }
        }
    }
    
}
