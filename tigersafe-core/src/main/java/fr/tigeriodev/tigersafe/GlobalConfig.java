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

package fr.tigeriodev.tigersafe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.utils.CheckUtils;

public final class GlobalConfig {
    
    private static final int ENCODING_STR_COL_RADIX = 32;
    
    private static GlobalConfig instance = null;
    private static boolean isTest = false;
    
    /**
     * Should only be called in tests, to allow several initializations of the global config.
     * @param userGlobalConfigFile
     * @throws IOException
     */
    public static void initForTest(File userGlobalConfigFile) throws IOException {
        if (instance != null && !isTest) {
            throw new IllegalStateException(
                    "The global config has already been initialized not for test."
            );
        }
        isTest = true;
        initialize(userGlobalConfigFile);
    }
    
    public static void init(File userGlobalConfigFile)
            throws GeneralSecurityException, IOException {
        if (isTest) {
            throw new GeneralSecurityException(
                    "The global config has already been initialized for test."
            );
        }
        initialize(userGlobalConfigFile);
    }
    
    private static void initialize(File userGlobalConfigFile) throws IOException {
        if (instance != null && !isTest) {
            throw new IllegalStateException("The global config has already been initialized."); // Prevent changing the config during the execution of the program, which could allow different attacks like changing the ciphers.
        }
        if (!userGlobalConfigFile.isFile()) {
            getDefaultProperties().store(new FileWriter(userGlobalConfigFile), null);
            if (!userGlobalConfigFile.isFile()) {
                throw new IllegalStateException(
                        "Failed to initialize the user global config file."
                );
            }
        }
        instance = new GlobalConfig(userGlobalConfigFile);
    }
    
    public static GlobalConfig getInstance() {
        return instance;
    }
    
