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
import javafx.stage.DirectoryChooser;

public class DirectoryField {
    
    public static boolean isValidDir(File dir) {
        return dir != null && dir.isDirectory() && dir.canWrite();
    }
    
    public final TextField textField;
    public final FieldValidityIndication validIndic;
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private File val = null;
    
    public DirectoryField(String validityRequirements) {
        textField = new TextField();
        textField.setEditable(false);
        
        validIndic = new FieldValidityIndication(textField, validityRequirements, true);
        textField.setOnMouseClicked((e) -> {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setInitialDirectory(val != null && val.isDirectory() ? val : new File("."));
            File newDir = dirChooser.showDialog(textField.getScene().getWindow());
            if (newDir != null) {
                setVal(newDir);
            }
        });
    }
    
    public void setVal(File newDir) {
        if (Objects.equals(newDir, val)) {
            return;
        }
        
        val = newDir;
        
        if (!isValidDir(newDir)) {
            textField.setText("");
            validIndic.setValidity(false);
        } else {
            textField.setText(newDir.getAbsolutePath());
            validIndic.setValidity(true);
        }
        
        valChangeNotifier.notifyListeners();
    }
    
    public File getVal() {
        return val;
    }
    
}
