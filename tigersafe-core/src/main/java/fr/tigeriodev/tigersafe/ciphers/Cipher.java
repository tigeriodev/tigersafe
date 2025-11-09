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
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;

import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.utils.CheckUtils;

public final class Cipher implements CipherImpl {
    
    private static final Logger log = Logs.newLogger(Cipher.class);
    
    private final String name;
    private final CipherImpl impl;
    private volatile WorkingStatus workingStatus = WorkingStatus.UNCHECKED;
    
    public Cipher(String name, CipherImpl impl) {
        this.name = name;
        this.impl = CheckUtils.notNull(impl);
    }
    
    public String getName() {
        return new String(name.toCharArray());
    }
    
    public static enum WorkingStatus {
        UNCHECKED,
        PENDING_CHECK,
        NOT_WORKING,
        WORKING;
    }
    
    public void checkWorkingAsync() {
        if (workingStatus != WorkingStatus.UNCHECKED) {
            return;
        }
        synchronized (impl) {
            if (workingStatus != WorkingStatus.UNCHECKED) {
                return;
            }
            workingStatus = WorkingStatus.PENDING_CHECK;
            
            log.debug(() -> "Start checking " + name + " cipher...");
            Thread thread = new Thread(() -> {
                boolean isWorking = false;
                try {
                    CipherImpl.checkWorking(impl);
                    isWorking = true;
                    log.debug(() -> name + " cipher is working");
                } catch (Exception ex) {
                    log.warn(() -> name + " cipher is not working: ", ex);
                }
                
                synchronized (impl) {
                    workingStatus = isWorking ? WorkingStatus.WORKING : WorkingStatus.NOT_WORKING;
                    impl.notifyAll();
                }
            });
            thread.start();
        }
    }
    
    public void waitWorkingCheck() {
        if (isChecked()) {
            return;
        }
        
        Logger methLog = log.newChildFromCurMethIf(Level.DEBUG);
        synchronized (impl) {
            try {
                while (isPendingCheck()) {
                    methLog.debug(() -> name + " cipher is pending check, start waiting...");
                    impl.wait();
                    methLog.debug(() -> "wait end for " + name + " cipher");
                }
            } catch (InterruptedException ex) {
                methLog.debug(() -> "interruption of waiting for " + name + " cipher", ex);
            }
        }
    }
    
    public boolean isPendingCheck() {
        return workingStatus == WorkingStatus.PENDING_CHECK;
    }
    
    public boolean isChecked() {
        return workingStatus == WorkingStatus.WORKING || workingStatus == WorkingStatus.NOT_WORKING;
    }
    
    public boolean isWorking() {
        return workingStatus == WorkingStatus.WORKING;
    }
    
    public boolean isWorkingOrUnchecked() {
        return workingStatus != WorkingStatus.NOT_WORKING;
    }
    
    private void ensureWorkingOrUnchecked() {
        if (!isWorkingOrUnchecked()) {
            throw new NotWorkingCipherException(this);
        }
    }
    
    private void ensureWorking() {
        if (!isWorking()) {
            throw new NotWorkingCipherException(this);
        }
    }
    
    @Override
    public SecretKey newKey() throws NoSuchAlgorithmException {
        ensureWorking();
        return impl.newKey();
    }
    
    @Override
    public int getKeySize() {
        return impl.getKeySize();
    }
    
    @Override
    public byte[] keyToBytes(SecretKey key) {
        ensureWorkingOrUnchecked();
        return impl.keyToBytes(key);
    }
    
    @Override
    public SecretKey bytesToKey(byte[] keyBytes) {
        ensureWorkingOrUnchecked();
        return impl.bytesToKey(keyBytes);
    }
    
    @Override
    public int getIvSize() {
        return impl.getIvSize();
    }
    
    @Override
    public byte[] newIv() throws NoSuchAlgorithmException {
        ensureWorking();
        return impl.newIv();
    }
    
    @Override
    public int getDerivationSaltSize() {
        return impl.getDerivationSaltSize();
    }
    
    @Override
    public byte[] newDerivationSalt() throws NoSuchAlgorithmException {
        ensureWorking();
        return impl.newDerivationSalt();
    }
    
    @Override
    public SecretKey getDerivatedKeyFrom(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        ensureWorkingOrUnchecked();
        return impl.getDerivatedKeyFrom(password, salt);
    }
    
    @Override
    public byte[] encryptBytes(byte[] plainBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        ensureWorking();
        return impl.encryptBytes(plainBytes, key, iv);
    }
    
    @Override
    public byte[] decryptBytes(byte[] encryptedBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        ensureWorkingOrUnchecked();
        return impl.decryptBytes(encryptedBytes, key, iv);
    }
    
    @Override
    public OutputStream newEncryptionStream(SecretKey key, byte[] iv, OutputStream out)
            throws GeneralSecurityException {
        ensureWorking();
        return impl.newEncryptionStream(key, iv, out);
    }
    
    @Override
    public InputStream newDecryptionStream(InputStream in, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        ensureWorkingOrUnchecked();
        return impl.newDecryptionStream(in, key, iv);
    }
    
    @Override
    public int getEncryptedLen(int plainLen) {
        return impl.getEncryptedLen(plainLen);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((impl == null) ? 0 : impl.hashCode());
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Cipher other = (Cipher) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (impl == null) {
            if (other.impl != null)
                return false;
        } else if (!impl.equals(other.impl))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "Cipher [name=" + name + ", impl=" + impl + ", workingStatus=" + workingStatus + "]";
    }
    
}
