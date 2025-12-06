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

import static fr.tigeriodev.tigersafe.GlobalConfig.PW_GENERATION_MAX_LEN;
import static fr.tigeriodev.tigersafe.GlobalConfig.PW_GENERATION_MIN_LEN;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIConfig;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.fields.DoubleDisplay;
import fr.tigeriodev.tigersafe.ui.fields.DoubleField;
import fr.tigeriodev.tigersafe.ui.fields.EditableComboBox;
import fr.tigeriodev.tigersafe.ui.fields.IntegerField;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.MutableString;
import fr.tigeriodev.tigersafe.utils.RandomUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GeneratePasswordPopup implements Destroyable {
    
    private static final Logger log = Logs.newLogger(GeneratePasswordPopup.class);
    static final String POPUP_LANG_BASE = "SafeContentsUI.passwordEntry.password.generate.popup";
    static final String ALPHABET_LANG_BASE = POPUP_LANG_BASE + ".alphabet";
    static final String LENGTH_LANG_BASE = POPUP_LANG_BASE + ".length";
    static final String STATS_LANG_BASE = POPUP_LANG_BASE + ".statistics";
    static final int[] LEN_BTNS_REL_ADD = new int[] {
            -10, -5, -1, 1, 5, 10
    };
    static final Set<Character> CUSTOM_CHARS_SEPARATORS = Set.of(' ', ',', ';', '/');
    
    private final CheckBox lowerLettersCheckB;
    private final CheckBox upperLettersCheckB;
    private final CheckBox digitsCheckB;
    private final CheckBox customCheckB;
    private final EditableComboBox customComboBox;
    private final IntegerField maxLenField;
    private final IntegerField minLenField;
    private final PossibilitiesHBox possibilitiesHBox;
    private final CrackTimeHBox crackTimeHBox;
    final Stage stage;
    final Scene scene;
    private final ViewableUnclearField resultField;
    private boolean isValidated = false;
    
    public GeneratePasswordPopup() {
        VBox rootVBox = new VBox();
        rootVBox.setId("safe-contents-generate-password-root-vbox");
        
        TitledPane alphabetTitledP = new TitledPane();
        alphabetTitledP.setText(Lang.get(ALPHABET_LANG_BASE + ".title"));
        alphabetTitledP.setCollapsible(false);
        
        VBox alphabetVBox = new VBox();
        alphabetVBox.getStyleClass().add("alphabet-vbox");
        
        lowerLettersCheckB = new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".lowercaseLetters"));
        upperLettersCheckB = new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".uppercaseLetters"));
        digitsCheckB = new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".digits"));
        
        HBox customHBox = new HBox();
        customHBox.getStyleClass().add("custom-chars-hbox");
        
        customCheckB = new CheckBox(Lang.get(ALPHABET_LANG_BASE + ".customChars"));
        customComboBox = new EditableComboBox();
        
        customHBox.getChildren().addAll(customCheckB, customComboBox);
        
        alphabetVBox.getChildren()
                .addAll(lowerLettersCheckB, upperLettersCheckB, digitsCheckB, customHBox);
        
        alphabetTitledP.setContent(alphabetVBox);
        
        TitledPane lengthTitledP = new TitledPane();
        lengthTitledP.setText(Lang.get(LENGTH_LANG_BASE + ".title"));
        lengthTitledP.setCollapsible(false);
        
        GridPane lengthGrid = new GridPane();
        lengthGrid.getStyleClass().add("length-grid");
        
        // Max before min to be able to type max=40 then min=20, otherwise typing min=20 then max=40 would be processed as min=20 then max=4 which would trigger min=4
        maxLenField =
                new IntegerField(PW_GENERATION_MIN_LEN, PW_GENERATION_MAX_LEN, LEN_BTNS_REL_ADD);
        UIUtils.addNodeToGrid(
                lengthGrid,
                0,
                LENGTH_LANG_BASE + ".max",
                maxLenField.rootHBox,
                false
        );
        
        minLenField =
                new IntegerField(PW_GENERATION_MIN_LEN, PW_GENERATION_MAX_LEN, LEN_BTNS_REL_ADD);
        UIUtils.addNodeToGrid(
                lengthGrid,
                1,
                LENGTH_LANG_BASE + ".min",
                minLenField.rootHBox,
                false
        );
        
        lengthTitledP.setContent(lengthGrid);
        
        TitledPane statsTitledP = new TitledPane();
        statsTitledP.setText(Lang.get(STATS_LANG_BASE + ".title"));
        statsTitledP.setCollapsible(true);
        
        VBox statsVBox = new VBox();
        statsVBox.getStyleClass().add("stats-vbox");
        
        possibilitiesHBox = new PossibilitiesHBox();
        crackTimeHBox = new CrackTimeHBox();
        
        statsVBox.getChildren().addAll(possibilitiesHBox, crackTimeHBox);
        
        statsTitledP.setContent(statsVBox);
        
        Button generateBtn = UIUtils
                .newBtn(POPUP_LANG_BASE + ".generate.button", "generate-password", true, false);
        
        TitledPane resultTitledP = new TitledPane();
        resultTitledP.setText(Lang.get(POPUP_LANG_BASE + ".result.title"));
        resultTitledP.setCollapsible(false);
        resultTitledP.getStyleClass().add("result-titled-pane");
        
        VBox resultVBox = new VBox();
        resultVBox.getStyleClass().add("result-vbox");
        
        resultField = new ViewableUnclearField(new MutableString.Simple());
        HBox resultPwHBox = UIUtils.newViewableUnclearFieldHBox(resultField);
        Button resultPwCopyBtn = UIUtils
                .newCopyBtn(resultField, "SafeContentsUI.passwordEntry.password.copy.button");
        HBox.setHgrow(resultPwCopyBtn, Priority.NEVER);
        resultPwHBox.getChildren().add(resultPwCopyBtn);
        
        Button validateBtn = new Button(Lang.get(POPUP_LANG_BASE + ".result.validate.button.text"));
        
        resultVBox.getChildren().addAll(resultPwHBox, validateBtn);
        
        resultTitledP.setContent(resultVBox);
        
        Button cancelBtn =
                UIUtils.newBtn(POPUP_LANG_BASE + ".cancel.button", "cancel", true, false);
        
        rootVBox.getChildren()
                .addAll(
                        alphabetTitledP,
                        lengthTitledP,
                        statsTitledP,
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
        
        ChangeListener<? super Boolean> checkboxListener = (ov, oldSelected, newSelected) -> {
            if (oldSelected != newSelected) {
                updateStats();
            }
        };
        lowerLettersCheckB.selectedProperty().addListener(checkboxListener);
        upperLettersCheckB.selectedProperty().addListener(checkboxListener);
        digitsCheckB.selectedProperty().addListener(checkboxListener);
        customCheckB.selectedProperty().addListener(checkboxListener);
        
        customComboBox.valueProperty().addListener((ov, oldCustomChars, newCustomChars) -> {
            if (CheckUtils.isNotEmpty(newCustomChars)) {
                customCheckB.setSelected(true);
                String newCustomCharsWithoutSeps =
                        StringUtils.removeSeparatorsIn(newCustomChars, CUSTOM_CHARS_SEPARATORS);
                if (
                    !newCustomCharsWithoutSeps.isEmpty()
                            && !newCustomChars.equals(newCustomCharsWithoutSeps)
                ) {
                    customComboBox.setValue(newCustomCharsWithoutSeps);
                }
            } // else keep checkbox as is, particularly useful in case the user wants to delete all saved customChars
            updateStats();
        });
        
        minLenField.valChangeNotifier.addListener(() -> {
            if (maxLenField.getVal() < minLenField.getVal()) {
                maxLenField.setVal(minLenField.getVal(), false);
            }
            updateStats();
        });
        maxLenField.valChangeNotifier.addListener(() -> {
            if (minLenField.getVal() > maxLenField.getVal()) {
                minLenField.setVal(maxLenField.getVal(), false);
            }
            updateStats();
        });
        
        generateBtn.setOnAction((e) -> {
            String alphabet = getAlphabet();
            log.debug(() -> "alphabet = " + alphabet);
            
            Character duplicateChar = StringUtils.getDuplicateCharIn(alphabet);
            if (duplicateChar != null) {
                Alert errorPopup = new Alert(
                        AlertType.ERROR,
                        Lang.get(POPUP_LANG_BASE + ".generate.error.duplicateChars", duplicateChar),
                        ButtonType.OK
                );
                UIUtils.showDialogAndWait(errorPopup);
                return;
            }
            try {
                char[] resChars = RandomUtils
                        .newRandomChars(minLenField.getVal(), maxLenField.getVal(), alphabet);
                resultField.setVal(resChars);
                MemUtils.clearCharArray(resChars);
            } catch (NoSuchAlgorithmException ex) {
                UIApp.getInstance().showError(ex);
            }
        });
        
        validateBtn.setOnAction((e) -> {
            try {
                GlobalConfig conf = GlobalConfig.getInstance();
                conf.setPwGenerationMinLen(minLenField.getVal());
                conf.setPwGenerationMaxLen(maxLenField.getVal());
                
                if (customCheckB.isSelected()) {
                    Set<String> customCharsSet = new LinkedHashSet<>(customComboBox.getItems());
                    String customChars = customComboBox.getValue();
                    if (CheckUtils.isNotEmpty(customChars)) {
                        customCharsSet.add(customChars);
                    }
                    conf.setPwGenerationCustomChars(customCharsSet);
                }
                conf.updateUserFile();
            } catch (IOException ex) {
                UIApp.getInstance().showError(ex);
            }
            isValidated = true;
            stage.close();
        });
        
        cancelBtn.setOnAction((e) -> {
            stage.close();
        });
        
        UIUtils.setButtonShortcut(
                scene,
                UIConfig.KeyboardShortcut.COPY_PASSWORD.getKeyCombination(),
                resultPwCopyBtn
        );
        
        GlobalConfig conf = GlobalConfig.getInstance();
        minLenField.setVal(conf.getPwGenerationMinLen(), false);
        maxLenField.setVal(conf.getPwGenerationMaxLen(), false);
        customComboBox.getItems().setAll(conf.getPwGenerationCustomChars());
        customComboBox.setValToFirstItem();
        updateStats();
    }
    
    private void updateStats() {
        possibilitiesHBox
                .update(minLenField.getVal(), maxLenField.getVal(), getAlphabet().length());
        crackTimeHBox.update(possibilitiesHBox.pwsNumDisplay.getVal());
    }
    
    private String getAlphabet() {
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
            String customChars = customComboBox.getValue();
            if (customChars != null) {
                alphabetSB.append(customChars);
            }
        }
        
        return alphabetSB.toString();
    }
    
    private static class LabelsHolder {
        
        private final Label[] labels;
        
        LabelsHolder(int size) {
            labels = new Label[size];
            for (int i = 0; i < size; i++) {
                labels[i] = UIUtils.newFullLabel("");
            }
        }
        
        void setText(String[] labelsText) {
            for (int i = 0; i < labelsText.length && i < labels.length; i++) {
                labels[i].setText(labelsText[i]);
            }
        }
        
        Label getLabel(int ind) {
            return labels[ind];
        }
        
    }
    
    private class PossibilitiesHBox extends HBox {
        
        final LabelsHolder labelsH;
        final DoubleDisplay pwsNumDisplay;
        
        PossibilitiesHBox() {
            super();
            getStyleClass().add("possibilities-hbox");
            
            labelsH = new LabelsHolder(2);
            pwsNumDisplay = new DoubleDisplay();
            
            getChildren().addAll(labelsH.getLabel(0), pwsNumDisplay, labelsH.getLabel(1));
        }
        
        void update(int minLen, int maxLen, int alphabetLen) {
            String possibilitiesStr = Lang.get(
                    POPUP_LANG_BASE + ".statistics.possibilities",
                    minLen,
                    maxLen,
                    alphabetLen
            );
            String[] possibilitiesParts = possibilitiesStr.split("%passwordsNum%", -1);
            
            labelsH.setText(possibilitiesParts);
            
            double pwsNum = 0d;
            if (minLen == maxLen) {
                pwsNum = Math.pow(alphabetLen, minLen);
            } else {
                for (int pwLen = minLen; pwLen <= maxLen; pwLen++) {
                    pwsNum += Math.pow(alphabetLen, pwLen);
                }
            }
            
            pwsNumDisplay.setVal(pwsNum);
        }
        
    }
    
    private class CrackTimeHBox extends HBox {
        
        static final String CRACK_LANG_BASE = POPUP_LANG_BASE + ".statistics.crackingTime";
        static final String CRACK_YEARS_PH = "%crackYears%";
        static final String ATTEMPTS_PER_SEC_PH = "%attemptsPerSec%";
        
        final LabelsHolder labelsH;
        final DoubleDisplay crackYearsNumDisplay;
        final DoubleField attemptsPerSecF;
        
        CrackTimeHBox() {
            super();
            getStyleClass().add("cracking-time-hbox");
            
            labelsH = new LabelsHolder(3);
            crackYearsNumDisplay = new DoubleDisplay();
            attemptsPerSecF = new DoubleField(
                    Lang.get(CRACK_LANG_BASE + ".attemptsPerSec.invalid"),
                    (d) -> d > 0,
                    1e9
            );
            
            String crackTimeStr = Lang.get(CRACK_LANG_BASE + ".text");
            String[] crackTimeParts =
                    crackTimeStr.split(CRACK_YEARS_PH + "|" + ATTEMPTS_PER_SEC_PH, -1);
            labelsH.setText(crackTimeParts);
            
            final Node firstNode;
            final Node secondNode;
            if (crackTimeStr.indexOf(ATTEMPTS_PER_SEC_PH) < crackTimeStr.indexOf(CRACK_YEARS_PH)) {
                firstNode = attemptsPerSecF.inputField;
                secondNode = crackYearsNumDisplay;
            } else {
                firstNode = crackYearsNumDisplay;
                secondNode = attemptsPerSecF.inputField;
            }
            
            getChildren().addAll(
                    labelsH.getLabel(0),
                    firstNode,
                    labelsH.getLabel(1),
                    secondNode,
                    labelsH.getLabel(2)
            );
            
            // Dynamic
            
            attemptsPerSecF.valChangeNotifier.addListener(() -> {
                updateStats();
            });
        }
        
        void update(double pwsNum) {
            double attemptsPerSec = attemptsPerSecF.getLastValidVal();
            double years = pwsNum / attemptsPerSec / (3600d * 24d * 365.25d);
            crackYearsNumDisplay.setVal(years);
        }
        
    }
    
    void showAndWait() {
        UIUtils.showSceneAndWait(scene, stage);
    }
    
    public boolean isValidated() {
        return isValidated;
    }
    
    public ViewableUnclearField getResultField() {
        return resultField;
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
