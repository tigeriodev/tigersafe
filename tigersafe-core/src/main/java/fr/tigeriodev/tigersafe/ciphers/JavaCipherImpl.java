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

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;

public abstract class JavaCipherImpl implements CipherImpl {
    
    /**
     * Java cipher algorithm name.
     */
    public final String algoName;
    /**
     * Java cipher transformation name.
     */
    public final String transformationName;
    /**
     * In bytes.
     */
    private final int keySize;
    /**
     * In bytes.
     */
    private final int ivSize;
    
    public JavaCipherImpl(String algoName, String transformationName, int keySize, int ivSize) {
        this.algoName = CheckUtils.notEmpty(algoName);
        this.transformationName = CheckUtils.notEmpty(transformationName);
        this.keySize = CheckUtils.strictlyPositive(keySize);
        this.ivSize = CheckUtils.strictlyPositive(ivSize);
    }
    
    public int getKeySizeBits() {
        return keySize * Byte.SIZE;
    }
    
    @Override
    public SecretKey newKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(algoName);
        keyGenerator.init(getKeySizeBits());
        return keyGenerator.generateKey();
    }
    
    @Override
    public int getKeySize() {
        return keySize;
    }
    
    @Override
    public byte[] keyToBytes(SecretKey key) {
        byte[] keyBytes = key.getEncoded();
        if (keyBytes.length != keySize) {
            throw new IllegalArgumentException("Invalid key size.");
        }
        return keyBytes;
    }
    
    @Override
    public SecretKey bytesToKey(byte[] keyBytes) {
        if (keyBytes.length != keySize) {
            throw new IllegalArgumentException("Invalid key size.");
        }
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, algoName);
    }
    
    @Override
    public byte[] newIv() throws NoSuchAlgorithmException {
        byte[] res = new byte[ivSize];
        SecureRandom.getInstanceStrong().nextBytes(res);
        return res;
    }
    
    @Override
    public int getIvSize() {
        return ivSize;
    }
    
    @Override
    public byte[] newDerivationSalt() throws NoSuchAlgorithmException {
        byte[] res = new byte[getDerivationSaltSize()];
        SecureRandom.getInstanceStrong().nextBytes(res);
        return res;
    }
    
    @Override
    public int getDerivationSaltSize() {
        return 32; // min 128 bits cf. https://www.baeldung.com/java-secure-aes-key, default 256 bits cf. https://javaee.github.io/javaee-spec/javadocs/javax/security/enterprise/identitystore/Pbkdf2PasswordHash.html
    }
    
    @Override
    public SecretKey getDerivatedKeyFrom(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (salt.length != getDerivationSaltSize()) {
            throw new IllegalArgumentException("Invalid salt size.");
        }
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = null;
        SecretKey generatedKey = null;
        try {
            spec = new PBEKeySpec(password, salt, 600000, getKeySizeBits()); // https://en.wikipedia.org/wiki/PBKDF2
            generatedKey = factory.generateSecret(spec);
            byte[] keyBytes = generatedKey.getEncoded(); // returns a clone
            SecretKey res = new SecretKeySpec(keyBytes, algoName); // keyBytes is cloned
            MemUtils.clearByteArray(keyBytes);
            return res;
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
            if (generatedKey != null) {
                MemUtils.tryDestroyPBKDF2Key(generatedKey);
            }
        }
    }
    
    @Override
    public byte[] encryptBytes(byte[] plainBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        javax.crypto.Cipher cipher = getCipher(javax.crypto.Cipher.ENCRYPT_MODE, key, iv);
        byte[] encryptedBytes = cipher.doFinal(plainBytes);
        return encryptedBytes;
    }
    
    @Override
    public byte[] decryptBytes(byte[] encryptedBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        javax.crypto.Cipher cipher = getCipher(javax.crypto.Cipher.DECRYPT_MODE, key, iv);
        byte[] plainBytes = cipher.doFinal(encryptedBytes);
        return plainBytes;
    }
    
    @Override
    public OutputStream newEncryptionStream(SecretKey key, byte[] iv, OutputStream out)
            throws GeneralSecurityException {
        return new CipherOutputStream(out, getCipher(javax.crypto.Cipher.ENCRYPT_MODE, key, iv));
    }
    
    @Override
    public InputStream newDecryptionStream(InputStream in, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        return new SecureCipherInputStream(
                in,
                getCipher(javax.crypto.Cipher.DECRYPT_MODE, key, iv)
        );
    }
    
    private javax.crypto.Cipher getCipher(int opmode, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        if (iv.length != ivSize) {
            throw new IllegalArgumentException("Invalid iv size.");
        }
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(transformationName);
        cipher.init(opmode, key, newParameterSpec(iv));
        return cipher;
    }
    
    public abstract AlgorithmParameterSpec newParameterSpec(byte[] iv);
    
}
