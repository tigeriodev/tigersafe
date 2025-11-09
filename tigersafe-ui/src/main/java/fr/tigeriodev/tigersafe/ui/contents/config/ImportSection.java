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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.ciphers.CiphersManager;
import fr.tigeriodev.tigersafe.data.PasswordEntry;
import fr.tigeriodev.tigersafe.data.SafeData;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.data.SafeSerializationManager;
import fr.tigeriodev.tigersafe.data.TOTP;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.contents.config.ConfigTab.ContentHolder.Section;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextArea;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextField;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.FileField;
import fr.tigeriodev.tigersafe.ui.fields.SecureUnclearField;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.DatetimeUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.MutableString;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;

class ImportSection extends Section implements Destroyable {
    
    static final String SECTION_LANG_BASE = ConfigTab.TAB_LANG_BASE + ".import";
    
    final VBox contentVBox;
    final FileField serialFileField;
    final ComboBox<String> serialCipherBox;
    final ViewableUnclearField serialPwField;
    final SecureUnclearField safePwField;
    final FieldValidityIndication safePwValidIndic;
    final Button importBtn;
    
    ImportSection(ConfigTab tab) {
        super(tab);
        contentVBox = new VBox();
        
        GridPane grid = new GridPane();
        grid.getStyleClass().add("grid");
        ColumnConstraints colConstraints = new ColumnConstraints();
        colConstraints.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().add(colConstraints);
        
        serialFileField = new FileField(
                Lang.get(SECTION_LANG_BASE + ".serializedFile.invalid"),
                new ExtensionFilter("TigerSafe serialized safe file", "*")
        );
        UIUtils.addFieldToGrid(
                grid,
                0,
                SECTION_LANG_BASE + ".serializedFile",
                serialFileField.textField,
                true
        );
        
        serialCipherBox = new ComboBox<>();
        serialCipherBox.setEditable(false);
        serialCipherBox.getItems().addAll(CiphersManager.getCiphersName());
        UIUtils.addFieldToGrid(
                grid,
                1,
                SECTION_LANG_BASE + ".serializedCipher",
                serialCipherBox,
                true
        );
        
        serialPwField = new ViewableUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addViewableUnclearFieldToGrid(
                grid,
                2,
                SECTION_LANG_BASE + ".serializedPassword",
                serialPwField,
                true,
                true
        );
        
        safePwField = new SecureUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addFieldToGrid(grid, 3, SECTION_LANG_BASE + ".safePassword", safePwField, true);
        safePwValidIndic = new FieldValidityIndication(
                safePwField,
                Lang.get(SECTION_LANG_BASE + ".safePassword.invalid"),
                true
        );
        
        importBtn = UIUtils.newBtn(SECTION_LANG_BASE + ".button", null, true, false);
        
        contentVBox.getChildren().addAll(grid, importBtn);
        
        // Dynamic
        
        serialFileField.valChangeNotifier.addListener(() -> {
            updateImportBtnAvailability();
        });
        
        serialPwField.valChangeNotifier.addListener(() -> {
            serialPwField.setValidity(serialPwField.getVal().length > 0);
            updateImportBtnAvailability();
        });
        
        safePwField.valChangeNotifier.addListener(() -> {
            safePwValidIndic.setValidity(true);
            updateImportBtnAvailability();
        });
        
        serialCipherBox.setValue(GlobalConfig.ConfigCipher.USER_DATA.getCipher().getName());
        
        importBtn.setOnAction((e) -> {
            if (dm.hasChanges()) {
                ConfigTab.showUnsavedChangesPopup();
                return;
            }
            
            File serialFile = serialFileField.checkValid() ? serialFileField.getVal() : null;
            if (serialFile == null) {
                updateImportBtnAvailability();
                return;
            }
            
            if (serialPwField.getVal().length == 0) {
                serialPwField.setValidity(false);
                updateImportBtnAvailability();
                return;
            }
            
            if (!dm.isSafePw(safePwField.getVal())) {
                if (ui.onIncorrectSafePwTyped()) {
                    safePwValidIndic.setValidity(false);
                    updateImportBtnAvailability();
                }
                return;
            }
            
            importBtn.setDisable(true);
            try {
                SafeData deserializedSafeData = SafeSerializationManager.read(
                        serialFile,
                        CiphersManager.getCipherByName(serialCipherBox.getValue()),
                        serialPwField.getVal()
                );
                
                safePwField.getValHolder().clear();
                serialPwField.getValHolder().clear();
                
                safePwField.refresh();
                serialPwField.refresh();
                
                ReviewPopup reviewPopup = new ReviewPopup(deserializedSafeData);
                deserializedSafeData.destroy();
                reviewPopup.showAndWait();
            } catch (Exception ex) {
                UIApp.getInstance().showError(ex);
            } finally {
                updateImportBtnAvailability();
            }
        });
        
        updateImportBtnAvailability();
    }
    
