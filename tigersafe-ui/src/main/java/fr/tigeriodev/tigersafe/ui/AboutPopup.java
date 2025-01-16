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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AboutPopup {
    
    public static void show() throws IOException, URISyntaxException {
        Stage stage = new Stage();
        stage.setTitle("TigerSafe - About");
        stage.initModality(Modality.APPLICATION_MODAL);
        UIUtils.setAppIcon(stage);
        
        VBox rootVBox = new VBox();
        rootVBox.setSpacing(10);
        rootVBox.setPadding(new Insets(10, 10, 10, 10));
        rootVBox.setAlignment(Pos.CENTER);
        
        TabPane tabPane = new TabPane();
        
        double prefHeight = 400;
        double prefWidth = 700;
        
        for (String fileName : new String[] {
                "NOTICE", "LICENSE"
        }) {
            TextArea fileContentsText = new TextArea();
            fileContentsText.setText(getLines(fileName));
            fileContentsText.setFont(Font.font("Monospaced", 12));
            fileContentsText.setEditable(false);
            fileContentsText.setWrapText(true);
            fileContentsText.setPrefHeight(prefHeight);
            fileContentsText.setPrefWidth(prefWidth);
            
            Tab tab = new Tab(fileName, fileContentsText);
            tab.setClosable(false);
            tabPane.getTabs().add(tab);
        }
        
        rootVBox.getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        
        Scene scene = new Scene(rootVBox, prefWidth + 20, prefHeight + 100);
        
        UIUtils.showSceneAndWait(scene, stage);
    }
    
    private static String getLines(String fileName) throws IOException, URISyntaxException {
        try (
                InputStream in = AboutPopup.class.getResourceAsStream("/" + fileName);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        ) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            if (!sb.isEmpty()) {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        }
    }
    
}
