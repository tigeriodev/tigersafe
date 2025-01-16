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

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.data.TOTP.Algorithm;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.DestroyableByteArrayOutputStream;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.NumberRange;
import fr.tigeriodev.tigersafe.utils.RandomUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import fr.tigeriodev.tigersafe.utils.UTFUtils;

public final class SafeFileManager {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(SafeFileManager.class);
    public static final NumberRange OUT_BLOCK_NOISE_LEN_RANGE = new NumberRange(1, 8);
    public static final NumberRange IN_BLOCK_NOISE_LEN_RANGE = new NumberRange(1, 5);
    public static final NumberRange INT_SIZE_RANGE = new NumberRange(1, 2);
    public static final NumberRange SHORT_SIZE_RANGE = new NumberRange(1, 1);
    
    private static byte[] positiveIntBuf = new byte[Integer.BYTES];
    
    private SafeFileManager() {}
    
    private static class DataBlock implements Destroyable {
        
        final int minDataLen;
        final boolean hasFixedDataLen;
        final Cipher cipher;
        private SecretKey key;
        private byte[] iv;
        private int inBeforeNoiseLen = -1;
        private int dataLen = -1;
        private int inAfterNoiseLen = -1;
        private int outAfterNoiseLen = -1;
        private DestroyableByteArrayOutputStream bytesOut;
        private DataOutputStream dataOut;
        private byte[] decryptedBytes;
        
        public DataBlock(int minDataLen, boolean hasFixedDataLen, Cipher cipher) {
            this.minDataLen = CheckUtils.positive(minDataLen);
            this.hasFixedDataLen = hasFixedDataLen;
            this.cipher = CheckUtils.notNull(cipher);
        }
        
        public void newKey() throws NoSuchAlgorithmException {
            setKey(cipher.newKey());
        }
        
        public void setKey(SecretKey key) {
            if (this.key != null) {
                throw new IllegalStateException();
            }
            this.key = CheckUtils.notNull(key);
        }
        
        public SecretKey getKey() {
            return CheckUtils.notNull(key);
        }
        
        public void newIv() throws NoSuchAlgorithmException {
            setIv(cipher.newIv());
        }
        
        public void setIv(byte[] newVal) {
            if (iv != null) {
                throw new IllegalStateException();
            }
            if (newVal == null || newVal.length != cipher.getIvSize()) {
                throw new IllegalArgumentException();
            }
            iv = newVal;
        }
        
        public byte[] getIv() {
            return CheckUtils.notNull(iv);
        }
        
        public void writeIvTo(DataOutput dataOut) throws IOException {
            dataOut.write(getIv());
        }
        
        public void readIvFrom(DataInput dataIn) throws IOException {
            byte[] readIv = new byte[cipher.getIvSize()];
            dataIn.readFully(readIv);
            setIv(readIv);
        }
        
        public void writeMetadataTo(DataOutput dataOut)
                throws IOException, NoSuchAlgorithmException {
            writeIvTo(dataOut);
            writeSmallNumber(getOutAfterNoiseLen(), OUT_BLOCK_NOISE_LEN_RANGE, dataOut);
            DataBlock.writeInNoiseLen(getInBeforeNoiseLen(), dataOut);
            if (!hasFixedDataLen) {
                writePositiveInt(getDataLen(), minDataLen, dataOut);
            }
            DataBlock.writeInNoiseLen(getInAfterNoiseLen(), dataOut);
        }
        
        public void readMetadataFrom(DataInput dataIn) throws IOException {
            readIvFrom(dataIn);
            setOutAfterNoiseLen(readSmallNumber(dataIn, OUT_BLOCK_NOISE_LEN_RANGE));
            setInBeforeNoiseLen(DataBlock.readInNoiseLen(dataIn));
            if (!hasFixedDataLen) {
                setDataLen(readPositiveInt(dataIn, minDataLen));
            }
            setInAfterNoiseLen(DataBlock.readInNoiseLen(dataIn));
        }
        
        DataOutputStream startDataWriting() throws IOException, NoSuchAlgorithmException {
            checkNotAlreadyEncrypted();
            if (bytesOut != null || dataOut != null) {
                throw new IllegalStateException("Data writing already started.");
            }
            
            bytesOut = new DestroyableByteArrayOutputStream();
            dataOut = new DataOutputStream(bytesOut);
            
            byte[] beforeNoise = newInNoise();
            setInBeforeNoiseLen(beforeNoise.length);
            dataOut.write(beforeNoise);
            
            return dataOut;
        }
        
        void finishDataWritingAndWriteTo(RandomAccessFile raf)
                throws GeneralSecurityException, IOException {
            byte[] encryptedBytes = finishDataWriting();
            raf.write(encryptedBytes);
            MemUtils.clearByteArray(encryptedBytes);
            writeOutAfterNoise(raf);
        }
        
