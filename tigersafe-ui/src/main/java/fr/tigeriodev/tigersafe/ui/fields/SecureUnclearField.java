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

import java.util.Arrays;

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
import fr.tigeriodev.tigersafe.utils.MutableString;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.application.Platform;
import javafx.scene.control.PasswordField;
import javafx.scene.input.Clipboard;

public class SecureUnclearField extends PasswordField implements Destroyable {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(SecureUnclearField.class);
    static final String FAKE_CHAR = " "; // Should be only 1 char
    
    private final Logger unsafeInstLog = unsafeLog.newChildFromInstance(this);
    private MutableString valHolder;
    /**
     * null = curValFakeText
     * !null = can be curValFakeText
     */
    private String curFakeText = null;
    private boolean isRefreshScheduled = false;
    /**
     * Manager of listeners for this field real value, which are not triggered when the real value holder is destroyed.
     */
    public final ChangeNotifier valChangeNotifier = new ChangeNotifier();
    private String longTextToInsert = null;
    
    public SecureUnclearField(MutableString valHolder) {
        this.valHolder = CheckUtils.notNull(valHolder);
        
        textProperty().addListener((ov, oldText, newText) -> {
            unsafeInstLog.debug(
                    () -> "textProp listener: " + StringUtils.quote(oldText) + " -> "
                            + StringUtils.quote(newText)
            );
            
            // Needed because JavaFX undo/redo doesn't use replaceText()
            if (!isCurFakeText(newText)) {
                unsafeInstLog.debug(
                        () -> "textProp listener: newText " + StringUtils.quote(newText)
                                + " != curFakeText " + StringUtils.quote(getCurFakeText())
                                + ", isCurValFakeText(newText) = " + isCurValFakeText(newText)
                                + ", will rollback..."
                );
                if (!isRefreshScheduled) {
                    isRefreshScheduled = true;
                    Platform.runLater(() -> { // Delay because of JavaFX undo/redo impl
                        unsafeInstLog.debug(
                                () -> "textProp listener: rollback to curFakeText = "
                                        + StringUtils.quote(getCurFakeText())
                                        + " after unwanted newText = " + StringUtils.quote(newText)
                        );
                        setFakeText(getCurFakeText());
                        end();
                        isRefreshScheduled = false;
                    });
                }
            }
        });
        
        // Simulate the user typed the initial characters of the field
        simulateUserTyping(valHolder.getVal().length);
    }
    
    /**
     * 
     * @param targetFieldLen the displayed fake text length to reach at the end of the simulation
     */
    private void simulateUserTyping(int targetFieldLen) {
        Logger unsafeMethLog = unsafeInstLog.newChildFromCurMeth();
        unsafeMethLog.debug(() -> "targetFieldLen = " + targetFieldLen);
        MutableString initValH = valHolder;
        
        valHolder = new MutableString.Simple(getText());
        for (int curLen = getLength(); curLen < targetFieldLen; curLen++) {
            replaceText(curLen, curLen, FAKE_CHAR);
        }
        unsafeMethLog.debug(() -> "simulation end, field len = " + getLength());
        MemUtils.tryDestroy(valHolder);
        
        valHolder = initValH;
    }
    
    /**
     * @return the real value (not a duplicate)
     * @NotNull
     */
    public char[] getVal() {
        return getValHolder().getVal();
    }
    
    public MutableString getValHolder() {
        return valHolder;
    }
    
    /**
     * NB: Doesn't change the displayed fake text.
     * @return true if changed.
     */
    private boolean updateCurFakeText() {
        if (curFakeText != null && !isCurValFakeText(curFakeText)) {
            curFakeText = null;
            return true;
        } else {
            return false;
        }
    }
    
