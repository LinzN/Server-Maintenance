package de.linzn.serverMaintenance.proxmox;

import de.linzn.serverMaintenance.MaintenancePlugin;
import de.linzn.serverMaintenance.proxmox.nodes.ProxmoxBackupServer;
import de.linzn.serverMaintenance.proxmox.nodes.ProxmoxNode;
import de.linzn.simplyConfiguration.FileConfiguration;
import de.linzn.simplyConfiguration.provider.YamlConfiguration;
import de.stem.stemSystem.STEMSystemApp;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ProxmoxBackupManager {

    private final Map<String, ProxmoxBackupServer> proxmoxBackupServerSet;
    private final Map<String, ProxmoxNode> proxmoxNodeSet;
    private MaintenancePlugin maintenancePlugin;

    public ProxmoxBackupManager(MaintenancePlugin maintenancePlugin) {
        this.maintenancePlugin = maintenancePlugin;
        this.proxmoxBackupServerSet = new HashMap<>();
        this.proxmoxNodeSet = new HashMap<>();
    }

    public void load() {
        this.loadPBSNodes();
        this.loadPVENodes();
        this.enablePBSTasks();
    }

    private void enablePBSTasks() {
        for (ProxmoxBackupServer pbs : this.proxmoxBackupServerSet.values()) {
            pbs.enable();
        }
    }

    private void loadPBSNodes() {
        File proxmoxFolder = new File(MaintenancePlugin.maintenancePlugin.getDataFolder(), "proxmoxConfig");
        File pbsFolder = new File(proxmoxFolder, "pbsConfig");
        if (proxmoxFolder.exists() && pbsFolder.exists()) {
            for (final File file : pbsFolder.listFiles()) {
                if (!file.getName().endsWith(".yml")) {
                    continue;
                }
                FileConfiguration pbsConfig = YamlConfiguration.loadConfiguration(file);

                String name = pbsConfig.getString("name");
                String ip = pbsConfig.getString("ip");
                int port = pbsConfig.getInt("port");
                String mac = pbsConfig.getString("mac");
                String broadcastIP = pbsConfig.getString("broadcast");
                String apiToken = pbsConfig.getString("apiToken");
                ProxmoxBackupServer proxmoxBackupServer = new ProxmoxBackupServer(name, ip, port, mac, broadcastIP, apiToken);
                this.proxmoxBackupServerSet.put(proxmoxBackupServer.getName(), proxmoxBackupServer);
            }
        }
    }

    private void loadPVENodes() {
        File proxmoxFolder = new File(MaintenancePlugin.maintenancePlugin.getDataFolder(), "proxmoxConfig");
        File pveFolder = new File(proxmoxFolder, "pveConfig");
        if (proxmoxFolder.exists() && pveFolder.exists()) {
            for (final File file : pveFolder.listFiles()) {
                if (!file.getName().endsWith(".yml")) {
                    continue;
                }

                FileConfiguration pveConfig = YamlConfiguration.loadConfiguration(file);

                String name = pveConfig.getString("name");
                String ip = pveConfig.getString("ip");
                int port = pveConfig.getInt("port");
                String apiToken = pveConfig.getString("apiToken");
                String pbsName = pveConfig.getString("pbsName");
                String pbsStorage = pveConfig.getString("pbsStorage");

                ProxmoxNode proxmoxNode = new ProxmoxNode(name, ip, port, apiToken);
                this.proxmoxNodeSet.put(proxmoxNode.getName(), proxmoxNode);

                ProxmoxBackupServer proxmoxBackupServer = this.proxmoxBackupServerSet.get(pbsName);
                if (proxmoxBackupServer != null) {
                    proxmoxNode.setPbsStorage(proxmoxBackupServer, pbsStorage);
                    proxmoxBackupServer.addPveNode(proxmoxNode.getName());
                }
            }
        }
    }

    public Map<String, ProxmoxBackupServer> getProxmoxBackupServerSet() {
        return proxmoxBackupServerSet;
    }

    public Map<String, ProxmoxNode> getProxmoxNodeSet() {
        return proxmoxNodeSet;
    }
}
