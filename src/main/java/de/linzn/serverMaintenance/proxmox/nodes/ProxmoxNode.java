package de.linzn.serverMaintenance.proxmox.nodes;

import de.linzn.openJL.pairs.Pair;
import de.stem.stemSystem.STEMSystemApp;
import it.corsinvest.proxmoxve.api.PveClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProxmoxNode extends PveClient {
    private final String name;
    private final Map<Integer, JSONObject> backupResultList;

    private Pair<String, String> pbsStorage;

    public ProxmoxNode(String name, String hostname, int port, String authToken) {
        super(hostname, port);
        this.name = name;
        this.backupResultList = new HashMap<>();
        this.setApiToken(authToken);
    }

    public String getName() {
        return name;
    }

    public PVENodes.PVENodeItem getNode() {
        return this.getNodes().get(this.name);
    }

    public Map<Integer, JSONObject> getBackupResultList() {
        return backupResultList;
    }

    public void executeVZDump() {
        this.backupResultList.clear();
        JSONArray vms = this.getNode().getQemu().vmlist().getResponse().getJSONArray("data");
        for (int i = 0; i < vms.length(); i++) {
            JSONObject vmObject = vms.getJSONObject(i);

            Map<String, Object> parameters = new HashMap();
            parameters.put("mailnotification", "always");
            parameters.put("quiet", 1);
            parameters.put("storage", this.pbsStorage.getValue());
            parameters.put("notes-template", "{{guestname}}-STEM-API");
            parameters.put("mode", "snapshot");
            parameters.put("vmid", vmObject.get("vmid"));

            String taskUUID;

            JSONObject vzBackups = this.create("/nodes/" + this.name + "/vzdump", parameters).getResponse();
            taskUUID = vzBackups.getString("data");

            JSONObject taskStatus;
            do {
                try {
                    taskStatus = this.get("/nodes/" + this.name + "/tasks/" + taskUUID + "/status", null).getResponse();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    taskStatus = null;
                    STEMSystemApp.LOGGER.ERROR(e);
                }
            } while (taskStatus == null || taskStatus.getJSONObject("data").getString("status").equalsIgnoreCase("running"));

            if (!taskStatus.getJSONObject("data").getString("exitstatus").equalsIgnoreCase("OK")) {
                STEMSystemApp.LOGGER.ERROR("Backup failed for VM " + taskStatus.getJSONObject("data").getInt("id"));
                STEMSystemApp.LOGGER.ERROR("ERROR: " + taskStatus.getJSONObject("data").getString("exitstatus"));
            }
            this.backupResultList.put(taskStatus.getJSONObject("data").getInt("id"), taskStatus.getJSONObject("data"));
        }
    }

    public void setPbsStorage(ProxmoxBackupServer pbs, String pbsStorage) {
        this.pbsStorage = new Pair<>(pbs.getName(), pbsStorage);
    }

}