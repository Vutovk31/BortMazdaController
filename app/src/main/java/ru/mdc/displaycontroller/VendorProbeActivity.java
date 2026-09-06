package ru.mdc.displaycontroller;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class VendorProbeActivity extends Activity {
    private static final String VERSION = "1.0.1-internal-5";
    private static final String[] PACKAGES = {
            "com.tw.carinfoservice", "com.tw.service", "com.tw.coreservice", "com.tw.core",
            "com.tw.car", "com.tw.carchoose", "com.tw.jar1", "com.tw.keypad", "com.tw.service.xt"
    };
    private static final String[] EXPORT_PACKAGES = {
            "com.tw.car", "com.tw.carinfoservice", "com.tw.service.xt", "com.tw.carchoose", "com.tw.service"
    };
    private static final String[] KEYS = {
            "can", "canbus", "mcu", "car", "vehicle", "climate", "air", "temp", "fan", "display", "lcd",
            "screen", "mazda", "steer", "key", "trip", "fuel", "range", "clock", "info", "radio", "service",
            "broadcast", "intent", "raise", "rzc", "twutil", "commandservice", "aidl", "remain", "water"
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView output;
    private String report = "NOT RUN";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,10,13));
        root.addView(text("MDC MAZDA CONTRACT PROBE • " + VERSION, 22, Color.WHITE));
        root.addView(text("READ-ONLY inspection. No Binder transactions, broadcasts, CAN writes, MCU writes or device-node writes.", 14, Color.LTGRAY));
        root.addView(button("1. RUN FOCUSED MAZDA CONTRACT PROBE", v -> runFocusedProbe()));
        root.addView(button("2. OPEN STOCK MAZDA PREFERENCE", v -> openMazdaPreference()));
        root.addView(button("3. EXPORT VENDOR APKS TO DOWNLOAD/MDC", v -> exportVendorApks()));
        root.addView(button("RUN FULL READ-ONLY VENDOR PROBE", v -> runFullProbe()));
        root.addView(button("COPY REPORT", v -> copyReport()));
        root.addView(button("SAVE REPORT", v -> saveReport()));
        output = text("Focused target: com.tw.car Mazda classes + CarInfoService + CommandService/AIDL + TWUtil.", 12, Color.rgb(185,220,185));
        ScrollView sc = new ScrollView(this); sc.addView(output); root.addView(sc, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(18,10,18,10); return t;
    }
    private Button button(String s, android.view.View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setOnClickListener(l); return b; }

    private void runFocusedProbe() {
        output.setText("RUNNING focused Mazda contract probe…");
        io.submit(() -> {
            StringBuilder b = header("MDC_MAZDA_CONTRACT_PROBE_SCHEMA=1");
            reflectTwUtil(b);
            inspectClass(b, "com.tw.car", "com.tw.car.MazdaPreference");
            inspectClass(b, "com.tw.car", "com.tw.car.MazdaRaiseActivity");
            inspectClass(b, "com.tw.car", "com.tw.car.MazdaFuleInfo");
            inspectClass(b, "com.tw.car", "com.tw.car.MazdaVehicleInfoActivity");
            inspectClass(b, "com.tw.carinfoservice", "com.tw.carinfoservice.CarService");
            inspectClass(b, "com.tw.carinfoservice", "com.tw.carinfoservice.CarServiceAidl");
            inspectClass(b, "com.tw.carinfoservice", "com.tw.carinfoservice.CarServiceCallBack");
            inspectClass(b, "com.tw.service.xt", "com.tw.service.xt.CommandService");
            inspectClass(b, "com.tw.service.xt", "com.tw.service.xt.aidl.ITWCommandAidl");
            inspectClass(b, "com.tw.service.xt", "com.tw.service.xt.aidl.ITWCommandCallbackAidl");
            appendPackageSummary(b, "com.tw.car");
            appendPackageSummary(b, "com.tw.carinfoservice");
            appendPackageSummary(b, "com.tw.service.xt");
            report = limit(b.toString(), 120000);
            runOnUiThread(() -> output.setText(report));
        });
    }

    private void runFullProbe() {
        output.setText("RUNNING full probe…");
        io.submit(() -> {
            StringBuilder b = header("MDC_VENDOR_PROBE_SCHEMA=3");
            reflectTwUtil(b);
            PackageManager pm = getPackageManager();
            for (String pkg : PACKAGES) probePackage(pm, pkg, b);
            report = limit(b.toString(), 160000);
            runOnUiThread(() -> output.setText(report));
        });
    }

    private StringBuilder header(String schema) {
        StringBuilder b = new StringBuilder();
        b.append(schema).append("\nVERSION=").append(VERSION).append("\n");
        b.append("READ_ONLY=true\nCAN_WRITE=false\nUNKNOWN_BINDER_CALL=false\nUNKNOWN_BROADCAST_SEND=false\nDEVICE_NODE_WRITE=false\n");
        b.append("manufacturer=").append(Build.MANUFACTURER).append("\nmodel=").append(Build.MODEL).append("\nandroid=").append(Build.VERSION.RELEASE).append("\n");
        return b;
    }

    private void openMazdaPreference() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.tw.car", "com.tw.car.MazdaPreference"));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Open failed: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportVendorApks() {
        output.setText("EXPORTING vendor APK copies…");
        io.submit(() -> {
            String name = "MDC-vendor-apks-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".zip";
            String result;
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/MDC");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IllegalStateException("MediaStore insert returned null");
                try (OutputStream raw = getContentResolver().openOutputStream(uri); ZipOutputStream z = new ZipOutputStream(raw)) {
                    byte[] buf = new byte[32768];
                    for (String pkg : EXPORT_PACKAGES) {
                        try {
                            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                            File src = new File(ai.sourceDir);
                            z.putNextEntry(new ZipEntry(pkg + ".apk"));
                            try (FileInputStream in = new FileInputStream(src)) {
                                int n; while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                            }
                            z.closeEntry();
                        } catch (Throwable t) {
                            z.putNextEntry(new ZipEntry(pkg + "-ERROR.txt"));
                            z.write((t.getClass().getSimpleName() + ": " + safe(t.getMessage())).getBytes(StandardCharsets.UTF_8));
                            z.closeEntry();
                        }
                    }
                }
                result = "EXPORT_OK\nFile: Download/MDC/" + name + "\nPlease upload this ZIP to ChatGPT for offline static analysis.";
            } catch (Throwable t) {
                result = "EXPORT_FAILED " + t.getClass().getSimpleName() + ": " + safe(t.getMessage());
            }
            final String finalResult = result;
            report = finalResult;
            runOnUiThread(() -> { output.setText(finalResult); Toast.makeText(this, finalResult, Toast.LENGTH_LONG).show(); });
        });
    }

    private void reflectTwUtil(StringBuilder b) {
        b.append("\n====================\nTWUTIL_REFLECTION\n");
        try {
            Class<?> c = Class.forName("android.tw.john.TWUtil", false, getClassLoader());
            b.append("classFound=true\n");
            for (java.lang.reflect.Constructor<?> x : c.getDeclaredConstructors()) b.append("ctor=").append(x).append("\n");
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) b.append("method=").append(m).append("\n");
            for (java.lang.reflect.Field f : c.getDeclaredFields()) b.append("field=").append(f).append("\n");
        } catch (Throwable t) { b.append("classFound=false error=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n"); }
    }

    private void inspectClass(StringBuilder b, String pkg, String className) {
        b.append("\n====================\nCLASS_INSPECT=").append(className).append("\npackage=").append(pkg).append("\n");
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            File opt = new File(getCodeCacheDir(), "probe-" + pkg.replace('.', '_')); opt.mkdirs();
            DexClassLoader cl = new DexClassLoader(ai.sourceDir, opt.getAbsolutePath(), ai.nativeLibraryDir, getClassLoader());
            Class<?> c = Class.forName(className, false, cl);
            b.append("loaded=true modifiers=").append(java.lang.reflect.Modifier.toString(c.getModifiers())).append("\n");
            for (java.lang.reflect.Constructor<?> x : c.getDeclaredConstructors()) b.append("ctor=").append(x).append("\n");
            int n=0; for (java.lang.reflect.Method m : c.getDeclaredMethods()) { if (n++ >= 250) break; b.append("method=").append(m).append("\n"); }
            n=0; for (java.lang.reflect.Field f : c.getDeclaredFields()) { if (n++ >= 250) break; b.append("field=").append(f).append("\n"); }
            for (Class<?> x : c.getDeclaredClasses()) b.append("innerClass=").append(x.getName()).append("\n");
        } catch (Throwable t) {
            b.append("loaded=false error=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n");
        }
    }

    private void appendPackageSummary(StringBuilder b, String pkg) {
        b.append("\n====================\nPACKAGE_SUMMARY=").append(pkg).append("\n");
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES|PackageManager.GET_RECEIVERS|PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS|PackageManager.GET_PERMISSIONS|PackageManager.GET_META_DATA);
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            b.append("versionName=").append(pi.versionName).append(" uid=").append(ai.uid).append(" sourceReadable=").append(new File(ai.sourceDir).canRead()).append("\n");
            if (pi.activities != null) for (ActivityInfo x : pi.activities) b.append("activity=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
            if (pi.receivers != null) for (ActivityInfo x : pi.receivers) b.append("receiver=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
            if (pi.services != null) for (ServiceInfo x : pi.services) b.append("service=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
        } catch (Throwable t) { b.append("error=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n"); }
    }

    private void probePackage(PackageManager pm, String pkg, StringBuilder b) {
        b.append("\n====================\nPACKAGE=").append(pkg).append("\n");
        try {
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA;
            PackageInfo pi = pm.getPackageInfo(pkg, flags); ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            b.append("versionName=").append(pi.versionName).append("\nuid=").append(ai.uid).append("\nprocess=").append(ai.processName).append("\nsourceDir=").append(ai.sourceDir).append("\nsourceReadable=").append(new File(ai.sourceDir).canRead()).append("\n");
            if (pi.activities != null) for (ActivityInfo x : pi.activities) b.append("activity=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
            if (pi.receivers != null) for (ActivityInfo x : pi.receivers) b.append("receiver=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
            if (pi.services != null) for (ServiceInfo x : pi.services) b.append("service=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
            if (pi.providers != null) for (ProviderInfo x : pi.providers) b.append("provider=").append(x.name).append(" exported=").append(x.exported).append(" authority=").append(x.authority).append("\n");
            probeDexClasses(ai.sourceDir, b); probeApkStrings(ai.sourceDir, b);
        } catch (Throwable t) { b.append("probeError=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n"); }
    }

    private void probeDexClasses(String apk, StringBuilder b) {
        b.append("## dex classes matching OEM keywords\n"); int count=0; DexFile dex=null;
        try { dex=new DexFile(apk); Enumeration<String> e=dex.entries(); while(e.hasMoreElements()&&count<700){String name=e.nextElement(); if(relevant(name)){b.append("class=").append(name).append("\n");count++;}} b.append("classMatchCount=").append(count).append("\n"); }
        catch(Throwable t){b.append("dexClassScanError=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n");}
        finally { if(dex!=null) try{dex.close();}catch(Throwable ignored){} }
    }

    private void probeApkStrings(String apk, StringBuilder b) {
        b.append("## APK raw strings matching OEM keywords\n"); Set<String> hits=new LinkedHashSet<>();
        try(ZipFile z=new ZipFile(apk)){Enumeration<? extends ZipEntry> es=z.entries(); while(es.hasMoreElements()&&hits.size()<1200){ZipEntry ze=es.nextElement(); String n=ze.getName(); if(!(n.equals("AndroidManifest.xml")||(n.startsWith("classes")&&n.endsWith(".dex"))||n.endsWith(".xml")))continue; try(BufferedInputStream in=new BufferedInputStream(z.getInputStream(ze))){StringBuilder s=new StringBuilder(); int c; long read=0,cap=n.endsWith(".dex")?10_000_000L:2_000_000L; while((c=in.read())!=-1&&read++<cap&&hits.size()<1200){if(c>=32&&c<=126){s.append((char)c);if(s.length()>240)flushCandidate(s,hits);}else flushCandidate(s,hits);}flushCandidate(s,hits);}catch(Throwable ignored){}} for(String h:hits)b.append("str=").append(h).append("\n"); b.append("stringMatchCount=").append(hits.size()).append("\n");}
        catch(Throwable t){b.append("apkStringScanError=").append(t.getClass().getSimpleName()).append(":").append(safe(t.getMessage())).append("\n");}
    }

    private void flushCandidate(StringBuilder s, Set<String> hits){if(s.length()>=5){String x=s.toString().trim();if(x.length()>=5&&x.length()<=240&&relevant(x))hits.add(sanitize(x));}s.setLength(0);}
    private boolean relevant(String s){if(s==null)return false;String l=s.toLowerCase(Locale.US);for(String k:KEYS)if(l.contains(k))return true;return false;}
    private String sanitize(String s){return s.replaceAll("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}","REDACTED_MAC");}
    private String safe(String s){return s==null?"":sanitize(s.replace('\n',' '));}
    private String limit(String s,int max){return s.length()>max?s.substring(0,max)+"\n[TRUNCATED]":s;}

    private void copyReport(){ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("MDC Probe",report));Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();}
    private void saveReport(){try{File dir=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);if(dir==null)dir=getFilesDir();File f=new File(dir,"MDC-probe-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".txt");try(FileOutputStream o=new FileOutputStream(f)){o.write(report.getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,"Saved: "+f.getAbsolutePath(),Toast.LENGTH_LONG).show();}catch(Throwable t){Toast.makeText(this,"Save failed: "+t.getClass().getSimpleName(),Toast.LENGTH_SHORT).show();}}
}
