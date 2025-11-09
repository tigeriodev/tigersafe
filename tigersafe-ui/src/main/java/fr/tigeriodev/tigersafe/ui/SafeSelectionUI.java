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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.ui.fields.FileField;
import fr.tigeriodev.tigersafe.ui.fields.SecureUnclearField;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.stage.FileChooser.ExtensionFilter;

public class SafeSelectionUI implements UI {
    
    private static final String OPEN_LANG_BASE = "SafeSelectionUI.openSafe";
    private final SecureUnclearField pwField;
    private final Scene scene;
    
    public SafeSelectionUI() {
        BorderPane rootBorderP = new BorderPane();
        rootBorderP.setStyle(
                "-fx-cursor: null; -fx-effect: null; -fx-focus-traversable: false; -fx-opacity: 1.0; -fx-blend-mode: null; -fx-rotate: 0.0; -fx-scale-x: 1.0; -fx-scale-y: 1.0; -fx-scale-z: 1.0; -fx-view-order: 0.0; -fx-translate-x: 0.0; -fx-translate-y: 0.0; -fx-translate-z: 0.0; visibility: true; -fx-managed: true; -fx-padding: 0; -fx-region-border: null; -fx-opaque-insets: null; -fx-shape: null; -fx-scale-shape: true; -fx-position-shape: true; -fx-snap-to-pixel: true; -fx-min-width: -1.0; -fx-pref-width: -1.0; -fx-max-width: -1.0; -fx-min-height: -1.0; -fx-pref-height: -1.0; -fx-max-height: -1.0;"
        );
        
        HBox topHBox = new HBox();
        topHBox.setStyle(
                "-fx-cursor: null; -fx-effect: null; -fx-focus-traversable: false; -fx-opacity: 1.0; -fx-blend-mode: null; -fx-rotate: 0.0; -fx-scale-x: 1.0; -fx-scale-y: 1.0; -fx-scale-z: 1.0; -fx-view-order: 0.0; -fx-translate-x: 0.0; -fx-translate-y: 0.0; -fx-translate-z: 0.0; visibility: true; -fx-managed: true; -fx-padding: 0 5 0 5;  -fx-background-color: #f4f4f4; -fx-background-insets: null; -fx-background-radius: null; -fx-background-image: null; -fx-background-repeat: null; -fx-background-position: null; -fx-background-size: null; -fx-region-border: null; -fx-border-color: null; -fx-border-style: null; -fx-border-width: null; -fx-border-radius: null; -fx-border-insets: null; -fx-border-image-source: null; -fx-border-image-repeat: null; -fx-border-image-slice: null; -fx-border-image-width: null; -fx-border-image-insets: null; -fx-opaque-insets: null; -fx-shape: null; -fx-scale-shape: true; -fx-position-shape: true; -fx-snap-to-pixel: true; -fx-min-width: -1.0; -fx-pref-width: -1.0; -fx-max-width: -1.0; -fx-min-height: 20; -fx-pref-height: 20; -fx-max-height: 20; -fx-alignment: CENTER_LEFT; -fx-fill-height: true; -fx-spacing: 40;"
        );
        
        Text credits = new Text("TigerSafe by tigeriodev");
        credits.setStyle(
                "-fx-cursor: null; -fx-effect: null; -fx-focus-traversable: false; -fx-opacity: 1.0; -fx-blend-mode: null; -fx-rotate: 0.0; -fx-scale-x: 1.0; -fx-scale-y: 1.0; -fx-scale-z: 1.0; -fx-view-order: 0.0; -fx-translate-x: 0.0; -fx-translate-y: 0.0; -fx-translate-z: 0.0; visibility: true; -fx-managed: true; -fx-fill: #000000ff; -fx-smooth: true; -fx-stroke: null; -fx-stroke-dash-array: null; -fx-stroke-dash-offset: 0.0; -fx-stroke-line-cap: SQUARE; -fx-stroke-line-join: MITER; -fx-stroke-type: CENTERED; -fx-stroke-miter-limit: 10.0; -fx-stroke-width: 1.0;  -fx-font-family: 'System'; -fx-font-size: 12.0px; -fx-font-style: normal; -fx-font-weight: normal; -fx-underline: false; -fx-strikethrough: false; -fx-text-alignment: LEFT; -fx-text-origin: BASELINE; -fx-font-smoothing-type: GRAY; -fx-line-spacing: 0; -fx-bounds-type: LOGICAL; -fx-tab-size: 8;"
        );
        
        Region spacer = new Region();
        
        Text aboutBtnText = new Text("About");
        aboutBtnText.setStyle(
                "-fx-cursor: hand; -fx-effect: null; -fx-focus-traversable: false; -fx-opacity: 1.0; -fx-blend-mode: null; -fx-rotate: 0.0; -fx-scale-x: 1.0; -fx-scale-y: 1.0; -fx-scale-z: 1.0; -fx-view-order: 0.0; -fx-translate-x: 0.0; -fx-translate-y: 0.0; -fx-translate-z: 0.0; visibility: true; -fx-managed: true; -fx-fill: #000000ff; -fx-smooth: true; -fx-stroke: null; -fx-stroke-dash-array: null; -fx-stroke-dash-offset: 0.0; -fx-stroke-line-cap: SQUARE; -fx-stroke-line-join: MITER; -fx-stroke-type: CENTERED; -fx-stroke-miter-limit: 10.0; -fx-stroke-width: 1.0; -fx-font-family: 'System'; -fx-font-size: 12.0px; -fx-font-style: normal; -fx-font-weight: normal; -fx-underline: true; -fx-strikethrough: false; -fx-text-alignment: LEFT; -fx-text-origin: BASELINE; -fx-font-smoothing-type: GRAY; -fx-line-spacing: 0; -fx-bounds-type: LOGICAL; -fx-tab-size: 8;"
        );
        
        topHBox.getChildren().addAll(credits, spacer, aboutBtnText);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        GridPane rootGrid = new GridPane();
        rootGrid.setId("safe-selection-root-grid");
        
        HBox titleHBox = new HBox();
        titleHBox.getStyleClass().add("tigersafe-title-hbox");
        
        Text title = new Text("TigerSafe");
        title.getStyleClass().add("tigersafe-title");
        
        titleHBox.getChildren().add(title);
        rootGrid.add(titleHBox, 0, 0, 2, 1);
        
        VBox navBtnsHBox = new VBox();
        navBtnsHBox.getStyleClass().add("navigation-buttons-hbox");
        
        Button globalConfigBtn =
                UIUtils.newBtn("SafeSelectionUI.openGlobalConfig.button", "config", true, false);
        Button createSafeBtn =
                UIUtils.newBtn("SafeSelectionUI.createSafe.button", "add", true, false);
        
        navBtnsHBox.getChildren().addAll(globalConfigBtn, createSafeBtn);
        rootGrid.add(navBtnsHBox, 0, 2, 2, 1);
        
        HBox openSafeTitleHBox = new HBox();
        openSafeTitleHBox.getStyleClass().add("open-safe-title-hbox");
        
        Text openSafeTitle = new Text(Lang.get(OPEN_LANG_BASE + ".title"));
        openSafeTitle.getStyleClass().add("open-safe-title");
        
        openSafeTitleHBox.getChildren().add(openSafeTitle);
        rootGrid.add(openSafeTitleHBox, 0, 4, 2, 1);
        
        FileField safeFileField = new FileField(
                Lang.get(OPEN_LANG_BASE + ".safeFile.invalid"),
                new ExtensionFilter("TigerSafe safe files", "*.dat")
        );
        UIUtils.addFieldToGrid(
                rootGrid,
                6,
                OPEN_LANG_BASE + ".safeFile",
                safeFileField.textField,
                false
        );
        
        pwField = new SecureUnclearField(SafeDataManager.newSafePwHolder());
        UIUtils.addFieldToGrid(rootGrid, 7, OPEN_LANG_BASE + ".safePassword", pwField, false);
        
        HBox openSafeButtonHBox = new HBox();
        openSafeButtonHBox.getStyleClass().add("open-safe-button-hbox");
        
        Button openSafeBtn = UIUtils.newBtn(OPEN_LANG_BASE + ".button", "open-safe", true, false);
        
        openSafeButtonHBox.getChildren().add(openSafeBtn);
        rootGrid.add(openSafeButtonHBox, 1, 9);
        
        // Dynamic
        
        aboutBtnText.setOnMouseClicked((ev) -> {
            try {
                AboutPopup.show();
            } catch (IOException | URISyntaxException ex) {
                UIApp.getInstance().showError(ex);
            }
        });
        
        globalConfigBtn.setOnAction((e) -> {
            UIApp.getInstance().showGlobalConfig();
        });
        
        createSafeBtn.setOnAction((e) -> {
            UIApp.getInstance().showSafeCreation();
        });
        
        File lastSafeFile = GlobalConfig.getInstance().getLastSafeFile();
        if (lastSafeFile != null) {
            safeFileField.setVal(lastSafeFile);
        }
        
        openSafeBtn.setOnAction((e) -> {
            openSafeBtn.setDisable(true);
            File safeFile = safeFileField.getVal();
            SafeDataManager dm = null;
            try {
                dm = new SafeDataManager(safeFile, pwField.getVal());
                // pwField should not be cleared here, to be properly destroyed (with simulateUserTyping) when this UI will be closed
                dm.loadSafeFile();
                
                GlobalConfig globalConfig = GlobalConfig.getInstance();
                globalConfig.setLastSafeFile(safeFile);
                globalConfig.updateUserFile();
                
                UIApp.getInstance().showSafeContents(dm);
            } catch (Exception ex) {
                UIApp.getInstance().showError(Lang.get(OPEN_LANG_BASE + ".error.title"), ex);
                if (dm != null) {
                    UIUtils.tryDestroy(dm);
                }
                openSafeBtn.setDisable(false);
            }
        });
        
        pwField.setOnKeyPressed((ev) -> {
            if (KeyCode.ENTER.equals(ev.getCode())) {
                UIUtils.clickButton(openSafeBtn);
                ev.consume();
            }
        });
        
        rootBorderP.setCenter(rootGrid);
        rootBorderP.setTop(topHBox);
        scene = new Scene(rootBorderP);
        
        UIUtils.onNextChange(topHBox.widthProperty(), (newVal) -> {
            topHBox.setBackground(Background.fill(Paint.valueOf("#f4f4f4")));
        });
        
        if (lastSafeFile != null) {
            pwField.requestFocus();
        }
    }
    
    @Override
    public Scene getScene() {
        return scene;
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        pwField.destroy();
    }
    
    @Override
    public boolean isDestroyed() {
        return pwField.isDestroyed();
    }
    
}
