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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.GlobalConfig.ConfigCipher;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.ciphers.Cipher;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.ui.UIConfig.KeyboardShortcut;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.OptionalFileField;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.ModifierValue;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser.ExtensionFilter;

public class GlobalConfigUI implements UI {
    
    private static final String CONFIG_LANG_BASE = "GlobalConfigUI.config";
    
    private final Map<String, Locale> langByDisplayName;
    private final ComboBox<String> langBox;
    private final OptionalFileField customLangFileF;
    private final OptionalFileField customStylesheetFileF;
    private final Map<ConfigCipher, ComboBox<String>> ciphersBoxByConfig;
    private final Map<KeyboardShortcut, KeyCombination> keyboardShortcuts;
    private final Button saveBtn;
    private final Scene scene;
    
    public GlobalConfigUI() {
        VBox rootVBox = new VBox();
        rootVBox.setId("global-config-root-vbox");
        
        HBox closeBtnHBox = new HBox();
        closeBtnHBox.getStyleClass().add("close-button-hbox");
        
        Button closeBtn = new Button(Lang.get("GlobalConfigUI.close.button.text"));
        
        closeBtnHBox.getChildren().add(closeBtn);
        
        HBox confTitleHBox = new HBox();
        confTitleHBox.getStyleClass().add("global-config-title-hbox");
        
        Text confTitle = new Text(Lang.get(CONFIG_LANG_BASE + ".title"));
        confTitle.getStyleClass().add("global-config-title");
        
        confTitleHBox.getChildren().add(confTitle);
        
        ScrollPane paramsScrollP = new ScrollPane();
        paramsScrollP.getStyleClass().add("params-scroll");
        
        GridPane paramsGrid = new GridPane();
        paramsGrid.getStyleClass().add("params-grid");
        ColumnConstraints colConstraints = new ColumnConstraints();
        colConstraints.setHalignment(HPos.RIGHT);
        paramsGrid.getColumnConstraints().add(colConstraints);
        int rowInd = 0;
        
        langByDisplayName = new HashMap<>();
        for (Locale lang : Lang.AVAILABLE_LANGUAGES) {
            String name = lang.getDisplayName();
            if (!langByDisplayName.containsKey(name)) {
                langByDisplayName.put(name, lang);
            } else {
                name = lang.toLanguageTag();
                if (!langByDisplayName.containsKey(name)) {
                    langByDisplayName.put(name, lang);
                } else {
                    name = lang.toString();
                    if (!langByDisplayName.containsKey(name)) {
                        langByDisplayName.put(name, lang);
                    } else {
                        throw new IllegalStateException(
                                "Failed to determine an unique display name for available languages (lang = "
                                        + lang + ")."
                        );
                    }
                }
            }
        }
        langBox = new ComboBox<>();
        langBox.setEditable(false);
        langBox.getItems().addAll(new ArrayList<>(langByDisplayName.keySet()));
        UIUtils.addFieldToGrid(paramsGrid, rowInd++, CONFIG_LANG_BASE + ".language", langBox, true);
        
        customLangFileF = new OptionalFileField(
                Lang.get(CONFIG_LANG_BASE + ".customLanguageFile.invalid"),
                new ExtensionFilter("TigerSafe language file", "*.properties")
        );
        UIUtils.addNodeToGrid(
                paramsGrid,
                rowInd++,
                CONFIG_LANG_BASE + ".customLanguageFile",
                customLangFileF.rootHBox,
                true,
                customLangFileF.textField
        );
        
        customStylesheetFileF = new OptionalFileField(
                Lang.get(CONFIG_LANG_BASE + ".customStylesheet.invalid"),
                new ExtensionFilter("JavaFX CSS file", "*.css")
        );
        UIUtils.addNodeToGrid(
                paramsGrid,
                rowInd++,
                CONFIG_LANG_BASE + ".customStylesheet",
                customStylesheetFileF.rootHBox,
                true,
                customStylesheetFileF.textField
        );
        
        ciphersBoxByConfig = new EnumMap<>(ConfigCipher.class);
        for (ConfigCipher confCipher : ConfigCipher.values()) {
            ComboBox<String> cipherBox = new ComboBox<>();
            cipherBox.setEditable(false);
            cipherBox.getItems().addAll(CiphersManager.getCiphersName());
            UIUtils.addFieldToGrid(
                    paramsGrid,
                    rowInd++,
                    CONFIG_LANG_BASE + ".cipher." + confCipher.getName(),
                    cipherBox,
                    true
            );
            ciphersBoxByConfig.put(confCipher, cipherBox);
            cipherBox.setValue(confCipher.getCipher().name);
            cipherBox.valueProperty().addListener((ov, oldVal, newVal) -> {
                updateSaveBtnAvailability();
            });
        }
        
        keyboardShortcuts = new EnumMap<>(KeyboardShortcut.class);
        for (KeyboardShortcut shortcut : KeyboardShortcut.values()) {
            HBox shortcutHBox = new HBox();
            shortcutHBox.getStyleClass().add("keyboard-shortcut-hbox");
            
            TextField shortcutField = new TextField();
            
            ToggleButton shortcutManualModeBtn = new ToggleButton();
            shortcutManualModeBtn.setGraphic(UIUtils.newIcon("config"));
            UIUtils.setTooltip(
                    shortcutManualModeBtn,
                    CONFIG_LANG_BASE + ".keyboardShortcut.manualMode.button"
            );
            
            shortcutHBox.getChildren().addAll(shortcutField, shortcutManualModeBtn);
            UIUtils.addNodeToGrid(
                    paramsGrid,
                    rowInd++,
                    CONFIG_LANG_BASE + ".keyboardShortcut." + shortcut.getName(),
                    shortcutHBox,
                    true,
                    shortcutField
            );
            
            KeyCombination initKeyComb = shortcut.getKeyCombination();
            keyboardShortcuts.put(shortcut, initKeyComb);
            shortcutField.setText(initKeyComb.getName());
            FieldValidityIndication shortcutValidIndic = new FieldValidityIndication(
                    shortcutField,
                    Lang.get(CONFIG_LANG_BASE + ".keyboardShortcut.invalid"),
                    true
            );
            shortcutField.editableProperty().bind(shortcutManualModeBtn.selectedProperty());
            
            shortcutField.setOnKeyPressed((ev) -> {
                if (shortcutManualModeBtn.isSelected()) {
                    return;
                }
                
                KeyCode keyCode = ev.getCode();
                if (keyCode.isModifierKey() || KeyCode.UNDEFINED.equals(keyCode)) {
                    KeyCombination oldKeyComb = keyboardShortcuts.get(shortcut);
                    if (oldKeyComb instanceof KeyCodeCombination oldKeyCodeComb) {
                        keyCode = oldKeyCodeComb.getCode();
                    }
                    if (keyCode.isModifierKey() || KeyCode.UNDEFINED.equals(keyCode)) {
                        return;
                    }
                }
                KeyCodeCombination newKeyComb = new KeyCodeCombination(
                        keyCode,
                        ev.isShiftDown() ? ModifierValue.DOWN : ModifierValue.UP,
                        ev.isControlDown() ? ModifierValue.DOWN : ModifierValue.UP,
                        ev.isAltDown() ? ModifierValue.DOWN : ModifierValue.UP,
                        ev.isMetaDown() ? ModifierValue.DOWN : ModifierValue.UP,
                        ModifierValue.UP
                );
                shortcutField.setText(newKeyComb.getName());
            });
            
            shortcutField.textProperty().addListener((ov, oldVal, newVal) -> {
                try {
                    keyboardShortcuts.put(
                            shortcut,
                            CheckUtils.notNull(KeyCombination.valueOf(CheckUtils.notEmpty(newVal)))
                    );
                    shortcutValidIndic.setValidity(true);
                } catch (IllegalArgumentException | NullPointerException ex) {
                    keyboardShortcuts.put(shortcut, initKeyComb);
                    shortcutValidIndic.setValidity(false);
                }
                updateSaveBtnAvailability();
            });
        }
        
        paramsScrollP.setContent(paramsGrid);
        
        HBox saveBtnHBox = new HBox();
        saveBtnHBox.getStyleClass().add("save-button-hbox");
        
        saveBtn = UIUtils.newBtn(CONFIG_LANG_BASE + ".save.button", "save", true, false);
        
        saveBtnHBox.getChildren().add(saveBtn);
        
        rootVBox.getChildren().addAll(closeBtnHBox, confTitleHBox, paramsScrollP, saveBtnHBox);
        
        // Dynamic
        
        GlobalConfig conf = GlobalConfig.getInstance();
        String curLangDisplayName = getLangDisplayName(conf.getLanguage());
        if (curLangDisplayName != null) {
            langBox.setValue(curLangDisplayName);
        }
        customLangFileF.setVal(conf.getCustomLanguageFile());
        customStylesheetFileF.setVal(conf.getCustomStylesheetFile());
        
        updateSaveBtnAvailability();
        
        langBox.valueProperty().addListener((ov, oldVal, newVal) -> {
            updateSaveBtnAvailability();
        });
        
        customLangFileF.valChangeNotifier.addListener(() -> {
            updateSaveBtnAvailability();
        });
        
        customStylesheetFileF.valChangeNotifier.addListener(() -> {
            updateSaveBtnAvailability();
        });
        
        closeBtn.setOnAction((e) -> {
            if (hasChanges()) {
                Alert confirmPopup = new Alert(
                        AlertType.CONFIRMATION,
                        Lang.get("GlobalConfigUI.close.unsaved.popup"),
                        ButtonType.YES,
                        ButtonType.NO
                );
                UIUtils.showDialogAndWait(confirmPopup).ifPresent((clickedBtn) -> {
                    if (clickedBtn == ButtonType.YES) {
                        save();
                    }
                });
            }
            UIApp.getInstance().showSafeSelection();
        });
        
        saveBtn.setOnAction((e) -> {
            boolean willNeedRefresh =
                    !Objects.equals(langByDisplayName.get(langBox.getValue()), conf.getLanguage());
            save();
            if (willNeedRefresh) {
                UIApp.getInstance().showGlobalConfig();
            }
        });
        
        scene = new Scene(rootVBox);
    }
    
