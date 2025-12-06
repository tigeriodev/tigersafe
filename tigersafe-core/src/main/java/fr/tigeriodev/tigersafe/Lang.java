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
import java.io.IOException;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

public final class Lang {
    
    public static final Set<Locale> AVAILABLE_LANGUAGES = Set.of(Locale.ENGLISH, Locale.FRENCH);
    
    private ResourceBundle bundle;
    private Properties customLangProps;
    
    Lang() {}
    
    void setLanguage(Locale locale) {
        if (!isAvailable(locale)) {
            throw new IllegalArgumentException("Unavailable language: " + locale + ".");
        }
        bundle = ResourceBundle.getBundle("languages.language", locale);
    }
    
    void setCustomLanguageFile(File langFile) {
        if (langFile == null) {
            customLangProps = null;
            return;
        }
        try (FileInputStream fileIn = new FileInputStream(langFile)) {
            customLangProps = new Properties();
            customLangProps.load(fileIn);
        } catch (IOException ex) {
            customLangProps = null;
            throw new IllegalArgumentException(ex);
        }
    }
    
    public static String getUnformatted(String key) {
        return getInstance().getUnformattedVal(key);
    }
    
    public static Lang getInstance() {
        return GlobalConfig.getInstance().getLang();
    }
    
    public String getUnformattedVal(String key) {
        String customLangVal = customLangProps != null ? customLangProps.getProperty(key) : null;
        if (customLangVal != null) {
            return customLangVal;
        }
        return bundle.getString(key);
    }
    
    public static String get(String key, Object... args) {
        return String.format(getUnformatted(key), args);
    }
    
    public static char getDecimalSep() {
        return DecimalFormatSymbols.getInstance(getLocale()).getDecimalSeparator();
    }
    
    public static Locale getLocale() {
        return Locale.getDefault(); // preferred to bundle.getLocale(), allowing custom locale
    }
    
    public static boolean isAvailable(Locale locale) {
        return AVAILABLE_LANGUAGES.contains(locale);
    }
    
}
