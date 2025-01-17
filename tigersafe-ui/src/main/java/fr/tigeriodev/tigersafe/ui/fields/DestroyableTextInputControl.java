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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.ReflectionUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;

public class DestroyableTextInputControl implements Destroyable {
    
    public interface InsertableLongText {
        
        /**
         * Must be called before {@link #replaceText(int, int, String)} and {@link TextInputControl.Content#insert(int, String, boolean)} when text length > 1.
         * Text should be cleared after {@link #replaceText(int, int, String)} and {@link TextInputControl.Content#insert(int, String, boolean)}.
         * @param newVal the text that will be inserted with {@link #replaceText(int, int, String)} and/or {@link TextInputControl.Content#insert(int, String, boolean)}
         */
        void setLongTextToInsert(String newVal);
        
        String getLongTextToInsert();
        
    }
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(DestroyableTextInputControl.class);
    
    private static final Method replaceTextMeth = ReflectionUtils.getMeth(
            TextInputControl.class,
            "replaceText",
            int.class,
            int.class,
            String.class,
            int.class,
            int.class
    );
    
    private final Logger unsafeInstLog = unsafeLog.newChildFromInstance(this);
    private final TextInputControl inputC;
    private final boolean allowNewLineAndTab;
    /**
     * Manager of listeners for this field text value, which are not triggered when this field is destroyed.
     */
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private boolean notifyValChanges = true;
    
    public DestroyableTextInputControl(TextInputControl inputC, boolean allowNewLineAndTab) {
        if (!(inputC instanceof InsertableLongText)) {
            throw new IllegalArgumentException(
                    "TextInputControl must implement InsertableLongText."
            );
        }
        this.inputC = CheckUtils.notNull(inputC);
        this.allowNewLineAndTab = allowNewLineAndTab;
        
        inputC.textFormatterProperty().addListener((ov, oldFormatter, newFormatter) -> {
            throw new UnsupportedOperationException("textFormatter cannot be used.");
        });
        
        inputC.selectedTextProperty().addListener((ov, oldSelectedText, newSelectedText) -> {
            if (oldSelectedText != inputC.getText()) { // JavaFX selectedText is updated with substring, which can return the same String instance (getText(), should not be cleared here) or a new String (can be cleared here)
                MemUtils.tryClearString(oldSelectedText);
            }
        });
    }
    
    public void copy() {
        String selectedText = inputC.getSelectedText();
        if (selectedText.length() > 0) {
            // selectedText will be cleared when the selection will change.
            UIUtils.setClipboardContent(StringUtils.clone(selectedText));
        }
    }
    
    public void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasString()) {
            return;
        }
        String clipboardStr = clipboard.getString();
        if (clipboardStr == null) {
            return;
        }
        String clipboardStr2 = clipboard.getString();
        
        ((InsertableLongText) inputC).setLongTextToInsert(clipboardStr);
        inputC.replaceSelection(clipboardStr);
        
        if (clipboardStr != clipboardStr2) { // clipboard source is outside of Java
            MemUtils.tryClearString(clipboardStr);
            MemUtils.tryClearString(clipboardStr2);
        } // else clipboard source is in Java, should be cleared when possible
    }
    
    public void replaceText(final int start, final int end, final String text) {
        unsafeInstLog.newChildFromCurMethIf(Level.DEBUG)
                .debug(
                        () -> "start = " + start + ", end = " + end + ", text = "
                                + StringUtils.quote(text)
                );
        if (start > end) {
            throw new IllegalArgumentException();
        }
        
        if (text == null) {
            throw new NullPointerException();
        }
        
        if (start < 0 || end > inputC.getLength()) {
            throw new IndexOutOfBoundsException();
        }
        
        if (inputC.textProperty().isBound()) {
            throw new IllegalStateException("textProperty is bound.");
        }
        
        if (text.length() > 1) {
            if (text != ((InsertableLongText) inputC).getLongTextToInsert()) {
                // defensive choice: avoid any dangerous oversight of a sensitive String that would not be cleared
                throw new IllegalArgumentException("Unexpected long text to insert.");
            } // else setLongTextToInsert(null) will be set in DestroyableContent.insert
        }
        
        String validText;
        int invalidCharsNum = UIUtils.countInvalidChars(text, allowNewLineAndTab);
        if (invalidCharsNum > 0) {
            char[] validChars = UIUtils.getValidChars(text, allowNewLineAndTab); // avoid StringBuilder because not clearable
            validText = new String(validChars);
            MemUtils.clearCharArray(validChars);
        } else {
            validText = text;
        }
        
        try {
            // Prevents issues with undo/redo creating new Strings of sensitive data.
            replaceTextMeth.invoke(
                    inputC,
                    start,
                    end,
                    validText,
                    start + validText.length(),
                    start + validText.length()
            );
            if (notifyValChanges) {
                valChangeNotifier.notifyListeners();
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (validText != text) {
                MemUtils.tryClearString(validText); // was created here
            }
            // text should be cleared after this method call
        }
    }
    
    public void clear() {
        Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
        unsafeMethLog.debug(() -> "start");
        notifyValChanges = false;
        setVal(SecureUnclearField.FAKE_CHAR.repeat(inputC.getLength()));
        setVal(SecureUnclearField.FAKE_CHAR.repeat(SafeDataManager.EXPECTED_PW_MAX_LEN));
        setVal(" ");
        notifyValChanges = true;
        setVal("");
        unsafeMethLog.debug(() -> "end");
    }
    
    /**
     * 
     * @param newValSrc the new text value as a String which is not kept, and should be {@link MemUtils#tryClearString(String)} after use if containing sensitive data.
     */
    public void setVal(String newValSrc) {
        ((InsertableLongText) inputC).setLongTextToInsert(newValSrc);
        inputC.replaceText(0, inputC.getLength(), newValSrc); // not directly replaceText() defined here to support override in custom inputC.
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = true;
        valChangeNotifier.remAllListeners();
        clear();
        
        // JavaFX undo/redo internal data is never created thanks to replaceText override
        
        success = MemUtils.tryClearString(inputC.getSelectedText()) && success;
        
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return inputC.getLength() == 0 && inputC.getSelectedText().length() == 0;
    }
    
}
