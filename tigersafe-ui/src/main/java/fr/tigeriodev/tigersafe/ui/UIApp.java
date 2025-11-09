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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.tigeriodev.tigersafe.GlobalConfig;
import fr.tigeriodev.tigersafe.GlobalConfig.InvalidConfigPropertyValueException;
import fr.tigeriodev.tigersafe.Lang;
import fr.tigeriodev.tigersafe.MonitoringManager;
import fr.tigeriodev.tigersafe.data.SafeDataManager;
import fr.tigeriodev.tigersafe.data.SafeFileManager;
import fr.tigeriodev.tigersafe.logs.ConsoleLogger;
import fr.tigeriodev.tigersafe.logs.Level;
import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;
import fr.tigeriodev.tigersafe.ui.contents.SafeContentsUI;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

public final class UIApp extends Application {
    
    static {
        Logs.setLoggerFactory(
                (displayName, initLevel) -> new ConsoleLogger(displayName, initLevel)
        );
    }
    
    private static final Logger log = Logs.newLogger(UIApp.class);
    
    public static final boolean ALLOW_UNSAFE_HEAP = Boolean.getBoolean("tigersafe.unsafeHeap");
    public static final int CLEAR_HEAP_MARGIN_BYTES =
            Integer.getInteger("tigersafe.clearHeapMarginBytes", 5000000);
    public static final int LAST_CLEAR_HEAP_DELAY_MS =
            Integer.getInteger("tigersafe.lastClearHeapDelayMs", 1000);
    public static final int AFTER_LAST_CLEAR_HEAP_DELAY_MS =
            Integer.getInteger("tigersafe.afterLastClearHeapDelayMs", 0);
    
    private static UIApp instance = null;
    private Stage primaryStage;
    private UI curUI;
    private ScheduledExecutorService befShutdownExecutor;
    private MonitoringManager monitoringManager;
    private AtomicBoolean isShutdown = new AtomicBoolean(false);
    private AtomicBoolean hasLastClearHeapStarted = new AtomicBoolean(false);
    
