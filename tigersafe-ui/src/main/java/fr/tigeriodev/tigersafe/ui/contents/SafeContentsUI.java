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

package fr.tigeriodev.tigersafe.ui.contents;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.data.SafeFileManager;
import fr.tigeriodev.tigersafe.ui.UI;
import fr.tigeriodev.tigersafe.ui.UIApp;
import fr.tigeriodev.tigersafe.ui.UIConfig;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.contents.config.ConfigTab;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;

public class SafeContentsUI implements UI {
    
    private final SafeDataManager dm;
    private final BorderPane rootBorderPane;
    private final Scene scene;
    
    final Tab passwordsTab, changesTab, configTab;
    private Tab curTab;
    private final Map<Tab, Button> visibleTabsBtn = new HashMap<>();
    private int incorrectSafePwTimes = 0;
    
    public SafeContentsUI(SafeDataManager dm) {
        this.dm = CheckUtils.notNull(dm);
        passwordsTab = new PasswordsTab(this);
        changesTab = new ChangesTab(this);
        configTab = new ConfigTab(this);
        Tab[] visibleTabs = new Tab[] {
                passwordsTab, configTab
        };
        curTab = null;
        
        rootBorderPane = new BorderPane();
        rootBorderPane.setId("safe-contents-root-border-pane");
        scene = new Scene(rootBorderPane);
        HBox headerHBox = new HBox();
        headerHBox.setId("safe-contents-header-hbox");
        
        Button closeSafeBtn =
                UIUtils.newBtn("SafeContentsUI.closeSafe.button", "close-safe", true, false);
        
        headerHBox.getChildren().add(closeSafeBtn);
        
        for (Tab tab : visibleTabs) {
            Button tabBtn = new Button(tab.getName());
            tabBtn.getStyleClass().add("tab-button");
            if (tab.getIconName() != null) {
                tabBtn.setGraphic(UIUtils.newIcon(tab.getIconName()));
            }
            visibleTabsBtn.put(tab, tabBtn);
            tabBtn.setOnAction((e) -> {
                selectTab(tab);
            });
            headerHBox.getChildren().add(tabBtn);
            UIUtils.setButtonShortcut(scene, tab.getKeyboardShortcut(), tabBtn);
        }
        
        rootBorderPane.setTop(headerHBox);
        
        HBox footerBox = new HBox();
        footerBox.setId("safe-contents-unsaved-footer-hbox");
        
        TilePane footerButtonsPane = new TilePane();
        footerButtonsPane.getStyleClass().add("buttons-tile");
        
        Label footerUnsavedLabel = new Label(Lang.get("SafeContentsUI.changes.unsavedFooterText"));
        UIUtils.forcePrefWidth(footerUnsavedLabel);
        
        Button footerShowChangesBtn =
                UIUtils.newBtn("SafeContentsUI.changes.show.button", "show-diff", true, false);
        Button footerSaveChangesBtn = newSaveChangesBtn();
        
        footerButtonsPane.getChildren().addAll(footerShowChangesBtn, footerSaveChangesBtn);
        
        footerBox.getChildren().addAll(footerUnsavedLabel, footerButtonsPane);
        HBox.setHgrow(footerButtonsPane, Priority.ALWAYS);
        rootBorderPane.setBottom(footerBox);
        
        // Dynamic
        
        selectTab(passwordsTab);
        updateUnsavedFooterDisplay();
        
        closeSafeBtn.setOnAction((e) -> {
            if (!dm.hasChanges()) {
                UIApp.getInstance().showSafeSelection();
                return;
            }
            
            ButtonType showChangesBtn =
                    new ButtonType(Lang.get("SafeContentsUI.changes.show.button.text"));
            ButtonType saveChangesBtn =
                    new ButtonType(Lang.get("SafeContentsUI.changes.save.button.text"));
            Alert warnPopup = new Alert(
                    AlertType.WARNING,
                    Lang.get("SafeContentsUI.closeSafe.unsavedChanges.popup"),
                    showChangesBtn,
                    saveChangesBtn,
                    ButtonType.CANCEL
            );
            UIUtils.showDialogAndWait(warnPopup).ifPresent((clickedBtn) -> {
                if (clickedBtn == showChangesBtn) {
                    selectTab(changesTab);
                } else if (clickedBtn == saveChangesBtn) {
                    showSaveChangesConfirmPopup();
                }
            });
        });
        
        footerShowChangesBtn.setOnAction((e) -> {
            selectTab(changesTab);
        });
        
        UIUtils.setButtonShortcut(
                scene,
                UIConfig.KeyboardShortcut.SHOW_CHANGES.getKeyCombination(),
                footerShowChangesBtn
        );
        UIUtils.setButtonShortcut(
                scene,
                UIConfig.KeyboardShortcut.SAVE_CHANGES.getKeyCombination(),
                footerSaveChangesBtn
        );
    }
    
