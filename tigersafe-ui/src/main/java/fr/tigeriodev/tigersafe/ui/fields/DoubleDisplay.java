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

import java.util.Locale;

import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

public class DoubleDisplay extends HBox {
    
    private final Locale locale = Lang.getLocale();
    private final char decimalSep = Lang.getDecimalSep();
    private final Canvas canvas = new Canvas();
    private double val;
    
    public DoubleDisplay() {
        super();
        canvas.setHeight(getStrHeight("10") * 1.5d);
        
        getStyleClass().add("double-display-root-hbox"); // canvas wrapped because does not support CSS
        getChildren().add(canvas);
        HBox.setHgrow(canvas, Priority.ALWAYS);
    }
    
    public void setVal(double newVal) {
        if (newVal == Double.POSITIVE_INFINITY) {
            printNumber("> ", Double.MAX_VALUE);
        } else if (newVal == Double.NEGATIVE_INFINITY) {
            printNumber("< ", -Double.MAX_VALUE);
        } else {
            printNumber("", newVal);
        }
        
        val = newVal;
    }
    
    public double getVal() {
        return val;
    }
    
    private void printNumber(String prefix, double num) {
        String formattedNum = String.format(locale, "%.4g", num).trim();
        
        String[] parts = formattedNum.split("e");
        if (parts.length == 1) {
            printSimple(prefix + stripZeros(parts[0]));
        } else if (parts.length == 2) {
            try {
                String expStr = Integer.toString(Integer.parseInt(parts[1]));
                printScientific(prefix, stripZeros(parts[0]), expStr);
            } catch (NumberFormatException ex) {
                printSimple(prefix + formattedNum);
            }
        } else {
            printSimple(prefix + formattedNum);
        }
    }
    
    private void printSimple(String line) {
        canvas.setWidth(getStrWidth(line));
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double height = canvas.getHeight();
        gc.clearRect(0, 0, canvas.getWidth(), height);
        gc.fillText(line, 0, height * 0.85d);
    }
    
    private void printScientific(String prefix, String mantisse, String exp) {
        String baseLine = prefix;
        if (mantisse.equals("1")) {
            baseLine += "10";
        } else if (mantisse.equals("-1")) {
            baseLine += "-10";
        } else {
            baseLine += mantisse + " × 10";
        }
        double baseLineWidth = getStrWidth(baseLine);
        canvas.setWidth(getStrWidth(baseLine + exp));
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double height = canvas.getHeight();
        gc.clearRect(0, 0, canvas.getWidth(), height);
        gc.fillText(baseLine, 0, height * 0.85d);
        gc.fillText(exp, baseLineWidth, height * 0.45d);
    }
    
    private String stripZeros(String numStr) {
        return StringUtils.stripZerosAfterSep(numStr, decimalSep);
    }
    
    private double getStrWidth(String str) {
        return getStrBounds(str).getWidth();
    }
    
    private double getStrHeight(String str) {
        return getStrBounds(str).getHeight();
    }
    
    private Bounds getStrBounds(String str) {
        Text text = new Text(str);
        text.setFont(canvas.getGraphicsContext2D().getFont());
        return text.getLayoutBounds();
    }
    
}
