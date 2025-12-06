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

package fr.tigeriodev.tigersafe.ui.fields;

import java.util.function.Function;

import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import javafx.scene.control.TextField;

public class DoubleField {
    
    private final Function<Double, Boolean> validChecker;
    public final TextField inputField;
    public final FieldValidityIndication validIndic;
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private double lastValidVal;
    
    public DoubleField(String validityRequirements, Function<Double, Boolean> validChecker,
            double initValidVal) {
        this.validChecker = CheckUtils.notNull(validChecker);
        inputField = new TextField();
        inputField.getStyleClass().add("double-field-text-field");
        validIndic = new FieldValidityIndication(inputField, validityRequirements, true);
        
        inputField.textProperty().addListener((ov, oldText, newText) -> {
            try {
                setVal(Double.parseDouble(newText), true);
            } catch (NumberFormatException ex) {
                validIndic.setValidity(false);
            }
        });
        
        lastValidVal = -1;
        setVal(initValidVal, false);
        if (!isValid()) {
            throw new IllegalArgumentException("The initValidVal is invalid.");
        }
    }
    
    public void setVal(double newVal, boolean isInputUpdated) {
        Boolean isNewValValid = validChecker.apply(newVal);
        validIndic.setValidity(isNewValValid);
        
        if (isNewValValid && newVal != lastValidVal) {
            lastValidVal = newVal;
            valChangeNotifier.notifyListeners();
        }
        
        if (!isInputUpdated) {
            inputField.setText(Double.toString(newVal));
        }
    }
    
    public double getLastValidVal() {
        return lastValidVal;
    }
    
    public boolean isValid() {
        return validIndic.isValid();
    }
    
}
