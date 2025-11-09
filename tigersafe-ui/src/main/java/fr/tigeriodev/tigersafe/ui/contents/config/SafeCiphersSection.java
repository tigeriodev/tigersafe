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

package fr.tigeriodev.tigersafe.ui.contents.config;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.contents.config.ConfigTab.ContentHolder.Section;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.SecureUnclearField;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

class SafeCiphersSection extends Section implements Destroyable {
    
    static final String SECTION_LANG_BASE = ConfigTab.TAB_LANG_BASE + ".safeCiphers";
    
    final VBox contentVBox;
    final ComboBox<String> internalDataBox;
    final ComboBox<String> userDataBox;
    final SecureUnclearField safePwField;
    final FieldValidityIndication safePwValidIndic;
    final Button saveBtn;
    
    SafeCiphersSection(ConfigTab tab) {
        super(tab);
        contentVBox = new VBox();
        
        GridPane grid = new GridPane();
        grid.getStyleClass().add("grid");
        ColumnConstraints colConstraints = new ColumnConstraints();
        colConstraints.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().add(colConstraints);
        
        internalDataBox = new ComboBox<>();
        internalDataBox.setEditable(false);
        internalDataBox.getItems().addAll(CiphersManager.getCiphersName());
        UIUtils.addFieldToGrid(grid, 0, SECTION_LANG_BASE + ".internalData", internalDataBox, true);
        
        userDataBox = new ComboBox<>();
        userDataBox.setEditable(false);
        userDataBox.getItems().addAll(CiphersManager.getCiphersName());
        UIUtils.addFieldToGrid(grid, 1, SECTION_LANG_BASE + ".userData", userDataBox, true);
        
        safePwField = new SecureUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addFieldToGrid(grid, 2, SECTION_LANG_BASE + ".safePassword", safePwField, true);
        safePwValidIndic = new FieldValidityIndication(
                safePwField,
                Lang.get(SECTION_LANG_BASE + ".safePassword.invalid"),
                true
        );
        
        saveBtn = UIUtils.newBtn(SECTION_LANG_BASE + ".save.button", "save", true, false);
        
        contentVBox.getChildren().addAll(grid, saveBtn);
        
        // Dynamic
        
        safePwField.valChangeNotifier.addListener(() -> {
            safePwValidIndic.setValidity(true);
            updateSaveBtnAvailability();
        });
        
        internalDataBox.setValue(GlobalConfig.ConfigCipher.INTERNAL_DATA.getCipher().getName());
        userDataBox.setValue(GlobalConfig.ConfigCipher.USER_DATA.getCipher().getName());
        
        internalDataBox.valueProperty().addListener((ov, oldVal, newVal) -> {
            updateSaveBtnAvailability();
        });
        userDataBox.valueProperty().addListener((ov, oldVal, newVal) -> {
            updateSaveBtnAvailability();
        });
        
        saveBtn.setOnAction((e) -> {
            if (dm.hasChanges()) {
                ConfigTab.showUnsavedChangesPopup();
                return;
            }
            if (!dm.isSafePw(safePwField.getVal())) {
                if (ui.onIncorrectSafePwTyped()) {
                    safePwValidIndic.setValidity(false);
                    updateSaveBtnAvailability();
                }
                return;
            }
            if (areCurCiphers(internalDataBox.getValue(), userDataBox.getValue())) {
                updateSaveBtnAvailability();
                return;
            }
            
            saveBtn.setDisable(true);
            try {
                dm.changeSafeCiphers(internalDataBox.getValue(), userDataBox.getValue());
                
                safePwField.getValHolder().clear();
                safePwField.refresh();
                
                Alert successPopup = new Alert(
                        AlertType.INFORMATION,
                        Lang.get(SECTION_LANG_BASE + ".save.success.popup"),
                        ButtonType.OK
                );
                UIUtils.showDialogAndWait(successPopup);
            } catch (Exception ex) {
                UIApp.getInstance().showError(ex);
            } finally {
                updateSaveBtnAvailability();
            }
        });
        
        updateSaveBtnAvailability();
    }
    
    private static boolean areCurCiphers(String newInternalCipherName, String newUserCipherName) {
        return GlobalConfig.ConfigCipher.INTERNAL_DATA.getCipher()
                .getName()
                .equals(newInternalCipherName)
                && GlobalConfig.ConfigCipher.USER_DATA.getCipher()
                        .getName()
                        .equals(newUserCipherName);
    }
    
    private void updateSaveBtnAvailability() {
        saveBtn.setDisable(
                !safePwValidIndic.isValid()
                        || areCurCiphers(internalDataBox.getValue(), userDataBox.getValue())
        );
    }
    
    @Override
    public String getTitle() {
        return Lang.get(SECTION_LANG_BASE + ".title");
    }
    
    @Override
    public String getCSSClass() {
        return "safe-ciphers";
    }
    
    @Override
    public Node getContent() {
        return contentVBox;
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        safePwField.destroy();
    }
    
    @Override
    public boolean isDestroyed() {
        return safePwField.isDestroyed();
    }
    
}
