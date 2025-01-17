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

import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ThrowableErrorPopup {
    
    private static final Logger log = Logs.newLogger(ThrowableErrorPopup.class);
    
    private ThrowableErrorPopup() {}
    
    /**
     * Should be called by {@link UIApp#showError(String, Throwable)}.
     * @param title
     * @param throwable
     */
    public static void show(String title, Throwable throwable) {
        Stage stage = new Stage();
        stage.setTitle("TigerSafe - Error");
        stage.initModality(Modality.APPLICATION_MODAL);
        UIUtils.setAppIcon(stage);
        
        VBox rootVBox = new VBox();
        rootVBox.setSpacing(10);
        rootVBox.setPadding(new Insets(10, 10, 10, 10));
        rootVBox.setAlignment(Pos.CENTER);
        
        Text titleText = new Text(title);
        Text errorMsg = new Text(throwable.getMessage());
        
        TextArea errorStackTrace = new TextArea();
        errorStackTrace.setText(StringUtils.getStackTrace(throwable));
        errorStackTrace.setEditable(false);
        errorStackTrace.setWrapText(true);
        errorStackTrace.setPrefHeight(400);
        errorStackTrace.setPrefWidth(700);
        
        rootVBox.getChildren().addAll(titleText, errorMsg, errorStackTrace);
        VBox.setVgrow(errorStackTrace, Priority.ALWAYS);
        
        Scene scene = new Scene(
                rootVBox,
                errorStackTrace.getPrefWidth() + 20,
                errorStackTrace.getPrefHeight() + 100
        );
        
        try {
            UIUtils.showSceneAndWait(scene, stage);
        } catch (Exception ex) {
            log.info(() -> "Failed to show error popup: ", ex);
        }
    }
    
}
