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

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.MutableString;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class ViewableUnclearField implements Destroyable {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(ViewableUnclearField.class);
    
    private static final String DISABLE_VISIBILITY_ICON_PATH =
            ViewableUnclearField.class.getResource("/icons/disable-visibility.png")
                    .toExternalForm();
    private static final String ENABLE_VISIBILITY_ICON_PATH =
            ViewableUnclearField.class.getResource("/icons/enable-visibility.png").toExternalForm();
    
    private final Logger unsafeInstLog = unsafeLog.newChildFromInstance(this);
    public final SecureUnclearField disabledVisibF;
    public final DestroyableTextField enabledVisibF;
    public final StackPane rootPane;
    public final Button visibilityBtn;
    private final ImageView visibilityBtnImgV;
    
    /**
     * Manager of listeners for this field real value, which are not triggered when the real value holder is destroyed.
     */
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private boolean visibWillChange = false;
    
    FieldValidityIndication disabledVisibFValidIndic;
    FieldValidityIndication enabledVisibFValidIndic;
    
    public ViewableUnclearField(char[] initValSrc) {
        this(new MutableString.Simple(initValSrc));
    }
    
    public ViewableUnclearField(MutableString valHolder) {
        disabledVisibF = new SecureUnclearField(valHolder);
        enabledVisibF = new DestroyableTextField();
        rootPane = new StackPane(disabledVisibF, enabledVisibF);
        rootPane.getStyleClass().add("viewable-unclear-field-root-pane");
        
        visibilityBtn = new Button();
        visibilityBtnImgV = new ImageView();
        visibilityBtnImgV.setFitHeight(17);
        visibilityBtnImgV.setPreserveRatio(true);
        visibilityBtn.setGraphic(visibilityBtnImgV);
        
        // Dynamic
        
        setVisibilityEnabled(false);
        visibilityBtn.setOnAction((e) -> {
            setVisibilityEnabled(!isVisibilityEnabled());
        });
        
        enabledVisibF.valChangeNotifier.addListener(() -> {
            String newVal = enabledVisibF.getVal();
            unsafeInstLog.debug(
                    () -> "enabVisib valChange listener: " + StringUtils.quote(newVal)
                            + ", isVisibilityEnabled = " + isVisibilityEnabled()
                            + ", visibWillChange = " + visibWillChange
            );
            if (!visibWillChange && isVisibilityEnabled()) {
                unsafeInstLog.debug(
                        () -> "enabVisib valChange listener: valHolder.setChars(" + newVal + ")"
                );
                getValHolder().setChars(newVal);
                valChangeNotifier.notifyListeners();
            }
        });
        
        disabledVisibF.valChangeNotifier.addListener(() -> {
            valChangeNotifier.notifyListeners();
        });
    }
    
    public boolean isVisibilityEnabled() {
        return enabledVisibF.isVisible();
    }
    
    public void setVisibilityEnabled(boolean newVal) {
        unsafeInstLog.newChildFromCurMethIf(Level.DEBUG).debug(() -> "newVal = " + newVal);
        visibWillChange = true;
        
        if (newVal) {
            enabledVisibF.setVal(getVal());
            disabledVisibF.setFakeText("");
        } else {
            enabledVisibF.clear();
            disabledVisibF.refresh();
        }
        
        enabledVisibF.setVisible(newVal);
        disabledVisibF.setVisible(!newVal);
        visibWillChange = false;
        visibilityBtnImgV.setImage(
                new Image(newVal ? DISABLE_VISIBILITY_ICON_PATH : ENABLE_VISIBILITY_ICON_PATH)
        );
        UIUtils.setTooltip(
                visibilityBtn,
                "ViewableUnclearField." + (newVal ? "hide" : "show") + ".button"
        );
    }
    
    public void setVal(char[] newValSrc) {
        getValHolder().setChars(newValSrc);
        refresh();
        valChangeNotifier.notifyListeners();
    }
    
    public void refresh() {
        setVisibilityEnabled(isVisibilityEnabled());
    }
    
    public void setupValidIndic(String validityRequirements, boolean isValid) {
        disabledVisibFValidIndic =
                new FieldValidityIndication(disabledVisibF, validityRequirements, isValid);
        enabledVisibFValidIndic =
                new FieldValidityIndication(enabledVisibF, validityRequirements, isValid);
    }
    
    public void setValidity(boolean newVal) {
        disabledVisibFValidIndic.setValidity(newVal);
        enabledVisibFValidIndic.setValidity(newVal);
    }
    
    public boolean isValid() {
        return isVisibilityEnabled()
                ? enabledVisibFValidIndic.isValid()
                : disabledVisibFValidIndic.isValid();
    }
    
    public void setTooltip(String tooltipLangKey) {
        setTooltip(new Tooltip(Lang.get(tooltipLangKey)));
    }
    
    public void setTooltip(Tooltip tooltip) {
        disabledVisibF.setTooltip(tooltip);
        enabledVisibF.setTooltip(tooltip);
    }
    
    /**
     * @return the real value (not a duplicate)
     * @NotNull
     */
    public char[] getVal() {
        return getValHolder().getVal();
    }
    
    public MutableString getValHolder() {
        return disabledVisibF.getValHolder();
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        unsafeInstLog.newChildFromCurMethIf(Level.DEBUG).debug(() -> "start");
        boolean success = true;
        valChangeNotifier.remAllListeners();
        success = MemUtils.tryDestroy(enabledVisibF) && success;
        success = MemUtils.tryDestroy(disabledVisibF) && success; // destroyed at the end because of valHolder use
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return disabledVisibF.isDestroyed() && enabledVisibF.isDestroyed();
    }
    
}
