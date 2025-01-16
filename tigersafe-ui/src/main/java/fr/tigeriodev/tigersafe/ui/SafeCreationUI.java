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
import java.security.GeneralSecurityException;

import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.ui.fields.DirectoryField;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public class SafeCreationUI implements UI {
    
    private static final String CREATE_LANG_BASE = "SafeCreationUI.createSafe";
    
    /**
     * @param name Name without extension
     * @return
     */
    private static boolean isValidSafeFileName(String name, File dir) {
        return name != null
                && !name.isEmpty()
                && name.matches("[a-zA-Z0-9]+")
                && (dir == null || !getSafeFile(dir, name).exists());
    }
    
    private static File getSafeFile(File dir, String fileName) {
        return dir.toPath().resolve(fileName + ".dat").toFile();
    }
    
    private final DirectoryField safeDirField;
    private final TextField safeFileNameField;
    private final ViewableUnclearField safePwField;
    private final Button createSafeBtn;
    private final Scene scene;
    
    public SafeCreationUI() {
        GridPane rootGrid = new GridPane();
        rootGrid.setId("safe-creation-root-grid");
        
        Button selectSafeBtn = new Button(Lang.get("SafeCreationUI.selectSafe.button.text"));
        rootGrid.add(selectSafeBtn, 0, 0, 2, 1);
        
        HBox createSafeTitleHBox = new HBox();
        createSafeTitleHBox.getStyleClass().add("create-safe-title-hbox");
        
        Text createSafeTitle = new Text(Lang.get(CREATE_LANG_BASE + ".title"));
        createSafeTitle.getStyleClass().add("create-safe-title");
        
        createSafeTitleHBox.getChildren().add(createSafeTitle);
        rootGrid.add(createSafeTitleHBox, 0, 2, 2, 1);
        
        safeDirField = new DirectoryField(Lang.get(CREATE_LANG_BASE + ".safeDirectory.invalid"));
        UIUtils.addFieldToGrid(
                rootGrid,
                4,
                CREATE_LANG_BASE + ".safeDirectory",
                safeDirField.textField,
                true
        );
        
        safeFileNameField = new TextField();
        UIUtils.addFieldToGrid(
                rootGrid,
                5,
                CREATE_LANG_BASE + ".safeFileName",
                safeFileNameField,
                true
        );
        
        safePwField = new ViewableUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addViewableUnclearFieldToGrid(
                rootGrid,
                6,
                CREATE_LANG_BASE + ".safePassword",
                safePwField,
                true,
                true
        );
        
        HBox createSafeBtnHBox = new HBox();
        createSafeBtnHBox.getStyleClass().add("create-safe-button-hbox");
        
        createSafeBtn = new Button(Lang.get(CREATE_LANG_BASE + ".button.text"));
        
        createSafeBtnHBox.getChildren().add(createSafeBtn);
        rootGrid.add(createSafeBtnHBox, 1, 8);
        
        // Dynamic
        
        updateCreateSafeBtnAvailability();
        
        selectSafeBtn.setOnAction((e) -> {
            UIApp.getInstance().showSafeSelection();
        });
        
        FieldValidityIndication safeFileNameValidIndic = new FieldValidityIndication(
                safeFileNameField,
                Lang.get(CREATE_LANG_BASE + ".safeFileName.invalid"),
                true
        );
        safeFileNameField.textProperty().addListener((ov, oldName, newName) -> {
            safeFileNameValidIndic.setValidity(isValidSafeFileName(newName, safeDirField.getVal()));
            updateCreateSafeBtnAvailability();
        });
        safeDirField.valChangeNotifier.addListener(() -> {
            if (CheckUtils.isNotEmpty(safeFileNameField.getText())) {
                safeFileNameValidIndic.setValidity(
                        isValidSafeFileName(safeFileNameField.getText(), safeDirField.getVal())
                );
            }
            updateCreateSafeBtnAvailability();
        });
        
        safePwField.valChangeNotifier.addListener(() -> {
            safePwField.setValidity(SafeDataManager.isValidSafePw(safePwField.getVal()));
            updateCreateSafeBtnAvailability();
        });
        
        File lastSafeFile = GlobalConfig.getInstance().getLastSafeFile();
        if (lastSafeFile != null) {
            File lastDir = lastSafeFile.getParentFile();
            if (lastDir != null) {
                safeDirField.setVal(lastDir);
            }
        }
        
        createSafeBtn.setOnAction((e) -> {
            File safeDir = safeDirField.getVal();
            if (!DirectoryField.isValidDir(safeDir)) {
                safeDirField.validIndic.setValidity(false);
                updateCreateSafeBtnAvailability();
                return;
            }
            if (!isValidSafeFileName(safeFileNameField.getText(), safeDir)) {
                safeFileNameValidIndic.setValidity(false);
                updateCreateSafeBtnAvailability();
                return;
            }
            if (!SafeDataManager.isValidSafePw(safePwField.getVal())) {
                safePwField.setValidity(false);
                updateCreateSafeBtnAvailability();
                return;
            }
            
            File safeFile = getSafeFile(safeDir, safeFileNameField.getText());
            if (safeFile == null || safeFile.exists()) {
                safeFileNameValidIndic.setValidity(false);
                updateCreateSafeBtnAvailability();
                return;
            }
            
            SafeDataManager dm = new SafeDataManager(safeFile, safePwField.getVal());
            createSafeBtn.setDisable(true);
            try {
                dm.updateSafeFile();
                
                GlobalConfig globalConfig = GlobalConfig.getInstance();
                globalConfig.setLastSafeFile(safeFile);
                globalConfig.updateUserFile();
                
                Alert successPopup = new Alert(
                        AlertType.INFORMATION,
                        Lang.get(CREATE_LANG_BASE + ".success.popup", safeFile.getAbsolutePath()),
                        ButtonType.OK
                );
                UIUtils.showDialogAndWait(successPopup);
                
                UIApp.getInstance().showSafeSelection();
            } catch (IOException | GeneralSecurityException | DestroyFailedException ex) {
                UIApp.getInstance().showError(ex);
                updateCreateSafeBtnAvailability();
            } finally {
                UIUtils.tryDestroy(dm);
            }
        });
        
        scene = new Scene(rootGrid);
    }
    
    @Override
    public Scene getScene() {
        return scene;
    }
    
    private void updateCreateSafeBtnAvailability() {
        File safeDir = safeDirField.getVal();
        boolean validInputs = DirectoryField.isValidDir(safeDir)
                && isValidSafeFileName(safeFileNameField.getText(), safeDir)
                && SafeDataManager.isValidSafePw(safePwField.getVal());
        createSafeBtn.setDisable(!validInputs);
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
