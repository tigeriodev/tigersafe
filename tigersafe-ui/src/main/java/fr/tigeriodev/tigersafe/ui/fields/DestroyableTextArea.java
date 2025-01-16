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

import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.scene.control.TextArea;

public class DestroyableTextArea extends TextArea
        implements DestroyableTextInputControl.InsertableLongText, Destroyable {
    
    private final DestroyableTextInputControl destroyInputC;
    /**
     * Manager of listeners for this field text value, which are not triggered when this field is destroyed.
     */
    public final ChangeNotifier valChangeNotifier;
    public FieldValidityIndication validIndic = null;
    private String longTextToInsert = null;
    
    public DestroyableTextArea() {
        super();
        
        destroyInputC = new DestroyableTextInputControl(this, true);
        valChangeNotifier = destroyInputC.valChangeNotifier;
    }
    
    @Override
    public void setLongTextToInsert(String newVal) {
        longTextToInsert = newVal;
    }
    
    @Override
    public String getLongTextToInsert() {
        return longTextToInsert;
    }
    
    @Override
    public void paste() {
        destroyInputC.paste();
    }
    
    @Override
    public void copy() {
        destroyInputC.copy();
    }
    
    @Override
    public void replaceText(final int start, final int end, final String text) {
        destroyInputC.replaceText(start, end, text);
    }
    
    @Override
    public void clear() {
        destroyInputC.clear();
    }
    
    /**
     * 
     * @param newValSrc the new text value as a String which is not kept, and should be {@link MemUtils#tryClearString(String)} after use if containing sensitive data.
     */
    public void setVal(String newValSrc) {
        setLongTextToInsert(newValSrc);
        replaceText(0, getLength(), newValSrc);
    }
    
    /**
     * NB: {@link #getText()} returns ephemeral current text value, which will be cleared when the field value changes.
     * @return a clone of the current text value
     */
    public String getValClone() {
        return StringUtils.clone(getText());
    }
    
    /**
     * 
     * @return ephemeral current text value, which will be cleared when the field value changes.
     */
    public String getVal() {
        return getText();
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        destroyInputC.destroy();
    }
    
    @Override
    public boolean isDestroyed() {
        return destroyInputC.isDestroyed();
    }
    
}
