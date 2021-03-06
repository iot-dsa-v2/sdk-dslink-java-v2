package com.acuity.iot.dsa.dslink.sys.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.iot.dsa.dslink.Action.ResultsType;
import org.iot.dsa.dslink.ActionResults;
import org.iot.dsa.logging.DSLevel;
import org.iot.dsa.logging.DSLogHandler;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.node.action.DSIActionRequest;
import org.iot.dsa.time.DSDateTime;

/**
 * @author Daniel Shapiro
 */
public abstract class StreamableLogNode extends DSNode {

    private static DSList levelRange;

    public abstract DSInfo<?> getLevelInfo();

    public abstract Logger getLoggerObj();

    public static boolean levelMatches(Level msgLevel, Level desiredLevel) {
        return msgLevel.intValue() >= desiredLevel.intValue();
    }

    public static boolean logNameMatches(String msgLogName, String desiredLogName) {
        return msgLogName != null && msgLogName.startsWith(desiredLogName);
    }

    public static boolean logTextMatches(String text, String filter) {
        Pattern p = Pattern.compile(filter);
        return p.matcher(text).find();
    }

    protected DSAction getStreamLogAction(boolean isRoot) {
        DSAction act = new DSAction() {
            @Override
            public ActionResults invoke(DSIActionRequest req) {
                return ((StreamableLogNode) req.getTarget()).startLogStream(req);
            }
        };
        if (isRoot) {
            act.addParameter("Log Name", DSString.NULL, "Optional log name to filter by");
        }
        act.addDefaultParameter("Log Level", DSLevel.ALL, "Log level filter");
        act.addParameter("Filter", DSString.NULL, "Optional regex filter");
        act.setResultsType(ResultsType.STREAM);
        act.addColumnMetadata("Timestamp", DSDateTime.NULL);
        act.addColumnMetadata("Log Name", DSString.NULL);
        act.addColumnMetadata("Level", DSLevel.ALL);
        act.addColumnMetadata("Message", DSString.NULL).setEditor("textarea");
        return act;
    }

    private static String encodeLogRecord(LogRecord record) {
        StringBuilder builder = new StringBuilder();
        // class
        if (record.getSourceClassName() != null) {
            builder.append(record.getSourceClassName());
            builder.append(" - ");
        }
        // method
        if (record.getSourceMethodName() != null) {
            builder.append(record.getSourceMethodName());
            builder.append(" - ");
        }
        // message
        String msg = record.getMessage();
        if ((msg != null) && (msg.length() > 0)) {
            Object[] params = record.getParameters();
            if (params != null) {
                msg = String.format(msg, params);
            }
            builder.append(msg);
        }
        // exception
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            builder.append("\n");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            thrown.printStackTrace(pw);
            pw.close();
            builder.append(sw.toString());
        }
        return builder.toString();
    }

    private ActionResults startLogStream(final DSIActionRequest req) {
        final Logger loggerObj = getLoggerObj();
        final String name = req.getParameters().getString("Log Name");
        final DSLevel level = DSLevel.make(req.getParameters().getString("Log Level"));
        final String filter = req.getParameters().getString("Filter");
        final List<DSList> lines = new LinkedList<>();
        final Handler handler = new DSLogHandler() {
            @Override
            protected void write(LogRecord record) {
                String recordName = record.getLoggerName();
                Level recordLevel = record.getLevel();
                String recordMsg = encodeLogRecord(record);
                DSDateTime ts = DSDateTime.valueOf(record.getMillis());
                if (levelMatches(recordLevel, level.toLevel()) &&
                        (name == null || name.isEmpty() || logNameMatches(recordName, name)) &&
                        (filter == null || filter.isEmpty() || logTextMatches(recordMsg, filter))) {

                    while (lines.size() > 1000) {
                        lines.remove(0);
                    }
                    lines.add(DSList.valueOf(ts.toString(), recordName,
                                             DSLevel.valueOf(recordLevel).toString(), recordMsg));
                    req.sendResults();
                }
            }
        };
        loggerObj.addHandler(handler);
        return new ActionResults() {
            @Override
            public int getColumnCount() {
                return 4;
            }

            @Override
            public void getColumnMetadata(int idx, DSMap bucket) {
                if (idx == 0) {
                    new DSMetadata(bucket).setName("Timestamp").setType(DSDateTime.NULL);
                } else if (idx == 1) {
                    new DSMetadata(bucket).setName("Log Name").setType(DSString.NULL);
                } else if (idx == 2) {
                    new DSMetadata(bucket).setName("Level").setType(DSLevel.ALL);
                } else if (idx == 3) {
                    new DSMetadata(bucket).setName("Message").setType(DSString.NULL);
                }
            }

            @Override
            public void getResults(DSList bucket) {
                bucket.addAll(lines.remove(0));
            }

            @Override
            public ResultsType getResultsType() {
                return ResultsType.STREAM;
            }

            @Override
            public boolean next() {
                return lines.size() > 0;
            }

            @Override
            public void onClose() {
                handler.close();
                loggerObj.removeHandler(handler);
            }
        };
    }

    static {
        levelRange = new DSList();
        DSLevel.ALL.getEnums(levelRange);
    }

}