    private void updateImportBtnAvailability() {
        importBtn.setDisable(
                !safePwValidIndic.isValid()
                        || !serialFileField.validIndic.isValid() // not checkValid() to make the field "valid" when never filled
                        || serialPwField.getVal().length == 0
        );
    }
    
    private class ReviewPopup implements Destroyable {
        
        static final String POPUP_LANG_BASE = SECTION_LANG_BASE + ".popup";
        
        private List<PasswordEntryElement> pwEntriesElement = new ArrayList<>();
        final Stage stage;
        final Scene scene;
        final Button validateBtn;
        
        private ReviewPopup(SafeData deserializedSafeData) {
            VBox rootVBox = new VBox();
            rootVBox.setId("safe-contents-import-review-root-vbox");
            
            ScrollPane elementsScrollP = new ScrollPane();
            elementsScrollP.getStyleClass().add("elements-scroll");
            
            VBox elementsVBox = new VBox();
            elementsVBox.getStyleClass().add("elements-vbox");
            
            validateBtn = UIUtils.newBtn(POPUP_LANG_BASE + ".validate.button", null, true, false); // declared here because needed when initializing fields (updateValidateBtnAvailability())
            
            for (PasswordEntry.Data pwEntryData : deserializedSafeData.getPwEntriesData()) {
                TitledPane titledPane = new TitledPane();
                titledPane.setText(Lang.get(POPUP_LANG_BASE + ".passwordEntry.title"));
                titledPane.getStyleClass().addAll("password-entry");
                titledPane.setExpanded(true);
                
                VBox titledPaneContent = new VBox();
                titledPaneContent.getStyleClass().add("password-entry-content-root");
                
                Button removeBtn = UIUtils.newBtn(
                        POPUP_LANG_BASE + ".passwordEntry.remove.button",
                        "cancel",
                        true,
                        false
                );
                
                PasswordEntryElement pwEntryEle = new PasswordEntryElement(pwEntryData, this);
                
                pwEntriesElement.add(pwEntryEle);
                titledPaneContent.getChildren().addAll(pwEntryEle.grid, removeBtn);
                
                titledPane.setContent(titledPaneContent);
                
                elementsVBox.getChildren().add(titledPane);
                
                removeBtn.setOnAction((e) -> {
                    String removedName = pwEntryEle.nameField.getText();
                    pwEntriesElement.remove(pwEntryEle);
                    elementsVBox.getChildren().remove(titledPane);
                    updatePwEntryNameAvailability(removedName);
                    UIUtils.tryDestroy(pwEntryEle);
                    updateValidateBtnAvailability();
                });
            }
            
            elementsScrollP.setContent(elementsVBox);
            
            Button cancelBtn =
                    UIUtils.newBtn(POPUP_LANG_BASE + ".cancel.button", "cancel", true, false);
            
            rootVBox.getChildren().addAll(elementsScrollP, validateBtn, cancelBtn);
            
            scene = new Scene(rootVBox);
            
            stage = new Stage();
            stage.setTitle(Lang.get(POPUP_LANG_BASE + ".title"));
            stage.initModality(Modality.APPLICATION_MODAL);
            UIUtils.setAppIcon(stage);
            
            // Dynamic
            
            validateBtn.setOnAction((e) -> {
                if (dm.hasChanges()) {
                    ConfigTab.showUnsavedChangesPopup();
                    return;
                }
                
                if (hasInvalid()) {
                    validateBtn.setDisable(true);
                    return;
                }
                
                PasswordEntry.Data[] pwEntriesData =
                        new PasswordEntry.Data[pwEntriesElement.size()];
                int i = 0;
                for (PasswordEntryElement ele : pwEntriesElement) {
                    pwEntriesData[i] = ele.getData();
                    if (pwEntriesData[i] == null) {
                        throw new IllegalStateException(
                                "PasswordEntryElement.getData() returns null."
                        );
                    }
                    i++;
                }
                
                validateBtn.setDisable(true);
                try {
                    SafeData validSafeData = new SafeData(pwEntriesData);
                    dm.importData(validSafeData);
                    validSafeData.dispose();
                    
                    Alert successPopup = new Alert(
                            AlertType.INFORMATION,
                            Lang.get(POPUP_LANG_BASE + ".validate.success.popup"),
                            ButtonType.OK
                    );
                    UIUtils.showDialogAndWait(successPopup);
                    
                    stage.close();
                } catch (Exception ex) {
                    for (PasswordEntry.Data pwEntryData : pwEntriesData) {
                        MemUtils.tryDestroy(pwEntryData);
                    }
                    UIApp.getInstance().showError(ex);
                    updateValidateBtnAvailability();
                }
            });
            
            cancelBtn.setOnAction((e) -> {
                stage.close();
            });
            
            for (PasswordEntryElement ele : pwEntriesElement) {
                if (ele.nameField.validIndic.isValid()) {
                    updatePwEntryNameAvailability(ele.nameField.getVal());
                }
            }
            
            updateValidateBtnAvailability();
        }
        
