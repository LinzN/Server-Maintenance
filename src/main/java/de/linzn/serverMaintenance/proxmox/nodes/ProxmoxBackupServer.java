package de.linzn.serverMaintenance.proxmox.nodes;


import de.linzn.serverMaintenance.MaintenancePlugin;
import de.linzn.serverMaintenance.utils.NetworkHelper;
import de.stem.stemSystem.STEMSystemApp;
import de.stem.stemSystem.modules.informationModule.InformationBlock;
import de.stem.stemSystem.modules.informationModule.InformationIntent;
import it.corsinvest.proxmoxbs.api.PbsClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxmoxBackupServer extends PbsClient {
    private final Set<String> pveNodes;
    private final String name;
    private final String ip;
    private final int port;
    private final String mac;
    private final String broadcastIP;
    private final AtomicBoolean locked;
    private InformationBlock informationBlock;

    public ProxmoxBackupServer(String name, String ip, int port, String mac, String broadcastIP, String apiToken) {
        super(ip, 8007);
        this.pveNodes = new HashSet<>();
        this.setApiToken(apiToken);
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.mac = mac;
        this.broadcastIP = broadcastIP;
        this.locked = new AtomicBoolean(false);
    }

    public void enable() {
        STEMSystemApp.getInstance().getScheduler().runFixedScheduler(MaintenancePlugin.maintenancePlugin, this::runBackupTask, 0, 3, 0, true);
        //STEMSystemApp.getInstance().getScheduler().runFixedScheduler(MaintenancePlugin.maintenancePlugin, this::runMaintenanceTask, 0, 12, 0, true);
    }

    public String getName() {
        return name;
    }

    private boolean isAvailable() {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), 2000);
            return true;
        } catch (IOException ex) {
            /* ignore */
        }
        return false;
    }

    public void waitReady() {
        if (!this.isAvailable()) {
            STEMSystemApp.LOGGER.DEBUG("Waiting for PBS to be ready...");
            do {
                NetworkHelper.sendMagicPackage(this.mac, this.broadcastIP);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            } while (!this.isAvailable());
        }
        STEMSystemApp.LOGGER.DEBUG("PBS is ready!");
    }

    public void sendSleep() {
        if (this.isAvailable()) {
            STEMSystemApp.LOGGER.DEBUG("Sending PBS sleeping...");
            Map<String, Object> parameters = new HashMap();
            parameters.put("command", "shutdown");
            JSONObject response = this.create("/nodes/elsa/status", parameters).getResponse();
        }
    }

    public void maintenanceGC() {
        STEMSystemApp.LOGGER.DEBUG("Starting GC Tasks!");
        this.informationBlock.setDescription("Garbage collector task running!");
        JSONArray datastores = this.get("/admin/datastore", null).getResponse().getJSONArray("data");
        for (int i = 0; i < datastores.length(); i++) {
            JSONObject gcJob = datastores.getJSONObject(i);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("store", gcJob.getString("store"));
            String jobId = this.create("/admin/datastore/" + gcJob.getString("store") + "/gc", null).getResponse().getString("data");

            JSONObject taskStatus;
            do {
                taskStatus = this.get("/nodes/" + this.name + "/tasks/" + jobId + "/status", null).getResponse();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } while (taskStatus.getJSONObject("data").getString("status").equalsIgnoreCase("running"));
        }
        STEMSystemApp.LOGGER.DEBUG("GC Tasks done!");
    }

    public void maintenancePrune() {
        STEMSystemApp.LOGGER.DEBUG("Starting Prune Tasks!");
        this.informationBlock.setDescription("Prune task running!");
        JSONArray pruneJobs = this.get("/admin/prune", null).getResponse().getJSONArray("data");
        for (int i = 0; i < pruneJobs.length(); i++) {
            JSONObject pruneJob = pruneJobs.getJSONObject(i);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("id", pruneJob.getString("id"));
            String jobId = this.create("/admin/prune/" + pruneJob.getString("id") + "/run", null).getResponse().getString("data");
            JSONObject taskStatus;
            do {
                taskStatus = this.get("/nodes/" + this.name + "/tasks/" + jobId + "/status", null).getResponse();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } while (taskStatus.getJSONObject("data").getString("status").equalsIgnoreCase("running"));
        }
        STEMSystemApp.LOGGER.DEBUG("Prune Tasks done!");
    }

    public void addPveNode(String proxmoxNodeName) {
        this.pveNodes.add(proxmoxNodeName);
    }

    public void runMaintenanceTask() {
        if (!this.locked.get()) {
            STEMSystemApp.LOGGER.INFO("Maintenance Task started for pbs " + this.name);
            if (this.informationBlock != null) {
                informationBlock.expire();
                informationBlock = null;
            }
            this.informationBlock = new InformationBlock("Maintenance - " + this.name.toUpperCase(), "Preparing cleanup task...", MaintenancePlugin.maintenancePlugin);
            this.informationBlock.setExpireTime(-1L);
            this.informationBlock.setIcon("PROGRESS");
            this.informationBlock.addIntent(InformationIntent.SHOW_DISPLAY);
            STEMSystemApp.getInstance().getInformationModule().queueInformationBlock(this.informationBlock);
            STEMSystemApp.LOGGER.DEBUG("Lock server " + this.name);
            this.locked.set(true);
            this.waitReady();
            this.maintenancePrune();
            this.maintenanceGC();
            this.informationBlock.setDescription("Cleanup task done!");
            this.informationBlock.setExpireTime(Instant.now().plus(8, ChronoUnit.HOURS));
            this.informationBlock.setIcon("SUCCESS");
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(5));
            } catch (InterruptedException ignored) {
            }
            this.sendSleep();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException ignored) {
            }
            STEMSystemApp.LOGGER.DEBUG("Unlock server " + this.name);
            this.locked.set(false);
            STEMSystemApp.LOGGER.INFO("Maintenance Task done for pbs " + this.name);
        } else {
            STEMSystemApp.LOGGER.ERROR("Server is locked: " + this.name);
        }
    }

    public void runBackupTask() {
        STEMSystemApp.LOGGER.INFO("Backup Task started for pbs " + this.name);
        if (!this.locked.get()) {
            if (this.informationBlock != null) {
                informationBlock.expire();
                informationBlock = null;
            }
            this.informationBlock = new InformationBlock("Maintenance - " + this.name.toUpperCase(), "Preparing backup task...", MaintenancePlugin.maintenancePlugin);
            this.informationBlock.setExpireTime(-1L);
            this.informationBlock.setIcon("PROGRESS");
            this.informationBlock.addIntent(InformationIntent.SHOW_DISPLAY);
            STEMSystemApp.getInstance().getInformationModule().queueInformationBlock(informationBlock);
            STEMSystemApp.LOGGER.DEBUG("Lock server " + this.name);
            this.locked.set(true);
            this.informationBlock.setDescription("Waiting to be ready...");
            this.waitReady();
            this.informationBlock.setDescription("Server is ready...");

            for (String nodeName : pveNodes) {
                ProxmoxNode proxmoxNode = MaintenancePlugin.maintenancePlugin.proxmoxBackupManager.getProxmoxNodeSet().get(nodeName);
                if (proxmoxNode != null) {
                    this.informationBlock.setDescription("Backup running for ProxmoxNode " + proxmoxNode.getName());
                    proxmoxNode.executeVZDump(this.informationBlock);
                    // later check if something failed!
                    this.informationBlock.setDescription("Backup done for ProxmoxNode " + proxmoxNode.getName());
                }
            }
            this.maintenancePrune();
            this.maintenanceGC();
            this.informationBlock.setDescription("Cleanup task done!");

            this.informationBlock.setDescription("Backups done!");
            this.informationBlock.setExpireTime(Instant.now().plus(8, ChronoUnit.HOURS));
            this.informationBlock.setIcon("SUCCESS");
            this.informationBlock = null;
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(5));
            } catch (InterruptedException ignored) {
            }
            this.sendSleep();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException ignored) {
            }
            STEMSystemApp.LOGGER.DEBUG("Unlock server " + this.name);
            this.locked.set(false);
            STEMSystemApp.LOGGER.INFO("Backup Task done for pbs " + this.name);
        } else {
            STEMSystemApp.LOGGER.ERROR("Server is locked: " + this.name);
        }
    }
}
