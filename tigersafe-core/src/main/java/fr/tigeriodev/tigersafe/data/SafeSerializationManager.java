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

package fr.tigeriodev.tigersafe.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;

public final class SafeSerializationManager {
    
    public static final short MAX_SERIAL_VER = 1; // min 1
    
    private SafeSerializationManager() {}
    
    public static void write(File targetFile, Cipher cipher, char[] serialPw, short serialVer,
            SafeData safeData)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        CheckUtils.notNull(safeData);
        PasswordEntry.Data[] pwEntriesData = safeData.getPwEntriesData();
        if (targetFile.isFile()) {
            throw new IllegalArgumentException("targetFile already exists.");
        }
        
        checkSerialVer(serialVer);
        
        byte[] serialKeySalt = cipher.newDerivationSalt();
        SecretKey serialKey = cipher.getDerivatedKeyFrom(serialPw, serialKeySalt);
        byte[] serialIv = cipher.newIv();
        
        try (
                FileOutputStream fileOut = new FileOutputStream(targetFile);
                BufferedOutputStream fileBufOut = new BufferedOutputStream(fileOut);
                DataOutputStream plainDataOut = new DataOutputStream(fileBufOut);
                OutputStream cipherOut =
                        cipher.newEncryptionStream(serialKey, serialIv, fileBufOut);
                // NB: could be optimized for really intense writing with "BufferedOutputStream cipherBufOut = new BufferedOutputStream(cipherOut); cipherDataOut = new DataOutputStream(cipherBufOut);", but should implement a destroyable BufferedOutputStream
                DataOutputStream cipherDataOut = new DataOutputStream(cipherOut);
        ) {
            plainDataOut.writeShort(serialVer);
            plainDataOut.write(serialKeySalt);
            plainDataOut.write(serialIv);
            
            cipherDataOut.writeInt(pwEntriesData.length);
            
            for (PasswordEntry.Data pwEntryData : pwEntriesData) {
                writeStr(pwEntryData.name, cipherDataOut);
                writeChars(pwEntryData.getPassword(), cipherDataOut);
                cipherDataOut.writeLong(pwEntryData.lastPasswordChangeTime.getEpochSecond());
                writeStr(pwEntryData.site, cipherDataOut);
                writeStr(pwEntryData.info, cipherDataOut);
                writeTOTP(pwEntryData.totp, cipherDataOut);
            }
        }
        
        MemUtils.clearByteArray(serialKeySalt);
        MemUtils.clearByteArray(serialIv);
        
        if (!MemUtils.tryDestroyKey(serialKey)) {
            throw new DestroyFailedException("serialKey could not be cleared from memory.");
        }
    }
    
    public static SafeData read(File srcFile, Cipher cipher, char[] serialPw)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        try (
                FileInputStream fileIn = new FileInputStream(srcFile);
                BufferedInputStream bufIn = new BufferedInputStream(fileIn);
                DataInputStream plainDataIn = new DataInputStream(bufIn);
        ) {
            short serialVer = plainDataIn.readShort();
            checkSerialVer(serialVer);
            byte[] serialKeySalt = new byte[cipher.getDerivationSaltSize()];
            plainDataIn.read(serialKeySalt);
            byte[] serialIv = new byte[cipher.getIvSize()];
            plainDataIn.read(serialIv);
            
            SecretKey serialKey = cipher.getDerivatedKeyFrom(serialPw, serialKeySalt);
            
            try (
                    InputStream cipherIn = cipher.newDecryptionStream(bufIn, serialKey, serialIv);
                    DataInputStream cipherDataIn = new DataInputStream(cipherIn);
            ) {
                int pwEntriesNum = cipherDataIn.readInt();
                PasswordEntry.Data[] pwEntriesData = new PasswordEntry.Data[pwEntriesNum];
                
                for (int i = 0; i < pwEntriesNum; i++) {
                    pwEntriesData[i] = new PasswordEntry.Data(
                            readStr(cipherDataIn),
                            readChars(cipherDataIn),
                            Instant.ofEpochSecond(cipherDataIn.readLong()),
                            readStr(cipherDataIn),
                            readStr(cipherDataIn),
                            readTOTP(cipherDataIn)
                    );
                }
                
                return new SafeData(pwEntriesData);
            } finally {
                MemUtils.clearByteArray(serialKeySalt);
                MemUtils.clearByteArray(serialIv);
                
                if (!MemUtils.tryDestroyKey(serialKey)) {
                    throw new DestroyFailedException("serialKey could not be cleared from memory.");
                }
            }
        }
    }
    
    public static void writeStr(String str, DataOutput dataOut) throws IOException {
        dataOut.writeInt(str.length());
        dataOut.writeChars(str);
    }
    
    public static String readStr(DataInput dataIn) throws IOException {
        char[] chars = readChars(dataIn);
        String res = new String(chars);
        MemUtils.clearCharArray(chars);
        return res;
    }
    
    public static void writeChars(char[] chars, DataOutput dataOut) throws IOException {
        dataOut.writeInt(chars.length);
        for (int i = 0; i < chars.length; i++) {
            dataOut.writeChar(chars[i]);
        }
    }
    
    public static char[] readChars(DataInput dataIn) throws IOException {
        int len = dataIn.readInt();
        char[] res = new char[len];
        for (int i = 0; i < len; i++) {
            res[i] = dataIn.readChar();
        }
        return res;
    }
    
    public static void writeTOTP(TOTP totp, DataOutputStream dataOut) throws IOException {
        if (totp == null) {
            dataOut.writeInt(-1);
        } else {
            dataOut.writeInt(totp.getKeyBytes().length);
            dataOut.write(totp.getKeyBytes());
            writeStr(totp.label, dataOut);
            writeStr(totp.issuer, dataOut);
            dataOut.writeInt(totp.algo.ordinal());
            dataOut.writeInt(totp.digitsNum);
            dataOut.writeInt(totp.periodSeconds);
        }
    }
    
    public static TOTP readTOTP(DataInputStream dataIn) throws IOException {
        int keyLen = dataIn.readInt();
        if (keyLen == -1) {
            return null;
        } else {
            byte[] keyBytes = new byte[keyLen];
            dataIn.readFully(keyBytes);
            return new TOTP(
                    keyBytes,
                    readStr(dataIn),
                    readStr(dataIn),
                    TOTP.Algorithm.getByOrdinal(dataIn.readInt()),
                    dataIn.readInt(),
                    dataIn.readInt()
            );
        }
    }
    
    public static void checkSerialVer(short serialVer) {
        if (serialVer < 1 || serialVer > MAX_SERIAL_VER) {
            throw new UnsupportedOperationException(
                    "The serialization version " + serialVer + " is not supported."
            );
        }
    }
    
}