    private boolean isCurValFakeText(String text) {
        char[] curVal = valHolder.getVal();
        int len = text.length();
        if (curVal.length != len) {
            return false;
        }
        char fakeChar = FAKE_CHAR.charAt(0);
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) != fakeChar) {
                return false;
            }
        }
        return true;
    }
    
    private String getCurFakeText() {
        if (curFakeText == null) {
            curFakeText = FAKE_CHAR.repeat(valHolder.getVal().length);
            unsafeInstLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(() -> "curFakeText := " + StringUtils.quote(curFakeText));
        }
        return curFakeText;
    }
    
    private boolean isCurFakeText(String text) {
        return (curFakeText != null && text.equals(curFakeText))
                || (curFakeText == null && isCurValFakeText(text));
    }
    
    @Override
    public void selectRange(int anchor, int caretPosition) {
        int forcedPos = getLength();
        super.selectRange(anchor == 0 ? 0 : forcedPos, forcedPos);
    }
    
    /**
     * NB: This is not called when doing {@link #setText(String)}.
     */
    @Override
    public void replaceText(int start, int end, String text) {
        final String fText = text;
        Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
        unsafeMethLog.debug(
                () -> "start = " + start + ", end = " + end + ", text = " + StringUtils.quote(fText)
        );
        int initFieldLen = getLength();
        boolean isFullSelectionReplacement = start == 0
                && end == initFieldLen
                && getAnchor() == 0
                && getCaretPosition() == initFieldLen;
        
        if (text.isEmpty()) {
            if (start == end - 1 && end == initFieldLen) {
                valHolder.remLastChar();
            } else if (isFullSelectionReplacement) {
                valHolder.clear();
            } else {
                return; // Cancel change
            }
        } else {
            if ((start == end && end == initFieldLen) || isFullSelectionReplacement) {
                if (UIUtils.countInvalidChars(text, false) > 0) {
                    return; // Cancel change
                }
                if (isFullSelectionReplacement) {
                    valHolder.setChars(text);
                } else {
                    valHolder.addChars(text);
                }
                int len = text.length();
                if (len > 1) {
                    if (text != longTextToInsert) {
                        // defensive choice: avoid any dangerous oversight of a sensitive String that would not be cleared
                        throw new IllegalArgumentException("Unexpected long text to insert.");
                    } else {
                        // text should be cleared after this method call
                        setLongTextToInsert(null);
                    }
                }
                text = FAKE_CHAR.repeat(len);
            } else {
                return; // Cancel change
            }
        }
        unsafeMethLog.debug(() -> "(modified) valHolder = " + Arrays.toString(valHolder.getVal()));
        updateCurFakeText();
        super.replaceText(start, end, text);
        valChangeNotifier.notifyListeners();
    }
    
    @Override
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
        
        setLongTextToInsert(clipboardStr);
        replaceSelection(clipboardStr);
        
        if (clipboardStr != clipboardStr2) { // clipboard source is outside of Java
            MemUtils.tryClearString(clipboardStr);
            MemUtils.tryClearString(clipboardStr2);
        } // else clipboard source is in Java, should be cleared when possible
    }
    
    @Override
    public void copy() {}
    
    @Override
    public void cut() {}
    
    /**
     * Must be called before {@link #replaceText(int, int, String)} when text length > 1.
     * Text should be cleared after {@link #replaceText(int, int, String)}.
     * @param newVal the text that will be inserted with {@link #replaceText(int, int, String)}
     */
    private void setLongTextToInsert(String newVal) {
        longTextToInsert = newVal;
    }
    
    public void setVal(char[] newValSrc) {
        valHolder.setChars(newValSrc);
        refresh();
        valChangeNotifier.notifyListeners();
    }
    
    /**
     * Refreshes the displayed fake text with the current real value.
     */
    public void refresh() {
        updateCurFakeText();
        if (!isCurFakeText(getText())) {
            setFakeText(getCurFakeText());
        }
    }
    
    public void setFakeText(String newVal) {
        unsafeInstLog.newChildFromCurMethIf(Level.DEBUG)
                .debug(() -> "curFakeText := " + StringUtils.quote(newVal));
        curFakeText = newVal;
        setText(newVal);
    }
    
    @Override
    public void clear() {
        valHolder.clear();
        updateCurFakeText();
        super.clear();
        valChangeNotifier.notifyListeners();
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
        unsafeMethLog.debug(() -> "start");
        
        boolean success = true;
        if (valHolder != null) {
            unsafeMethLog.debug(() -> "destroy valHolder");
            success = MemUtils.tryDestroy(valHolder) && success;
        }
        
        valChangeNotifier.remAllListeners();
        
        // Simulate the user typed max characters, taking into account the already typed ones
        simulateUserTyping(SafeDataManager.EXPECTED_PW_MAX_LEN);
        
        unsafeMethLog.debug(() -> "setFakeText calls...");
        setFakeText(FAKE_CHAR.repeat(SafeDataManager.EXPECTED_PW_MAX_LEN));
        setFakeText(" ");
        setFakeText("");
        
        // JavaFX undo/redo internal data is cleared by setFakeText() which calls resetUndoRedoState()
        
        valHolder = null;
        longTextToInsert = null;
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return valHolder == null;
    }
    
}
