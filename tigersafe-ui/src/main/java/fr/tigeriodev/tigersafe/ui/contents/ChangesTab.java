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

package fr.tigeriodev.tigersafe.ui.contents;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.ExistingPasswordEntry;
import fr.tigeriodev.tigersafe.data.NewPasswordEntry;
import fr.tigeriodev.tigersafe.data.PasswordEntry;
import fr.tigeriodev.tigersafe.data.SafeDataManager.NameAlreadyUsedException;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextArea;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextField;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class ChangesTab extends SafeContentsUI.Tab {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(ChangesTab.class);
    
    private ContentHolder contentH;
    
    ChangesTab(SafeContentsUI ui) {
        super(ui);
    }
    
    @Override
    public String getName() {
        return "Changes"; // Never shown
    }
    
    @Override
    protected Node newContent() {
        ui.updateUnsavedFooterDisplay();
        contentH = new ContentHolder();
        return contentH.rootVBox;
    }
    
    class ContentHolder implements Destroyable {
        
        final VBox rootVBox;
        final Set<Destroyable> destroyables = new HashSet<>();
        
        ContentHolder() {
            rootVBox = new VBox();
            rootVBox.setId("safe-contents-changes-root-vbox");
            
            Text title = new Text(Lang.get("SafeContentsUI.changes.title"));
            
            ScrollPane changesScrollP = new ScrollPane();
            changesScrollP.getStyleClass().add("changes-scroll");
            
            VBox changesVBox = new VBox();
            changesVBox.getStyleClass().add("changes-vbox");
            
            for (PasswordEntry pwEntry : dm.getPwEntries()) {
                if (
                    pwEntry instanceof ExistingPasswordEntry
                            && ((ExistingPasswordEntry) pwEntry).isModified()
                ) {
                    changesVBox.getChildren()
                            .add(
                                    newModifiedPasswordEntry(
                                            (ExistingPasswordEntry) pwEntry,
                                            changesVBox
                                    )
                            );
                } else if (pwEntry instanceof NewPasswordEntry && pwEntry.isValid()) {
                    TitledPane titledPane = new TitledPane();
                    titledPane
                            .setText(Lang.get("SafeContentsUI.changes.passwordEntry.added.title"));
                    titledPane.getStyleClass().addAll("password-entry", "added-password-entry");
                    titledPane.setExpanded(false);
                    
                    VBox titledPaneContent = new VBox();
                    titledPaneContent.getStyleClass().add("password-entry-content-root");
                    
                    Button cancelBtn = UIUtils.newBtn(
                            "SafeContentsUI.changes.passwordEntry.added.cancel.button",
                            "cancel",
                            true,
                            false
                    );
                    
                    titledPaneContent.getChildren()
                            .addAll(newPasswordEntryWithoutFieldsChangeNode(pwEntry), cancelBtn);
                    
                    titledPane.setContent(titledPaneContent);
                    
                    changesVBox.getChildren().add(titledPane);
                    
                    cancelBtn.setOnAction((e) -> {
                        dm.deletePwEntry(pwEntry);
                        changesVBox.getChildren().remove(titledPane);
                    });
                }
            }
            
            for (ExistingPasswordEntry pwEntry : dm.getDeletedPwEntries()) {
                TitledPane titledPane = new TitledPane();
                titledPane.setText(Lang.get("SafeContentsUI.changes.passwordEntry.deleted.title"));
                titledPane.getStyleClass().addAll("password-entry", "deleted-password-entry");
                titledPane.setExpanded(true);
                
                VBox titledPaneContent = new VBox();
                titledPaneContent.getStyleClass().add("password-entry-content-root");
                
                Node pwEntryFieldsNode = newPasswordEntryWithFieldsChangeNode(
                        (ExistingPasswordEntry) pwEntry,
                        () -> {}
                );
                Button cancelBtn = UIUtils.newBtn(
                        "SafeContentsUI.changes.passwordEntry.deleted.cancel.button",
                        "cancel",
                        true,
                        false
                );
                
                titledPaneContent.getChildren().addAll(pwEntryFieldsNode, cancelBtn);
                
                titledPane.setContent(titledPaneContent);
                
                changesVBox.getChildren().add(titledPane);
                
                cancelBtn.setOnAction((e) -> {
                    try {
                        dm.restorePwEntry(pwEntry);
                        if (pwEntry.isModified()) {
                            int prevPaneInd = changesVBox.getChildren().indexOf(titledPane);
                            TitledPane newPwEntryPane =
                                    newModifiedPasswordEntry(pwEntry, changesVBox);
                            if (prevPaneInd != -1) {
                                changesVBox.getChildren().set(prevPaneInd, newPwEntryPane);
                            } else {
                                changesVBox.getChildren().remove(titledPane);
                                changesVBox.getChildren().add(newPwEntryPane);
                            }
                        } else {
                            changesVBox.getChildren().remove(titledPane);
                        }
                    } catch (NameAlreadyUsedException ex) {
                        Alert errorPopup = new Alert(
                                AlertType.ERROR,
                                Lang.get(
                                        "SafeContentsUI.changes.passwordEntry.deleted.cancel.failed.nameAlreadyUsed",
                                        pwEntry.getCurrentName()
                                ),
                                ButtonType.OK
                        );
                        UIUtils.showDialogAndWait(errorPopup);
                    }
                });
            }
            
            changesScrollP.setContent(changesVBox);
            
            rootVBox.getChildren().addAll(title, changesScrollP, ui.newSaveChangesBtn());
        }
        
        private TitledPane newModifiedPasswordEntry(ExistingPasswordEntry pwEntry,
                VBox changesVBox) {
            TitledPane titledPane = new TitledPane();
            titledPane.setText(Lang.get("SafeContentsUI.changes.passwordEntry.modified.title"));
            titledPane.getStyleClass().addAll("password-entry", "modified-password-entry");
            titledPane.setExpanded(true);
            
            titledPane.setContent(
                    newPasswordEntryWithFieldsChangeNode((ExistingPasswordEntry) pwEntry, () -> {
                        changesVBox.getChildren().remove(titledPane);
                    })
            );
            titledPane.getContent().getStyleClass().add("password-entry-content-root");
            return titledPane;
        }
        
        private GridPane newPasswordEntryFieldsGrid() {
            GridPane res = new GridPane();
            res.getStyleClass().add("fields-grid");
            
            ColumnConstraints labelsColConstraints = new ColumnConstraints();
            labelsColConstraints.setHalignment(HPos.RIGHT);
            ColumnConstraints fieldsColsConstraints = new ColumnConstraints();
            fieldsColsConstraints.setPercentWidth(40); // Forces same width for original and current vals, and same width than for no fields changes. It is better than setFillWidth(true) and setHgrow(Priority.ALWAYS) that do not guarantee same width for the 2 columns, and between different titled panes.
            res.getColumnConstraints()
                    .addAll(
                            labelsColConstraints,
                            fieldsColsConstraints,
                            new ColumnConstraints(),
                            fieldsColsConstraints
                    );
            
            Label nameLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.name", false);
            res.add(nameLabel, 0, 0);
            
            Label pwLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.password", false);
            res.add(pwLabel, 0, 1);
            
            Label siteLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.site", false);
            res.add(siteLabel, 0, 2);
            
            Label infoLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.info", false);
            res.add(infoLabel, 0, 3);
            
            Label totpLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.totp", false);
            res.add(totpLabel, 0, 4);
            
            return res;
        }
        
        private Node newPasswordEntryWithoutFieldsChangeNode(PasswordEntry pwEntry) {
            GridPane fieldsGrid = newPasswordEntryFieldsGrid();
            
            fieldsGrid.add(newDestroyTextField(pwEntry.getCurrentName()), 1, 0);
            
            fieldsGrid.add(newViewableUnclearField(pwEntry.getCurrentPassword()), 1, 1);
            
            fieldsGrid.add(newDestroyTextField(pwEntry.getCurrentSite()), 1, 2);
            
            fieldsGrid.add(newTextArea(pwEntry.getCurrentInfo()), 1, 3);
            
            fieldsGrid.add(
                    newViewableUnclearField(UIUtils.totpToFieldVal(pwEntry.getCurrentTOTP())),
                    1,
                    4
            );
            
            return fieldsGrid;
        }
        
        private Node newPasswordEntryWithFieldsChangeNode(ExistingPasswordEntry pwEntry,
                Runnable isNoLongerModified) {
            GridPane fieldsGrid = newPasswordEntryFieldsGrid();
            
            addFieldToChangesGrid(
                    fieldsGrid,
                    0,
                    this::newDestroyTextField,
                    pwEntry.originalData.name,
                    pwEntry.getCurrentName(),
                    () -> {
                        try {
                            pwEntry.setName(pwEntry.originalData.name, dm);
                            if (!pwEntry.isModified()) {
                                isNoLongerModified.run();
                            }
                            return true;
                        } catch (NameAlreadyUsedException ex) {
                            Alert errorPopup = new Alert(
                                    AlertType.ERROR,
                                    Lang.get(
                                            "SafeContentsUI.changes.passwordEntry.rollbackField.failed.nameAlreadyUsed",
                                            pwEntry.originalData.name
                                    ),
                                    ButtonType.OK
                            );
                            UIUtils.showDialogAndWait(errorPopup);
                            return false;
                        }
                    }
            );
            
            addFieldToChangesGrid(
                    fieldsGrid,
                    1,
                    this::newViewableUnclearField,
                    pwEntry.originalData.getPassword(),
                    pwEntry.getCurrentPassword(),
                    () -> {
                        pwEntry.setPassword(pwEntry.originalData.getPassword());
                        if (!pwEntry.isModified()) {
                            isNoLongerModified.run();
                        }
                        return true;
                    }
            );
            
            addFieldToChangesGrid(
                    fieldsGrid,
                    2,
                    this::newDestroyTextField,
                    pwEntry.originalData.site,
                    pwEntry.getCurrentSite(),
                    () -> {
                        pwEntry.setSite(pwEntry.originalData.site);
                        if (!pwEntry.isModified()) {
                            isNoLongerModified.run();
                        }
                        return true;
                    }
            );
            
            addFieldToChangesGrid(
                    fieldsGrid,
                    3,
                    this::newTextArea,
                    pwEntry.originalData.info,
                    pwEntry.getCurrentInfo(),
                    () -> {
                        pwEntry.setInfo(pwEntry.originalData.info);
                        if (!pwEntry.isModified()) {
                            isNoLongerModified.run();
                        }
                        return true;
                    }
            );
            
            addFieldToChangesGrid(
                    fieldsGrid,
                    4,
                    this::newViewableUnclearField,
                    UIUtils.totpToFieldVal(pwEntry.originalData.totp),
                    UIUtils.totpToFieldVal(pwEntry.getCurrentTOTP()),
                    () -> {
                        pwEntry.setTOTP(pwEntry.originalData.totp);
                        if (!pwEntry.isModified()) {
                            isNoLongerModified.run();
                        }
                        return true;
                    }
            );
            return fieldsGrid;
        }
        
        private Node newDestroyTextField(String valSrc) {
            DestroyableTextField res = new DestroyableTextField();
            res.setVal(valSrc);
            res.setEditable(false);
            res.setFocusTraversable(false);
            destroyables.add(res);
            return res;
        }
        
        private Node newViewableUnclearField(char[] valSrc) {
            ViewableUnclearField pwField = new ViewableUnclearField(valSrc);
            pwField.enabledVisibF.setEditable(false);
            pwField.disabledVisibF.setEditable(false);
            pwField.enabledVisibF.setFocusTraversable(false);
            pwField.disabledVisibF.setFocusTraversable(false);
            destroyables.add(pwField);
            return UIUtils.newViewableUnclearFieldHBox(pwField);
        }
        
        private TextArea newTextArea(String valSrc) {
            DestroyableTextArea res = new DestroyableTextArea();
            res.setVal(valSrc);
            res.setEditable(false);
            res.setFocusTraversable(false);
            UIUtils.setupOptimalTextArea(res);
            destroyables.add(res);
            return res;
        }
        
        private <T> void addFieldToChangesGrid(GridPane changesGrid, int rowInd,
                Function<T, Node> fieldMakerByVal, T originalVal, T currentVal,
                BooleanSupplier rollbackExecutor) {
            Node originalValField = fieldMakerByVal.apply(originalVal);
            changesGrid.add(originalValField, 1, rowInd);
            
            if (
                (!(originalVal instanceof char[]) && !Objects.equals(originalVal, currentVal))
                        || (originalVal instanceof char[]
                                && !Arrays.equals((char[]) originalVal, (char[]) currentVal))
            ) {
                ImageView rightArrowImgV = UIUtils.newIcon("right-arrow");
                changesGrid.add(rightArrowImgV, 2, rowInd);
                
                Node newValField = fieldMakerByVal.apply(currentVal);
                changesGrid.add(newValField, 3, rowInd);
                Button rollbackBtn = UIUtils.newBtn(
                        "SafeContentsUI.changes.passwordEntry.rollbackField.button",
                        "rollback",
                        false,
                        true
                );
                changesGrid.add(rollbackBtn, 4, rowInd);
                
                rollbackBtn.setOnAction((e) -> {
                    boolean isSuccessful = rollbackExecutor.getAsBoolean();
                    if (isSuccessful) {
                        changesGrid.getChildren()
                                .removeAll(rightArrowImgV, newValField, rollbackBtn);
                    }
                });
            }
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            Logger unsafeMethLog = unsafeLog.newChildFromCurMethIf(Level.DEBUG);
            boolean success = true;
            for (Destroyable item : destroyables) {
                unsafeMethLog.debug(() -> "item = " + StringUtils.getSafeObjName(item));
                success = MemUtils.tryDestroy(item) && success;
            }
            destroyables.clear();
            
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return destroyables.isEmpty();
        }
        
    }
    
    @Override
    protected void onDeselectedBefore() {
        if (contentH != null) {
            UIUtils.tryDestroy(contentH);
            contentH = null;
        }
    }
    
    @Override
    protected void onDeselectedAfter() {
        ui.updateUnsavedFooterDisplay();
    }
    
}
