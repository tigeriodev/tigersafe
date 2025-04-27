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

package fr.tigeriodev.tigersafe.ui;

import java.util.EnumMap;
import java.util.Map;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.GlobalConfig.InvalidConfigException;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import javafx.scene.input.KeyCombination;

public final class UIConfig {
    
    private static UIConfig instance;
    
    public static UIConfig getInstance() {
        if (instance == null) {
            instance = new UIConfig();
        }
        return instance;
    }
    
    private final Map<KeyboardShortcut, KeyCombination> keyboardShortcuts;
    
    private UIConfig() {
        keyboardShortcuts = new EnumMap<>(KeyboardShortcut.class);
        GlobalConfig config = GlobalConfig.getInstance();
        for (KeyboardShortcut shortcut : KeyboardShortcut.values()) {
            try {
                keyboardShortcuts.put(
                        shortcut,
                        KeyCombination.valueOf(config.getProp(shortcut.getConfigKey()))
                );
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw InvalidConfigException.invalidVal(shortcut.getConfigKey(), ex);
            }
        }
    }
    
    public enum KeyboardShortcut {
        
        SAVE_CHANGES("saveChanges"),
        SHOW_CHANGES("showChanges"),
        FILTER("filter"),
        ADD("add"),
        EDIT_MODE("editMode"),
        COPY_PASSWORD("copyPassword"),
        COPY_CURRENT_TOTP("copyCurrentTOTP"),
        COPY_NEXT_TOTP("copyNextTOTP"),
        PASSWORDS_TAB("passwordsTab"),
        CONFIG_TAB("configTab");
        
        private final String name;
        
        private KeyboardShortcut(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public String getConfigKey() {
            return "KeyboardShortcut." + name;
        }
        
        public KeyCombination getKeyCombination() {
            return getInstance().getKeyboardShortcut(this);
        }
        
    }
    
    public KeyCombination getKeyboardShortcut(KeyboardShortcut shortcut) {
        return keyboardShortcuts.get(shortcut);
    }
    
    public void setKeyboardShortcut(KeyboardShortcut shortcut, KeyCombination keyComb) {
        keyboardShortcuts.put(shortcut, CheckUtils.notNull(keyComb));
        GlobalConfig.getInstance().setProp(shortcut.getConfigKey(), keyComb.getName());
    }
    
}
