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

package fr.tigeriodev.tigersafe.ui.contents.config;

import java.util.ArrayList;
import java.util.List;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.ui.UIConfig;
import fr.tigeriodev.tigersafe.ui.UIUtils;
import fr.tigeriodev.tigersafe.ui.contents.SafeContentsUI;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;

public class ConfigTab extends SafeContentsUI.Tab {
    
    static final String TAB_LANG_BASE = "SafeContentsUI.config";
    
    private ContentHolder contentH;
    
    public ConfigTab(SafeContentsUI ui) {
        super(ui);
    }
    
    @Override
    public String getName() {
        return Lang.get(TAB_LANG_BASE + ".tabName");
    }
    
    @Override
    public String getIconName() {
        return "config";
    }
    
    @Override
    public KeyCombination getKeyboardShortcut() {
        return UIConfig.KeyboardShortcut.CONFIG_TAB.getKeyCombination();
    }
    
    @Override
    protected Node newContent() {
        contentH = new ContentHolder();
        return contentH.rootVBox;
    }
    
    class ContentHolder implements Destroyable {
        
        final VBox rootVBox;
        final Destroyable[] destroyables;
        
        ContentHolder() {
            rootVBox = new VBox();
            rootVBox.setId("safe-contents-config-root-vbox");
            
            ScrollPane sectionsScrollP = new ScrollPane();
            sectionsScrollP.getStyleClass().add("sections-scroll");
            
            VBox sectionsVBox = new VBox();
            sectionsVBox.getStyleClass().add("sections-vbox");
            
            Section[] sections = new Section[] {
                    new SafePasswordSection(ConfigTab.this),
                    new SafeCiphersSection(ConfigTab.this),
                    new ExportSection(ConfigTab.this),
                    new ImportSection(ConfigTab.this)
            };
            
            List<Destroyable> destroyablesList = new ArrayList<>();
            for (Section sec : sections) {
                if (sec instanceof Destroyable) {
                    destroyablesList.add((Destroyable) sec);
                }
                sectionsVBox.getChildren().add(newSectionNode(sec));
            }
            destroyables = destroyablesList.toArray(new Destroyable[0]);
            
            sectionsScrollP.setContent(sectionsVBox);
            
            rootVBox.getChildren().addAll(sectionsScrollP);
        }
        
        static abstract class Section {
            
            protected final SafeContentsUI ui;
            protected final SafeDataManager dm;
            
            protected Section(ConfigTab tab) {
                this.ui = tab.ui;
                this.dm = tab.dm;
            }
            
            abstract String getTitle();
            
            abstract String getCSSClass();
            
            abstract Node getContent();
            
        }
        
        private static Node newSectionNode(Section section) {
            TitledPane titledPane = new TitledPane();
            titledPane.setText(section.getTitle());
            titledPane.getStyleClass().addAll("section-titled-pane", section.getCSSClass());
            titledPane.setExpanded(false);
            
            Node content = section.getContent();
            content.getStyleClass().add("content-root");
            titledPane.setContent(content);
            return titledPane;
        }
        
        @Override
        public void destroy() throws DestroyFailedException {
            boolean success = true;
            for (Destroyable destroyable : destroyables) {
                success = MemUtils.tryDestroy(destroyable) && success;
            }
            if (!success) {
                throw new DestroyFailedException();
            }
        }
        
        @Override
        public boolean isDestroyed() {
            for (Destroyable destroyable : destroyables) {
                if (!destroyable.isDestroyed()) {
                    return false;
                }
            }
            return true;
        }
        
    }
    
    @Override
    protected void onDeselectedBefore() {
        if (contentH != null) {
            UIUtils.tryDestroy(contentH);
            contentH = null;
        }
    }
    
    public static void showUnsavedChangesPopup() {
        Alert unsavedChangesPopup = new Alert(
                AlertType.ERROR,
                Lang.get(TAB_LANG_BASE + ".unsavedChanges.popup"),
                ButtonType.OK
        );
        UIUtils.showDialogAndWait(unsavedChangesPopup);
    }
    
}
