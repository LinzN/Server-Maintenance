/*
 * Copyright (C) 2023. Niklas Linz - All Rights Reserved
 * You may use, distribute and modify this code under the
 * terms of the LGPLv3 license, which unfortunately won't be
 * written for another century.
 *
 * You should have received a copy of the LGPLv3 license with
 * this file. If not, please write to: niklas.linz@enigmar.de
 *
 */

package de.linzn.serverMaintenance;


import de.linzn.serverMaintenance.proxmox.ProxmoxBackupManager;
import de.linzn.serverMaintenance.proxmox.commands.MaintenanceCommand;
import de.stem.stemSystem.STEMSystemApp;
import de.stem.stemSystem.modules.pluginModule.STEMPlugin;


public class MaintenancePlugin extends STEMPlugin {


    public static MaintenancePlugin maintenancePlugin;
    public ProxmoxBackupManager proxmoxBackupManager;


    public MaintenancePlugin() {
        maintenancePlugin = this;
        STEMSystemApp.getInstance().getCommandModule().registerCommand("maintenance", new MaintenanceCommand());
        this.proxmoxBackupManager = new ProxmoxBackupManager(this);
    }

    @Override
    public void onEnable() {
        this.proxmoxBackupManager.load();
    }

    @Override
    public void onDisable() {

    }
}
