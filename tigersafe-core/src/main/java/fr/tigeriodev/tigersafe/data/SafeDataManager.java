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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.data.PasswordEntry.Data;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.MutableString;

public final class SafeDataManager implements Destroyable {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(SafeDataManager.class);
    private static final Logger log = Logs.newLogger(SafeDataManager.class);
    public static final int EXPECTED_PW_MAX_LEN = 80;
    
    public static boolean isValidSafePw(char[] pw) {
        return pw != null && pw.length >= 10;
    }
    
    public static class NameAlreadyUsedException extends IllegalArgumentException {
        
        public NameAlreadyUsedException(String msg) {
            super(msg);
        }
        
    }
    
    public static MutableString newSafePwHolder() {
        return new MutableString.Advanced(1, EXPECTED_PW_MAX_LEN);
    }
    
    private final File safeFile;
    private final MutableString safePwH;
    private SortedMap<String, PasswordEntry> pwEntriesByCurName = new TreeMap<>();
    private Set<ExistingPasswordEntry> deletedPwEntries = new HashSet<>();
    
    public SafeDataManager(File safeFile, char[] safePwSrc) {
        this.safeFile = CheckUtils.notNull(safeFile);
        this.safePwH = newSafePwHolder();
        this.safePwH.setChars(safePwSrc);
        if (!isValidSafePw(this.safePwH.getVal()) || this.safePwH.isDestroyed()) {
            throw new IllegalArgumentException("Invalid safe password.");
        }
    }
    
    public File getSafeFile() {
        return safeFile;
    }
    
    public void loadSafeFile()
            throws IOException, GeneralSecurityException, DestroyFailedException {
        checkNotDestroyed();
        SafeData safeData = SafeFileManager.read(safeFile, safePwH.getVal());
        destroyEntries();
        pwEntriesByCurName.clear();
        deletedPwEntries.clear();
        pwEntriesByCurName = new TreeMap<>();
        deletedPwEntries = new HashSet<>();
        addSafeData(safeData);
        safeData.dispose();
    }
    
    /**
     * NB: The safe file should be updated after this method, because added data is not considered as "changes" for {@link #hasChanges()}.
     * @param safeData
     */
    private void addSafeData(SafeData safeData) {
        checkNotDestroyed();
        for (Data pwEntryData : safeData.getPwEntriesData()) {
            PasswordEntry prevEntry = pwEntriesByCurName
                    .put(pwEntryData.name, new ExistingPasswordEntry(pwEntryData));
            if (prevEntry != null) {
                throw new IllegalStateException(
                        "Duplicate password entry with name: " + pwEntryData.name + "."
                );
            }
        }
    }
    
    public void updateSafeFile()
            throws IOException, GeneralSecurityException, DestroyFailedException {
        checkNotDestroyed();
        updateSafeFile(safePwH.getVal());
    }
    
    private void updateSafeFile(char[] safePw)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        checkNotDestroyed();
        File tempFile = safeFile.toPath().resolveSibling("_temp_-" + safeFile.getName()).toFile();
        if (tempFile.exists()) {
            throw new IllegalStateException(
                    "Temp file " + tempFile.getAbsolutePath()
                            + " already exists, it cannot be used to update the safe file."
            );
        }
        
        GlobalConfig.ConfigCipher.INTERNAL_DATA.getCipher().waitWorkingCheck();
        GlobalConfig.ConfigCipher.USER_DATA.getCipher().waitWorkingCheck();
        
        Data[] pwEntriesData = getValidPwEntriesData();
        SafeData safeData = new SafeData(pwEntriesData);
        SafeFileManager.write(tempFile, safePw, safeData);
        safeData.dispose();
        SafeDataManager tempDM = new SafeDataManager(tempFile, safePw);
        try {
            tempDM.loadSafeFile();
            boolean isDataPersistent = Arrays.equals(tempDM.getValidPwEntriesData(), pwEntriesData);
            if (!isDataPersistent) {
                unsafeLog.newChildFromCurMethIf(Level.ERROR)
                        .error(
                                () -> "\n tempDM: "
                                        + Arrays.toString(tempDM.getValidPwEntriesData())
                                        + ",\n realDM: " + Arrays.toString(pwEntriesData) + "."
                        );
                throw new IllegalStateException(
                        "Password entries are not correctly written or loaded."
                );
            }
        } catch (
                IOException | GeneralSecurityException | DestroyFailedException
                | RuntimeException ex
        ) {
            tempFile.delete();
            throw ex;
        } finally {
            try {
                tempDM.destroy();
            } catch (Exception ex) {
                log.newChildFromCurMeth().error(() -> "Error while destroying temp dm: ", ex);
            }
        }
        