    public static void main(String[] args) {
        String globalConfigPath = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "-config" -> globalConfigPath = args[++i];
            }
        }
        
        if (globalConfigPath == null) {
            throw new IllegalArgumentException(
                    "A (possibly non-existent/empty) configuration file path must be defined with: -config <path>."
            );
        }
        File userGlobalConfigFile = new File(globalConfigPath);
        
        try (Scanner scanner = new Scanner(new FilterInputStream(System.in) {
            
            @Override
            public void close() throws IOException {
                // do not close System.in
            }
            
        })) {
            boolean isGlobalConfigInitialized = false;
            while (!isGlobalConfigInitialized) {
                try {
                    GlobalConfig.initFile(userGlobalConfigFile);
                    GlobalConfig globalConfig = new GlobalConfig(userGlobalConfigFile);
                    UIConfig uiConfig = new UIConfig(globalConfig);
                    GlobalConfig.setInstance(globalConfig, true, true);
                    UIConfig.setInstance(uiConfig, true, true);
                    isGlobalConfigInitialized = true;
                } catch (InvalidConfigPropertyValueException ex) {
                    log.error(
                            () -> "Invalid global config value for \"" + ex.getPropKey() + "\": ",
                            ex
                    );
                    askInvalidConfigPropOption(ex.getPropKey(), userGlobalConfigFile, scanner);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to initialize global config.", ex);
                }
            }
        }
        
        System.setProperty("prism.cacheLayoutSize", "0"); // Avoid caching of strings typed in TextFields
        System.setProperty("prism.cacheshapes", "false");
        
        try {
            launch(UIApp.class, args);
        } finally {
            log.debug(() -> "UI app launch end");
            getInstance().onShutdown();
        }
    }
    
    private static void askInvalidConfigPropOption(String propKey, File userGlobalConfigFile,
            Scanner scanner) {
        String propDefVal;
        try {
            propDefVal = GlobalConfig.getDefaultProperties().getProperty(propKey);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to get global config default properties.", ex);
        }
        String notice = """
        ====================================================================================
        Your configuration file contains an invalid value for "%1$s" property.
        This value must be changed in order to launch TigerSafe.

        You can easily fix your configuration file with one of these options:
         - Type "r" to reset the "%1$s" property to its default value ("%2$s").
         - Type "s", then space, then the new value to set for "%1$s" property.
           For example: "s 50" to set the new value of the property to "50".
         - Type "f" to reset your whole configuration file to the default
           configuration file (your configuration file will be deleted).
        (after typing your option, you need to press Enter to validate it)

        As a reminder, your configuration file is located at:
        %3$s
        ====================================================================================
        """.formatted(propKey, propDefVal, userGlobalConfigFile.getAbsolutePath());
        System.out.println(notice);
        
        switch (scanner.next()) {
            case "r" -> {
                try {
                    changeConfigProp(userGlobalConfigFile, propKey, propDefVal);
                    System.out.println(
                            "The \"%s\" property has been successfully reset to its default value \"%s\"."
                                    .formatted(propKey, propDefVal)
                    );
                } catch (IOException ex) {
                    System.out.println(
                            "Failed to reset \"%s\" property to its default value \"%s\"."
                                    .formatted(propKey, propDefVal)
                    );
                    throw new RuntimeException(ex);
                }
            }
            case "s" -> {
                String propNewVal = scanner.nextLine();
                if (!propNewVal.isEmpty()) {
                    propNewVal = propNewVal.substring(1); // rm first space
                }
                try {
                    changeConfigProp(userGlobalConfigFile, propKey, propNewVal);
                    System.out.println(
                            "The \"%s\" property has been successfully set to \"%s\"."
                                    .formatted(propKey, propNewVal)
                    );
                } catch (IOException ex) {
                    System.out.println(
                            "Failed to set \"%s\" property to \"%s\"."
                                    .formatted(propKey, propNewVal)
                    );
                    throw new RuntimeException(ex);
                }
            }
            case "f" -> {
                try {
                    Files.delete(userGlobalConfigFile.toPath());
                    System.out.println(
                            "Your configuration file has been successfully deleted in order to recreate it later by copying the default configuration file."
                    );
                } catch (IOException ex) {
                    System.out.println(
                            "Failed to delete your configuration file in order to recreate it later by copying the default configuration file."
                    );
                    throw new RuntimeException(ex);
                }
            }
            default -> {
                System.out.println("You typed an invalid option.");
            }
        }
    }
    
    private static void changeConfigProp(File configFile, String propKey, String propNewVal)
            throws IOException {
        Properties props = new Properties();
        try (FileInputStream configFileIn = new FileInputStream(configFile)) {
            props.load(configFileIn);
        }
        props.setProperty(propKey, propNewVal);
        try (FileWriter writer = new FileWriter(configFile)) {
            props.store(writer, null);
        }
    }
    
    public static UIApp getInstance() {
        return instance;
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        log.newChildFromCurMethIf(Level.DEBUG).debug(() -> "start");
        if (instance != null) {
            throw new IllegalStateException("UI app already started.");
        }
        instance = this;
        
        Runtime runtime = Runtime.getRuntime();
        if (runtime.totalMemory() != runtime.maxMemory() && !ALLOW_UNSAFE_HEAP) {
            throw new GeneralSecurityException(
                    "TigerSafe should be run with the same fixed value for -Xms and -Xmx JVM parameters."
            );
        }
        
        long maxClearHeapMargin = (long) (0.2d * runtime.maxMemory());
        if (CLEAR_HEAP_MARGIN_BYTES > maxClearHeapMargin && !ALLOW_UNSAFE_HEAP) {
            throw new GeneralSecurityException(
                    "tigersafe.clearHeapMarginBytes option should not exceed 20% of max heap size ("
                            + maxClearHeapMargin + ")."
            );
        }
        
        int maxLastClearHeapDelayMs = 10000;
        if (LAST_CLEAR_HEAP_DELAY_MS > maxLastClearHeapDelayMs && !ALLOW_UNSAFE_HEAP) {
            throw new GeneralSecurityException(
                    "tigersafe.lastClearHeapDelayMs option should not exceed "
                            + maxLastClearHeapDelayMs + "."
            );
        }
        
        this.primaryStage = primaryStage;
        befShutdownExecutor = Executors.newSingleThreadScheduledExecutor();
        
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            getInstance().showError(throwable);
        });
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug(() -> "shutdown hook: onShutdown()");
            onShutdown();
        }));
        
        monitoringManager = MonitoringManager.start(this::shutdown);
        
        primaryStage.setTitle("TigerSafe");
        UIUtils.setAppIcon(primaryStage);
        showSafeSelection();
        if (ALLOW_UNSAFE_HEAP || AFTER_LAST_CLEAR_HEAP_DELAY_MS > 0 || Logs.ALLOW_UNSAFE_LOGGERS) {
            Alert unsafeWarnPopup = new Alert(
                    AlertType.WARNING,
                    "You are executing TigerSafe in unsafe mode.\nUnsafe parameters should only be used for debugging, not for a real use of TigerSafe.\nIf you want to safely use TigerSafe to store sensitive data, you should stop the program now, remove the unsafe parameters from your executing environment, then restart the program.",
                    ButtonType.OK
            );
            UIUtils.showDialogAndWait(unsafeWarnPopup);
        }
    }
    
    public void showSafeSelection() {
        show(new SafeSelectionUI());
    }
    
    public void showSafeCreation() {
        show(new SafeCreationUI());
    }
    
    public void showSafeContents(SafeDataManager dm) {
        show(new SafeContentsUI(dm));
    }
    
    public void showGlobalConfig() {
        show(new GlobalConfigUI());
    }
    
    public void show(UI ui) {
        if (isShutdown.get() && !(ui instanceof SafeSelectionUI)) {
            log.newChildFromCurMethIf(Level.DEBUG)
                    .debug(() -> "shutdown and ui != SafeSelectionUI, cancel");
            return;
        }
        if (curUI != null) {
            UIUtils.tryDestroy(curUI);
        }
        curUI = ui;
        UIUtils.showScene(ui.getScene(), primaryStage);
    }
    
    public void showError(Throwable thrown) {
        showError(Lang.get("ThrowableErrorPopup.defaultTitle"), thrown);
    }
    
    public void showError(String title, Throwable thrown) {
        log.error(() -> "An error has occurred: ", thrown);
        if (!isShutdown.get()) {
            ThrowableErrorPopup.show(title, thrown);
        }
    }
    
    @Override
    public void stop() throws Exception {
        log.newChildFromCurMethIf(Level.DEBUG).debug(() -> "start");
        onShutdown();
    }
    
    private void shutdown() {
        onShutdown(true);
    }
    
    private void onShutdown() {
        onShutdown(false);
    }
    
    private void onShutdown(boolean triggerUIShutdown) {
        if (!isShutdown.compareAndSet(false, true)) {
            return;
        }
        
        Logger methLog = log.newChildFromCurMethIf(Level.DEBUG);
        methLog.debug(() -> "triggerUIShutdown = " + triggerUIShutdown);
        
        UIUtils.clearClipboardIfUsed();
        SafeFileManager.clearBuffers();
        if (monitoringManager != null) {
            monitoringManager.stop();
        }
        
        boolean failedClearUI = false;
        try {
            Platform.runLater(() -> { // ensures to be run after all eventual ui creation
                try {
                    methLog.debug(() -> "clear ui...");
                    UIUtils.closeLastShownAwaitedDialog();
                    UIUtils.closeLastShownAwaitedStage();
                    showSafeSelection();
                    if (triggerUIShutdown) {
                        Platform.exit();
                    }
                } finally {
                    if (hasLastClearHeapStarted.get()) {
                        methLog.debug(
                                () -> "last clearHeap() started before ui fully cleared, redo beforeShutdown()..."
                        );
                        befShutdownExecutor.schedule(() -> {
                            beforeShutdown();
                        }, 0L, TimeUnit.MILLISECONDS);
                    }
                    befShutdownExecutor.shutdown();
                }
            });
        } catch (Exception ex) {
            methLog.debug(() -> "failed to clear ui: ", ex);
            failedClearUI = true;
        }
        
        methLog.debug(() -> MemUtils.getMemDebug() + ", first clearHeap()");
        MemUtils.clearHeap(CLEAR_HEAP_MARGIN_BYTES);
        methLog.debug(() -> "first clearHeap() done, " + MemUtils.getMemDebug());
        
        befShutdownExecutor.schedule(() -> {
            beforeShutdown();
        }, LAST_CLEAR_HEAP_DELAY_MS, TimeUnit.MILLISECONDS);
        if (failedClearUI) {
            befShutdownExecutor.shutdown();
        }
    }
    
    private void beforeShutdown() {
        hasLastClearHeapStarted.set(true);
        Logger methLog = log.newChildFromCurMethIf(Level.DEBUG);
        methLog.debug(() -> "start, last clearHeap()");
        MemUtils.clearHeap(CLEAR_HEAP_MARGIN_BYTES);
        methLog.debug(() -> "last clearHeap() done, " + MemUtils.getMemDebug());
        
        if (befShutdownExecutor.isShutdown() && AFTER_LAST_CLEAR_HEAP_DELAY_MS > 0) {
            methLog.debug(
                    () -> "clearHeap() finished, program maintened alive for AFTER_CLEAR_HEAP_DELAY_MS"
            );
            try {
                Thread.sleep(AFTER_LAST_CLEAR_HEAP_DELAY_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        methLog.debug(() -> "end");
    }
    
    public boolean isShutdown() {
        return isShutdown.get();
    }
    
}
