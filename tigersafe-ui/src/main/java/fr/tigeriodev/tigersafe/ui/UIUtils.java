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

package fr.tigeriodev.tigersafe.ui;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import com.sun.javafx.scene.text.FontHelper;
import com.sun.javafx.scene.text.TextLayout;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.TOTP;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextArea;
import fr.tigeriodev.tigersafe.ui.fields.DestroyableTextField;
import fr.tigeriodev.tigersafe.ui.fields.FieldValidityIndication;
import fr.tigeriodev.tigersafe.ui.fields.SingleLineTextLayout;
import fr.tigeriodev.tigersafe.ui.fields.ViewableUnclearField;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.ReflectionUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

public class UIUtils {
    
    private static final String DEF_STYLESHEET_URL =
            UIUtils.class.getResource("/default-style.css").toExternalForm();
    
    private static final Field textLayoutF = ReflectionUtils.getField(Text.class, "layout");
    
    public static final int LINE_HEIGHT = 19;
    public static final int H_SCROLL_HEIGHT = 20;
    
    private static String clipboardLastContent = null;
    private static Dialog<?> lastShownAwaitedDialog = null;
    private static Stage lastShownAwaitedStage = null;
    
    private UIUtils() {}
    
    public static void forcePrefWidth(Region region) {
        region.setMinWidth(javafx.scene.control.Control.USE_PREF_SIZE);
        region.setMaxWidth(javafx.scene.control.Control.USE_PREF_SIZE);
    }
    
    public static void forcePrefHeight(Region region) {
        region.setMinHeight(javafx.scene.control.Control.USE_PREF_SIZE);
        region.setMaxHeight(javafx.scene.control.Control.USE_PREF_SIZE);
    }
    
    public static void showScene(Scene scene, Stage stage) {
        setScene(scene, stage);
        stage.show();
    }
    
    public static void showSceneAndWait(Scene scene, Stage stage) {
        setScene(scene, stage);
        lastShownAwaitedStage = stage;
        stage.showAndWait();
    }
    
    public static void setScene(Scene scene, Stage stage) {
        scene.getStylesheets().add(DEF_STYLESHEET_URL);
        String customStylesheetURL = GlobalConfig.getInstance().getCustomStylesheetURL();
        if (customStylesheetURL != null) {
            scene.getStylesheets().add(customStylesheetURL);
        }
        
        boolean isMaximized = stage.isMaximized();
        if (isMaximized) {
            stage.setMaximized(false);
        }
        
        stage.setMinHeight(0);
        stage.setMinWidth(0);
        
        stage.setScene(scene);
        
        if (Double.isNaN(stage.getHeight()) || Double.isNaN(stage.getWidth())) {
            onNextChange(stage.showingProperty(), (newShowing) -> {
                if (newShowing) {
                    stage.setMinHeight(stage.getHeight());
                    stage.setMinWidth(stage.getWidth());
                }
            });
        } else {
            stage.setMinHeight(stage.getHeight());
            stage.setMinWidth(stage.getWidth());
        }
        
        if (isMaximized) {
            stage.setMaximized(true);
        }
    }
    
    public static void closeLastShownAwaitedStage() {
        if (lastShownAwaitedStage != null) {
            lastShownAwaitedStage.close();
            lastShownAwaitedStage = null;
        }
    }
    
    public static void whenScene(Node node, Consumer<Scene> exec) {
        Scene scene = node.getScene();
        if (scene != null) {
            exec.accept(scene);
        } else {
            onNextChange(node.sceneProperty(), exec);
        }
    }
    
    public static void whenWindow(Node node, Consumer<Window> exec) {
        whenScene(node, (scene) -> whenWindow(scene, exec));
    }
    
    public static void whenWindow(Scene scene, Consumer<Window> exec) {
        Window win = scene.getWindow();
        if (win != null) {
            exec.accept(win);
        } else {
            onNextChange(scene.windowProperty(), exec);
        }
    }
    
