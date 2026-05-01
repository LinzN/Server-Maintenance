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

package de.linzn.serverMaintenance.proxmox.nodes;

import de.linzn.openJL.pairs.Pair;
import de.linzn.stem.STEMApp;
import de.linzn.stem.modules.informationModule.InformationBlock;
import it.corsinvest.proxmoxve.api.PveClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProxmoxNode extends PveClient {
    private final String name;
    private final Map<Integer, JSONObject> backupResultList;
    private final Set<String> errorResultSet;

    private Pair<String, String> pbsStorage;

    public ProxmoxNode(String name, String hostname, int port, String authToken) {
        super(hostname, port);
        this.name = name;
        this.backupResultList = new HashMap<>();
        this.errorResultSet = new HashSet<>();
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

    public Set<String> getErrorResultSet() {
        return errorResultSet;
    }

    public boolean isReachable(){
        int timeoutMillis = 10000;
        try {
            URL url = new URL(this.getApiUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("HEAD");
            connection.connect();
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public void executeVZDump(InformationBlock informationBlock) {
        this.backupResultList.clear();
        this.errorResultSet.clear();

        JSONArray vms = this.getNode().getQemu().vmlist().getResponse().getJSONArray("data");
        vms.putAll(this.getNode().getLxc().vmlist().getResponse().getJSONArray("data"));
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
            informationBlock.setDescription("Backup running for ProxmoxNode " + this.getName() + " - VM: " + vmObject.get("vmid"));

            JSONObject taskStatus;
            do {
                try {
                    taskStatus = this.get("/nodes/" + this.name + "/tasks/" + taskUUID + "/status", null).getResponse();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    taskStatus = null;
                    STEMApp.LOGGER.ERROR(e);
                }
            } while (taskStatus == null || taskStatus.getJSONObject("data").getString("status").equalsIgnoreCase("running"));

            if (!taskStatus.getJSONObject("data").getString("exitstatus").equalsIgnoreCase("OK")) {
                STEMApp.LOGGER.ERROR("Backup failed for VM " + taskStatus.getJSONObject("data").getInt("id"));
                STEMApp.LOGGER.ERROR("ERROR: " + taskStatus.getJSONObject("data").getString("exitstatus"));
                this.errorResultSet.add(this.getName() + " - VM: " + vmObject.get("vmid"));
            }
            this.backupResultList.put(taskStatus.getJSONObject("data").getInt("id"), taskStatus.getJSONObject("data"));
        }

    }

    public void setPbsStorage(ProxmoxBackupServer pbs, String pbsStorage) {
        this.pbsStorage = new Pair<>(pbs.getName(), pbsStorage);
    }

}