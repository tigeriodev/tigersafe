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

package fr.tigeriodev.tigersafe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import fr.tigeriodev.tigersafe.logs.Logger;
import fr.tigeriodev.tigersafe.logs.Logs;

public final class MonitoringManager {
    
    private static final Logger log = Logs.newLogger(MonitoringManager.class);
    public static final int MONITORING_PERIOD_MS =
            Integer.getInteger("tigersafe.monitoringPeriodMs", -1);
    public static final int MONITORING_MIN_PROCESSES_NUM =
            Integer.getInteger("tigersafe.monitoringMinProcessesNum", -1);
    public static final int MONITORING_MAX_PROCESSES_NUM =
            Integer.getInteger("tigersafe.monitoringMaxProcessesNum", -1);
    public static final boolean MONITORING_ALLOW_NEW_PROCESSES =
            Boolean.getBoolean("tigersafe.monitoringAllowNewProcesses");
    public static final boolean MONITORING_ALLOW_UNKNOWN_COMMAND =
            Boolean.getBoolean("tigersafe.monitoringAllowUnknownCommand");
    public static final String MONITORING_PROCESSES_COMMAND_FILE =
            System.getProperty("tigersafe.monitoringProcessesCommandFile");
    public static final boolean MONITORING_UPDATE_PROCESSES_COMMAND_FILE =
            Boolean.getBoolean("tigersafe.monitoringUpdateProcessesCommandFile");
    
    private static MonitoringManager instance = null;
    
    public static MonitoringManager start(final Runnable shutdownRunner)
            throws GeneralSecurityException {
        if (instance != null) {
            throw new IllegalStateException("Already started.");
        }
        if (MONITORING_PERIOD_MS <= 0) {
            if (MONITORING_UPDATE_PROCESSES_COMMAND_FILE) {
                throw new IllegalArgumentException(
                        "tigersafe.monitoringUpdateProcessesCommandFile is enabled but monitoring of processes is disabled (tigersafe.monitoringPeriodMs = "
                                + MONITORING_PERIOD_MS + ")."
                );
            }
            String osName = System.getProperty("os.name");
            if (osName != null && osName.toLowerCase().contains("windows")) {
                log.warn(
                        () -> "Monitoring of processes executed on the current user is not enabled (tigersafe.monitoringPeriodMs = "
                                + MONITORING_PERIOD_MS + "), which is not recommended in Windows."
                );
            }
            return null;
        }
        instance = new MonitoringManager(shutdownRunner);
        log.info(() -> "Started monitoring of processes executed on the current user.");
        return instance;
    }
    
    private final String curUserName;
    private ScheduledExecutorService executor = null;
    private Set<ProcessHandle> lastUserProcesses = null;
    
