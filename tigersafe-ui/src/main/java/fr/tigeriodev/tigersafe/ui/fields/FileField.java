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

import java.io.File;
import java.util.Objects;

import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class FileField {
    
    public final TextField textField;
    public final FieldValidityIndication validIndic;
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private File val = null;
    
    public FileField(String validityRequirements, ExtensionFilter extensionFilter) {
        textField = new TextField();
        textField.setEditable(false);
        
        validIndic = new FieldValidityIndication(textField, validityRequirements, true);
        textField.setOnMouseClicked((e) -> {
            FileChooser fileChooser = new FileChooser();
            if (extensionFilter != null) {
                fileChooser.getExtensionFilters().add(extensionFilter);
            }
            File parentDir = val != null ? val.getParentFile() : null;
            fileChooser.setInitialDirectory(
                    parentDir != null && parentDir.isDirectory() ? parentDir : new File(".")
            );
            if (val != null) {
                fileChooser.setInitialFileName(val.getName());
            }
            File newFile = fileChooser.showOpenDialog(textField.getScene().getWindow());
            if (newFile != null) {
                setVal(newFile);
            }
        });
    }
    
    public void setVal(File newFile) {
        boolean hasChanged = !Objects.equals(newFile, val);
        val = newFile;
        checkValid();
        if (hasChanged) {
            valChangeNotifier.notifyListeners();
        }
    }
    
    public File getVal() {
        return val;
    }
    
    public boolean checkValid() {
        boolean isValid = val != null && val.isFile();
        textField.setText(val != null ? val.getAbsolutePath() : "");
        validIndic.setValidity(isValid);
        return isValid;
    }
    
}
