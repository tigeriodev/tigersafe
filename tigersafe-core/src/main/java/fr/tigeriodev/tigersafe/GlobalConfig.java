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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.utils.CheckUtils;

public final class GlobalConfig {
    
    public static final int PW_GENERATION_MIN_LEN = 1;
    public static final int PW_GENERATION_MAX_LEN = 10000;
    
    public static final String LANGUAGE_KEY = "language";
    public static final String CUSTOM_LANGUAGE_FILE_KEY = "customLanguageFile";
    public static final String CUSTOM_STYLESHEET_KEY = "customStylesheet";
    public static final String LAST_SAFE_FILE_KEY = "lastSafeFile";
    public static final String PW_GENERATION_CUSTOM_CHARS_KEY = "PasswordGeneration.customChars";
    public static final String PW_GENERATION_MIN_LEN_KEY = "PasswordGeneration.minLength";
    public static final String PW_GENERATION_MAX_LEN_KEY = "PasswordGeneration.maxLength";
    
    private static final int ENCODING_STR_COL_RADIX = 32;
    
    private static GlobalConfig instance = null;
    private static boolean isLocked = false;
    
    public synchronized static void setInstance(GlobalConfig newInstance, boolean checkUnique,
            boolean lock) {
        if (isLocked) {
            throw new IllegalStateException("The global config instance is locked."); // Prevent changing the config during the execution of the program, which could allow different attacks like changing the ciphers.
        }
        
        if (checkUnique && instance != null) {
            throw new IllegalStateException("The global config has already been initialized.");
        }
        
        instance = Objects.requireNonNull(newInstance);
        isLocked = lock;
    }
    
    public static void initFile(File userGlobalConfigFile) throws IOException {
        if (!userGlobalConfigFile.isFile()) {
            try (FileWriter writer = new FileWriter(userGlobalConfigFile)) {
                getDefaultProperties().store(writer, null);
            }
            if (!userGlobalConfigFile.isFile()) {
                throw new IllegalStateException(
                        "Failed to initialize the user global config file."
                );
            }
        }
    }
    
    public static GlobalConfig getInstance() {
        return instance;
    }
    
    public static Properties getDefaultProperties() throws IOException {
        Properties defProps = new Properties();
        try (
                InputStream defFileIn =
                        GlobalConfig.class.getResourceAsStream("/default-global-config.properties")
        ) {
            defProps.load(defFileIn);
        }
        return defProps;
    }
    
    private final Properties props;
    private final File userFile;
    private final Lang lang;
    private Locale language;
    private File customLanguageFile;
    private String customStylesheetURL;
    private File customStylesheetFile;
    private File lastSafeFile;
    private final Map<ConfigCipher, Cipher> ciphers;
    private Set<String> pwGenerationCustomChars;
    private int pwGenerationMinLen;
    private int pwGenerationMaxLen;
    
    public GlobalConfig(File userGlobalConfigFile)
            throws IOException, IllegalArgumentException, InvalidConfigPropertyValueException {
        userFile = userGlobalConfigFile;
        lang = new Lang();
        Properties defProps = getDefaultProperties();
        
        if (userGlobalConfigFile != null) {
            props = new Properties(defProps);
            try (FileInputStream configFileIn = new FileInputStream(userGlobalConfigFile)) {
                props.load(configFileIn);
            } catch (IOException ex) {
                throw new IllegalArgumentException(ex);
            }
        } else {
            props = defProps;
        }
        
        language = deserializeProp(LANGUAGE_KEY, this::deserializeLanguage);
        customLanguageFile =
                deserializeProp(CUSTOM_LANGUAGE_FILE_KEY, this::deserializeCustomLanguageFile);
        customStylesheetFile =
                deserializeProp(CUSTOM_STYLESHEET_KEY, this::deserializeCustomStylesheet);
        // customStylesheetURL is set by deserializeCustomStylesheet
        lastSafeFile = deserializeProp(LAST_SAFE_FILE_KEY, this::deserializeFile);
        
        ciphers = new EnumMap<>(ConfigCipher.class);
        for (ConfigCipher confCipher : ConfigCipher.values()) {
            ciphers.put(
                    confCipher,
                    deserializeProp(confCipher.getConfigKey(), this::deserializeCipher)
            );
        }
        
        pwGenerationCustomChars = deserializeProp(
                PW_GENERATION_CUSTOM_CHARS_KEY,
                this::deserializePwGenerationCustomChars
        );
        pwGenerationMinLen =
                deserializeProp(PW_GENERATION_MIN_LEN_KEY, this::deserializePwGenerationLen);
        pwGenerationMaxLen =
                deserializeProp(PW_GENERATION_MAX_LEN_KEY, this::deserializePwGenerationLen);
    }
    
    public Lang getLang() {
        return lang;
    }
    
    private String serializeLanguage(Locale val) {
        return val.toLanguageTag();
    }
    
    private Locale deserializeLanguage(String serialized) {
        Locale res = Locale.forLanguageTag(serialized);
        lang.setLanguage(res);
        return res;
    }
    
    public Locale getLanguage() {
        return language;
    }
    
    public void setLanguage(Locale newLocale) {
        String serializedNewVal = serializeLanguage(newLocale);
        language = deserializeLanguage(serializedNewVal);
        setProp(LANGUAGE_KEY, serializedNewVal);
    }
    
    private String serializeFile(File file) {
        return file != null ? file.getAbsolutePath() : "";
    }
    
    private File deserializeFile(String path) {
        return CheckUtils.isNotEmpty(path) ? new File(path) : null;
    }
    
    private File deserializeCustomLanguageFile(String path) {
        File res = deserializeFile(path);
        lang.setCustomLanguageFile(res);
        return res;
    }
    
