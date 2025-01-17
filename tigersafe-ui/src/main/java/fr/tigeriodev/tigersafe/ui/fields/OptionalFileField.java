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

import fr.tigeriodev.tigersafe.ui.UIUtils;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser.ExtensionFilter;

public class OptionalFileField extends FileField {
    
    public final HBox rootHBox;
    private final Button clearBtn;
    
    public OptionalFileField(String validityRequirements, ExtensionFilter extensionFilter) {
        super(validityRequirements, extensionFilter);
        rootHBox = new HBox();
        rootHBox.getStyleClass().add("optional-file-field-root-hbox");
        
        clearBtn = UIUtils.newBtn("OptionalFileField.clear.button", "cancel", false, true);
        clearBtn.setVisible(false);
        
        rootHBox.getChildren().addAll(textField, clearBtn);
        HBox.setHgrow(textField, Priority.ALWAYS);
        
        clearBtn.setOnAction((e) -> {
            setVal(null);
        });
    }
    
    @Override
    public void setVal(File newFile) {
        super.setVal(newFile);
        clearBtn.setVisible(getVal() != null);
    }
    
    @Override
    public boolean checkValid() {
        if (getVal() == null) {
            textField.setText("");
            validIndic.setValidity(true);
            return true;
        }
        return super.checkValid();
    }
    
}
