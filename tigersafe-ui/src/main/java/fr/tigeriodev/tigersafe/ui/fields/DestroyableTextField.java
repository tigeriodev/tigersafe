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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.ChangeNotifier;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.MutableString;
import fr.tigeriodev.tigersafe.utils.ReflectionUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.text.Text;

public class DestroyableTextField extends TextField
        implements DestroyableTextInputControl.InsertableLongText, Destroyable {
    
    private static final Logger unsafeLog = Logs.newUnsafeLogger(DestroyableTextField.class);
    
    private static final Field contentF =
            ReflectionUtils.getField(TextInputControl.class, "content");
    private static final Field textFieldSkinTextNodeF =
            ReflectionUtils.getField(TextFieldSkin.class, "textNode");
    private static final Field textLayoutF = ReflectionUtils.getField(Text.class, "layout");
    
    private static final class DestroyableContent implements Content, Destroyable {
        
        private static final Logger unsafeDestroyContentLog =
                unsafeLog.newChild(DestroyableContent.class);
        
        private final Logger unsafeInstLog = unsafeDestroyContentLog.newChildFromInstance(this);
        private final Content defContent;
        private MutableString valBuilder;
        private final Method fireValueChangedEventMeth;
        private String prevVal = null;
        private String curVal;
        private List<String> otherPrevVals = null;
        private List<String> otherCurVals;
        private String longTextToInsert = null;
        
        private DestroyableContent(Content defaultContent) {
            this.defContent = CheckUtils.notNull(defaultContent);
            this.valBuilder = new MutableString.Simple();
            try {
                fireValueChangedEventMeth = defContent.getClass()
                        .getSuperclass()
                        .getDeclaredMethod("fireValueChangedEvent");
                fireValueChangedEventMeth.setAccessible(true);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
            updateCurVal();
            updateDefContentHelper();
        }
        
        private void updateCurVal() {
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            if (prevVal != null) {
                unsafeMethLog.debug(() -> "clear prevVal = " + StringUtils.quote(prevVal));
                MemUtils.tryClearString(prevVal);
            }
            if (otherPrevVals != null) {
                unsafeMethLog
                        .debug(() -> "clear otherPrevVals (size = " + otherPrevVals.size() + ")");
                for (String str : otherPrevVals) {
                    MemUtils.tryClearString(str);
                }
            }
            
            prevVal = curVal;
            curVal = new String(valBuilder.getVal());
            unsafeMethLog.debug(() -> "curVal := " + StringUtils.quote(curVal));
            
            otherPrevVals = otherCurVals;
            otherCurVals = new ArrayList<>();
        }
        
        private void updateDefContentHelper() {
            // Fields are not cached because this method will rarely or only once be called.
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMeth();
            unsafeMethLog.debug(() -> "start");
            try {
                Field helperF = defContent.getClass().getSuperclass().getDeclaredField("helper");
                helperF.setAccessible(true);
                Object helper = helperF.get(defContent);
                if (helper != null) {
                    Field observableF =
                            helper.getClass().getSuperclass().getDeclaredField("observable");
                    observableF.setAccessible(true);
                    observableF.set(helper, this);
                    
                    // The helper observable is replaced by this DestroyableContent just after the creation of this TextField.
                    // At this replacement time, helper is SingleInvalidation, which doesn't hold currentValue.
                    // After that, if addListener is called on content (DestroyableContent because of replacement), currentValue will be filled with observable.getValue(), which is not DestroyableContent.getValue() but defContent.getValue()
                    // But defContent doesn't hold the real content, no need to clear its getValue (currentValue of helper) from memory.
                    unsafeMethLog.debug(() -> "defContent helper class = " + helper.getClass());
                    try {
                        Field curValField = helper.getClass().getDeclaredField("currentValue");
                        curValField.setAccessible(true);
                        curValField.set(helper, getValue());
                    } catch (NoSuchFieldException ex) {
                        unsafeMethLog.debug(() -> "defContent helper doesn't store currentValue");
                    }
                } else {
                    unsafeMethLog.warn(() -> "defContent helper is null");
                }
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        /**
         * Must be called before {@link #insert(int, String, boolean)} when text length > 1.
         * Text should be cleared after {@link #insert(int, String, boolean)}.
         * @param newVal the text that will be inserted with {@link #insert(int, String, boolean)}
         */
        private void setLongTextToInsert(String newVal) {
            longTextToInsert = newVal;
        }
        
        @Override
        public String get(int start, int end) {
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            if (isDestroyed()) {
                unsafeMethLog.debug(() -> "is destroyed");
                return "";
            }
            unsafeMethLog.debug(
                    () -> "start = " + start + ", end = " + end + ", curLen = " + curVal.length()
            );
            if (start == 0 && end == curVal.length()) {
                return curVal;
            } else {
                String res = curVal.substring(start, end);
                if (!res.isEmpty()) {
                    unsafeMethLog.debug(() -> "add to otherCurVals: " + StringUtils.quote(res));
                    otherCurVals.add(res);
                }
                return res;
            }
        }
        
        @Override
        public void insert(int index, String text, boolean notifyListeners) {
            if (text.isEmpty()) {
                return;
            }
            
            char[] textToInsert = UIUtils.getValidChars(text, false);
            
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            unsafeMethLog.debug(
                    () -> "text = " + StringUtils.quote(text) + ", textToInsert = "
                            + Arrays.toString(textToInsert)
            );
            
            if (textToInsert.length > 0) {
                if (!isDestroyed()) {
                    valBuilder.insertChars(index, textToInsert);
                    updateCurVal();
                } else {
                    unsafeMethLog.debug(() -> "is destroyed");
                }
                
                if (notifyListeners) {
                    notifyListeners();
                }
                
                MemUtils.clearCharArray(textToInsert);
            }
            
            if (text.length() > 1) {
                if (text != longTextToInsert) {
                    // defensive choice: avoid any dangerous oversight of a sensitive String that would not be cleared
                    throw new IllegalArgumentException("Unexpected long text to insert.");
                } else {
                    // text should be cleared after this method call
                    setLongTextToInsert(null);
                }
            }
        }
        
        @Override
        public void delete(int start, int end, boolean notifyListeners) {
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            unsafeMethLog.debug(
                    () -> "start = " + start + ", end = " + end + ", notifyListeners = "
                            + notifyListeners
            );
            if (end > start) {
                if (!isDestroyed()) {
                    valBuilder.remChars(start, end);
                    updateCurVal();
                } else {
                    unsafeMethLog.debug(() -> "is destroyed");
                }
                if (notifyListeners) {
                    notifyListeners();
                }
            }
        }
        
        @Override
        public int length() {
            if (isDestroyed()) {
                unsafeInstLog.newChildFromCurMethIf(Level.DEBUG).debug(() -> "is destroyed");
                return 0;
            }
            return curVal.length();
        }
        
        @Override
        public String get() {
            if (isDestroyed()) {
                unsafeInstLog.newChildFromCurMethIf(Level.DEBUG).debug(() -> "is destroyed");
                return "";
            }
            return curVal;
        }
        
        @Override
        public String getValue() {
            return get();
        }
        
        private void notifyListeners() {
            try {
                fireValueChangedEventMeth.invoke(defContent);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        @Override
        public void addListener(ChangeListener<? super String> changeListener) {
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            if (isDestroyed()) {
                unsafeMethLog.debug(() -> "is destroyed");
                return;
            }
            unsafeMethLog.debug(() -> "changeListener = " + changeListener);
            defContent.addListener(changeListener);
            updateDefContentHelper();
        }
        
        @Override
        public void removeListener(ChangeListener<? super String> changeListener) {
            unsafeInstLog.newChildFromCurMethIf(Level.DEBUG)
                    .debug(() -> "changeListener = " + changeListener);
            defContent.removeListener(changeListener);
            updateDefContentHelper();
        }
        
        @Override
        public void addListener(InvalidationListener listener) {
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            if (isDestroyed()) {
                unsafeMethLog.debug(() -> "is destroyed");
                return;
            }
            unsafeMethLog.debug(() -> "listener = " + listener);
            defContent.addListener(listener);
            updateDefContentHelper();
        }
        
        @Override
        public void removeListener(InvalidationListener listener) {
            unsafeInstLog.newChildFromCurMethIf(Level.DEBUG).debug(() -> "listener = " + listener);
            defContent.removeListener(listener);
            updateDefContentHelper();
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            Logger unsafeMethLog = unsafeInstLog.newChildFromCurMethIf(Level.DEBUG);
            unsafeMethLog.debug(() -> "start");
            boolean success = true;
            if (valBuilder != null) {
                unsafeMethLog.debug(() -> "destroy valBuilder");
                success = MemUtils.tryDestroy(valBuilder) && success;
                valBuilder = null;
            }
            if (prevVal != null) {
                success = MemUtils.tryClearString(prevVal) && success;
            }
            if (curVal != null) {
                success = MemUtils.tryClearString(curVal) && success;
            }
            if (otherCurVals != null) {
                for (String str : otherCurVals) {
                    success = MemUtils.tryClearString(str) && success;
                }
            }
            if (otherPrevVals != null) {
                for (String str : otherPrevVals) {
                    success = MemUtils.tryClearString(str) && success;
                }
            }
            prevVal = null;
            curVal = null;
            otherPrevVals = null;
            otherCurVals = null;
            longTextToInsert = null;
            
            notifyListeners();
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            return curVal == null && valBuilder == null;
        }
        
    }
    
    private final Logger unsafeInstLog = unsafeLog.newChildFromInstance(this);
    private final DestroyableTextInputControl destroyInputC;
    /**
     * Manager of listeners for this field text value, which are not triggered when this field is destroyed.
     */
    public final ChangeNotifier valChangeNotifier;
    public FieldValidityIndication validIndic = null;
    private boolean isTextLayoutChecked = false;
    
    public DestroyableTextField() {
        super();
        
        try {
            contentF.set(this, new DestroyableContent(getContent()));
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
        if (!(getContent() instanceof DestroyableContent)) {
            throw new IllegalStateException("Failed to set destroyable content.");
        }
        
        destroyInputC = new DestroyableTextInputControl(this, false);
        valChangeNotifier = destroyInputC.valChangeNotifier;
        
        skinProperty().addListener((o, oldSkin, newSkin) -> {
            if (oldSkin != null) {
                throw new UnsupportedOperationException("Cannot change the skin.");
            }
            try {
                TextFieldSkin skin = (TextFieldSkin) newSkin;
                Text textNode = (Text) textFieldSkinTextNodeF.get(skin);
                
                UIUtils.setupSingleLineText(textNode);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
    
    @Override
    public void setLongTextToInsert(String newVal) {
        ((DestroyableContent) getContent()).setLongTextToInsert(newVal);
    }
    
    @Override
    public String getLongTextToInsert() {
        return ((DestroyableContent) getContent()).longTextToInsert;
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
        checkTextLayout();
        destroyInputC.replaceText(start, end, text);
    }
    
    @Override
    public void clear() {
        destroyInputC.clear();
    }
    
    private void checkTextLayout() {
        if (isTextLayoutChecked) {
            return;
        }
        
        TextFieldSkin skin = (TextFieldSkin) getSkin();
        if (skin == null) { // Avoid issues when disposing
            return;
        }
        try {
            Text textNode = (Text) textFieldSkinTextNodeF.get(skin);
            Object textLayout = textLayoutF.get(textNode);
            if (!(textLayout instanceof SingleLineTextLayout)) {
                throw new IllegalStateException("Invalid internal textLayout.");
            }
            isTextLayoutChecked = true;
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public void setVal(char[] newValSrc) {
        String str = new String(newValSrc);
        setVal(str);
        MemUtils.tryClearString(str);
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
        boolean success = true;
        
        success = MemUtils.tryDestroy(destroyInputC) && success;
        
        if (getContent() instanceof DestroyableContent) {
            success = MemUtils.tryDestroy((DestroyableContent) getContent()) && success;
        } else {
            unsafeInstLog.error(() -> "content is not DestroyableContent"); // no need for safe log because success = false
            success = false;
        }
        
        try {
            isTextLayoutChecked = false;
            checkTextLayout();
        } catch (RuntimeException ex) {
            unsafeInstLog.error(() -> "internal textLayout is invalid", ex);
            success = false;
        }
        
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return getContent() instanceof DestroyableContent
                && ((DestroyableContent) getContent()).isDestroyed();
    }
    
}