    public File getCustomLanguageFile() {
        return customLanguageFile;
    }
    
    public void setCustomLanguageFile(File newFile) {
        String serializedNewFile = serializeFile(newFile);
        customLanguageFile = deserializeCustomLanguageFile(serializedNewFile);
        setProp(CUSTOM_LANGUAGE_FILE_KEY, serializedNewFile);
    }
    
    /**
     * NB: Modifies both customStylesheetURL and customStylesheetFile.
     * @param path
     * @return customStylesheetFile after modification.
     */
    private File deserializeCustomStylesheet(String path) {
        if (CheckUtils.isNotEmpty(path)) {
            File stylesheetFile = new File(path);
            if (!stylesheetFile.isFile()) {
                throw new IllegalArgumentException("The file \"" + path + "\" does not exist.");
            }
            try {
                customStylesheetURL = stylesheetFile.toURI().toURL().toExternalForm();
                customStylesheetFile = stylesheetFile;
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException(ex);
            }
        } else {
            customStylesheetURL = null;
            customStylesheetFile = null;
        }
        return customStylesheetFile;
    }
    
    public String getCustomStylesheetURL() {
        return customStylesheetURL;
    }
    
    public File getCustomStylesheetFile() {
        return customStylesheetFile;
    }
    
    public void setCustomStylesheet(File newFile) {
        String serializedNewFile = serializeFile(newFile);
        customStylesheetFile = deserializeCustomStylesheet(serializedNewFile);
        // customStylesheetURL is modified by deserializeCustomStylesheet
        setProp(CUSTOM_STYLESHEET_KEY, serializedNewFile);
    }
    
    public File getLastSafeFile() {
        return lastSafeFile;
    }
    
    public void setLastSafeFile(File newFile) {
        String serializedNewFile = serializeFile(newFile);
        lastSafeFile = deserializeFile(serializedNewFile);
        setProp(LAST_SAFE_FILE_KEY, serializedNewFile);
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
    
    private Cipher deserializeCipher(String cipherName) {
        Cipher res = CiphersManager.getCipherByName(cipherName);
        res.checkWorkingAsync();
        return res;
    }
    
    public Cipher getCipher(ConfigCipher configCipher) {
        return ciphers.get(configCipher);
    }
    
    public void setCipher(ConfigCipher configCipher, String newCipherName) {
        Cipher newCipher = deserializeCipher(newCipherName);
        ciphers.put(configCipher, newCipher);
        setProp(configCipher.getConfigKey(), newCipherName);
    }
    
    private String serializePwGenerationLen(int val) {
        return Integer.toString(val);
    }
    
    private int deserializePwGenerationLen(String serialized) {
        int res = Integer.parseInt(serialized);
        return CheckUtils.inRange(res, PW_GENERATION_MIN_LEN, PW_GENERATION_MAX_LEN);
    }
    
    public int getPwGenerationMinLen() {
        return pwGenerationMinLen;
    }
    
    public void setPwGenerationMinLen(int newVal) {
        String serializedNewVal = serializePwGenerationLen(newVal);
        pwGenerationMinLen = deserializePwGenerationLen(serializedNewVal);
        setProp(PW_GENERATION_MIN_LEN_KEY, serializedNewVal);
    }
    
    public int getPwGenerationMaxLen() {
        return pwGenerationMaxLen;
    }
    
    public void setPwGenerationMaxLen(int newVal) {
        String serializedNewVal = serializePwGenerationLen(newVal);
        pwGenerationMaxLen = deserializePwGenerationLen(serializedNewVal);
        setProp(PW_GENERATION_MAX_LEN_KEY, serializedNewVal);
    }
    
    private String serializePwGenerationCustomChars(Set<String> val) {
        return encodeStrCol(val);
    }
    
    private Set<String> deserializePwGenerationCustomChars(String serialized) {
        Set<String> res = new LinkedHashSet<>();
        decodeStrCol(serialized, res);
        return Collections.unmodifiableSet(res);
    }
    
    public Set<String> getPwGenerationCustomChars() {
        return pwGenerationCustomChars;
    }
    
    public void setPwGenerationCustomChars(Set<String> newVal) {
        String serializedNewVal = serializePwGenerationCustomChars(newVal);
        pwGenerationCustomChars = deserializePwGenerationCustomChars(serializedNewVal);
        setProp(PW_GENERATION_CUSTOM_CHARS_KEY, serializedNewVal);
    }
    
    public <T> T deserializeProp(String key, Function<String, T> deserializer)
            throws InvalidConfigPropertyValueException {
        try {
            return deserializer.apply(getProp(key));
        } catch (Exception ex) {
            throw new InvalidConfigPropertyValueException(key, ex);
        }
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
        try (FileWriter writer = new FileWriter(userFile)) {
            props.store(writer, null);
        }
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
    
    public static class InvalidConfigPropertyValueException extends IllegalStateException {
        
        private final String propKey;
        
        public InvalidConfigPropertyValueException(String propKey) {
            this(propKey, "Invalid value for \"" + propKey + "\".");
        }
        
        public InvalidConfigPropertyValueException(String propKey, Throwable cause) {
            this(propKey, "Invalid value for \"" + propKey + "\".", cause);
        }
        
        public InvalidConfigPropertyValueException(String propKey, String msg) {
            super(msg);
            this.propKey = propKey;
        }
        
        public InvalidConfigPropertyValueException(String propKey, String msg, Throwable cause) {
            super(msg, cause);
            this.propKey = propKey;
        }
        
        public String getPropKey() {
            return propKey;
        }
        
    }
    
}
