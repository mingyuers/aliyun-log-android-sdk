package com.aliyun.sls.android.crashreporter.parser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Locale;

import android.content.Context;
import android.text.TextUtils;
import com.aliyun.sls.android.crashreporter.otel.CrashReporterOTel;
import com.aliyun.sls.android.otel.common.AttributesHelper;
import com.aliyun.sls.android.otel.common.Configuration;
import com.aliyun.sls.android.otel.common.ConfigurationManager;
import com.aliyun.sls.android.otel.common.ConfigurationManager.ConfigurationDelegate;
import io.opentelemetry.api.trace.SpanBuilder;

import static android.util.Log.w;
import static java.lang.String.format;

/**
 * @author gordon
 * @date 2022/5/8
 */
public class CrashFileHelper {
    private static final String TAG = "CrashFileHelper";

    private static final String PATH_ROOT = "sls_rum" + File.separator + "crashreporter";
    public static final String PATH_ITRACE_LOGS = PATH_ROOT + File.separator + "itrace_logs";
    public static final String PATH_ITRACE_TAGS = PATH_ROOT + File.separator + "itrace_tags";

    public static void scanAndReport(Context context) {
        CrashFileHelper helper = new CrashFileHelper();
        final File crashLogFile = new File(context.getFilesDir(), PATH_ITRACE_LOGS);
        helper.scanAndReport(context, crashLogFile);
    }

    public static void parseCrashFile(Context context, File file, String type) {
        CrashFileHelper helper = new CrashFileHelper();
        helper.parseTraceFile(context, type, file);
    }

    public void parseTraceFile(Context context, String type, File file) {
        if (TextUtils.isEmpty(type)) {
            w(TAG, format("parseTraceFile, type is empty. type: %s", type));
            return;
        }

        if (null == file || !file.exists()) {
            w(TAG, format("parseTraceFile, file is not exists or null. type: %s, file: %s"
                , type
                , (null != file ? file.getAbsolutePath() : "null"), type));
            return;
        }

        if ("jni".equals(type)) {
            type = "native";
        }

        LogParserResult result = LogParser.getInstance().parser(context, file, type);
        onLogParsedEnd(context, type, file, result);
    }

    private void onLogParsedEnd(final Context context, final String type, final File file,
        final LogParserResult result) {
        //if (options.debuggable) {
        //    v(TAG, SLSLog.format("onLogParsedEnd. type: %s, content length: %d", type, content.length()));
        //}

        String time = result.getString("time");
        String id = result.getString("id");
        String catId = result.getString("catId");
        result.remove("time");
        result.remove("id");
        //long start = TimeUtils.getInstance().getTimeInMillis();
        //if (!TextUtils.isEmpty(time)) {
        //    start = TimeUnit.MILLISECONDS.toNanos(parseTime(time));
        //}

        SpanBuilder builder = CrashReporterOTel.spanBuilder("crashreporter");
        // reset span start time to crash time
        //builder.setStartTimestamp(start, TimeUnit.MILLISECONDS);

        //List<Attribute> attributes = new ArrayList<>();
        Iterator<String> it = result.keys();
        while (it.hasNext()) {
            String key = it.next();
            if ("basic_info".equalsIgnoreCase(key) ||
                "summary".equalsIgnoreCase(key) ||
                "stacktrace".equalsIgnoreCase(key)) {
                builder.setAttribute("ex." + key, result.getString(key));
            }
            //attributes.add(
            //    Attribute.of("ex." + key, result.getString(key))
            //);
        }

        ConfigurationDelegate delegate = ConfigurationManager.getInstance().getConfigurationDelegate();
        final Configuration configuration = null != delegate ? delegate.getConfiguration("uem") : null;
        builder.setAttribute("state", type)
            //.setAttribute("page.name")
            .setAttribute("t", "error")
            .setAttribute("ex.type", "crash")
            .setAttribute("ex.sub_type", type)
            .setAttribute("ex.id", id)
            .setAttribute("ex.catId", catId)
            .setAllAttributes(AttributesHelper.create(context))
            .setAttribute("uid", null != configuration ? configuration.getUid() : "");

        builder.startSpan().end();
        //
        //final String topActivity = AppUtils.getTopActivity();
        //final String eventName = "Application crashed";
        //SpanBuilder builder = SLSAndroidRum.spanBuilder(eventName)
        //    .setStart(start)
        //    .addAttribute(
        //        Attribute.of(
        //            Pair.create("title", eventName),
        //            Pair.create("state", type),
        //            Pair.create("page.name", TextUtils.isEmpty(topActivity) ? "" : topActivity),
        //            Pair.create("t", "error"),
        //            Pair.create("ex.type", "crash"),
        //            Pair.create("ex.sub_type", type),
        //            Pair.create("ex.id", id),
        //            Pair.create("ex.catId", catId),
        //            Pair.create("ex.file", file.getName())
        //        )
        //    )
        //    .addAttribute(attributes);
        //Span span = builder.build();
        //final boolean ret = span.end();
        //if (ret) {
        //    //noinspection ResultOfMethodCallIgnored
        //    file.delete();
        //} else {
        //    w(TAG, "send crash span to log producer error.");
        //}
    }

    private String obtainTime(String info) {
        try {
            String time = info.split("time:")[1].trim();
            return time.substring(0, time.length() - 1);
        } catch (Throwable t) {
            return null;
        }
    }

    private long toLongTime(String time) {
        try {
            return Long.parseLong(time);
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    private long parseTime(String time) {
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).parse(time).getTime();
        } catch (Throwable e) {
            return System.currentTimeMillis();
        }
    }

    private void scanAndReport(Context context, File logFolder) {
        //if (options.debuggable) {
        //    SLSLog.v(TAG, "scanAndReport. logFolder: " + logFolder.getAbsolutePath());
        //}

        final File[] files = logFolder.listFiles();
        if (null == files) {
            //if (options.debuggable) {
            //    SLSLog.v(TAG, "scanAndReport. folder is empty.");
            //}
            return;
        }
        //else if (options.debuggable) {
        //    SLSLog.v(TAG, "scanAndReport. file count: " + files.length);
        //}

        for (File file : files) {
            //if (options.debuggable) {
            //    SLSLog.v(TAG, "scanAndReport. file: " + file.getName() + ", path: " + file.getAbsolutePath());
            //}

            String type = "unknown";
            if (file.getName().endsWith("jni.log")) {
                type = "jni";
            } else if (file.getName().endsWith("anr.log")) {
                type = "anr";
            } else if (file.getName().endsWith("java.log")) {
                type = "java";
            }
            //else if (file.getName().endsWith("unexp.log")) {
            //    type = "unexp";
            //}

            parseTraceFile(context, type, file);
        }
    }
}
