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

import fr.tigeriodev.tigersafe.ui.UIUtils;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class EditableComboBox extends ComboBox<String> {
    
    public EditableComboBox() {
        super();
        setEditable(true);
        getStyleClass().add("editable-combobox");
        
        setCellFactory(lv -> new ListCell<String>() {
            
            private final HBox hBox;
            
            {
                Label itemLabel = new Label();
                itemLabel.textProperty().bind(itemProperty());
                itemLabel.setMaxWidth(Double.POSITIVE_INFINITY);
                itemLabel.setMaxHeight(Double.POSITIVE_INFINITY);
                itemLabel.setOnMouseClicked((ev) -> EditableComboBox.this.hide());
                itemLabel.getStyleClass().add("combobox-item-label");
                
                ImageView deleteBtnImgV = UIUtils.newIcon("delete");
                deleteBtnImgV.getStyleClass().add("icon-button-imgv");
                Button itemDeleteBtn = new Button();
                itemDeleteBtn.setPickOnBounds(true);
                itemDeleteBtn.setGraphic(deleteBtnImgV);
                itemDeleteBtn.setFocusTraversable(false);
                itemDeleteBtn.getStyleClass().add("icon-button");
                itemDeleteBtn.setOnMouseClicked((ev) -> {
                    EditableComboBox.this.getItems().remove(getItem());
                });
                
                hBox = new HBox(itemLabel, itemDeleteBtn);
                HBox.setHgrow(itemLabel, Priority.ALWAYS);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hBox);
            }
            
        });
        
        // Dynamic
        
        skinProperty().addListener((ov, oldSkin, newSkin) -> {
            if (newSkin != null && newSkin instanceof ComboBoxListViewSkin<?> skin) {
                skin.setHideOnClick(false);
            }
        });
    }
    
    public void setValToFirstItem() {
        if (getItems().isEmpty()) {
            return;
        }
        String firstItem = getItems().get(0);
        if (firstItem != null) {
            setValue(firstItem);
        }
    }
    
}