        Files.move(tempFile.toPath(), safeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    
    public PasswordEntry[] getPwEntries() {
        return pwEntriesByCurName.values().toArray(new PasswordEntry[0]);
    }
    
    public PasswordEntry.Data[] getValidPwEntriesData() {
        List<PasswordEntry.Data> validPwEntries = new ArrayList<>();
        for (PasswordEntry pwEntry : pwEntriesByCurName.values()) {
            Data pwEntryData = pwEntry.getData();
            if (pwEntryData != null) {
                validPwEntries.add(pwEntryData);
            } else if (!(pwEntry instanceof NewPasswordEntry)) {
                throw new IllegalStateException();
            }
        }
        return validPwEntries.toArray(new PasswordEntry.Data[0]);
    }
    
    public PasswordEntry getPwEntryByCurName(String name) {
        return pwEntriesByCurName.get(name);
    }
    
    public boolean isActivePwEntry(PasswordEntry pwEntry) {
        PasswordEntry activePwEntry = pwEntriesByCurName.get(pwEntry.getCurrentName());
        return activePwEntry == pwEntry;
    }
    
    public boolean isDeletedPwEntry(PasswordEntry pwEntry) {
        return deletedPwEntries.contains(pwEntry);
    }
    
    public NewPasswordEntry addNewPwEntry() {
        if (isDestroyed()) {
            return null;
        }
        NewPasswordEntry newPwEntry = new NewPasswordEntry("");
        addPwEntryByCurName(newPwEntry, newPwEntry.getCurrentName());
        return newPwEntry;
    }
    
    /**
     * Only affects this instance, not the password entry data.
     * Should use {@link fr.tigeriodev.tigersafe.data.PasswordEntry#setName(String, SafeDataManager)} to change the name of a password entry.
     * No effect if the password entry is deleted.
     * @param pwEntry
     * @param newName
     */
    void changePwEntryName(PasswordEntry pwEntry, String newName)
            throws IllegalArgumentException, NameAlreadyUsedException {
        if (isDestroyed()) {
            return;
        }
        if (!isActivePwEntry(pwEntry)) {
            if (isDeletedPwEntry(pwEntry)) {
                return; // allow renaming without affecting this dm
            }
            throw new IllegalArgumentException("Unknown password entry.");
        }
        
        String currentName = pwEntry.getCurrentName();
        addPwEntryByCurName(pwEntry, newName);
        pwEntriesByCurName.remove(currentName);
    }
    
    private void addPwEntryByCurName(PasswordEntry pwEntry, String name)
            throws NameAlreadyUsedException {
        if (isDestroyed()) {
            return;
        }
        PasswordEntry pwEntryWithThisName = pwEntriesByCurName.get(name);
        if (pwEntryWithThisName != null) {
            throw new NameAlreadyUsedException(
                    "The name \"" + name + "\" is already used by another password entry."
            );
        }
        pwEntriesByCurName.put(name, pwEntry);
    }
    
    /**
     * No effect if the password entry is already deleted.
     * @param pwEntry
     */
    public void deletePwEntry(PasswordEntry pwEntry) {
        if (isDestroyed()) {
            return;
        }
        if (!isActivePwEntry(pwEntry)) {
            if (isDeletedPwEntry(pwEntry)) {
                return;
            }
            throw new IllegalArgumentException("Unknown password entry.");
        }
        
        pwEntriesByCurName.remove(pwEntry.getCurrentName());
        if (pwEntry instanceof ExistingPasswordEntry) {
            deletedPwEntries.add((ExistingPasswordEntry) pwEntry);
        } else {
            MemUtils.tryDestroy(pwEntry);
        }
    }
    
    public ExistingPasswordEntry[] getDeletedPwEntries() {
        return deletedPwEntries.toArray(new ExistingPasswordEntry[0]);
    }
    
    /**
     * No effect if the password entry is already active (restored).
     * @param deletedPwEntry
     */
    public void restorePwEntry(ExistingPasswordEntry deletedPwEntry) {
        if (isDestroyed()) {
            return;
        }
        if (!isDeletedPwEntry(deletedPwEntry)) {
            if (isActivePwEntry(deletedPwEntry)) {
                return;
            }
            throw new IllegalArgumentException("Unknown password entry.");
        }
        
        addPwEntryByCurName(deletedPwEntry, deletedPwEntry.getCurrentName());
        deletedPwEntries.remove(deletedPwEntry);
    }
    
    /**
     * NB: This method doesn't consider data added with {@link #addSafeData(SafeData)} as "changes", even if the safe file has not been updated with {@link #updateSafeFile()}.
     * @return
     */
    public boolean hasChanges() {
        if (!deletedPwEntries.isEmpty()) {
            return true;
        }
        for (PasswordEntry pwEntry : pwEntriesByCurName.values()) {
            if (
                (pwEntry instanceof NewPasswordEntry && pwEntry.isValid())
                        || (pwEntry instanceof ExistingPasswordEntry
                                && ((ExistingPasswordEntry) pwEntry).isModified())
            ) {
                return true;
            }
        }
        return false;
    }
    
    public void clearInvalidNewPwEntries() {
        if (isDestroyed()) {
            return;
        }
        pwEntriesByCurName.values().removeIf((pwEntry) -> {
            boolean isInvalid = pwEntry instanceof NewPasswordEntry && !pwEntry.isValid();
            if (isInvalid) {
                MemUtils.tryDestroy(pwEntry); // Caution: this clears the name string during the loop, which is used for key of pwEntriesByCurName, but with TreeMap no issue since the deletion is not based on the key
            }
            return isInvalid;
        });
    }
    
    public boolean isSafePw(char[] pw) {
        return Arrays.equals(safePwH.getVal(), pw);
    }
    
    public void changeSafePw(char[] newPw)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        checkNotDestroyed();
        if (!isValidSafePw(newPw)) {
            throw new IllegalArgumentException("Invalid safe password.");
        }
        updateSafeFile(newPw);
        safePwH.setChars(newPw);
    }
    