    private MonitoringManager(final Runnable shutdownRunner) throws GeneralSecurityException {
        if (
            MONITORING_MIN_PROCESSES_NUM > -1
                    && MONITORING_MAX_PROCESSES_NUM > -1
                    && MONITORING_MIN_PROCESSES_NUM > MONITORING_MAX_PROCESSES_NUM
        ) {
            throw new IllegalArgumentException(
                    "tigersafe.monitoringMinProcessesNum " + MONITORING_MIN_PROCESSES_NUM
                            + " > tigersafe.monitoringMaxProcessesNum "
                            + MONITORING_MAX_PROCESSES_NUM
            );
        }
        
        final Set<String> allowedCmds;
        if (
            !MONITORING_UPDATE_PROCESSES_COMMAND_FILE
                    && MONITORING_PROCESSES_COMMAND_FILE != null
                    && !MONITORING_PROCESSES_COMMAND_FILE.isEmpty()
        ) {
            try {
                List<String> lines = Files.readAllLines(Path.of(MONITORING_PROCESSES_COMMAND_FILE));
                allowedCmds = !lines.isEmpty() ? new HashSet<>(lines) : null;
            } catch (InvalidPathException | IOException ex) {
                throw new IllegalArgumentException(
                        "Failed to read file configured for tigersafe.monitoringProcessesCommandFile: "
                                + MONITORING_PROCESSES_COMMAND_FILE,
                        ex
                );
            }
        } else {
            allowedCmds = null;
        }
        
        try {
            curUserName = ProcessHandle.current().info().user().orElse(null);
        } catch (Exception ex) {
            throw new GeneralSecurityException(
                    "Failed to retrieve user name of current process, needed for monitoring processes.",
                    ex
            );
        }
        
        if (curUserName == null || curUserName.isBlank()) {
            throw new GeneralSecurityException(
                    "Failed to retrieve user name of current process, needed for monitoring processes."
            );
        }
        
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            Set<ProcessHandle> userCurProcesses = ProcessHandle.allProcesses()
                    .filter((p) -> curUserName.equals(p.info().user().orElse(null)))
                    .collect(Collectors.toSet());
            final int curProcessesNum = userCurProcesses.size();
            
            if (log.isDebugLoggable()) {
                StringBuilder sb = new StringBuilder(
                        "----- " + curUserName + ": " + curProcessesNum + " processes -----"
                );
                userCurProcesses.forEach((p) -> {
                    sb.append("\n - " + p.pid() + ": " + p.info().command().orElse("Unknown"));
                });
                log.debug(() -> sb.toString());
            }
            
            if (
                MONITORING_MAX_PROCESSES_NUM > -1 && curProcessesNum > MONITORING_MAX_PROCESSES_NUM
            ) {
                log.error(
                        () -> "Too many processes (" + curProcessesNum + ", max configured: "
                                + MONITORING_MAX_PROCESSES_NUM + ") executed on the current user ("
                                + curUserName + "), stopping for security reasons"
                );
                shutdownRunner.run();
                return;
            }
            if (
                MONITORING_MIN_PROCESSES_NUM > -1 && curProcessesNum < MONITORING_MIN_PROCESSES_NUM
            ) {
                log.error(
                        () -> "Not enough processes (" + curProcessesNum + ", min configured: "
                                + MONITORING_MIN_PROCESSES_NUM + ") executed on the current user ("
                                + curUserName + "), stopping for security reasons"
                );
                shutdownRunner.run();
                return;
            }
            
            if (lastUserProcesses != null) {
                if (MONITORING_ALLOW_NEW_PROCESSES && allowedCmds == null) {
                    return;
                }
                if (!userCurProcesses.equals(lastUserProcesses)) {
                    if (MONITORING_ALLOW_NEW_PROCESSES) {
                        for (ProcessHandle proc : userCurProcesses) {
                            String cmd = proc.info().command().orElse(null);
                            if (cmd != null) {
                                if (!allowedCmds.contains(cmd)) {
                                    log.error(
                                            () -> "A new process with unallowed command (" + cmd
                                                    + ") is executed on the current user ("
                                                    + curUserName
                                                    + "), stopping for security reasons"
                                    );
                                    shutdownRunner.run();
                                    return;
                                }
                            } else if (!MONITORING_ALLOW_UNKNOWN_COMMAND) {
                                log.error(
                                        () -> "A process with unknown command is executed on the current user ("
                                                + curUserName + "), stopping for security reasons"
                                );
                                shutdownRunner.run();
                                return;
                            }
                        }
                    } else {
                        for (ProcessHandle curProc : userCurProcesses) {
                            if (!lastUserProcesses.contains(curProc)) {
                                log.error(
                                        () -> "A new process is executed on the current user ("
                                                + curUserName + "), stopping for security reasons"
                                );
                                shutdownRunner.run();
                                return;
                            }
                        }
                    }
                } else {
                    return;
                }
            } else {
                if (allowedCmds != null) {
                    for (ProcessHandle proc : userCurProcesses) {
                        String cmd = proc.info().command().orElse(null);
                        if (cmd != null) {
                            if (!allowedCmds.contains(cmd)) {
                                log.error(
                                        () -> "A process with unallowed command (" + cmd
                                                + ") is executed on the current user ("
                                                + curUserName + "), stopping for security reasons"
                                );
                                shutdownRunner.run();
                                return;
                            }
                        } else if (!MONITORING_ALLOW_UNKNOWN_COMMAND) {
                            log.error(
                                    () -> "A process with unknown command is executed on the current user ("
                                            + curUserName + "), stopping for security reasons"
                            );
                            shutdownRunner.run();
                            return;
                        }
                    }
                } else if (MONITORING_UPDATE_PROCESSES_COMMAND_FILE) {
                    Set<String> curCmds = userCurProcesses.stream()
                            .filter((p) -> p.info().command().isPresent())
                            .map((p) -> p.info().command().get())
                            .collect(Collectors.toSet());
                    try {
                        Files.write(
                                Path.of(MONITORING_PROCESSES_COMMAND_FILE),
                                curCmds,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        );
                        log.info(
                                () -> "Updated the file defined for tigersafe.monitoringProcessesCommandFile ("
                                        + MONITORING_PROCESSES_COMMAND_FILE + ")"
                        );
                    } catch (
                            IllegalArgumentException | IOException
                            | UnsupportedOperationException ex
                    ) {
                        log.error(
                                () -> "Failed to update the file defined for tigersafe.monitoringProcessesCommandFile ("
                                        + MONITORING_PROCESSES_COMMAND_FILE + ")",
                                ex
                        );
                    }
                    shutdownRunner.run();
                    return;
                }
            }
            lastUserProcesses = userCurProcesses;
        }, 0L, MONITORING_PERIOD_MS, TimeUnit.MILLISECONDS);
    }
    
    public void stop() {
        log.debug(() -> "stopped");
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
    
}