    private static Properties getDefaultProperties() {
        Properties defProps = new Properties();
        try {
            defProps.load(
                    GlobalConfig.class.getResourceAsStream("/default-global-config.properties")
            );
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return defProps;
    }
    
    private final Properties props;
    private final File userFile;
    private final Map<ConfigCipher, Cipher> ciphers;
    private String customStylesheetURL;
    
    private GlobalConfig(File userGlobalConfigFile) {
        userFile = userGlobalConfigFile;
        Properties defProps = getDefaultProperties();
        
        if (userGlobalConfigFile != null) {
            props = new Properties(defProps);
            try {
                props.load(new FileInputStream(userGlobalConfigFile));
            } catch (IOException ex) {
                throw new IllegalArgumentException(ex);
            }
        } else {
            props = defProps;
        }
        
        try {
            Lang.setLanguage(getLanguage());
        } catch (IllegalArgumentException ex) {
            throw InvalidConfigException.invalidVal("language", ex);
        }
        
        try {
            Lang.setCustomLanguageFile(getCustomLanguageFile());
        } catch (IllegalArgumentException ex) {
            throw InvalidConfigException.invalidVal("customLanguageFile", ex);
        }
        
        ciphers = new EnumMap<>(ConfigCipher.class);
        for (ConfigCipher confCipher : ConfigCipher.values()) {
            try {
                Cipher cipher = CiphersManager.getCipherByName(getProp(confCipher.getConfigKey()));
                ciphers.put(confCipher, cipher);
                cipher.checkWorkingAsync();
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw InvalidConfigException.invalidVal(confCipher.getConfigKey());
            }
        }
        
        String customStylesheetPath = getProp("customStylesheet");
        if (CheckUtils.isNotEmpty(customStylesheetPath)) {
            try {
                File stylesheetFile = new File(customStylesheetPath);
                if (!stylesheetFile.isFile()) {
                    throw InvalidConfigException.invalidVal("customStylesheet");
                }
                customStylesheetURL = stylesheetFile.toURI().toURL().toExternalForm();
            } catch (MalformedURLException ex) {
                throw InvalidConfigException.invalidVal("customStylesheet", ex);
            }
        } else {
            customStylesheetURL = null;
        }
    }
    
    public Locale getLanguage() {
        return Locale.forLanguageTag(getProp("language"));
    }
    
    public void setLanguage(Locale locale) {
        Lang.setLanguage(locale);
        setProp("language", locale.toLanguageTag());
    }
    
    public File getCustomLanguageFile() {
        String path = getProp("customLanguageFile");
        return CheckUtils.isNotEmpty(path) ? new File(path) : null;
    }
    
    public void setCustomLanguageFile(File newFile) {
        Lang.setCustomLanguageFile(newFile);
        setProp("customLanguageFile", newFile != null ? newFile.getAbsolutePath() : "");
    }
    
    public String getCustomStylesheetURL() {
        return customStylesheetURL;
    }
    
    public File getCustomStylesheetFile() {
        String path = getProp("customStylesheet");
        return CheckUtils.isNotEmpty(path) ? new File(path) : null;
    }
    
    public void setCustomStylesheet(File newFile) {
        if (newFile == null) {
            customStylesheetURL = null;
            setProp("customStylesheet", "");
            return;
        }
        try {
            customStylesheetURL = newFile.toURI().toURL().toExternalForm();
            setProp("customStylesheet", newFile.getAbsolutePath());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    public File getLastSafeFile() {
        String path = getProp("lastSafeFile");
        return CheckUtils.isNotEmpty(path) ? new File(path) : null;
    }
    
    public void setLastSafeFile(File newFile) {
        setProp("lastSafeFile", newFile.getAbsolutePath());
    }
    
    public enum ConfigCipher {
        
        INTERNAL_DATA("internalData"),
        USER_DATA("userData");
        
        private final String name;
        
        private ConfigCipher(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public String getConfigKey() {
            return "Cipher." + name;
        }
        
        public Cipher getCipher() {
            return getInstance().getCipher(this);
        }
        
    }
    
    public Cipher getCipher(ConfigCipher configCipher) {
        return ciphers.get(configCipher);
    }
    
    public void setCipher(ConfigCipher configCipher, String newCipherName) {
        Cipher cipher = CiphersManager.getCipherByName(newCipherName);
        ciphers.put(configCipher, cipher);
        setProp(configCipher.getConfigKey(), newCipherName);
        cipher.checkWorkingAsync();
    }
    
    public int getPwGenerationMinLen() {
        return Integer.parseInt(getProp("PasswordGeneration.minLength"));
    }
    
    public void setPwGenerationMinLen(int newVal) {
        setProp("PasswordGeneration.minLength", Integer.toString(newVal));
    }
    
    public int getPwGenerationMaxLen() {
        return Integer.parseInt(getProp("PasswordGeneration.maxLength"));
    }
    
    public void setPwGenerationMaxLen(int newVal) {
        setProp("PasswordGeneration.maxLength", Integer.toString(newVal));
    }
    
    public Set<String> getPwGenerationCustomChars() {
        Set<String> res = new LinkedHashSet<>();
        decodeStrCol(getProp("PasswordGeneration.customChars"), res);
        return res;
    }
    
    public void setPwGenerationCustomChars(Set<String> newVal) {
        setProp("PasswordGeneration.customChars", encodeStrCol(newVal));
    }
    
    public String getProp(String key) {
        return props.getProperty(key);
    }
    
    public void setProp(String key, String newVal) {
        props.setProperty(key, newVal);
    }
    
    public void updateUserFile() throws IOException {
        if (userFile == null) {
            return;
        }
        props.store(new FileWriter(userFile), null);
    }
    
    /**
     * Encodes a collection of strings, preserving the order.
     * @param col collection of strings of length in [0; 1023]
     * @return
     */
    public static String encodeStrCol(Collection<String> col) {
        StringBuilder res = new StringBuilder();
        for (String str : col) {
            int strLen = str.length();
            if (strLen > 1023) {
                throw new UnsupportedOperationException("Cannot encode string of length > 1023.");
            }
            String encodedStrLen = Integer.toUnsignedString(strLen, ENCODING_STR_COL_RADIX);
            if (encodedStrLen.length() == 1) {
                res.append("0");
            }
            res.append(encodedStrLen);
            res.append(str);
        }
        return res.toString();
    }
    
    /**
     * Decodes a collection of strings encoded by {@link #encodeStrCol(Collection)}, preserving the order.
     * @param encodedCol encoded by {@link #encodeStrCol(Collection)}
     * @param res the collection to fill with the encoded strings in {@code encodedCol}
     */
    public static void decodeStrCol(String encodedCol, Collection<String> res) {
        if (!res.isEmpty()) {
            throw new IllegalArgumentException();
        }
        int ind = 0;
        int encodedColLen = encodedCol.length();
        while (ind < encodedColLen) {
            int strStartInd = ind + 2;
            if (strStartInd > encodedColLen) {
                throw new IllegalArgumentException(
                        "Invalid encoded collection of strings (missing a string length)."
                );
            }
            String encodedStrLen = encodedCol.substring(ind, strStartInd);
            if (encodedStrLen.startsWith("0")) {
                encodedStrLen = encodedStrLen.substring(1);
            }
            int strLen = Integer.parseUnsignedInt(encodedStrLen, ENCODING_STR_COL_RADIX);
            if (strLen < 0) {
                throw new IllegalArgumentException(
                        "Invalid encoded collection of strings (a string length < 0)."
                );
            }
            ind = strStartInd + strLen;
            if (ind > encodedColLen) {
                throw new IllegalArgumentException(
                        "Invalid encoded collection of strings (a string exceeds encoded length)."
                );
            }
            res.add(encodedCol.substring(strStartInd, ind));
        }
    }
    
    public static class InvalidConfigException extends IllegalStateException {
        
        public static InvalidConfigException invalidVal(String configKey) {
            return new InvalidConfigException("Invalid value for \"" + configKey + "\".");
        }
        
        public static InvalidConfigException invalidVal(String configKey, Throwable cause) {
            return new InvalidConfigException("Invalid value for \"" + configKey + "\".", cause);
        }
        
        public InvalidConfigException(String msg) {
            super(msg);
        }
        
        public InvalidConfigException(String msg, Throwable cause) {
            super(msg, cause);
        }
        
    }
    
}