        /**
         * The returned array should be cleared from memory after use.
         * @return
         * @throws InvalidKeyException
         * @throws NoSuchPaddingException
         * @throws NoSuchAlgorithmException
         * @throws InvalidAlgorithmParameterException
         * @throws BadPaddingException
         * @throws IllegalBlockSizeException
         * @throws IOException
         */
        byte[] finishDataWriting() throws GeneralSecurityException, IOException {
            checkNotAlreadyEncrypted();
            if (bytesOut == null || dataOut == null) {
                throw new IllegalStateException("Should startDataWriting first.");
            }
            dataOut.flush();
            
            setDataLen(dataOut.size() - getInBeforeNoiseLen());
            
            byte[] afterNoise = newInNoise();
            setInAfterNoiseLen(afterNoise.length);
            bytesOut.write(afterNoise);
            bytesOut.flush();
            byte[] plainBytes = bytesOut.toByteArray();
            
            byte[] encryptedBytes = cipher.encryptBytes(plainBytes, getKey(), getIv());
            MemUtils.clearByteArray(plainBytes);
            if (getEncryptedLen() != encryptedBytes.length) {
                throw new IllegalStateException();
            }
            return encryptedBytes;
        }
        
        DataInputStream startDataReading(RandomAccessFile raf)
                throws GeneralSecurityException, IOException {
            if (decryptedBytes != null) {
                throw new IllegalStateException("Data reading has already been started.");
            }
            byte[] encryptedBytes = new byte[getEncryptedLen()];
            raf.readFully(encryptedBytes);
            decryptedBytes = cipher.decryptBytes(encryptedBytes, getKey(), getIv());
            MemUtils.clearByteArray(encryptedBytes);
            
            DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(decryptedBytes));
            skipBytes(dataIn, getInBeforeNoiseLen());
            
            return dataIn;
        }
        
        void writeOutAfterNoise(RandomAccessFile raf) throws IOException, NoSuchAlgorithmException {
            if (outAfterNoiseLen >= 0) {
                throw new IllegalStateException();
            }
            byte[] noise = newOutBlockNoise();
            setOutAfterNoiseLen(noise.length);
            raf.write(noise);
        }
        
        private void checkNotAlreadyEncrypted() {
            if (inAfterNoiseLen >= 0) {
                throw new IllegalStateException("Data block already encrypted.");
            }
        }
        
        int getEncryptedLen() {
            return cipher
                    .getEncryptedLen(getInBeforeNoiseLen() + getDataLen() + getInAfterNoiseLen());
        }
        
        void setDataLen(int newVal) {
            if (dataLen >= 0) {
                throw new IllegalStateException();
            }
            if (newVal < 0) {
                throw new IllegalArgumentException();
            }
            if (hasFixedDataLen && newVal != minDataLen) {
                throw new IllegalArgumentException(
                        "The block has a fixed data length (" + newVal + " != " + minDataLen + ")."
                );
            }
            dataLen = newVal;
        }
        
        int getDataLen() {
            if (hasFixedDataLen) {
                return minDataLen;
            }
            if (dataLen < 0) {
                throw new IllegalStateException();
            }
            return dataLen;
        }
        
        void setInBeforeNoiseLen(int newVal) {
            if (inBeforeNoiseLen >= 0) {
                throw new IllegalStateException();
            }
            if (newVal < 0) {
                throw new IllegalArgumentException();
            }
            inBeforeNoiseLen = newVal;
        }
        
        int getInBeforeNoiseLen() {
            if (inBeforeNoiseLen < 0) {
                throw new IllegalStateException();
            }
            return inBeforeNoiseLen;
        }
        
        void setInAfterNoiseLen(int newVal) {
            if (inAfterNoiseLen >= 0) {
                throw new IllegalStateException();
            }
            if (newVal < 0) {
                throw new IllegalArgumentException();
            }
            inAfterNoiseLen = newVal;
        }
        
        int getInAfterNoiseLen() {
            if (inAfterNoiseLen < 0) {
                throw new IllegalStateException();
            }
            return inAfterNoiseLen;
        }
        
        void setOutAfterNoiseLen(int newVal) {
            if (outAfterNoiseLen >= 0) {
                throw new IllegalStateException();
            }
            if (newVal < 0) {
                throw new IllegalArgumentException();
            }
            outAfterNoiseLen = newVal;
        }
        
        int getOutAfterNoiseLen() {
            if (outAfterNoiseLen < 0) {
                throw new IllegalStateException();
            }
            return outAfterNoiseLen;
        }
        
        /**
         * 
         * @param outAfterNoiseEndExclInd the index just after the end of the outAfterNoise of this DataBlock
         * @return the start index of this DataBlock.
         */
        long getStartInd(long outAfterNoiseEndExclInd) {
            return outAfterNoiseEndExclInd - getOutAfterNoiseLen() - getEncryptedLen();
        }
        
        static byte[] newInNoise() throws NoSuchAlgorithmException {
            return RandomUtils.newRandomBytes(
                    IN_BLOCK_NOISE_LEN_RANGE.min,
                    IN_BLOCK_NOISE_LEN_RANGE.getMax()
            );
        }
        