    public void changeSafeCiphers(String newInternalDataCipherName, String newUserDataCipherName)
            throws Exception {
        checkNotDestroyed();
        String initInternalCipherName = GlobalConfig.ConfigCipher.INTERNAL_DATA.getCipher().name;
        String initUserCipherName = GlobalConfig.ConfigCipher.USER_DATA.getCipher().name;
        
        GlobalConfig conf = GlobalConfig.getInstance();
        try {
            conf.setCipher(GlobalConfig.ConfigCipher.INTERNAL_DATA, newInternalDataCipherName);
            conf.setCipher(GlobalConfig.ConfigCipher.USER_DATA, newUserDataCipherName);
            
            updateSafeFile();
            conf.updateUserFile();
        } catch (Exception ex) {
            conf.setCipher(GlobalConfig.ConfigCipher.INTERNAL_DATA, initInternalCipherName);
            conf.setCipher(GlobalConfig.ConfigCipher.USER_DATA, initUserCipherName);
            throw ex;
        }
    }
    
    public void exportDataTo(File targetFile, Cipher cipher, char[] serialPw, short serialVer)
            throws Exception {
        checkNotDestroyed();
        Data[] pwEntriesData = getValidPwEntriesData();
        SafeData safeData = new SafeData(pwEntriesData);
        SafeSerializationManager.write(targetFile, cipher, serialPw, serialVer, safeData);
        safeData.dispose();
        SafeData readSafeData = SafeSerializationManager.read(targetFile, cipher, serialPw);
        Data[] readPwEntriesData = readSafeData.getPwEntriesData();
        readSafeData.dispose();
        
        boolean isDataPersistent = Arrays.equals(pwEntriesData, readPwEntriesData);
        if (!isDataPersistent) {
            unsafeLog.newChildFromCurMethIf(Level.ERROR)
                    .error(
                            () -> "\n deserialized pw entries: "
                                    + Arrays.toString(readPwEntriesData)
                                    + ",\n serialized pw entries: " + Arrays.toString(pwEntriesData)
                                    + "."
                    );
            throw new IllegalStateException(
                    "Password entries are not correctly serialized or deserialized."
            );
        }
    }
    
    public void importData(SafeData safeData) throws Exception {
        checkNotDestroyed();
        checkHasNoChanges();
        try {
            addSafeData(safeData);
            updateSafeFile();
        } catch (Exception ex) {
            loadSafeFile(); // restore to before adding safeData
            throw ex;
        }
    }
    
    public void destroyEntries() throws DestroyFailedException {
        boolean success = true;
        if (pwEntriesByCurName != null) {
            for (PasswordEntry pwEntry : pwEntriesByCurName.values()) {
                success = MemUtils.tryDestroy(pwEntry) && success;
            }
            pwEntriesByCurName.clear();
            pwEntriesByCurName = new TreeMap<>();
        }
        if (deletedPwEntries != null) {
            for (ExistingPasswordEntry pwEntry : deletedPwEntries) {
                success = MemUtils.tryDestroy(pwEntry) && success;
            }
            deletedPwEntries.clear();
            deletedPwEntries = new HashSet<>();
        }
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    private void checkNotDestroyed() {
        if (isDestroyed()) {
            throw new IllegalStateException("This data manager has been destroyed.");
        }
    }
    
    private void checkHasNoChanges() {
        if (hasChanges()) {
            throw new IllegalStateException("This data manager has unsaved changes.");
        }
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = MemUtils.tryDestroy(safePwH);
        try {
            destroyEntries();
        } catch (DestroyFailedException ex) {
            success = false;
        }
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return safePwH.isDestroyed();
    }
    
}
