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
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Popup;
import javafx.stage.Window;

public class FieldValidityIndication {
    
    public final Control field;
    private final String validityRequirements;
    private boolean isValid;
    private Popup requirementsPopup;
    private ChangeListener<Object> updaterListener;
    private ChangeListener<Boolean> winShowListener;
    private ChangeListener<Scene> winSceneListener;
    private boolean isListeningScrollPane = false;
    private boolean isListeningBoundsInParent = false;
    private boolean frozenParentScrollP = false;
    private ScrollPane parentScrollPane = null;
    private Window win = null;
    
    public FieldValidityIndication(Control field, String validityRequirements, boolean isValid) {
        this.field = CheckUtils.notNull(field);
        this.validityRequirements = validityRequirements;
        this.isValid = !isValid; // execute setValidity like with a new value.
        setValidity(isValid);
    }
    
    private boolean isSetup() {
        return requirementsPopup != null;
    }
    
    private void setupIfNeeded() {
        if (isSetup()) {
            return;
        }
        requirementsPopup = new Popup();
        Label requirementsLabel = new Label(validityRequirements);
        requirementsLabel.setWrapText(true);
        requirementsLabel.setMinHeight(50);
        requirementsLabel.minWidthProperty().bind(field.widthProperty());
        requirementsLabel.maxWidthProperty().bind(field.widthProperty());
        requirementsLabel.getStyleClass().add("invalid-field-requirements");
        requirementsPopup.getContent().add(requirementsLabel);
        
        updaterListener = (ov, oldVal, newVal) -> {
            updateRequirementsPopupDisplay();
        };
        
        winShowListener = (ov, oldShown, newShown) -> {
            if (!newShown) {
                remWindowPropsListeners();
            }
        };
        
        winSceneListener = (ov, oldV, newV) -> {
            remWindowPropsListeners();
        };
        
        // winShowListener and winSceneListener should be useless because focusedProperty() should be triggered, but they are still listened to avoid any unexpected, sneaky OutOfMemory 
    }
    
    public void setValidity(boolean newVal) {
        if (isValid == newVal) {
            return;
        }
        isValid = newVal;
        if (isValid && !isSetup()) {
            return;
        }
        setupIfNeeded();
        
        if (isValid) {
            field.getStyleClass().remove("invalid-field");
            field.focusedProperty().removeListener(updaterListener);
        } else {
            field.getStyleClass().add("invalid-field");
            field.focusedProperty().addListener(updaterListener);
        }
        updateRequirementsPopupDisplay(); // window props are not managed here but in updateRequirementsPopupDisplay() to be cleared as soon as possible, particularly when the field is unfocused (can occur when the scene/stage is closed while the field is invalid and focused) and therefore without waiting for the scene/stage to close (there is no direct way of listening to the visibility on the screen of a field, therefore we can only rely on the visibility of the scene). Since only one field can be focused at a time, this avoids the accumulation of listeners of different instances of this class on the window until the scene/stage would be closed even for fields being removed from the screen (dynamic addition and removal of fields on the same scene).
    }
    
    public boolean isValid() {
        return isValid;
    }
    
    private void updateRequirementsPopupDisplay() {
        ScrollPane parentScrollP = getParentScrollPane();
        if (parentScrollP != null) {
            if (!isValid && field.isFocused()) {
                if (!isListeningScrollPane) {
                    parentScrollP.vvalueProperty().addListener(updaterListener);
                    parentScrollP.viewportBoundsProperty().addListener(updaterListener);
                    // parentScrollP.hvalueProperty() currently useless
                    isListeningScrollPane = true;
                }
            } else {
                if (isListeningScrollPane) {
                    parentScrollP.vvalueProperty().removeListener(updaterListener);
                    parentScrollP.viewportBoundsProperty().removeListener(updaterListener);
                    isListeningScrollPane = false;
                }
            }
        }
        
        if (
            !isValid
                    && field.isFocused()
                    && (parentScrollP == null
                            || UIUtils.isVisibleInScrollPane(field, parentScrollP))
        ) { // field.isVisible() currently useless
            Point2D pos = field.localToScreen(0, field.getHeight());
            requirementsPopup.show(field.getParent(), pos.getX(), pos.getY());
            
            if (!isListeningBoundsInParent) {
                field.boundsInParentProperty().addListener(updaterListener);
                isListeningBoundsInParent = true;
            }
            
            Scene scene = field.getScene();
            if (scene != null) {
                Window win = scene.getWindow();
                if (win != null) {
                    frozenParentScrollP = true;
                    addWindowPropsListeners(win);
                }
            }
        } else {
            requirementsPopup.hide();
            if (isListeningBoundsInParent) {
                field.boundsInParentProperty().removeListener(updaterListener);
                isListeningBoundsInParent = false;
            }
            remWindowPropsListeners();
        }
    }
    
    private ScrollPane getParentScrollPane() {
        if (parentScrollPane != null || frozenParentScrollP) {
            return parentScrollPane;
        }
        
        int i = 0;
        Parent parent = field;
        do {
            parent = parent.getParent();
            if (parent instanceof ScrollPane scrollPane) {
                parentScrollPane = scrollPane;
                return parentScrollPane;
            }
        } while (parent != null && ++i < 20);
        return null;
    }
    
    private void addWindowPropsListeners(Window win) {
        if (this.win == win) {
            return;
        }
        if (this.win != null) {
            remWindowPropsListeners();
        }
        this.win = win;
        win.xProperty().addListener(updaterListener);
        win.yProperty().addListener(updaterListener);
        win.widthProperty().addListener(updaterListener);
        win.heightProperty().addListener(updaterListener);
        win.sceneProperty().addListener(winSceneListener);
        win.showingProperty().addListener(winShowListener);
        
    }
    
    private void remWindowPropsListeners() {
        if (win == null) {
            return;
        }
        win.xProperty().removeListener(updaterListener);
        win.yProperty().removeListener(updaterListener);
        win.widthProperty().removeListener(updaterListener);
        win.heightProperty().removeListener(updaterListener);
        win.sceneProperty().removeListener(winSceneListener);
        win.showingProperty().removeListener(winShowListener);
        win = null;
    }
    
}
