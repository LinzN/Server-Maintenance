/*
 * Copyright (c) 2025 MirraNET, Niklas Linz. All rights reserved.
 *
 * This file is part of the MirraNET project and is licensed under the
 * GNU Lesser General Public License v3.0 (LGPLv3).
 *
 * You may use, distribute and modify this code under the terms
 * of the LGPLv3 license. You should have received a copy of the
 * license along with this file. If not, see <https://www.gnu.org/licenses/lgpl-3.0.html>
 * or contact: niklas.linz@mirranet.de
 */

package de.linzn.serverMaintenance.proxmox.commands;

import de.linzn.serverMaintenance.MaintenancePlugin;
import de.linzn.serverMaintenance.proxmox.nodes.ProxmoxBackupServer;
import de.linzn.stem.STEMApp;
import de.linzn.stem.modules.commandModule.ICommand;


public class MaintenanceCommand implements ICommand {
    @Override
    public boolean executeTerminal(String[] strings) {
        if (strings.length > 1) {
            String command = strings[0];

            if (command.equalsIgnoreCase("backup")) {
                String pbsName = strings[1];
                ProxmoxBackupServer pbs = MaintenancePlugin.maintenancePlugin.proxmoxBackupManager.getProxmoxBackupServerSet().get(pbsName);
                if (pbs != null) {
                    STEMApp.LOGGER.INFO("Found PBS " + pbsName);
                    STEMApp.getInstance().getScheduler().runTask(MaintenancePlugin.maintenancePlugin, pbs::runBackupTask);
                } else {
                    STEMApp.LOGGER.ERROR("No server found with name " + pbsName);
                }
            } else if (command.equalsIgnoreCase("clean")) {
                String pbsName = strings[1];
                ProxmoxBackupServer pbs = MaintenancePlugin.maintenancePlugin.proxmoxBackupManager.getProxmoxBackupServerSet().get(pbsName);
                if (pbs != null) {
                    STEMApp.LOGGER.INFO("Found PBS " + pbsName);
                    STEMApp.getInstance().getScheduler().runTask(MaintenancePlugin.maintenancePlugin, pbs::runMaintenanceTask);
                } else {
                    STEMApp.LOGGER.ERROR("No server found with name " + pbsName);
                }
            }
        }
        return false;
    }
}
