package com.acuity.iot.dsa.dslink.sys.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.DSRuntime.Timer;
import org.iot.dsa.dslink.DSLink;
import org.iot.dsa.io.NodeEncoder;
import org.iot.dsa.io.json.JsonWriter;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSLong;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.time.DSTime;

public class SysBackupService extends DSNode implements Runnable {
    
    static final String INTERVAL = "Backup Interval";
    static final String MAXIMUM = "Max Number of Backups";
    static final String SAVE = "Save";
    
    private DSInfo interval = getInfo(INTERVAL);
    private DSInfo maximum = getInfo(MAXIMUM);
    private DSInfo save = getInfo(SAVE);
    
    private DSLink link;
    private Timer nextSave;
    private Object lock = new Object();
    
    @Override
    protected void declareDefaults() {
        declareDefault(SAVE, DSAction.DEFAULT);
        declareDefault(INTERVAL, DSLong.valueOf(60));
        declareDefault(MAXIMUM, DSLong.valueOf(3));
    }
    
    @Override
    protected void onStable() {
        DSRuntime.run(this);
    }
    
    private DSLink getLink() {
        if (link == null) {
            link = (DSLink) getAncestor(DSLink.class);
        }
        return link;
    }
    
    @Override
    public ActionResult onInvoke(DSInfo action, ActionInvocation invocation) {
        if (action == save) {
            save();
        } else {
            super.onInvoke(action, invocation);
        }
        return null;
    }
    
    @Override
    public void onSet(DSInfo info, DSIValue value) {
        super.onSet(info, value);
        if (info == interval) {
            synchronized (lock) {
                if (nextSave != null) {
                    long newNextRun = (value.toElement().toLong() * 60000) + System.currentTimeMillis();
                    long scheduledNextRun = nextSave.nextRun();
                    if (newNextRun < scheduledNextRun) {
                        nextSave.cancel();
                        DSRuntime.runAt(this, newNextRun);
                    }
                }
            }
        }
    }
    
    /**
     * Serializes the configuration database.
     */
    public void save() {
        if (!getLink().isSaveEnabled()) {
            return;
        }
        ZipOutputStream zos = null;
        InputStream in = null;
        try {
            File nodes = getLink().getConfig().getNodesFile();
            String name = nodes.getName();
            if (nodes.exists()) {
                info("Backing up the node database...");
                StringBuilder buf = new StringBuilder();
                Calendar cal = DSTime.getCalendar(System.currentTimeMillis());
                if (name.endsWith(".zip")) {
                    String tmp = name.substring(0, name.lastIndexOf(".zip"));
                    buf.append(tmp).append('.');
                    DSTime.encodeForFiles(cal, buf);
                    buf.append(".zip");
                    File bakFile = new File(nodes.getParent(), buf.toString());
                    nodes.renameTo(bakFile);
                } else {
                    buf.append(name).append('.');
                    DSTime.encodeForFiles(cal, buf);
                    buf.append(".zip");
                    File back = new File(nodes.getParent(), buf.toString());
                    FileOutputStream fos = new FileOutputStream(back);
                    zos = new ZipOutputStream(fos);
                    zos.putNextEntry(new ZipEntry(nodes.getName()));
                    byte[] b = new byte[4096];
                    in = new FileInputStream(nodes);
                    int len = in.read(b);
                    while (len > 0) {
                        zos.write(b, 0, len);
                        len = in.read(b);
                    }
                    in.close();
                    in = null;
                    zos.closeEntry();
                    zos.close();
                    zos = null;
                }
                DSTime.recycle(cal);
            }
            long time = System.currentTimeMillis();
            info("Saving node database " + nodes.getAbsolutePath());
            JsonWriter writer = null;
            if (name.endsWith(".zip")) {
                String tmp = name.substring(0, name.lastIndexOf(".zip"));
                writer = new JsonWriter(nodes, tmp + ".json");
            } else {
                writer = new JsonWriter(nodes);
            }
            NodeEncoder.encode(writer, this);
            writer.close();
            trimBackups();
            time = System.currentTimeMillis() - time;
            info("Node database saved: " + time + "ms");
        } catch (Exception x) {
            error("Saving node database", x);
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException x) {
            error("Closing input", x);
        }
        try {
            if (zos != null) {
                zos.close();
            }
        } catch (IOException x) {
            error("Closing output", x);
        }
    }
    
    /**
     * Called by save, no need to explicitly call.
     */
    private void trimBackups() {
        final File nodes = getLink().getConfig().getNodesFile();
        if (nodes == null) {
            return;
        }
        final String nodesName = nodes.getName();
        final boolean isZip = nodesName.endsWith(".zip");
        int idx = nodesName.lastIndexOf('.');
        final String nameBase = nodesName.substring(0, idx);
        File dir = nodes.getAbsoluteFile().getParentFile();
        File[] backups = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.equals(nodesName)) {
                    return false;
                }
                if (isZip) {
                    if (name.endsWith(".zip")) {
                        return name.startsWith(nameBase);
                    }
                } else {
                    if (name.endsWith(".json")) {
                        return name.startsWith(nameBase);
                    }
                }
                return false;
            }
        });
        if (backups == null) {
            return;
        }
        Arrays.sort(backups);
        int maxBackups = maximum.getElement().toInt();
        if (backups.length <= maxBackups) {
            return;
        }
        for (int i = 0, len = backups.length - maxBackups; i < len; i++) {
            backups[i].delete();
        }
    }

    @Override
    public void run() {
        synchronized(lock) {
            save();
            long saveInterval = interval.getElement().toLong();
            saveInterval *= 60000;
            nextSave = DSRuntime.runDelayed(this, saveInterval);
        }
    }

}