    public static <T> void onNextChange(ObservableValue<T> observed, Consumer<T> exec) {
        final ChangeListener<T> singleTimeListener = new ChangeListener<T>() {
            
            @Override
            public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
                exec.accept(newValue);
                observed.removeListener(this);
            }
            
        };
        observed.addListener(singleTimeListener);
    }
    
    /**
     * Must be called on the JavaFX Application thread.
     * @param dialog
     */
    public static <T> Optional<T> showDialogAndWait(Dialog<T> dialog) {
        lastShownAwaitedDialog = dialog;
        return dialog.showAndWait();
    }
    
    /**
     * Must be called on the JavaFX Application thread.
     */
    public static void closeLastShownAwaitedDialog() {
        if (lastShownAwaitedDialog != null) {
            lastShownAwaitedDialog.close();
            lastShownAwaitedDialog = null;
        }
    }
    
    public static void setupOptimalTextArea(TextArea textArea) {
        int linesNum = StringUtils.getLinesNumber(textArea.getText());
        textArea.setPrefHeight(H_SCROLL_HEIGHT + Math.min(linesNum, 5) * LINE_HEIGHT);
        forcePrefHeight(textArea);
    }
    
    public static ImageView newIcon(String iconName) {
        return newImageView(
                UIUtils.class.getResource("/icons/" + iconName + ".png").toExternalForm(),
                17
        );
    }
    
    public static ImageView newImageView(String url, double size) {
        ImageView res = new ImageView(url);
        res.setFitHeight(size);
        res.setPreserveRatio(true);
        return res;
    }
    
    public static void setAppIcon(Stage stage) {
        for (int size : new int[] {
                16, 24, 32, 48, 256
        }) {
            stage.getIcons()
                    .add(
                            new Image(
                                    UIUtils.class.getResourceAsStream(
                                            "/icons/app/app-" + size + "x" + size + ".png"
                                    )
                            )
                    );
        }
    }
    
    public static void setButtonShortcut(Scene scene, KeyCombination shortcut, Button btn) {
        scene.getAccelerators().put(shortcut, () -> {
            clickButton(btn);
        });
    }
    
    public static void clickButton(Button btn) {
        btn.requestFocus();
        btn.arm();
        btn.fire();
        btn.disarm();
    }
    
    public static Button newCopyBtn(TextField targetField, String langKeyBase) {
        return newCopyBtn(() -> targetField.getText(), langKeyBase);
    }
    
    public static Button newCopyBtn(ViewableUnclearField targetField, String langKeyBase) {
        return newCopyBtn(() -> new String(targetField.getVal()), langKeyBase);
    }
    
    public static Button newCopyBtn(Supplier<String> contentSupplier, String langKeyBase) {
        Button res = newBtn(langKeyBase, "copy", false, true);
        
        res.setOnAction((e) -> {
            setClipboardContent(contentSupplier.get());
        });
        return res;
    }
    
    public static void setClipboardContent(String newVal) {
        if (clipboardLastContent != null) {
            MemUtils.tryClearString(clipboardLastContent);
        }
        clipboardLastContent = newVal;
        ClipboardContent content = new ClipboardContent();
        content.putString(newVal);
        Clipboard.getSystemClipboard().setContent(content);
    }
    
    /**
     * Since the clipboard value is hold by JavaFX and not by the OS (when using {@link ClipboardContent#putString(String)}), the value can only be cleared from program's memory when it is no longer used.
     */
    public static void clearClipboardIfUsed() {
        if (clipboardLastContent != null) {
            setClipboardContent("");
        }
    }
    
    public static Button newBtn(String langKeyBase, String iconName, boolean withText,
            boolean withTooltip) {
        Button res;
        String text = withText ? Lang.get(langKeyBase + ".text") : "";
        if (iconName != null) {
            res = new Button(text, UIUtils.newIcon(iconName));
        } else {
            res = new Button(text);
        }
        if (withTooltip) {
            res.setTooltip(new Tooltip(Lang.get(langKeyBase + ".tooltip")));
        }
        return res;
    }
    
    public static char[] totpToFieldVal(TOTP totp) {
        return totp != null ? totp.getURI() : new char[0];
    }
    
    public static TOTP totpFromURI(char[] uri) throws IllegalArgumentException {
        return uri.length > 0 ? TOTP.fromURI(uri) : null;
    }
    
    public static Label newLabel(String langKeyBase, boolean withTooltip) {
        Label res = new Label(Lang.get(langKeyBase + ".label"));
        UIUtils.forcePrefWidth(res);
        if (withTooltip) {
            res.setTooltip(new Tooltip(Lang.get(langKeyBase + ".tooltip")));
        }
        return res;
    }
    
    public static void addViewableUnclearFieldToGrid(GridPane grid, int rowInd, String langKeyBase,
            ViewableUnclearField field, boolean withTooltip, boolean withValidIndic) {
        Label label = newLabel(langKeyBase, withTooltip);
        
        HBox hbox = newViewableUnclearFieldHBox(field);
        if (withTooltip) {
            field.setTooltip(label.getTooltip());
        }
        if (withValidIndic) {
            field.setupValidIndic(Lang.get(langKeyBase + ".invalid"), true);
        }
        
        grid.add(label, 0, rowInd);
        grid.add(hbox, 1, rowInd);
    }
    
    public static HBox newViewableUnclearFieldHBox(ViewableUnclearField field) {
        HBox hbox = new HBox();
        hbox.getStyleClass().add("viewable-unclear-field-hbox");
        HBox.setHgrow(field.rootPane, Priority.ALWAYS);
        HBox.setHgrow(field.visibilityBtn, Priority.NEVER);
        hbox.getChildren().addAll(field.rootPane, field.visibilityBtn);
        return hbox;
    }
    
    public static void addDestroyTextFieldToGrid(GridPane grid, int rowInd, String langKeyBase,
            DestroyableTextField field, boolean withTooltip, boolean withValidIndic) {
        if (withValidIndic) {
            field.validIndic =
                    new FieldValidityIndication(field, Lang.get(langKeyBase + ".invalid"), true);
        }
        addFieldToGrid(grid, rowInd, langKeyBase, field, withTooltip);
    }
    
    public static void addDestroyTextAreaToGrid(GridPane grid, int rowInd, String langKeyBase,
            DestroyableTextArea field, boolean withTooltip, boolean withValidIndic) {
        if (withValidIndic) {
            field.validIndic =
                    new FieldValidityIndication(field, Lang.get(langKeyBase + ".invalid"), true);
        }
        addFieldToGrid(grid, rowInd, langKeyBase, field, withTooltip);
    }
    
    public static void addFieldToGrid(GridPane grid, int rowInd, String langKeyBase, Control field,
            boolean withTooltip) {
        Label label = newLabel(langKeyBase, withTooltip);
        if (withTooltip) {
            field.setTooltip(label.getTooltip());
        }
        grid.add(label, 0, rowInd);
        grid.add(field, 1, rowInd);
    }
    
    public static void addNodeToGrid(GridPane grid, int rowInd, String langKeyBase, Node node,
            boolean withTooltip, Control... fieldsWithTooltip) {
        Label label = newLabel(langKeyBase, withTooltip);
        if (withTooltip && fieldsWithTooltip.length > 0) {
            for (Control field : fieldsWithTooltip) {
                field.setTooltip(label.getTooltip());
            }
        }
        grid.add(label, 0, rowInd);
        grid.add(node, 1, rowInd);
    }
    
    public static void setTooltip(Control control, String langKeyBase) {
        control.setTooltip(new Tooltip(Lang.get(langKeyBase + ".tooltip")));
    }
    
    public static boolean isVisibleInScrollPane(Region region, ScrollPane scrollPane) {
        double regMidHeight = region.getHeight() * 0.5d;
        double regMidY = region.localToScreen(0, regMidHeight).getY();
        double regBottomY = regMidY + regMidHeight;
        double scrollPTopY = scrollPane.localToScreen(0, 0).getY();
        double scrollPBottomY = scrollPTopY + scrollPane.getHeight();
        
        return scrollPTopY <= regMidY && regBottomY <= scrollPBottomY;
    }
    
    public static Text newSingleLineText() {
        Text res = new Text();
        setupSingleLineText(res);
        return res;
    }
    
    public static void setupSingleLineText(Text textNode) {
        try {
            TextLayout customTextLayout = new SingleLineTextLayout();
            Object font = FontHelper.getNativeFont(textNode.getFont());
            customTextLayout.setContent(textNode.getText(), font);
            
            textLayoutF.set(textNode, customTextLayout);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static final char[] getValidChars(final String text, final boolean allowNewLineAndTab) {
        int invalidCharsNum = countInvalidChars(text, allowNewLineAndTab);
        if (invalidCharsNum == 0) {
            return text.toCharArray();
        } else {
            char[] res = new char[text.length() - invalidCharsNum];
            int j = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (!isInvalidChar(c, allowNewLineAndTab)) {
                    res[j++] = c;
                }
            }
            if (j != res.length) {
                throw new IllegalStateException("j " + j + " != len " + res.length);
            }
            return res;
        }
    }
    
    public static final int countInvalidChars(final String text, final boolean allowNewLineAndTab) {
        int res = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isInvalidChar(text.charAt(i), allowNewLineAndTab)) {
                res++;
            }
        }
        return res;
    }
    
    public static final boolean isInvalidChar(final char c, final boolean allowNewLineAndTab) {
        if (c == '\n' || c == '\t') {
            return !allowNewLineAndTab;
        }
        return c == 0x7f || c < 0x20; // NB: \n and \t are < 0x20
    }
    
    public static void tryDestroy(Destroyable obj) {
        try {
            obj.destroy();
        } catch (DestroyFailedException ex) {
            UIApp.getInstance().showError(ex);
        }
    }
    
}
