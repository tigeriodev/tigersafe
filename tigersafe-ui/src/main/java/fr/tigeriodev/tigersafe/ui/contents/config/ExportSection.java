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

import java.io.File;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.data.SafeSerializationManager;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.contents.config.ConfigTab.ContentHolder.Section;
import fr.tigeriodev.tigersafe.ui.fields.DirectoryField;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.IntegerField;
import fr.tigeriodev.tigersafe.ui.fields.SecureUnclearField;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

class ExportSection extends Section implements Destroyable {
    
    static final String SECTION_LANG_BASE = ConfigTab.TAB_LANG_BASE + ".export";
    
    final VBox contentVBox;
    final DirectoryField serialDirField;
    final TextField serialFileNameField;
    final ComboBox<String> serialCipherBox;
    final ViewableUnclearField serialPwField;
    final IntegerField serialVerField;
    final SecureUnclearField safePwField;
    final FieldValidityIndication safePwValidIndic;
    final Button exportBtn;
    
    private static boolean isValidFileName(String name, File dir) {
        return name != null && !name.isEmpty() && (dir == null || !getFile(dir, name).exists());
    }
    
    private static File getFile(File dir, String fileName) {
        return dir.toPath().resolve(fileName).toFile();
    }
    
    ExportSection(ConfigTab tab) {
        super(tab);
        contentVBox = new VBox();
        
        GridPane grid = new GridPane();
        grid.getStyleClass().add("grid");
        ColumnConstraints colConstraints = new ColumnConstraints();
        colConstraints.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().add(colConstraints);
        
        serialDirField =
                new DirectoryField(Lang.get(SECTION_LANG_BASE + ".serializedDirectory.invalid"));
        UIUtils.addFieldToGrid(
                grid,
                0,
                SECTION_LANG_BASE + ".serializedDirectory",
                serialDirField.textField,
                true
        );
        
        serialFileNameField = new TextField();
        UIUtils.addFieldToGrid(
                grid,
                1,
                SECTION_LANG_BASE + ".serializedFileName",
                serialFileNameField,
                true
        );
        
        serialCipherBox = new ComboBox<>();
        serialCipherBox.setEditable(false);
        serialCipherBox.getItems().addAll(CiphersManager.getCiphersName());
        UIUtils.addFieldToGrid(
                grid,
                2,
                SECTION_LANG_BASE + ".serializedCipher",
                serialCipherBox,
                true
        );
        
        serialPwField = new ViewableUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addViewableUnclearFieldToGrid(
                grid,
                3,
                SECTION_LANG_BASE + ".serializedPassword",
                serialPwField,
                true,
                true
        );
        
        serialVerField = new IntegerField(1, SafeSerializationManager.MAX_SERIAL_VER, new int[] {
                -1, 1
        });
        UIUtils.addNodeToGrid(
                grid,
                4,
                SECTION_LANG_BASE + ".serializedVersion",
                serialVerField.rootHBox,
                true,
                serialVerField.inputField
        );
        
        safePwField = new SecureUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addFieldToGrid(grid, 5, SECTION_LANG_BASE + ".safePassword", safePwField, true);
        safePwValidIndic = new FieldValidityIndication(
                safePwField,
                Lang.get(SECTION_LANG_BASE + ".safePassword.invalid"),
                true
        );
        
        exportBtn = UIUtils.newBtn(SECTION_LANG_BASE + ".button", null, true, false);
        
        contentVBox.getChildren().addAll(grid, exportBtn);
        
        // Dynamic
        
        FieldValidityIndication serialFileNameValidIndic = new FieldValidityIndication(
                serialFileNameField,
                Lang.get(SECTION_LANG_BASE + ".serializedFileName.invalid"),
                true
        );
        serialFileNameField.textProperty().addListener((ov, oldName, newName) -> {
            serialFileNameValidIndic.setValidity(isValidFileName(newName, serialDirField.getVal()));
            updateExportBtnAvailability();
        });
        serialDirField.valChangeNotifier.addListener(() -> {
            serialFileNameValidIndic.setValidity(
                    isValidFileName(serialFileNameField.getText(), serialDirField.getVal())
            );
            updateExportBtnAvailability();
        });
        
        serialPwField.valChangeNotifier.addListener(() -> {
            serialPwField.setValidity(SafeDataManager.isValidSafePw(serialPwField.getVal()));
            updateExportBtnAvailability();
        });
        
        safePwField.valChangeNotifier.addListener(() -> {
            safePwValidIndic.setValidity(true);
            updateExportBtnAvailability();
        });
        
        File safeFile = dm.getSafeFile();
        serialDirField.setVal(safeFile.getParentFile());
        serialFileNameField.setText("serialized-" + safeFile.getName());
        
        serialCipherBox.setValue(GlobalConfig.ConfigCipher.USER_DATA.getCipher().getName());
        serialVerField.setVal(SafeSerializationManager.MAX_SERIAL_VER, false);
        
        exportBtn.setOnAction((e) -> {
            if (dm.hasChanges()) {
                ConfigTab.showUnsavedChangesPopup();
                return;
            }
            File serialDir = serialDirField.getVal();
            if (!DirectoryField.isValidDir(serialDir)) {
                serialDirField.validIndic.setValidity(false);
                updateExportBtnAvailability();
                return;
            }
            if (!isValidFileName(serialFileNameField.getText(), serialDir)) {
                serialFileNameValidIndic.setValidity(false);
                updateExportBtnAvailability();
                return;
            }
            File serialFile = getFile(serialDir, serialFileNameField.getText());
            if (serialFile == null || serialFile.exists()) {
                serialFileNameValidIndic.setValidity(false);
                updateExportBtnAvailability();
                return;
            }
            if (!SafeDataManager.isValidSafePw(serialPwField.getVal())) {
                serialPwField.setValidity(false);
                updateExportBtnAvailability();
                return;
            }
            
            if (!dm.isSafePw(safePwField.getVal())) {
                if (ui.onIncorrectSafePwTyped()) {
                    safePwValidIndic.setValidity(false);
                    updateExportBtnAvailability();
                }
                return;
            }
            
            exportBtn.setDisable(true);
            try {
                dm.exportDataTo(
                        serialFile,
                        CiphersManager.getCipherByName(serialCipherBox.getValue()),
                        serialPwField.getVal(),
                        (short) serialVerField.getVal()
                );
                
                safePwField.getValHolder().clear();
                serialPwField.getValHolder().clear();
                
                safePwField.refresh();
                serialPwField.refresh();
                
                Alert successPopup = new Alert(
                        AlertType.INFORMATION,
                        Lang.get(
                                SECTION_LANG_BASE + ".success.popup",
                                serialFile.getAbsolutePath()
                        ),
                        ButtonType.OK
                );
                UIUtils.showDialogAndWait(successPopup);
            } catch (Exception ex) {
                UIApp.getInstance().showError(ex);
            } finally {
                updateExportBtnAvailability();
            }
        });
        
        updateExportBtnAvailability();
    }
    
    private void updateExportBtnAvailability() {
        exportBtn.setDisable(
                !safePwValidIndic.isValid()
                        || !DirectoryField.isValidDir(serialDirField.getVal())
                        || !isValidFileName(serialFileNameField.getText(), serialDirField.getVal())
                        || !SafeDataManager.isValidSafePw(serialPwField.getVal())
        );
    }
    
    @Override
    public String getTitle() {
        return Lang.get(SECTION_LANG_BASE + ".title");
    }
    
    @Override
    public String getCSSClass() {
        return "export";
    }
    
    @Override
    public Node getContent() {
        return contentVBox;
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = true;
        success = MemUtils.tryDestroy(safePwField) && success;
        success = MemUtils.tryDestroy(serialPwField) && success;
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return safePwField.isDestroyed() && serialPwField.isDestroyed();
    }
    
}