    @Override
    public Scene getScene() {
        return scene;
    }
    
    private void updateSaveBtnAvailability() {
        saveBtn.setDisable(!hasChanges());
    }
    
    private boolean hasChanges() {
        GlobalConfig conf = GlobalConfig.getInstance();
        
        if (!Objects.equals(langByDisplayName.get(langBox.getValue()), conf.getLanguage())) {
            return true;
        }
        
        if (
            customLangFileF.checkValid()
                    && !Objects.equals(customLangFileF.getVal(), conf.getCustomLanguageFile())
        ) {
            return true;
        }
        
        if (
            customStylesheetFileF.checkValid()
                    && !Objects
                            .equals(customStylesheetFileF.getVal(), conf.getCustomStylesheetFile())
        ) {
            return true;
        }
        
        for (Map.Entry<ConfigCipher, ComboBox<String>> ent : ciphersBoxByConfig.entrySet()) {
            if (!Objects.equals(ent.getKey().getCipher().name, ent.getValue().getValue())) {
                return true;
            }
        }
        
        for (Map.Entry<KeyboardShortcut, KeyCombination> ent : keyboardShortcuts.entrySet()) {
            if (!Objects.equals(ent.getKey().getKeyCombination(), ent.getValue())) {
                return true;
            }
        }
        
        return false;
    }
    
