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

import java.util.ArrayList;
import java.util.List;

import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class NumberField {
    
    public final int minVal;
    public final int maxVal;
    public final HBox rootHBox;
    public final TextField inputField;
    private final List<Button> incrBtns;
    private final List<Button> decrBtns;
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private int val;
    
    public NumberField(int minVal, int maxVal, int[] btnsRelativeAdd) {
        if (minVal > maxVal) {
            throw new IllegalArgumentException("minVal > maxVal");
        }
        this.minVal = minVal;
        this.maxVal = maxVal;
        
        rootHBox = new HBox();
        rootHBox.getStyleClass().add("number-field-root-hbox");
        
        inputField = new TextField();
        
        incrBtns = new ArrayList<>(btnsRelativeAdd.length);
        decrBtns = new ArrayList<>(btnsRelativeAdd.length);
        for (int btnRelativeAdd : btnsRelativeAdd) {
            Button btn = newBtn(btnRelativeAdd);
            if (btnRelativeAdd < 0) {
                decrBtns.add(btn);
            } else {
                incrBtns.add(btn);
            }
        }
        
        rootHBox.getChildren().addAll(decrBtns);
        rootHBox.getChildren().add(inputField);
        rootHBox.getChildren().addAll(incrBtns);
        
        inputField.textProperty().addListener((ov, oldText, newText) -> {
            try {
                setVal(Integer.parseInt(newText), true);
            } catch (NumberFormatException ex) {
                inputField.setText(oldText);
            }
        });
        
        setVal(minVal, false);
    }
    
    private Button newBtn(int relativeAdd) {
        Button res = new Button((relativeAdd > 0 ? "+" : "") + relativeAdd);
        res.setOnAction((e) -> {
            int newVal = val + relativeAdd;
            if (newVal < minVal) {
                newVal = minVal;
            } else if (newVal > maxVal) {
                newVal = maxVal;
            }
            setVal(newVal, false);
        });
        return res;
    }
    
    public boolean isValidVal(int val) {
        return val >= minVal && val <= maxVal;
    }
    
    public void setVal(int newVal, boolean isInputUpdated) {
        if (newVal == val) {
            return;
        }
        
        boolean isNewValValid = isValidVal(newVal);
        if (isNewValValid) {
            boolean wasLimitVal = val == minVal || val == maxVal;
            val = newVal;
            if (wasLimitVal || newVal == minVal || newVal == maxVal) {
                for (Button decrBtn : decrBtns) {
                    decrBtn.setDisable(newVal == minVal);
                }
                for (Button incrBtn : incrBtns) {
                    incrBtn.setDisable(newVal == maxVal);
                }
            }
            valChangeNotifier.notifyListeners();
        }
        if (!isInputUpdated || !isNewValValid) {
            inputField.setText(val + "");
        }
    }
    
    public int getVal() {
        return val;
    }
    
}