        static void writeInNoiseLen(int noiseLen, DataOutput dataOut)
                throws IOException, NoSuchAlgorithmException {
            writeSmallNumber(noiseLen, IN_BLOCK_NOISE_LEN_RANGE, dataOut);
        }
        
        static int readInNoiseLen(DataInput dataIn) throws IOException {
            return readSmallNumber(dataIn, IN_BLOCK_NOISE_LEN_RANGE);
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            boolean success = true;
            if (key != null) {
                success = MemUtils.tryDestroyKey(key) && success;
                key = null;
            }
            if (iv != null) {
                MemUtils.clearByteArray(iv);
                iv = null;
            }
            inBeforeNoiseLen = -1;
            dataLen = -1;
            inAfterNoiseLen = -1;
            outAfterNoiseLen = -1;
            
            if (dataOut != null) {
                try {
                    dataOut.writeLong(0); // Clear internal writeBuffer
                } catch (IOException ex) {
                    ex.printStackTrace();
                    success = false;
                }
                // Internal bytearr doesn't need to be cleared, because writeUTF never called.
                dataOut = null;
            }
            
            // Internal bytearr and chararr of DataInputStream returned by startDataReading() don't need to to be cleared, because readUTF never called.
            
            if (bytesOut != null) {
                success = MemUtils.tryDestroy(bytesOut) && success;
                bytesOut = null;
            }
            
            if (decryptedBytes != null) {
                MemUtils.clearByteArray(decryptedBytes);
            }
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return key == null
                    && iv == null
                    && inBeforeNoiseLen == -1
                    && dataLen == -1
                    && inAfterNoiseLen == -1
                    && outAfterNoiseLen == -1
                    && bytesOut == null
                    && dataOut == null
                    && decryptedBytes == null;
        }
        
    }
    
    private static DataBlock newMainHeaderBlock() {
        Cipher headersCipher = getHeaderBlocksCipher();
        return new DataBlock(
                headersCipher.getKeySize() + 3 * (headersCipher.getIvSize() + 3),
                true,
                headersCipher
        );
    }
    
    private static DataBlock newHeaderBlock() {
        Cipher targetCipher = getUserDataBlocksCipher();
        return new DataBlock(
                targetCipher.getKeySize() + targetCipher.getIvSize() + 8,
                true,
                getHeaderBlocksCipher()
        );
    }
    
    private static DataBlock newPasswordsBlock() {
        return new DataBlock(0, false, getUserDataBlocksCipher());
    }
    
    private static DataBlock newPasswordsDataBlock() {
        return new DataBlock(0, false, getUserDataBlocksCipher());
    }
    
    private static DataBlock newTOTPBlock() {
        return new DataBlock(0, false, getUserDataBlocksCipher());
    }
    
    private static Cipher getHeaderBlocksCipher() {
        return GlobalConfig.ConfigCipher.INTERNAL_DATA.getCipher();
    }
    
    private static Cipher getUserDataBlocksCipher() {
        return GlobalConfig.ConfigCipher.USER_DATA.getCipher();
    }
    
    public static void write(File targetFile, char[] safePw, SafeData safeData)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        Logger unsafeMethLog = unsafeLog.newChildFromCurMeth();
        CheckUtils.notNull(safeData);
        PasswordEntry.Data[] passwordEntriesData = safeData.getPwEntriesData();
        unsafeMethLog.debug(() -> "passwordEntriesData num = " + passwordEntriesData.length);
        
        try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");) {
            if (raf.length() != 0) {
                throw new IllegalArgumentException("targetFile is not empty.");
            }
            raf.write(
                    RandomUtils.newRandomBytes(
                            OUT_BLOCK_NOISE_LEN_RANGE.min,
                            OUT_BLOCK_NOISE_LEN_RANGE.min + 1023
                    )
            );
            
            unsafeMethLog.debug(() -> "passwordsBlock start ind = " + raf.getFilePointer());
            DataBlock passwordsBlock = writePasswordsBlock(passwordEntriesData, raf);
            
            unsafeMethLog.debug(() -> "passwordsDataBlock start ind = " + raf.getFilePointer());
            DataBlock passwordsDataBlock = writePasswordsDataBlock(passwordEntriesData, raf);
            
            unsafeMethLog.debug(() -> "totpBlock start ind = " + raf.getFilePointer());
            DataBlock totpBlock = writeTOTPBlock(passwordEntriesData, raf);
            
            Cipher headersCipher = getHeaderBlocksCipher();
            SecretKey mainKey = headersCipher.newKey();
            
            unsafeMethLog.debug(() -> "passwordsHeaderBlock start ind = " + raf.getFilePointer());
            DataBlock passwordsHeaderBlock = writeHeaderBlockFor(passwordsBlock, mainKey, raf);
            
            unsafeMethLog
                    .debug(() -> "passwordsDataHeaderBlock start ind = " + raf.getFilePointer());
            DataBlock passwordsDataHeaderBlock =
                    writeHeaderBlockFor(passwordsDataBlock, mainKey, raf);
            
            unsafeMethLog.debug(() -> "totpHeaderBlock start ind = " + raf.getFilePointer());
            DataBlock totpHeaderBlock = writeHeaderBlockFor(totpBlock, mainKey, raf);
            
            DataBlock mainHeaderBlock = newMainHeaderBlock();
            
            byte[] safeKeySalt = mainHeaderBlock.cipher.newDerivationSalt();
            
            SecretKey safeKey = mainHeaderBlock.cipher.getDerivatedKeyFrom(safePw, safeKeySalt);
            
            mainHeaderBlock.setKey(safeKey);
            mainHeaderBlock.newIv();
            
            DataOutputStream mainHeaderDataOut = mainHeaderBlock.startDataWriting();
            writeKey(mainKey, headersCipher, mainHeaderDataOut);
            passwordsHeaderBlock.writeMetadataTo(mainHeaderDataOut);
            passwordsDataHeaderBlock.writeMetadataTo(mainHeaderDataOut);
            totpHeaderBlock.writeMetadataTo(mainHeaderDataOut);
            
            byte[] mainHeaderBlockBytes = mainHeaderBlock.finishDataWriting();
            unsafeMethLog.debug(() -> "mainHeaderBlock start ind = " + raf.getFilePointer());
            raf.write(mainHeaderBlockBytes);
            
            unsafeMethLog.debug(() -> "mainHeaderBlock meta start ind = " + raf.getFilePointer());
            DataBlock.writeInNoiseLen(mainHeaderBlock.getInBeforeNoiseLen(), raf);
            DataBlock.writeInNoiseLen(mainHeaderBlock.getInAfterNoiseLen(), raf);
            mainHeaderBlock.writeIvTo(raf);
            
            unsafeMethLog.debug(() -> "safeKeySalt start ind = " + raf.getFilePointer());
            raf.write(safeKeySalt);
            
            int endNoiseLen = getEndNoiseLen(safePw);
            unsafeMethLog.debug(() -> "endNoise start ind = " + raf.getFilePointer());
            raf.write(RandomUtils.newRandomBytesOfLen(endNoiseLen));
            unsafeMethLog.debug(
                    () -> "writting end, total size = " + raf.getFilePointer()
                            + ",\n endNoiseLen = " + endNoiseLen + ",\n safeKeySalt = "
                            + StringUtils.bytesToStr(safeKeySalt)
            );
            
            MemUtils.clearByteArray(safeKeySalt);
            MemUtils.clearByteArray(mainHeaderBlockBytes);
            
            boolean success = true;
            success = MemUtils.tryDestroy(passwordsBlock) && success;
            success = MemUtils.tryDestroy(passwordsDataBlock) && success;
            success = MemUtils.tryDestroy(totpBlock) && success;
            success = MemUtils.tryDestroy(passwordsHeaderBlock) && success;
            success = MemUtils.tryDestroy(passwordsDataHeaderBlock) && success;
            success = MemUtils.tryDestroy(totpHeaderBlock) && success;
            success = MemUtils.tryDestroy(mainHeaderBlock) && success;
            
            clearBuffers();
            
            if (!success) {
                throw new DestroyFailedException("A DataBlock could not be cleared from memory.");
            }
            
            if (!MemUtils.isKeyDestroyed(safeKey) || !MemUtils.isKeyDestroyed(mainKey)) {
                throw new DestroyFailedException(
                        "safeKey and/or mainKey could not be cleared from memory."
                );
            }
        }
    }
    
    private static DataBlock writePasswordsBlock(PasswordEntry.Data[] passwordEntriesData,
            RandomAccessFile raf) throws GeneralSecurityException, IOException {
        DataBlock block = newPasswordsBlock();
        block.newKey();
        block.newIv();
        DataOutputStream dataOut = block.startDataWriting();
        
        writePositiveInt(passwordEntriesData.length, 0, dataOut);
        for (PasswordEntry.Data entryData : passwordEntriesData) {
            writeChars(entryData.getPassword(), dataOut);
        }
        
        block.finishDataWritingAndWriteTo(raf);
        return block;
    }
    
    private static char[][] readPasswordsBlock(DataBlock block, RandomAccessFile raf)
            throws GeneralSecurityException, IOException {
        DataInputStream dataIn = block.startDataReading(raf);
        
        int pwsNum = readPositiveInt(dataIn, 0);
        char[][] passwords = new char[pwsNum][];
        for (int i = 0; i < pwsNum; i++) {
            passwords[i] = readChars(dataIn);
        }
        return passwords;
    }
    
    private static DataBlock writePasswordsDataBlock(PasswordEntry.Data[] passwordEntriesData,
            RandomAccessFile raf) throws GeneralSecurityException, IOException {
        DataBlock block = newPasswordsDataBlock();
        block.newKey();
        block.newIv();
        DataOutputStream dataOut = block.startDataWriting();
        
        writePositiveInt(passwordEntriesData.length, 0, dataOut);
        for (PasswordEntry.Data entryData : passwordEntriesData) {
            writeStr(entryData.name, dataOut);
            dataOut.writeLong(entryData.lastPasswordChangeTime.getEpochSecond());
            writeStr(entryData.site, dataOut);
            writeStr(entryData.info, dataOut);
        }
        
        block.finishDataWritingAndWriteTo(raf);
        return block;
    }
    
    private static class PasswordData {
        
        final String name;
        final Instant lastPasswordChangeTime;
        final String site;
        final String info;
        
        public PasswordData(String name, Instant lastPasswordChangeTime, String site, String info) {
            this.name = name;
            this.lastPasswordChangeTime = lastPasswordChangeTime;
            this.site = site;
            this.info = info;
        }
        
    }
    
    private static PasswordData[] readPasswordsDataBlock(DataBlock block, RandomAccessFile raf)
            throws GeneralSecurityException, IOException {
        DataInputStream dataIn = block.startDataReading(raf);
        
        int pwsNum = readPositiveInt(dataIn, 0);
        PasswordData[] passwordsData = new PasswordData[pwsNum];
        for (int i = 0; i < pwsNum; i++) {
            passwordsData[i] = new PasswordData(
                    readStr(dataIn),
                    Instant.ofEpochSecond(dataIn.readLong()),
                    readStr(dataIn),
                    readStr(dataIn)
            );
        }
        return passwordsData;
    }
    
    private static DataBlock writeTOTPBlock(PasswordEntry.Data[] passwordEntriesData,
            RandomAccessFile raf) throws GeneralSecurityException, IOException {
        DataBlock block = newTOTPBlock();
        block.newKey();
        block.newIv();
        DataOutputStream dataOut = block.startDataWriting();
        
        Map<Integer, TOTP> totpByPwEntryInd = new HashMap<>();
        int pwEntryInd = 0;
        for (PasswordEntry.Data entryData : passwordEntriesData) {
            if (entryData.totp != null) {
                totpByPwEntryInd.put(pwEntryInd, entryData.totp);
            }
            pwEntryInd++;
        }
        
        writePositiveInt(totpByPwEntryInd.size(), 0, dataOut);
        for (Map.Entry<Integer, TOTP> ent : totpByPwEntryInd.entrySet()) {
            writePositiveInt(ent.getKey(), 0, dataOut);
            writeTOTP(ent.getValue(), dataOut);
        }
        
        block.finishDataWritingAndWriteTo(raf);
        return block;
    }
    
    private static Map<Integer, TOTP> readTOTPBlock(DataBlock block, RandomAccessFile raf)
            throws GeneralSecurityException, IOException {
        DataInputStream dataIn = block.startDataReading(raf);
        
        HashMap<Integer, TOTP> res = new HashMap<>();
        int totpsNum = readPositiveInt(dataIn, 0);
        for (int i = 0; i < totpsNum; i++) {
            res.put(readPositiveInt(dataIn, 0), readTOTP(dataIn));
        }
        return res;
    }
    
    private static void writeTOTP(TOTP totp, DataOutput dataOut)
            throws IOException, NoSuchAlgorithmException {
        writeUnsignedShort(totp.getKeyBytes().length, dataOut);
        dataOut.write(totp.getKeyBytes());
        writeStr(totp.label, dataOut);
        writeStr(totp.issuer, dataOut);
        writeSmallNumber(totp.algo.ordinal(), TOTP.ALGO_ORD_RANGE, dataOut);
        writeSmallNumber(totp.digitsNum, TOTP.DIGITS_NUM_RANGE, dataOut);
        writeSmallNumber(totp.periodSeconds, TOTP.PERIOD_SECONDS_RANGE, dataOut);
    }
    
    private static TOTP readTOTP(DataInput dataIn) throws IOException {
        int keyLen = readUnsignedShort(dataIn);
        byte[] keyBytes = new byte[keyLen];
        dataIn.readFully(keyBytes);
        String label = readStr(dataIn);
        String issuer = readStr(dataIn);
        int algoOrd = readSmallNumber(dataIn, TOTP.ALGO_ORD_RANGE);
        Algorithm algo = TOTP.Algorithm.getByOrdinal(algoOrd);
        int digitsNum = readSmallNumber(dataIn, TOTP.DIGITS_NUM_RANGE);
        int periodSeconds = readSmallNumber(dataIn, TOTP.PERIOD_SECONDS_RANGE);
        return new TOTP(keyBytes, label, issuer, algo, digitsNum, periodSeconds);
    }
    
    private static DataBlock writeHeaderBlockFor(DataBlock targetBlock, SecretKey headerKey,
            RandomAccessFile raf) throws GeneralSecurityException, IOException {
        DataBlock headerBlock = newHeaderBlock();
        headerBlock.setKey(headerKey);
        headerBlock.newIv();
        DataOutputStream headerDataOut = headerBlock.startDataWriting();
        writeKey(targetBlock.getKey(), targetBlock.cipher, headerDataOut);
        targetBlock.writeMetadataTo(headerDataOut);
        headerBlock.finishDataWritingAndWriteTo(raf);
        return headerBlock;
    }
    
    private static void readHeaderBlock(DataBlock header, DataBlock target, RandomAccessFile raf)
            throws GeneralSecurityException, IOException {
        DataInputStream headerDataIn = header.startDataReading(raf);
        target.setKey(readKey(headerDataIn, target.cipher));
        target.readMetadataFrom(headerDataIn);
    }
    
    public static int getEndNoiseLen(char[] safePw) {
        if (safePw.length < 2) {
            throw new IllegalArgumentException();
        }
        int hash = 31 * Character.hashCode(safePw[0]) + Character.hashCode(safePw[1]);
        return hash % 1024;
    }
    
    public static SafeData read(File srcFile, char[] safePw)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        Logger unsafeMethLog = unsafeLog.newChildFromCurMeth();
        try (RandomAccessFile raf = new RandomAccessFile(srcFile, "r");) {
            int endNoiseLen = getEndNoiseLen(safePw);
            unsafeMethLog.debug(() -> "endNoiseLen = " + endNoiseLen);
            
            DataBlock mainHeaderBlock = newMainHeaderBlock();
            long mainHeaderBlockMetaStartInd = raf.length()
                    - endNoiseLen
                    - mainHeaderBlock.cipher.getDerivationSaltSize()
                    - mainHeaderBlock.cipher.getIvSize()
                    - (2 * Byte.BYTES);
            unsafeMethLog
                    .debug(() -> "mainHeaderBlockMetaStartInd = " + mainHeaderBlockMetaStartInd);
            raf.seek(mainHeaderBlockMetaStartInd);
            
            mainHeaderBlock.setInBeforeNoiseLen(DataBlock.readInNoiseLen(raf));
            mainHeaderBlock.setInAfterNoiseLen(DataBlock.readInNoiseLen(raf));
            mainHeaderBlock.readIvFrom(raf);
            
            unsafeMethLog.debug(
                    () -> "mainHeaderBlock encrypted len = " + mainHeaderBlock.getEncryptedLen()
            );
            byte[] safeKeySalt = new byte[mainHeaderBlock.cipher.getDerivationSaltSize()];
            raf.readFully(safeKeySalt);
            unsafeMethLog.debug(() -> "safeKeySalt = " + StringUtils.bytesToStr(safeKeySalt));
            
            SecretKey safeKey = mainHeaderBlock.cipher.getDerivatedKeyFrom(safePw, safeKeySalt);
            mainHeaderBlock.setKey(safeKey);
            
            long mainHeaderStartInd =
                    mainHeaderBlockMetaStartInd - mainHeaderBlock.getEncryptedLen();
            unsafeMethLog.debug(() -> "mainHeaderStartInd = " + mainHeaderStartInd);
            raf.seek(mainHeaderStartInd);
            
            DataInputStream mainHeaderDataIn = mainHeaderBlock.startDataReading(raf);
            SecretKey mainKey = readKey(mainHeaderDataIn, getHeaderBlocksCipher());
            
            DataBlock passwordsHeaderBlock = newHeaderBlock();
            passwordsHeaderBlock.setKey(mainKey);
            passwordsHeaderBlock.readMetadataFrom(mainHeaderDataIn);
            
            DataBlock passwordsDataHeaderBlock = newHeaderBlock();
            passwordsDataHeaderBlock.setKey(mainKey);
            passwordsDataHeaderBlock.readMetadataFrom(mainHeaderDataIn);
            
            DataBlock totpHeaderBlock = newHeaderBlock();
            totpHeaderBlock.setKey(mainKey);
            totpHeaderBlock.readMetadataFrom(mainHeaderDataIn);
            
            long totpHeaderStartInd = totpHeaderBlock.getStartInd(mainHeaderStartInd);
            unsafeMethLog.debug(() -> "totpHeaderStartInd = " + totpHeaderStartInd);
            raf.seek(totpHeaderStartInd);
            DataBlock totpBlock = newTOTPBlock();
            readHeaderBlock(totpHeaderBlock, totpBlock, raf);
            
            long pwsDataHeaderStartInd = passwordsDataHeaderBlock.getStartInd(totpHeaderStartInd);
            unsafeMethLog.debug(() -> "pwsDataHeaderStartInd = " + pwsDataHeaderStartInd);
            
            raf.seek(pwsDataHeaderStartInd);
            DataBlock passwordsDataBlock = newPasswordsDataBlock();
            readHeaderBlock(passwordsDataHeaderBlock, passwordsDataBlock, raf);
            
            long pwsHeaderStartInd = passwordsHeaderBlock.getStartInd(pwsDataHeaderStartInd);
            unsafeMethLog.debug(() -> "pwsHeaderStartInd = " + pwsHeaderStartInd);
            raf.seek(pwsHeaderStartInd);
            DataBlock passwordsBlock = newPasswordsBlock();
            readHeaderBlock(passwordsHeaderBlock, passwordsBlock, raf);
            
            long totpStartInd = totpBlock.getStartInd(pwsHeaderStartInd);
            unsafeMethLog.debug(() -> "totpStartInd = " + totpStartInd);
            raf.seek(totpStartInd);
            Map<Integer, TOTP> totpByPwEntryInd = readTOTPBlock(totpBlock, raf);
            
            long pwsDataStartInd = passwordsDataBlock.getStartInd(totpStartInd);
            unsafeMethLog.debug(() -> "pwsDataStartInd = " + pwsDataStartInd);
            raf.seek(pwsDataStartInd);
            PasswordData[] passwordsData = readPasswordsDataBlock(passwordsDataBlock, raf);
            
            long pwsStartInd = passwordsBlock.getStartInd(pwsDataStartInd);
            unsafeMethLog.debug(() -> "pwsStartInd = " + pwsStartInd);
            raf.seek(pwsStartInd);
            char[][] passwords = readPasswordsBlock(passwordsBlock, raf);
            
            int pwsNum = passwords.length;
            if (passwordsData.length != pwsNum) {
                throw new IllegalArgumentException(
                        "PasswordsData block entries amount is different than Passwords block entries amount."
                );
            }
            
            PasswordEntry.Data[] pwEntriesData = new PasswordEntry.Data[pwsNum];
            for (int i = 0; i < pwsNum; i++) {
                pwEntriesData[i] = new PasswordEntry.Data(
                        passwordsData[i].name,
                        passwords[i].clone(),
                        passwordsData[i].lastPasswordChangeTime,
                        passwordsData[i].site,
                        passwordsData[i].info,
                        totpByPwEntryInd.getOrDefault(i, null)
                );
            }
            unsafeMethLog
                    .debug(() -> "reading end, passwordEntriesData num = " + pwEntriesData.length);
            
            MemUtils.clearCharMatrix(passwords);
            MemUtils.clearByteArray(safeKeySalt);
            Arrays.fill(passwordsData, null);
            
            boolean success = true;
            success = MemUtils.tryDestroy(passwordsBlock) && success;
            success = MemUtils.tryDestroy(passwordsDataBlock) && success;
            success = MemUtils.tryDestroy(totpBlock) && success;
            success = MemUtils.tryDestroy(passwordsHeaderBlock) && success;
            success = MemUtils.tryDestroy(passwordsDataHeaderBlock) && success;
            success = MemUtils.tryDestroy(totpHeaderBlock) && success;
            success = MemUtils.tryDestroy(mainHeaderBlock) && success;
            
            clearBuffers();
            
            if (!success) {
                throw new DestroyFailedException("A DataBlock could not be cleared from memory.");
            }
            
            if (!MemUtils.isKeyDestroyed(safeKey) || !MemUtils.isKeyDestroyed(mainKey)) {
                throw new DestroyFailedException(
                        "safeKey and/or mainKey could not be cleared from memory."
                );
            }
            
            return new SafeData(pwEntriesData);
        }
    }
    
    private static void writeKey(SecretKey key, Cipher cipher, DataOutput dataOut)
            throws IOException {
        byte[] keyBytes = cipher.keyToBytes(key);
        dataOut.write(keyBytes);
        MemUtils.clearByteArray(keyBytes);
    }
    
    private static SecretKey readKey(DataInput dataIn, Cipher cipher) throws IOException {
        byte[] keyBytes = new byte[cipher.getKeySize()];
        dataIn.readFully(keyBytes);
        SecretKey res = cipher.bytesToKey(keyBytes);
        MemUtils.clearByteArray(keyBytes);
        return res;
    }
    
    private static byte[] newOutBlockNoise() throws NoSuchAlgorithmException {
        return RandomUtils
                .newRandomBytes(OUT_BLOCK_NOISE_LEN_RANGE.min, OUT_BLOCK_NOISE_LEN_RANGE.getMax());
    }
    
    /**
     * Writes a small number in a non obvious way.
     * @param num a number between range.min and range.min + 2^range.pow - 1
     * @param range
     * @param dataOut
     * @throws IOException
     * @throws NoSuchAlgorithmException 
     */
    public static void writeSmallNumber(int num, NumberRange range, DataOutput dataOut)
            throws IOException, NoSuchAlgorithmException {
        if (!range.contains(num) || range.pow > Byte.SIZE) {
            throw new IllegalArgumentException();
        }
        if (range.pow == Byte.SIZE) {
            dataOut.writeByte(num - range.min);
        } else {
            int div = 1 << (Byte.SIZE - range.pow);
            dataOut.writeByte(
                    RandomUtils
                            .newRandomInt((num - range.min) * div, (num - range.min + 1) * div - 1)
            );
        }
    }
    
    public static int readSmallNumber(DataInput dataIn, NumberRange range) throws IOException {
        if (range.pow == Byte.SIZE) {
            return range.min + dataIn.readUnsignedByte();
        } else {
            return range.min + (dataIn.readUnsignedByte() >>> (Byte.SIZE - range.pow));
        }
    }
    
    public static void writePositiveInt(int num, int min, DataOutput dataOut)
            throws IOException, NoSuchAlgorithmException {
        if (min < 0 || num < min) {
            throw new IllegalArgumentException();
        }
        
        int writtenNum = num - min;
        positiveIntBuf[0] = (byte) (writtenNum >>> 24);
        positiveIntBuf[1] = (byte) (writtenNum >>> 16);
        positiveIntBuf[2] = (byte) (writtenNum >>> 8);
        positiveIntBuf[3] = (byte) (writtenNum >>> 0);
        
        int startInd = 0;
        while (positiveIntBuf[startInd] == 0 && startInd < Integer.BYTES - 1) {
            startInd++;
        }
        
        if (startInd == 0 && RandomUtils.newRandomBoolean()) {
            positiveIntBuf[0] |= 1 << 7;
        }
        
        int size = Integer.BYTES - startInd;
        writeSmallNumber(size, INT_SIZE_RANGE, dataOut);
        if (startInd > 0) {
            dataOut.write(RandomUtils.newRandomBytesOfLen(startInd));
        }
        dataOut.write(positiveIntBuf, startInd, size);
    }
    
    /**
     * NB: Only supports DataInput with always first call success of {@link DataInput#skipBytes(int)}, like with DataInputStream(ByteArrayInputStream), for optimization reasons.
     * @param dataIn
     * @param min
     * @return
     * @throws IOException
     */
    public static int readPositiveInt(DataInput dataIn, int min) throws IOException {
        int size = readSmallNumber(dataIn, INT_SIZE_RANGE);
        int startInd = Integer.BYTES - size;
        if (startInd > 0) {
            skipBytes(dataIn, startInd);
        }
        dataIn.readFully(positiveIntBuf, startInd, size);
        if (startInd == 0) {
            positiveIntBuf[0] &= ~(1 << 7);
        }
        
        int res = min;
        for (int i = startInd; i < Integer.BYTES; i++) {
            res += (positiveIntBuf[i] & 0xff) << ((3 - i) * 8);
        }
        return res;
    }
    
    public static void writeStr(String str, DataOutput dataOut)
            throws NoSuchAlgorithmException, IOException {
        char[] chars = str.toCharArray();
        writeChars(chars, dataOut);
        MemUtils.clearCharArray(chars);
    }
    
    public static String readStr(DataInput dataIn) throws IOException {
        char[] chars = readChars(dataIn);
        String res = new String(chars);
        MemUtils.clearCharArray(chars);
        return res;
    }
    
    public static void writeChars(char[] chars, DataOutput dataOut)
            throws IOException, NoSuchAlgorithmException {
        int utflen = UTFUtils.getUTFLen(chars);
        writeUnsignedShort(utflen, dataOut);
        UTFUtils.writeChars(chars, utflen, dataOut);
    }
    
    public static char[] readChars(DataInput dataIn) throws IOException {
        int utflen = readUnsignedShort(dataIn);
        return UTFUtils.readChars(utflen, dataIn);
    }
    
    public static void writeUnsignedShort(int num, DataOutput dataOut)
            throws NoSuchAlgorithmException, IOException {
        if (num > 65535 || num < 0) {
            throw new IllegalArgumentException();
        }
        
        byte first = (byte) (num >>> 8);
        byte last = (byte) (num >>> 0);
        
        int size = first == 0 ? 1 : 2;
        writeSmallNumber(size, SHORT_SIZE_RANGE, dataOut);
        if (size == 1) {
            dataOut.write(RandomUtils.newRandomBytesOfLen(1));
        } else {
            dataOut.writeByte(first);
        }
        dataOut.writeByte(last);
    }
    
    public static int readUnsignedShort(DataInput dataIn) throws IOException {
        int size = readSmallNumber(dataIn, SHORT_SIZE_RANGE);
        if (size == 2) {
            return dataIn.readUnsignedShort();
        } else {
            dataIn.readUnsignedByte(); // ignored
            return dataIn.readUnsignedByte();
        }
    }
    
    public static String checkValidLen(String str) throws IllegalArgumentException {
        char[] chars = str.toCharArray();
        checkValidLen(chars);
        MemUtils.clearCharArray(chars);
        return str;
    }
    
    public static char[] checkValidLen(char[] chars) throws IllegalArgumentException {
        try {
            if (UTFUtils.getUTFLen(chars) >= 0) {
                return chars;
            } else {
                throw new IllegalArgumentException("Negative UTF length.");
            }
        } catch (UTFDataFormatException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    /**
     * NB: Only supports DataInput with always first call success of {@link DataInput#skipBytes(int)}, like with DataInputStream(ByteArrayInputStream), for optimization reasons.
     * @param dataIn
     * @param num
     * @throws IOException
     */
    private static void skipBytes(DataInput dataIn, int num) throws IOException {
        int skipped = dataIn.skipBytes(num); // avoids readFully(new byte[startInd]) and clearing of that filled array
        if (skipped != num) {
            throw new RuntimeException(
                    "Unexpected failure of skipBytes(" + num + "): " + skipped + " skipped."
            );
        }
    }
    
    public static void clearBuffers() {
        MemUtils.clearByteArray(positiveIntBuf);
        UTFUtils.clearBuffers();
    }
    
}
