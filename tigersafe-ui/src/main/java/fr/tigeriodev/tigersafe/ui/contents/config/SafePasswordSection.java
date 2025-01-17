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

import java.util.Arrays;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.contents.config.ConfigTab.ContentHolder.Section;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.SecureUnclearField;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

class SafePasswordSection extends Section implements Destroyable {
    
    static final String SECTION_LANG_BASE = ConfigTab.TAB_LANG_BASE + ".safePassword";
    
    final VBox contentVBox;
    final ViewableUnclearField newSafePwField;
    final ViewableUnclearField newSafePwConfirmField;
    final SecureUnclearField curSafePwField;
    final FieldValidityIndication curSafePwValidIndic;
    final Button saveBtn;
    
    SafePasswordSection(ConfigTab tab) {
        super(tab);
        contentVBox = new VBox();
        
        GridPane grid = new GridPane();
        grid.getStyleClass().add("grid");
        ColumnConstraints colConstraints = new ColumnConstraints();
        colConstraints.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().add(colConstraints);
        
        newSafePwField = new ViewableUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addViewableUnclearFieldToGrid(
                grid,
                0,
                SECTION_LANG_BASE + ".newValue",
                newSafePwField,
                true,
                true
        );
        
        newSafePwConfirmField = new ViewableUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addViewableUnclearFieldToGrid(
                grid,
                1,
                SECTION_LANG_BASE + ".newValueConfirmation",
                newSafePwConfirmField,
                true,
                true
        );
        
        curSafePwField = new SecureUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addFieldToGrid(grid, 2, SECTION_LANG_BASE + ".currentValue", curSafePwField, true);
        curSafePwValidIndic = new FieldValidityIndication(
                curSafePwField,
                Lang.get(SECTION_LANG_BASE + ".currentValue.invalid"),
                true
        );
        
        saveBtn = UIUtils.newBtn(SECTION_LANG_BASE + ".save.button", "save", true, false);
        
        contentVBox.getChildren().addAll(grid, saveBtn);
        
        // Dynamic
        
        Runnable onNewSafePwChange = () -> {
            boolean validNewPw = SafeDataManager.isValidSafePw(newSafePwField.getVal());
            newSafePwField.setValidity(validNewPw);
            boolean sameConfirm =
                    Arrays.equals(newSafePwField.getVal(), newSafePwConfirmField.getVal());
            newSafePwConfirmField.setValidity(sameConfirm);
            updateSaveBtnAvailability();
        };
        newSafePwField.valChangeNotifier.addListener(onNewSafePwChange);
        
        newSafePwConfirmField.valChangeNotifier.addListener(onNewSafePwChange);
        
        curSafePwField.valChangeNotifier.addListener(() -> {
            curSafePwValidIndic.setValidity(true);
            updateSaveBtnAvailability();
        });
        
        saveBtn.setOnAction((e) -> {
            if (dm.hasChanges()) {
                ConfigTab.showUnsavedChangesPopup();
                return;
            }
            if (!SafeDataManager.isValidSafePw(newSafePwField.getVal())) {
                newSafePwField.setValidity(false);
                updateSaveBtnAvailability();
                return;
            }
            if (!Arrays.equals(newSafePwField.getVal(), newSafePwConfirmField.getVal())) {
                newSafePwConfirmField.setValidity(false);
                updateSaveBtnAvailability();
                return;
            }
            if (!dm.isSafePw(curSafePwField.getVal())) {
                if (ui.onIncorrectSafePwTyped()) {
                    curSafePwValidIndic.setValidity(false);
                    updateSaveBtnAvailability();
                }
                return;
            }
            
            saveBtn.setDisable(true);
            try {
                dm.changeSafePw(newSafePwField.getVal());
                
                newSafePwField.getValHolder().clear();
                newSafePwConfirmField.getValHolder().clear();
                curSafePwField.getValHolder().clear();
                
                newSafePwField.refresh();
                newSafePwConfirmField.refresh();
                curSafePwField.refresh();
                
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
    
    private void updateSaveBtnAvailability() {
        saveBtn.setDisable(
                !newSafePwField.isValid()
                        || !newSafePwConfirmField.isValid()
                        || !curSafePwValidIndic.isValid()
        );
    }
    
    @Override
    public String getTitle() {
        return Lang.get(SECTION_LANG_BASE + ".title");
    }
    
    @Override
    public String getCSSClass() {
        return "safe-password";
    }
    
    @Override
    public Node getContent() {
        return contentVBox;
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = true;
        success = MemUtils.tryDestroy(newSafePwField) && success;
        success = MemUtils.tryDestroy(newSafePwConfirmField) && success;
        success = MemUtils.tryDestroy(curSafePwField) && success;
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return newSafePwField.isDestroyed()
                && newSafePwConfirmField.isDestroyed()
                && curSafePwField.isDestroyed();
    }
    
}
