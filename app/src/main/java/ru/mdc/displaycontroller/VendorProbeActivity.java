package ru.mdc.displaycontroller;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;

public class VendorProbeActivity extends Activity {
    private static final String VERSION = "1.0.1-internal-4";
    private static final String[] PACKAGES = {
            "com.tw.carinfoservice",
            "com.tw.service",
            "com.tw.coreservice",
            "com.tw.core",
            "com.tw.car",
            "com.tw.carchoose",
            "com.tw.jar1",
            "com.tw.keypad",
            "com.tw.service.xt"
    };
    private static final String[] KEYS = {
            "can", "canbus", "mcu", "car", "vehicle", "climate", "air", "temp", "fan",
            "display", "lcd", "screen", "mazda", "steer", "key", "trip", "fuel", "range",
            "clock", "info", "radio", "service", "broadcast", "intent", "raise", "rzc"
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView output;
    private String report = "NOT RUN";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,10,13));
        TextView h = text("MDC TS10 VENDOR PROBE • " + VERSION, 22, Color.WHITE);
        root.addView(h);
        root.addView(text("READ-ONLY. No Binder calls, broadcasts, CAN writes or device-node writes. This probe only inventories installed TS10 vendor packages/APKs/classes/strings.", 14, Color.LTGRAY));
        Button run = new Button(this); run.setText("RUN FULL READ-ONLY VENDOR PROBE"); run.setOnClickListener(v -> runProbe()); root.addView(run);
        Button copy = new Button(this); copy.setText("COPY PROBE REPORT"); copy.setOnClickListener(v -> copyReport()); root.addView(copy);
        Button save = new Button(this); save.setText("SAVE PROBE REPORT"); save.setOnClickListener(v -> saveReport()); root.addView(save);
        output = text("Press RUN FULL READ-ONLY VENDOR PROBE", 12, Color.rgb(185,220,185));
        ScrollView sc = new ScrollView(this); sc.addView(output); root.addView(sc, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(18,12,18,12); return t;
    }

    private void runProbe() {
        output.setText("RUNNING… this can take 10–40 seconds on TS10.");
        io.submit(() -> {
            StringBuilder b = new StringBuilder();
            b.append("MDC_VENDOR_PROBE_SCHEMA=1\nVERSION=").append(VERSION).append("\n");
            b.append("READ_ONLY=true\nCAN_WRITE=false\nUNKNOWN_BINDER_CALL=false\nUNKNOWN_BROADCAST_SEND=false\nDEVICE_NODE_WRITE=false\n");
            b.append("manufacturer=").append(Build.MANUFACTURER).append("\nmodel=").append(Build.MODEL).append("\nandroid=").append(Build.VERSION.RELEASE).append("\n\n");
            PackageManager pm = getPackageManager();
            for (String pkg : PACKAGES) probePackage(pm, pkg, b);
            report = limit(b.toString(), 120000);
            runOnUiThread(() -> output.setText(report));
        });
    }

    private void probePackage(PackageManager pm, String pkg, StringBuilder b) {
        b.append("\n====================\nPACKAGE=").append(pkg).append("\n");
        try {
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES |
                    PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA;
            PackageInfo pi = pm.getPackageInfo(pkg, flags);
            ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            b.append("versionName=").append(pi.versionName).append("\n");
            b.append("uid=").append(ai.uid).append("\n");
            b.append("process=").append(ai.processName).append("\n");
            b.append("sourceDir=").append(ai.sourceDir).append("\n");
            b.append("sourceReadable=").append(new File(ai.sourceDir).canRead()).append("\n");
            b.append("systemApp=").append((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0).append("\n");
            b.append("updatedSystemApp=").append((ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0).append("\n");
            if (ai.metaData != null) for (String k : ai.metaData.keySet()) b.append("meta.").append(k).append("=").append(ai.metaData.get(k)).append("\n");
            if (pi.activities != null) for (ActivityInfo x : pi.activities) b.append("activity=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append(" process=").append(x.processName).append("\n");
            if (pi.receivers != null) for (ActivityInfo x : pi.receivers) b.append("receiver=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append(" process=").append(x.processName).append("\n");
            if (pi.services != null) for (ServiceInfo x : pi.services) b.append("service=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append(" process=").append(x.processName).append("\n");
            if (pi.providers != null) for (ProviderInfo x : pi.providers) b.append("provider=").append(x.name).append(" exported=").append(x.exported).append(" authority=").append(x.authority).append(" readPerm=").append(x.readPermission).append(" writePerm=").append(x.writePermission).append("\n");
            if (pi.requestedPermissions != null) for (String p : pi.requestedPermissions) if (relevant(p)) b.append("permission=").append(p).append("\n");
            probeDexClasses(ai.sourceDir, b);
            probeApkStrings(ai.sourceDir, b);
        } catch (PackageManager.NameNotFoundException e) {
            b.append("NOT_INSTALLED\n");
        } catch (Throwable t) {
            b.append("probeError=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n");
        }
    }

    private void probeDexClasses(String apk, StringBuilder b) {
        b.append("## dex classes matching OEM keywords\n");
        int count = 0;
        try (DexFile dex = new DexFile(apk)) {
            Enumeration<String> e = dex.entries();
            while (e.hasMoreElements() && count < 500) {
                String name = e.nextElement();
                if (relevant(name)) { b.append("class=").append(name).append("\n"); count++; }
            }
            b.append("classMatchCount=").append(count).append("\n");
        } catch (Throwable t) {
            b.append("dexClassScanError=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n");
        }
    }

    private void probeApkStrings(String apk, StringBuilder b) {
        b.append("## APK raw strings matching OEM keywords\n");
        Set<String> hits = new LinkedHashSet<>();
        try (ZipFile z = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> es = z.entries();
            while (es.hasMoreElements() && hits.size() < 800) {
                ZipEntry ze = es.nextElement();
                String n = ze.getName();
                if (!(n.equals("AndroidManifest.xml") || n.startsWith("classes") && n.endsWith(".dex") || n.endsWith(".xml"))) continue;
                try (BufferedInputStream in = new BufferedInputStream(z.getInputStream(ze))) {
                    StringBuilder s = new StringBuilder();
                    int c; long read = 0; long cap = n.endsWith(".dex") ? 6_000_000L : 2_000_000L;
                    while ((c = in.read()) != -1 && read++ < cap && hits.size() < 800) {
                        if (c >= 32 && c <= 126) {
                            s.append((char)c);
                            if (s.length() > 240) flushCandidate(s, hits);
                        } else {
                            flushCandidate(s, hits);
                        }
                    }
                    flushCandidate(s, hits);
                } catch (Throwable ignored) {}
            }
            for (String h : hits) b.append("str=").append(h).append("\n");
            b.append("stringMatchCount=").append(hits.size()).append("\n");
        } catch (Throwable t) {
            b.append("apkStringScanError=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n");
        }
    }

    private void flushCandidate(StringBuilder s, Set<String> hits) {
        if (s.length() >= 5) {
            String x = s.toString().trim();
            if (x.length() >= 5 && x.length() <= 240 && relevant(x)) hits.add(sanitize(x));
        }
        s.setLength(0);
    }

    private boolean relevant(String s) {
        if (s == null) return false;
        String l = s.toLowerCase(Locale.US);
        for (String k : KEYS) if (l.contains(k)) return true;
        return false;
    }

    private String sanitize(String s) {
        return s.replaceAll("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}", "REDACTED_MAC");
    }

    private String safe(String s) { return s == null ? "" : sanitize(s.replace('\n',' ')); }
    private String limit(String s, int max) { return s.length() > max ? s.substring(0,max) + "\n[TRUNCATED]" : s; }

    private void copyReport() {
        ClipboardManager c = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        c.setPrimaryClip(ClipData.newPlainText("MDC Vendor Probe", report));
        Toast.makeText(this, "Vendor probe copied", Toast.LENGTH_SHORT).show();
    }

    private void saveReport() {
        try {
            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS); if (dir == null) dir = getFilesDir();
            File f = new File(dir, "MDC-vendor-probe-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(report.getBytes(StandardCharsets.UTF_8)); }
            Toast.makeText(this, "Saved: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable t) { Toast.makeText(this, "Save failed: " + t.getClass().getSimpleName(), Toast.LENGTH_SHORT).show(); }
    }
}
