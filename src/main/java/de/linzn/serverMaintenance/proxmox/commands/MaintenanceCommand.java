package de.linzn.serverMaintenance.proxmox.commands;

import de.linzn.serverMaintenance.MaintenancePlugin;
import de.linzn.serverMaintenance.proxmox.nodes.ProxmoxBackupServer;
import de.stem.stemSystem.STEMSystemApp;
import de.stem.stemSystem.modules.commandModule.ICommand;

public class MaintenanceCommand implements ICommand {
    @Override
    public boolean executeTerminal(String[] strings) {
        if (strings.length > 1) {
            String command = strings[0];

            if (command.equalsIgnoreCase("backup")) {
                String pbsName = strings[1];
                ProxmoxBackupServer pbs = MaintenancePlugin.maintenancePlugin.proxmoxBackupManager.getProxmoxBackupServerSet().get(pbsName);
                if (pbs != null) {
                    STEMSystemApp.LOGGER.INFO("Found PBS " + pbsName);
                    STEMSystemApp.getInstance().getScheduler().runTask(MaintenancePlugin.maintenancePlugin, pbs::runBackupTask);
                } else {
                    STEMSystemApp.LOGGER.ERROR("No server found with name " + pbsName);
                }
            } else if(command.equalsIgnoreCase("clean")){
                String pbsName = strings[1];
                ProxmoxBackupServer pbs = MaintenancePlugin.maintenancePlugin.proxmoxBackupManager.getProxmoxBackupServerSet().get(pbsName);
                if (pbs != null) {
                    STEMSystemApp.LOGGER.INFO("Found PBS " + pbsName);
                    STEMSystemApp.getInstance().getScheduler().runTask(MaintenancePlugin.maintenancePlugin, pbs::runMaintenanceTask);
                } else {
                    STEMSystemApp.LOGGER.ERROR("No server found with name " + pbsName);
                }
            }
        }
        return false;
    }
}