        private static class PasswordEntryElement implements Destroyable {
            
            public final GridPane grid;
            private String prevName;
            private final DestroyableTextField nameField;
            private final char[] initPw;
            private final ViewableUnclearField pwField;
            private final Instant lastPasswordChangeTime;
            private final DestroyableTextField siteField;
            private final DestroyableTextArea infoField;
            private final ViewableUnclearField totpField;
            
            private PasswordEntryElement(PasswordEntry.Data pwEntryData, ReviewPopup popup) {
                grid = new GridPane();
                grid.getStyleClass().add("fields-grid");
                ColumnConstraints labelsColContraints = new ColumnConstraints();
                labelsColContraints.setHalignment(HPos.RIGHT);
                grid.getColumnConstraints().add(labelsColContraints);
                
                nameField = new DestroyableTextField();
                nameField.setVal(pwEntryData.name);
                prevName = StringUtils.clone(pwEntryData.name);
                UIUtils.addDestroyTextFieldToGrid(
                        grid,
                        0,
                        "SafeContentsUI.passwordEntry.name",
                        nameField,
                        false,
                        true
                );
                nameField.validIndic.setValidity(
                        CheckUtils
                                .isNotIllegal(() -> PasswordEntry.Data.checkName(pwEntryData.name))
                ); // will be updated with first updatePwEntryNameAvailability() call when all PasswordEntryElements are setup
                
                initPw = pwEntryData.getPassword().clone();
                
                pwField = new ViewableUnclearField(new MutableString.Simple());
                UIUtils.addViewableUnclearFieldToGrid(
                        grid,
                        1,
                        "SafeContentsUI.passwordEntry.name",
                        pwField,
                        false,
                        true
                );
                
                lastPasswordChangeTime = pwEntryData.lastPasswordChangeTime;
                
                siteField = new DestroyableTextField();
                UIUtils.addDestroyTextFieldToGrid(
                        grid,
                        2,
                        "SafeContentsUI.passwordEntry.site",
                        siteField,
                        false,
                        true
                );
                
                infoField = new DestroyableTextArea();
                UIUtils.addDestroyTextAreaToGrid(
                        grid,
                        3,
                        "SafeContentsUI.passwordEntry.info",
                        infoField,
                        false,
                        true
                );
                
                totpField = new ViewableUnclearField(new MutableString.Simple());
                UIUtils.addViewableUnclearFieldToGrid(
                        grid,
                        4,
                        "SafeContentsUI.passwordEntry.totp",
                        totpField,
                        false,
                        false // false because special langKey
                );
                totpField.setupValidIndic(
                        Lang.get("SafeContentsUI.passwordEntry.totp.config.uri.invalid"),
                        true // setup with init val
                );
                
                // Dynamic
                
                nameField.valChangeNotifier.addListener(() -> {
                    try {
                        String newName = PasswordEntry.Data.checkName(nameField.getVal());
                        popup.updatePwEntryNameAvailability(newName);
                        if (prevName != null) {
                            popup.updatePwEntryNameAvailability(prevName);
                            MemUtils.tryClearString(prevName);
                        }
                        prevName = newName;
                    } catch (IllegalArgumentException ex) {
                        nameField.validIndic.setValidity(false);
                    }
                    popup.updateValidateBtnAvailability();
                });
                
                pwField.valChangeNotifier.addListener(() -> {
                    try {
                        PasswordEntry.Data.checkPassword(pwField.getVal());
                        pwField.setValidity(true);
                    } catch (IllegalArgumentException ex) {
                        pwField.setValidity(false);
                    }
                    popup.updateValidateBtnAvailability();
                });
                
                siteField.valChangeNotifier.addListener(() -> {
                    try {
                        PasswordEntry.Data.checkSite(siteField.getVal());
                        siteField.validIndic.setValidity(true);
                    } catch (IllegalArgumentException ex) {
                        siteField.validIndic.setValidity(false);
                    }
                    popup.updateValidateBtnAvailability();
                });
                
                infoField.valChangeNotifier.addListener(() -> {
                    try {
                        PasswordEntry.Data.checkInfo(infoField.getVal());
                        infoField.validIndic.setValidity(true);
                    } catch (IllegalArgumentException ex) {
                        infoField.validIndic.setValidity(false);
                    }
                    popup.updateValidateBtnAvailability();
                });
                
                totpField.valChangeNotifier.addListener(() -> {
                    try {
                        TOTP newTOTP = UIUtils.totpFromURI(totpField.getVal());
                        if (newTOTP != null) {
                            UIUtils.tryDestroy(newTOTP);
                        }
                        totpField.setValidity(true);
                    } catch (IllegalArgumentException ex) {
                        totpField.setValidity(false);
                    }
                    popup.updateValidateBtnAvailability();
                });
                
                // nameField is initialized differently because init validity depends on all PasswordEntryElements
                pwField.setVal(pwEntryData.getPassword());
                siteField.setVal(pwEntryData.site);
                infoField.setVal(pwEntryData.info);
                totpField.setVal(UIUtils.totpToFieldVal(pwEntryData.totp));
            }
            
