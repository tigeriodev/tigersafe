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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.NewPasswordEntry;
import fr.tigeriodev.tigersafe.data.PasswordEntry;
import fr.tigeriodev.tigersafe.data.TOTP;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIConfig;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextArea;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextField;
import fr.tigeriodev.tigersafe.ui.fields.NumberField;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.DatetimeUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.MutableString;
import fr.tigeriodev.tigersafe.utils.RandomUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PasswordsTab extends SafeContentsUI.Tab {
    
    static final String TAB_LANG_BASE = "SafeContentsUI.passwords";
    
    private ContentHolder contentH;
    
    PasswordsTab(SafeContentsUI ui) {
        super(ui);
    }
    
    @Override
    public String getName() {
        return Lang.get(TAB_LANG_BASE + ".tabName");
    }
    
    @Override
    public String getIconName() {
        return "password";
    }
    
    @Override
    public KeyCombination getKeyboardShortcut() {
        return UIConfig.KeyboardShortcut.PASSWORDS_TAB.getKeyCombination();
    }
    
    @Override
    protected Node newContent() {
        contentH = new ContentHolder();
        return contentH.rootHBox;
    }
    
    private static class PasswordsListCell extends ListCell<PasswordEntry> implements Destroyable {
        
        private Text nameText;
        private Text siteText;
        
        @Override
        protected void updateItem(PasswordEntry pwEntry, boolean empty) {
            super.updateItem(pwEntry, empty);
            
            if (nameText == null) {
                VBox vBox = new VBox();
                vBox.getStyleClass().add("passwords-list-cell-vbox");
                
                nameText = UIUtils.newSingleLineText();
                nameText.getStyleClass().add("password-entry-name");
                
                siteText = UIUtils.newSingleLineText();
                siteText.getStyleClass().add("password-entry-site");
                
                vBox.getChildren().addAll(nameText, siteText);
                setGraphic(vBox);
            }
            nameText.setText(pwEntry != null ? pwEntry.getCurrentName() : "");
            siteText.setText(pwEntry != null ? pwEntry.getCurrentSite() : "");
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            if (nameText != null) {
                nameText.setText("");
            }
            if (siteText != null) {
                siteText.setText("");
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return nameText.getText().isEmpty() && siteText.getText().isEmpty();
        }
        
    }
    
    class ContentHolder implements Destroyable {
        
        final HBox rootHBox;
        final Button addBtn;
        final DestroyableTextField filterNameField;
        final DestroyableTextField filterSiteField;
        final ListView<PasswordEntry> pwsListV;
        final List<Destroyable> destroyables = new ArrayList<>();
        PasswordPaneHolder pwPaneH;
        
        ContentHolder() {
            rootHBox = new HBox();
            rootHBox.setId("safe-contents-passwords-root-hbox");
            
            VBox leftVBox = new VBox();
            leftVBox.getStyleClass().add("left-vbox");
            
            GridPane filterGrid = new GridPane();
            filterGrid.getStyleClass().add("filter-grid");
            
            Text filterTitle = new Text(Lang.get(TAB_LANG_BASE + ".filter"));
            filterGrid.add(filterTitle, 0, 0, 2, 1);
            
            filterNameField = new DestroyableTextField();
            UIUtils.addDestroyTextFieldToGrid(
                    filterGrid,
                    1,
                    "SafeContentsUI.passwordEntry.name",
                    filterNameField,
                    false,
                    false
            );
            
            filterSiteField = new DestroyableTextField();
            UIUtils.addDestroyTextFieldToGrid(
                    filterGrid,
                    2,
                    "SafeContentsUI.passwordEntry.site",
                    filterSiteField,
                    false,
                    false
            );
            
            pwsListV = new ListView<PasswordEntry>();
            pwsListV.getStyleClass().add("passwords-list");
            pwsListV.setCellFactory((p) -> {
                PasswordsListCell res = new PasswordsListCell();
                destroyables.add(res);
                return res;
            });
            
            addBtn = new Button(Lang.get(TAB_LANG_BASE + ".add"), UIUtils.newIcon("add"));
            
            leftVBox.getChildren().addAll(filterGrid, pwsListV, addBtn);
            VBox.setVgrow(pwsListV, Priority.ALWAYS);
            
            VBox rightVBox = new VBox();
            rightVBox.setAlignment(Pos.CENTER);
            
            rootHBox.getChildren().addAll(leftVBox, rightVBox);
            HBox.setHgrow(rightVBox, Priority.ALWAYS);
            
            // Dynamic
            
            addBtn.setOnAction((e) -> {
                NewPasswordEntry newPwEntry = dm.addNewPwEntry();
                pwsListV.getItems().add(newPwEntry);
                if (CheckUtils.isNotEmpty(filterSiteField.getVal())) {
                    newPwEntry.setSite(filterSiteField.getVal());
                }
                updateAddBtnAvailability();
                pwsListV.getSelectionModel().select(pwsListV.getItems().size() - 1);
            });
            pwsListV.getSelectionModel()
                    .selectedItemProperty()
                    .addListener((ov, oldPwEntry, newPwEntry) -> {
                        if (newPwEntry == null) { // Items have been reset
                            rightVBox.getChildren().clear();
                            return;
                        }
                        if (oldPwEntry instanceof NewPasswordEntry && !oldPwEntry.isValid()) {
                            dm.deletePwEntry(oldPwEntry);
                            updatePasswordsList(false);
                            updateAddBtnAvailability();
                        }
                        
                        if (pwPaneH != null) {
                            MemUtils.tryDestroy(pwPaneH);
                        }
                        pwPaneH = new PasswordPaneHolder(newPwEntry);
                        rightVBox.getChildren().setAll(pwPaneH.rootVBox);
                    });
            pwsListV.setOnKeyPressed((ev) -> {
                if (pwPaneH != null && KeyCode.ENTER.equals(ev.getCode()) && pwsListV.isFocused()) {
                    pwPaneH.nameField.requestFocus();
                    ev.consume();
                }
            });
            filterNameField.valChangeNotifier.addListener(() -> {
                updatePasswordsList(false);
            });
            setupEnterKeyForFilter(filterNameField);
            filterSiteField.valChangeNotifier.addListener(() -> {
                updatePasswordsList(false);
            });
            setupEnterKeyForFilter(filterSiteField);
            updatePasswordsList(false);
            updateAddBtnAvailability();
            
            ui.getScene()
                    .getAccelerators()
                    .put(UIConfig.KeyboardShortcut.FILTER.getKeyCombination(), () -> {
                        filterNameField.requestFocus();
                    });
            ui.getScene()
                    .getAccelerators()
                    .put(UIConfig.KeyboardShortcut.ADD.getKeyCombination(), () -> {
                        UIUtils.clickButton(addBtn);
                        if (pwPaneH != null) {
                            pwPaneH.nameField.requestFocus();
                        }
                    });
        }
        
        private void updatePasswordsList(boolean refresh) {
            ObservableList<PasswordEntry> items = pwsListV.getItems();
            Set<PasswordEntry> pwEntriesToAddOrKeep = new LinkedHashSet<PasswordEntry>();
            String filterName = StringUtils.nullToEmpty(filterNameField.getVal());
            String filterNameLow = filterName.toLowerCase();
            String filterSite = StringUtils.nullToEmpty(filterSiteField.getVal());
            String filterSiteLow = filterSite.toLowerCase();
            for (PasswordEntry pwEntry : dm.getPwEntries()) {
                boolean nameMatch = filterNameLow.isEmpty();
                if (!nameMatch) {
                    String pwEntryName = pwEntry.getCurrentName();
                    String pwEntryNameLow = pwEntryName.toLowerCase();
                    nameMatch = pwEntryNameLow.contains(filterNameLow);
                    if (pwEntryNameLow != pwEntryName) {
                        MemUtils.tryClearString(pwEntryNameLow);
                    }
                }
                
                if (!nameMatch) {
                    continue;
                }
                
                boolean siteMatch = filterSiteLow.isEmpty();
                if (!siteMatch) {
                    String pwEntrySite = pwEntry.getCurrentSite();
                    String pwEntrySiteLow = pwEntrySite.toLowerCase();
                    siteMatch = pwEntrySiteLow.contains(filterSiteLow);
                    if (pwEntrySiteLow != pwEntrySite) {
                        MemUtils.tryClearString(pwEntrySiteLow);
                    }
                }
                
                if (siteMatch) { // nameMatch = true
                    pwEntriesToAddOrKeep.add(pwEntry);
                }
            }
            if (filterNameLow != filterName) {
                MemUtils.tryClearString(filterNameLow);
            }
            if (filterSiteLow != filterSite) {
                MemUtils.tryClearString(filterSiteLow);
            }
            
            if (items == null || (items.isEmpty() && pwEntriesToAddOrKeep.size() > 0)) {
                pwsListV.setItems(FXCollections.observableArrayList(pwEntriesToAddOrKeep));
            } else {
                Iterator<PasswordEntry> itemsIt = items.iterator();
                while (itemsIt.hasNext()) {
                    PasswordEntry pwEntry = itemsIt.next();
                    boolean isStillPresent = pwEntriesToAddOrKeep.remove(pwEntry);
                    if (!isStillPresent) {
                        itemsIt.remove();
                    }
                }
                items.addAll(pwEntriesToAddOrKeep);
            }
            if (refresh) {
                pwsListV.refresh();
            }
        }
        
        private void setupEnterKeyForFilter(TextField filterTextField) {
            filterTextField.setOnKeyPressed((ev) -> {
                if (KeyCode.ENTER.equals(ev.getCode())) {
                    int pwsNum = pwsListV.getItems().size();
                    if (pwsNum > 0) {
                        pwsListV.getSelectionModel().select(0);
                    }
                    if (pwPaneH != null && pwsNum == 1) {
                        pwPaneH.nameField.requestFocus();
                    } else {
                        pwsListV.requestFocus();
                    }
                    ev.consume();
                }
            });
        }
        
        private void updateAddBtnAvailability() {
            PasswordEntry newInvalidPwEntry = dm.getPwEntryByCurName("");
            if (newInvalidPwEntry != null) {
                addBtn.setDisable(true);
                return;
            }
            for (PasswordEntry pwEntry : dm.getPwEntries()) {
                if (pwEntry instanceof NewPasswordEntry && !pwEntry.isValid()) {
                    addBtn.setDisable(true);
                    return;
                }
            }
            addBtn.setDisable(false);
        }
        
        class PasswordPaneHolder implements Destroyable {
            
            private static final Logger unsafePwPaneLog =
                    Logs.newUnsafeLogger(PasswordPaneHolder.class);
            
            final VBox rootVBox;
            final DestroyableTextField nameField;
            final ViewableUnclearField pwField;
            final DestroyableTextField siteField;
            final DestroyableTextArea infoField;
            ViewableUnclearField uriField;
            Timeline totpTimeline;
            GeneratePasswordPopupHolder genPwPopupH;
            
            PasswordPaneHolder(PasswordEntry pwEntry) {
                rootVBox = new VBox();
                rootVBox.getStyleClass().add("password-root-vbox");
                
                GridPane grid = new GridPane();
                grid.getStyleClass().add("fields-grid");
                ColumnConstraints labelsColContraints = new ColumnConstraints();
                labelsColContraints.setHalignment(HPos.RIGHT);
                grid.getColumnConstraints().add(labelsColContraints);
                
                nameField = new DestroyableTextField();
                nameField.setVal(pwEntry.getCurrentName());
                UIUtils.addDestroyTextFieldToGrid(
                        grid,
                        0,
                        "SafeContentsUI.passwordEntry.name",
                        nameField,
                        false,
                        true
                );
                nameField.validIndic.setValidity(CheckUtils.isNotEmpty(pwEntry.getCurrentName()));
                
                Label pwLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.password", false);
                grid.add(pwLabel, 0, 1);
                
                VBox pwVBox = new VBox();
                pwVBox.setAlignment(Pos.TOP_LEFT);
                
                HBox pwHBox = new HBox();
                pwHBox.setAlignment(Pos.CENTER_LEFT);
                
                pwField = new ViewableUnclearField(pwEntry.getCurrentPassword());
                pwField.setupValidIndic(
                        Lang.get("SafeContentsUI.passwordEntry.password.invalid"),
                        pwEntry.getCurrentPassword().length > 0
                );
                
                TilePane pwBtnsTileP = new TilePane();
                pwBtnsTileP.getStyleClass().add("password-buttons-tile");
                UIUtils.forcePrefWidth(pwBtnsTileP);
                
                Button pwCopyBtn = UIUtils
                        .newCopyBtn(pwField, "SafeContentsUI.passwordEntry.password.copy.button");
                
                Button pwGenerateBtn = UIUtils.newBtn(
                        "SafeContentsUI.passwordEntry.password.generate.button",
                        "generate-password",
                        false,
                        true
                );
                
                pwBtnsTileP.getChildren().addAll(pwCopyBtn, pwField.visibilityBtn, pwGenerateBtn);
                
                pwHBox.getChildren().addAll(pwField.rootPane, pwBtnsTileP);
                HBox.setHgrow(pwField.rootPane, Priority.ALWAYS);
                HBox.setHgrow(pwBtnsTileP, Priority.NEVER);
                
                Text pwLastChangeTimeText = new Text();
                updateLastChangeTimeText(pwLastChangeTimeText, pwEntry);
                
                pwVBox.getChildren().addAll(pwHBox, pwLastChangeTimeText);
                
                grid.add(pwVBox, 1, 1);
                
                siteField = new DestroyableTextField();
                siteField.setVal(pwEntry.getCurrentSite());
                UIUtils.addDestroyTextFieldToGrid(
                        grid,
                        2,
                        "SafeContentsUI.passwordEntry.site",
                        siteField,
                        false,
                        true
                );
                
                infoField = new DestroyableTextArea();
                infoField.setVal(pwEntry.getCurrentInfo());
                infoField.setPrefHeight(400);
                UIUtils.addDestroyTextAreaToGrid(
                        grid,
                        3,
                        "SafeContentsUI.passwordEntry.info",
                        infoField,
                        false,
                        true
                );
                
                Label totpLabel = UIUtils.newLabel("SafeContentsUI.passwordEntry.totp", false);
                grid.add(totpLabel, 0, 4);
                
                HBox totpHBox = new HBox();
                totpHBox.getStyleClass().add("totp-hbox");
                
                ToggleButton totpConfigBtn = new ToggleButton();
                totpConfigBtn.setGraphic(UIUtils.newIcon("config"));
                UIUtils.setTooltip(
                        totpConfigBtn,
                        "SafeContentsUI.passwordEntry.totp.config.button"
                );
                
                totpHBox.getChildren().addAll(totpConfigBtn);
                grid.add(totpHBox, 1, 4);
                
                HBox bottomHBox = new HBox();
                bottomHBox.getStyleClass().add("bottom-hbox");
                
                CheckBox editModeCheckbox =
                        new CheckBox(Lang.get("SafeContentsUI.passwordEntry.editMode"));
                editModeCheckbox.setSelected(!pwEntry.isValid());
                
                Button deleteBtn = UIUtils.newBtn(
                        "SafeContentsUI.passwordEntry.delete.button",
                        "delete",
                        true,
                        false
                );
                
                bottomHBox.getChildren().addAll(editModeCheckbox, deleteBtn);
                
                rootVBox.getChildren().addAll(grid, bottomHBox);
                
                // Dynamic
                
                nameField.editableProperty().bind(editModeCheckbox.selectedProperty());
                nameField.valChangeNotifier.addListener(() -> {
                    try {
                        String newName = nameField.getVal();
                        pwEntry.setName(newName, dm);
                        nameField.validIndic.setValidity(true);
                        ui.updateUnsavedFooterDisplay();
                        updateAddBtnAvailability();
                        if (CheckUtils.isNotEmpty(filterNameField.getVal())) {
                            filterNameField.setVal(newName);
                        }
                        pwsListV.refresh();
                    } catch (IllegalArgumentException ex) {
                        nameField.validIndic.setValidity(false);
                    }
                });
                
                pwField.disabledVisibF.editableProperty().bind(editModeCheckbox.selectedProperty());
                pwField.enabledVisibF.editableProperty().bind(editModeCheckbox.selectedProperty());
                pwField.valChangeNotifier.addListener(() -> {
                    try {
                        pwEntry.setPassword(pwField.getVal());
                        pwField.setValidity(true);
                        updateLastChangeTimeText(pwLastChangeTimeText, pwEntry);
                        ui.updateUnsavedFooterDisplay();
                        updateAddBtnAvailability();
                    } catch (IllegalArgumentException ex) {
                        pwField.setValidity(false);
                    }
                });
                
                pwGenerateBtn.setOnAction((e) -> {
                    if (genPwPopupH != null) {
                        throw new IllegalStateException();
                    }
                    genPwPopupH = new GeneratePasswordPopupHolder();
                    genPwPopupH.showAndWait();
                    if (genPwPopupH.isValidated) {
                        if (!editModeCheckbox.isSelected()) {
                            editModeCheckbox.setSelected(true);
                        }
                        pwField.setVal(genPwPopupH.resultField.getVal());
                    }
                    UIUtils.tryDestroy(genPwPopupH);
                    genPwPopupH = null;
                });
                
                siteField.editableProperty().bind(editModeCheckbox.selectedProperty());
                siteField.valChangeNotifier.addListener(() -> {
                    try {
                        String newSite = siteField.getVal();
                        pwEntry.setSite(newSite);
                        siteField.validIndic.setValidity(true);
                        ui.updateUnsavedFooterDisplay();
                        if (CheckUtils.isNotEmpty(filterSiteField.getVal())) {
                            filterSiteField.setVal(newSite);
                        }
                        pwsListV.refresh();
                    } catch (IllegalArgumentException ex) {
                        siteField.validIndic.setValidity(false);
                    }
                });
                
                infoField.editableProperty().bind(editModeCheckbox.selectedProperty());
                infoField.valChangeNotifier.addListener(() -> {
                    try {
                        pwEntry.setInfo(infoField.getVal());
                        infoField.validIndic.setValidity(true);
                        ui.updateUnsavedFooterDisplay();
                    } catch (IllegalArgumentException ex) {
                        infoField.validIndic.setValidity(false);
                    }
                });
                
                deleteBtn.setOnAction((e) -> {
                    Alert confirmPopup = new Alert(
                            AlertType.CONFIRMATION,
                            Lang.get(
                                    "SafeContentsUI.passwordEntry.delete.confirm",
                                    pwEntry.getCurrentName()
                            ),
                            ButtonType.YES,
                            ButtonType.CANCEL
                    );
                    UIUtils.showDialogAndWait(confirmPopup).ifPresent((clickedBtn) -> {
                        if (clickedBtn == ButtonType.YES) {
                            dm.deletePwEntry(pwEntry);
                            ui.updateUnsavedFooterDisplay();
                            updatePasswordsList(false);
                            updateAddBtnAvailability();
                        }
                    });
                });
                
                ui.getScene()
                        .getAccelerators()
                        .put(UIConfig.KeyboardShortcut.EDIT_MODE.getKeyCombination(), () -> {
                            editModeCheckbox.setSelected(!editModeCheckbox.isSelected());
                        });
                UIUtils.setButtonShortcut(
                        ui.getScene(),
                        UIConfig.KeyboardShortcut.COPY_PASSWORD.getKeyCombination(),
                        pwCopyBtn
                );
                
                totpConfigBtn.selectedProperty().addListener((ov, oldSelected, newSelected) -> {
                    if (totpTimeline != null) {
                        totpTimeline.stop();
                    }
                    TOTP totp = pwEntry.getCurrentTOTP();
                    if (totp == null && !newSelected) {
                        totpConfigBtn.setText(
                                Lang.get("SafeContentsUI.passwordEntry.totp.config.button.text")
                        );
                        totpConfigBtn.disableProperty()
                                .bind(editModeCheckbox.selectedProperty().not());
                        totpHBox.getChildren().setAll(totpConfigBtn);
                    } else {
                        totpConfigBtn.setText("");
                        totpConfigBtn.disableProperty().unbind();
                        Node totpPane = newSelected
                                ? newTOTPConfigPane(pwEntry, editModeCheckbox)
                                : newTOTPCodesPane(totp);
                        totpHBox.getChildren().setAll(totpPane, totpConfigBtn);
                        HBox.setHgrow(totpPane, Priority.ALWAYS);
                    }
                });
                totpConfigBtn.setSelected(!totpConfigBtn.isSelected());
                totpConfigBtn.setSelected(!totpConfigBtn.isSelected());
            }
            
            private static void updateLastChangeTimeText(Text text, PasswordEntry pwEntry) {
                Period timeAgoPeriod = DatetimeUtils.getPeriodBetween(
                        pwEntry.getCurrentLastPasswordChangeTime(),
                        Instant.now()
                );
                text.setText(
                        Lang.get(
                                "SafeContentsUI.passwordEntry.password.lastChangeTime",
                                pwEntry.getCurrentLastPasswordChangeTime().toEpochMilli(),
                                timeAgoPeriod.toTotalMonths()
                        )
                );
            }
            
            private Node newTOTPCodesPane(TOTP totp) {
                HBox res = new HBox();
                res.getStyleClass().add("totp-codes-hbox");
                
                TextField curCodeField = newTOTPCodeField(totp);
                Button curCodeCopyBtn = UIUtils.newCopyBtn(
                        curCodeField,
                        "SafeContentsUI.passwordEntry.totp.currentCode.copy.button"
                );
                
                VBox totpProgressVBox = new VBox();
                totpProgressVBox.getStyleClass().add("progress-vbox");
                
                ProgressBar totpProgressBar = new ProgressBar();
                Text totpRemainingTimeText = new Text();
                
                totpProgressVBox.getChildren().addAll(totpProgressBar, totpRemainingTimeText);
                
                TextField nextCodeField = newTOTPCodeField(totp);
                Button nextCodeCopyBtn = UIUtils.newCopyBtn(
                        nextCodeField,
                        "SafeContentsUI.passwordEntry.totp.nextCode.copy.button"
                );
                
                res.getChildren()
                        .addAll(
                                curCodeField,
                                curCodeCopyBtn,
                                totpProgressVBox,
                                nextCodeField,
                                nextCodeCopyBtn
                        );
                
                // Dynamic
                
                if (totpTimeline != null) {
                    totpTimeline.stop();
                }
                
                Runnable totpUpdater = () -> {
                    Instant curTime = Instant.now();
                    unsafePwPaneLog.debug(() -> "totpUpdater update: " + curTime);
                    if (totp.updateCurTime(curTime) || curCodeField.getText().isEmpty()) {
                        curCodeField.setText(totp.getCurCode());
                        nextCodeField.setText(totp.getNextCode());
                    }
                    long curIntervalSecondsLeft =
                            java.time.Duration.between(curTime, totp.getNextIntervalStartTime())
                                    .getSeconds();
                    totpProgressBar.setProgress(
                            (double) curIntervalSecondsLeft / (double) totp.periodSeconds
                    );
                    totpRemainingTimeText.setText(
                            Lang.get(
                                    "SafeContentsUI.passwordEntry.totp.currentCode.secondsLeft",
                                    curIntervalSecondsLeft
                            )
                    );
                };
                totpUpdater.run();
                totpTimeline = new Timeline(new KeyFrame(Duration.seconds(1), (e) -> {
                    totpUpdater.run();
                }));
                totpTimeline.setCycleCount(Animation.INDEFINITE);
                
                // Avoid issues of timeline execution delay lower than 1 second which can induce 2 executions for the same UTC second, inducing the progress bar to be visually updated every 2 seconds instead of 1
                int curTimeMs = Instant.now().atZone(ZoneOffset.UTC).getNano() / 1000000;
                if (curTimeMs < 400) {
                    totpTimeline.setDelay(Duration.millis(500 - curTimeMs));
                } else if (curTimeMs > 600) {
                    totpTimeline.setDelay(Duration.millis(1000 - curTimeMs + 500));
                }
                
                totpTimeline.play();
                
                UIUtils.setButtonShortcut(
                        ui.getScene(),
                        UIConfig.KeyboardShortcut.COPY_CURRENT_TOTP.getKeyCombination(),
                        curCodeCopyBtn
                );
                UIUtils.setButtonShortcut(
                        ui.getScene(),
                        UIConfig.KeyboardShortcut.COPY_NEXT_TOTP.getKeyCombination(),
                        nextCodeCopyBtn
                );
                
                return res;
            }
            
            private static TextField newTOTPCodeField(TOTP totp) {
                TextField res = new TextField();
                res.setEditable(false);
                res.setFont(Font.font("monospace", Font.getDefault().getSize()));
                res.setPrefColumnCount(totp.digitsNum);
                return res;
            }
            
            private Node newTOTPConfigPane(PasswordEntry pwEntry, CheckBox editModeCheckbox) {
                HBox res = new HBox();
                res.getStyleClass().add("totp-config-hbox");
                
                if (uriField != null) {
                    MemUtils.tryDestroy(uriField);
                }
                uriField =
                        new ViewableUnclearField(UIUtils.totpToFieldVal(pwEntry.getCurrentTOTP()));
                String uriPlaceholder =
                        Lang.get("SafeContentsUI.passwordEntry.totp.config.uri.placeholder");
                uriField.enabledVisibF.setPromptText(uriPlaceholder);
                uriField.disabledVisibF.setPromptText(uriPlaceholder);
                
                TilePane btnsTileP = new TilePane();
                btnsTileP.getStyleClass().add("buttons-tile");
                UIUtils.forcePrefWidth(btnsTileP);
                
                Button pwCopyBtn = UIUtils.newCopyBtn(
                        uriField,
                        "SafeContentsUI.passwordEntry.totp.config.copy.button"
                );
                
                btnsTileP.getChildren().addAll(pwCopyBtn, uriField.visibilityBtn);
                
                res.getChildren().addAll(uriField.rootPane, btnsTileP);
                HBox.setHgrow(uriField.rootPane, Priority.ALWAYS);
                HBox.setHgrow(btnsTileP, Priority.NEVER);
                
                // Dynamic
                
                uriField.disabledVisibF.editableProperty()
                        .bind(editModeCheckbox.selectedProperty());
                uriField.enabledVisibF.editableProperty().bind(editModeCheckbox.selectedProperty());
                uriField.setupValidIndic(
                        Lang.get("SafeContentsUI.passwordEntry.totp.config.uri.invalid"),
                        true
                );
                uriField.valChangeNotifier.addListener(() -> {
                    try {
                        TOTP totp = UIUtils.totpFromURI(uriField.getVal());
                        pwEntry.setTOTP(totp);
                        if (totp != null) {
                            UIUtils.tryDestroy(totp);
                        }
                        uriField.setValidity(true);
                        ui.updateUnsavedFooterDisplay();
                    } catch (IllegalArgumentException ex) {
                        uriField.setValidity(false);
                    }
                });
                
                return res;
            }
            
            private static class GeneratePasswordPopupHolder implements Destroyable {
                
                private static final Logger popupLog =
                        Logs.newLogger(GeneratePasswordPopupHolder.class);
                static final String POPUP_LANG_BASE =
                        "SafeContentsUI.passwordEntry.password.generate.popup";
                static final String ALPHABET_LANG_BASE = POPUP_LANG_BASE + ".alphabet";
                static final String LENGTH_LANG_BASE = POPUP_LANG_BASE + ".length";
                static final int MIN_LEN = 1;
                static final int MAX_LEN = 10000;
                static final int[] LEN_BTNS_REL_ADD = new int[] {
                        -10, -5, -1, 1, 5, 10
                };
                
                final Stage stage;
                final Scene scene;
                final ViewableUnclearField resultField;
                boolean isValidated = false;
                
                private GeneratePasswordPopupHolder() {
                    VBox rootVBox = new VBox();
                    rootVBox.setId("safe-contents-generate-password-root-vbox");
                    
                    TitledPane alphabetTitledP = new TitledPane();
                    alphabetTitledP.setText(Lang.get(ALPHABET_LANG_BASE + ".title"));
                    alphabetTitledP.setCollapsible(false);
                    
                    VBox alphabetVBox = new VBox();
                    alphabetVBox.getStyleClass().add("alphabet-vbox");
                    
                    CheckBox lowerLettersCheckB =
                            new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".lowercaseLetters"));
                    CheckBox upperLettersCheckB =
                            new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".uppercaseLetters"));
                    CheckBox digitsCheckB = new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".digits"));
                    
                    HBox customHBox = new HBox();
                    customHBox.getStyleClass().add("custom-alphabet-hbox");
                    
                    CheckBox customCheckB =
                            new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".customChars"));
                    TextField customTextF = new TextField();
                    
                    customHBox.getChildren().addAll(customCheckB, customTextF);
                    
                    alphabetVBox.getChildren()
                            .addAll(
                                    lowerLettersCheckB,
                                    upperLettersCheckB,
                                    digitsCheckB,
                                    customHBox
                            );
                    
                    alphabetTitledP.setContent(alphabetVBox);
                    
                    TitledPane lengthTitledP = new TitledPane();
                    lengthTitledP.setText(Lang.get(LENGTH_LANG_BASE + ".title"));
                    lengthTitledP.setCollapsible(false);
                    
                    GridPane lengthGrid = new GridPane();
                    lengthGrid.getStyleClass().add("length-grid");
                    
                    // Max before min to be able to type max=40 then min=20, otherwise typing min=20 then max=40 would be processed as min=20 then max=4 which would trigger min=4
                    NumberField maxLenField = new NumberField(MIN_LEN, MAX_LEN, LEN_BTNS_REL_ADD);
                    UIUtils.addNodeToGrid(
                            lengthGrid,
                            0,
                            LENGTH_LANG_BASE + ".max",
                            maxLenField.rootHBox,
                            false
                    );
                    
                    NumberField minLenField = new NumberField(MIN_LEN, MAX_LEN, LEN_BTNS_REL_ADD);
                    UIUtils.addNodeToGrid(
                            lengthGrid,
                            1,
                            LENGTH_LANG_BASE + ".min",
                            minLenField.rootHBox,
                            false
                    );
                    
                    lengthTitledP.setContent(lengthGrid);
                    
                    Button generateBtn = UIUtils.newBtn(
                            POPUP_LANG_BASE + ".generate.button",
                            "generate-password",
                            true,
                            false
                    );
                    
                    TitledPane resultTitledP = new TitledPane();
                    resultTitledP.setText(Lang.get(POPUP_LANG_BASE + ".result.title"));
                    resultTitledP.setCollapsible(false);
                    resultTitledP.getStyleClass().add("result-titled-pane");
                    
                    VBox resultVBox = new VBox();
                    resultVBox.getStyleClass().add("result-vbox");
                    
                    resultField = new ViewableUnclearField(new MutableString.Simple());
                    HBox resultPwHBox = UIUtils.newViewableUnclearFieldHBox(resultField);
                    
                    Button validateBtn =
                            new Button(Lang.get(POPUP_LANG_BASE + ".result.validate.button.text"));
                    
                    resultVBox.getChildren().addAll(resultPwHBox, validateBtn);
                    
                    resultTitledP.setContent(resultVBox);
                    
                    Button cancelBtn = UIUtils
                            .newBtn(POPUP_LANG_BASE + ".cancel.button", "cancel", true, false);
                    
                    rootVBox.getChildren()
                            .addAll(
                                    alphabetTitledP,
                                    lengthTitledP,
                                    generateBtn,
                                    resultTitledP,
                                    cancelBtn
                            );
                    
                    scene = new Scene(rootVBox);
                    
                    stage = new Stage();
                    stage.setTitle(Lang.get(POPUP_LANG_BASE + ".title"));
                    stage.initModality(Modality.APPLICATION_MODAL);
                    UIUtils.setAppIcon(stage);
                    
                    // Dynamic
                    
                    lowerLettersCheckB.setSelected(true);
                    upperLettersCheckB.setSelected(true);
                    digitsCheckB.setSelected(true);
                    
                    customTextF.textProperty().isEmpty().addListener((ov, oldEmpty, newEmpty) -> {
                        customCheckB.setSelected(!newEmpty);
                    });
                    
                    minLenField.valChangeNotifier.addListener(() -> {
                        if (maxLenField.getVal() < minLenField.getVal()) {
                            maxLenField.setVal(minLenField.getVal(), false);
                        }
                    });
                    maxLenField.valChangeNotifier.addListener(() -> {
                        if (minLenField.getVal() > maxLenField.getVal()) {
                            minLenField.setVal(maxLenField.getVal(), false);
                        }
                    });
                    
                    generateBtn.setOnAction((e) -> {
                        StringBuilder alphabetSB = new StringBuilder();
                        if (lowerLettersCheckB.isSelected()) {
                            alphabetSB.append("abcdefghijklmnopqrstuvwxyz");
                        }
                        if (upperLettersCheckB.isSelected()) {
                            alphabetSB.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                        }
                        if (digitsCheckB.isSelected()) {
                            alphabetSB.append("0123456789");
                        }
                        if (customCheckB.isSelected()) {
                            alphabetSB.append(customTextF.getText());
                        }
                        
                        String alphabet = alphabetSB.toString();
                        popupLog.debug(() -> "alphabet = " + alphabet);
                        
                        if (!StringUtils.containsUniqueChars(alphabet)) {
                            Alert errorPopup = new Alert(
                                    AlertType.ERROR,
                                    Lang.get(POPUP_LANG_BASE + ".generate.error.duplicateChars"),
                                    ButtonType.OK
                            );
                            UIUtils.showDialogAndWait(errorPopup);
                            return;
                        }
                        try {
                            char[] resChars = RandomUtils.newRandomChars(
                                    minLenField.getVal(),
                                    maxLenField.getVal(),
                                    alphabet
                            );
                            resultField.setVal(resChars);
                            MemUtils.clearCharArray(resChars);
                            
                            GlobalConfig conf = GlobalConfig.getInstance();
                            conf.setPwGenerationMinLen(minLenField.getVal());
                            conf.setPwGenerationMaxLen(maxLenField.getVal());
                            if (customCheckB.isSelected()) {
                                conf.setPwGenerationCustomChars(customTextF.getText());
                            }
                            conf.updateUserFile();
                        } catch (NoSuchAlgorithmException | IOException ex) {
                            UIApp.getInstance().showError(ex);
                        }
                    });
                    
                    validateBtn.setOnAction((e) -> {
                        isValidated = true;
                        stage.close();
                    });
                    
                    cancelBtn.setOnAction((e) -> {
                        stage.close();
                    });
                    
                    GlobalConfig conf = GlobalConfig.getInstance();
                    minLenField.setVal(conf.getPwGenerationMinLen(), false);
                    maxLenField.setVal(conf.getPwGenerationMaxLen(), false);
                    customTextF.setText(conf.getPwGenerationCustomChars());
                }
                
                void showAndWait() {
                    UIUtils.showSceneAndWait(scene, stage);
                }
                
                @Override
                public void destroy() throws DestroyFailedException {
                    MemUtils.tryDestroy(resultField);
                }
                
                @Override
                public boolean isDestroyed() {
                    return resultField.isDestroyed();
                }
                
            }
            
            @Override
            public void destroy() throws DestroyFailedException {
                boolean success = true;
                success = MemUtils.tryDestroy(pwField) && success;
                if (totpTimeline != null) {
                    totpTimeline.stop();
                }
                if (uriField != null) {
                    success = MemUtils.tryDestroy(uriField) && success;
                }
                if (genPwPopupH != null) {
                    success = MemUtils.tryDestroy(genPwPopupH) && success;
                }
                success = MemUtils.tryDestroy(nameField) && success;
                success = MemUtils.tryDestroy(siteField) && success;
                success = MemUtils.tryDestroy(infoField) && success;
                
                ObservableMap<KeyCombination, Runnable> accelerators =
                        ui.getScene().getAccelerators();
                accelerators
                        .remove(UIConfig.KeyboardShortcut.COPY_CURRENT_TOTP.getKeyCombination());
                accelerators.remove(UIConfig.KeyboardShortcut.COPY_NEXT_TOTP.getKeyCombination());
                
                if (!success) {
                    throw new DestroyFailedException();
                }
            }
            
            @Override
            public boolean isDestroyed() {
                return pwField.isDestroyed();
            }
            
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            boolean success = true;
            
            if (pwPaneH != null) {
                success = MemUtils.tryDestroy(pwPaneH) && success;
                pwPaneH = null;
            }
            success = MemUtils.tryDestroy(filterNameField) && success;
            success = MemUtils.tryDestroy(filterSiteField) && success;
            for (Destroyable destroyable : destroyables) {
                destroyable.destroy();
            }
            destroyables.clear();
            
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return pwPaneH == null
                    && filterNameField.isDestroyed()
                    && filterSiteField.isDestroyed();
        }
        
    }
    
    @Override
    protected void onSelectedAfter() {
        if (contentH != null) {
            contentH.filterNameField.requestFocus();
        }
    }
    
    @Override
    protected void onDeselectedBefore() {
        dm.clearInvalidNewPwEntries();
        ObservableMap<KeyCombination, Runnable> accelerators = ui.getScene().getAccelerators();
        accelerators.remove(UIConfig.KeyboardShortcut.FILTER.getKeyCombination());
        accelerators.remove(UIConfig.KeyboardShortcut.ADD.getKeyCombination());
        accelerators.remove(UIConfig.KeyboardShortcut.EDIT_MODE.getKeyCombination());
        accelerators.remove(UIConfig.KeyboardShortcut.COPY_PASSWORD.getKeyCombination());
        accelerators.remove(UIConfig.KeyboardShortcut.COPY_CURRENT_TOTP.getKeyCombination());
        accelerators.remove(UIConfig.KeyboardShortcut.COPY_NEXT_TOTP.getKeyCombination());
        
        if (contentH != null) {
            UIUtils.tryDestroy(contentH);
            contentH = null;
        }
    }
    
}