    private void save() {
        GlobalConfig conf = GlobalConfig.getInstance();
        boolean hasChanged = false;
        
        Locale newLang = langByDisplayName.get(langBox.getValue());
        if (!Objects.equals(newLang, conf.getLanguage())) {
            conf.setLanguage(newLang);
            hasChanged = true;
        }
        
        if (customLangFileF.checkValid()) {
            File newCustomLangFile = customLangFileF.getVal();
            if (!Objects.equals(newCustomLangFile, conf.getCustomLanguageFile())) {
                try {
                    conf.setCustomLanguageFile(newCustomLangFile);
                    hasChanged = true;
                } catch (IllegalArgumentException ex) {
                    UIApp.getInstance()
                            .showError(
                                    Lang.get(CONFIG_LANG_BASE + ".customLanguageFile.error.title"),
                                    ex
                            );
                }
            }
        }
        
        if (customStylesheetFileF.checkValid()) {
            File newCustomStylesheetFile = customStylesheetFileF.getVal();
            if (!Objects.equals(newCustomStylesheetFile, conf.getCustomStylesheetFile())) {
                try {
                    conf.setCustomStylesheet(newCustomStylesheetFile);
                    hasChanged = true;
                } catch (IllegalArgumentException ex) {
                    UIApp.getInstance()
                            .showError(
                                    Lang.get(CONFIG_LANG_BASE + ".customStylesheet.error.title"),
                                    ex
                            );
                }
            }
        }
        
        for (Map.Entry<ConfigCipher, ComboBox<String>> ent : ciphersBoxByConfig.entrySet()) {
            ConfigCipher confCipher = ent.getKey();
            Cipher newCipher = CiphersManager.getCipherByName(ent.getValue().getValue());
            if (!Objects.equals(confCipher.getCipher(), newCipher)) {
                conf.setCipher(confCipher, newCipher.name);
                hasChanged = true;
            }
        }
        
        UIConfig uiConf = UIConfig.getInstance();
        for (Map.Entry<KeyboardShortcut, KeyCombination> ent : keyboardShortcuts.entrySet()) {
            KeyboardShortcut shortcut = ent.getKey();
            KeyCombination newKeyComb = ent.getValue();
            if (!Objects.equals(shortcut.getKeyCombination(), newKeyComb)) {
                uiConf.setKeyboardShortcut(shortcut, newKeyComb);
                hasChanged = true;
            }
        }
        
        if (hasChanged) {
            try {
                conf.updateUserFile();
            } catch (IOException ex) {
                UIApp.getInstance().showError(ex);
            }
            Alert successPopup = new Alert(
                    AlertType.INFORMATION,
                    Lang.get(CONFIG_LANG_BASE + ".save.success.popup"),
                    ButtonType.OK
            );
            UIUtils.showDialogAndWait(successPopup);
        }
        updateSaveBtnAvailability();
    }
    
    @Override
    public void destroy() throws DestroyFailedException {}
    
    @Override
    public boolean isDestroyed() {
        return true;
    }
    
    private String getLangDisplayName(Locale lang) {
        if (lang != null) {
            for (Map.Entry<String, Locale> ent : langByDisplayName.entrySet()) {
                if (ent.getValue().equals(lang)) {
                    return ent.getKey();
                }
            }
        }
        return null;
    }
    
}