            boolean isValid() {
                return nameField.validIndic.isValid()
                        && pwField.isValid()
                        && siteField.validIndic.isValid()
                        && infoField.validIndic.isValid()
                        && totpField.isValid();
            }
            
            PasswordEntry.Data getData() {
                try {
                    return new PasswordEntry.Data(
                            nameField.getValClone(),
                            pwField.getVal().clone(),
                            Arrays.equals(pwField.getVal(), initPw)
                                    ? lastPasswordChangeTime
                                    : DatetimeUtils.nowWithoutNanos(),
                            siteField.getValClone(),
                            infoField.getValClone(),
                            UIUtils.totpFromURI(totpField.getVal())
                    );
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
            
            @Override
            public void destroy() throws DestroyFailedException {
                boolean success = true;
                
                MemUtils.clearCharArray(initPw);
                success = MemUtils.tryDestroy(pwField) && success;
                success = MemUtils.tryDestroy(totpField) && success;
                success = MemUtils.tryDestroy(nameField) && success;
                success = MemUtils.tryClearString(prevName) && success;
                prevName = null;
                success = MemUtils.tryDestroy(siteField) && success;
                success = MemUtils.tryDestroy(infoField) && success;
                
                if (!success) {
                    throw new DestroyFailedException();
                }
            }
            
            @Override
            public boolean isDestroyed() {
                return pwField.isDestroyed()
                        && totpField.isDestroyed()
                        && nameField.isDestroyed()
                        && siteField.isDestroyed()
                        && infoField.isDestroyed()
                        && prevName == null;
            }
            
        }
        
        void showAndWait() {
            UIUtils.showSceneAndWait(scene, stage);
            UIUtils.tryDestroy(this);
        }
        
        void updatePwEntryNameAvailability(String validName) {
            List<PasswordEntryElement> matchingPwEntriesEle = new ArrayList<>();
            for (PasswordEntryElement pwEntryEle : pwEntriesElement) {
                if (validName.equals(pwEntryEle.nameField.getVal())) {
                    matchingPwEntriesEle.add(pwEntryEle);
                }
            }
            boolean isValid =
                    dm.getPwEntryByCurName(validName) == null && matchingPwEntriesEle.size() < 2;
            for (PasswordEntryElement pwEntryEle : matchingPwEntriesEle) {
                pwEntryEle.nameField.validIndic.setValidity(isValid);
            }
        }
        
        void updateValidateBtnAvailability() {
            validateBtn.setDisable(hasInvalid());
        }
        
        boolean hasInvalid() {
            for (PasswordEntryElement ele : pwEntriesElement) {
                if (!ele.isValid()) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            boolean success = true;
            
            for (PasswordEntryElement pwEntryEle : pwEntriesElement) {
                success = MemUtils.tryDestroy(pwEntryEle) && success;
            }
            pwEntriesElement.clear();
            pwEntriesElement = null;
            
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return pwEntriesElement == null;
        }
        
    }
    
    @Override
    public String getTitle() {
        return Lang.get(SECTION_LANG_BASE + ".title");
    }
    
    @Override
    public String getCSSClass() {
        return "import";
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
        return safePwField.isDestroyed();
    }
    
}