    @Override
    public Scene getScene() {
        return scene;
    }
    
    public static abstract class Tab {
        
        protected final SafeContentsUI ui;
        protected final SafeDataManager dm;
        
        protected Tab(SafeContentsUI ui) {
            this.ui = CheckUtils.notNull(ui);
            this.dm = ui.dm;
        }
        
        public abstract String getName();
        
        public String getIconName() {
            return null;
        }
        
        public KeyCombination getKeyboardShortcut() {
            return null;
        }
        
        protected abstract Node newContent();
        
        protected void onDeselectedBefore() {}
        
        protected void onDeselectedAfter() {}
        
        protected void onSelectedAfter() {}
        
    }
    
    private void selectTab(Tab tab) {
        selectTab(tab, false);
    }
    
    private void selectTab(Tab tab, boolean refresh) {
        if (!refresh && curTab == tab) {
            return;
        }
        Tab prevTab = curTab;
        if (prevTab != null) {
            prevTab.onDeselectedBefore();
        }
        curTab = tab;
        rootBorderPane.setCenter(tab.newContent());
        if (prevTab != null) {
            prevTab.onDeselectedAfter();
        }
        tab.onSelectedAfter();
        
        Button prevTabBtn = visibleTabsBtn.get(prevTab);
        if (prevTabBtn != null) {
            prevTabBtn.getStyleClass().remove("selected-tab");
        }
        Button curTabBtn = visibleTabsBtn.get(curTab);
        if (curTabBtn != null) {
            curTabBtn.getStyleClass().add("selected-tab");
        }
    }
    
    public void updateUnsavedFooterDisplay() {
        rootBorderPane.getBottom().setVisible(curTab != changesTab && dm.hasChanges());
    }
    
    Button newSaveChangesBtn() {
        Button res = UIUtils.newBtn("SafeContentsUI.changes.save.button", "save", true, false);
        res.setOnAction((e) -> {
            showSaveChangesConfirmPopup();
        });
        return res;
    }
    
    void showSaveChangesConfirmPopup() {
        Alert confirmPopup = new Alert(
                AlertType.CONFIRMATION,
                Lang.get("SafeContentsUI.changes.save.confirm"),
                ButtonType.YES,
                ButtonType.CANCEL
        );
        UIUtils.showDialogAndWait(confirmPopup).ifPresent((clickedBtn) -> {
            if (clickedBtn == ButtonType.YES) {
                try {
                    dm.updateSafeFile();
                    dm.loadSafeFile(); // Reset unsaved changes
                    selectTab(curTab, true);
                    updateUnsavedFooterDisplay();
                } catch (Exception ex) {
                    UIApp.getInstance().showError(ex);
                }
            }
        });
    }
    
    /**
     * 
     * @return true if max incorrect times has not been reached, the user is allowed to continue.
     */
    public boolean onIncorrectSafePwTyped() {
        incorrectSafePwTimes++;
        if (incorrectSafePwTimes >= 3) {
            UIUtils.tryDestroy(this); // Not mandatory but preferable
            UIUtils.clearClipboardIfUsed();
            SafeFileManager.clearBuffers();
            
            UIApp.getInstance().showSafeSelection();
            MemUtils.clearHeap(UIApp.CLEAR_HEAP_MARGIN_BYTES);
            
            Alert alert = new Alert(
                    AlertType.ERROR,
                    Lang.get("SafeContentsUI.incorrectSafePassword.popup"),
                    ButtonType.OK
            );
            UIUtils.showDialogAndWait(alert);
            return false;
        } else {
            return true;
        }
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        if (curTab != null) {
            curTab.onDeselectedBefore();
            curTab.onDeselectedAfter();
        }
        
        dm.destroy();
    }
    
    @Override
    public boolean isDestroyed() {
        return dm.isDestroyed();
    }
    
}
