package ru.mdc.displaycontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String VERSION="1.0.1-internal-3";
    private static final String CAR_PKG="com.tw.carinfoservice";
    private static final UUID SPP_UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long GESTURE_MS=420L;
    private static final String[] PAGES={"OEM","RETRO","DASHBOARD","DIAGNOSTICS","SETTINGS"};

    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<String> events=new ArrayList<>();
    private final List<String> snapshotA=new ArrayList<>();
    private final List<String> snapshotB=new ArrayList<>();

    private LinearLayout content;
    private TextView status,steeringChip;
    private int pageIndex=0;
    private String lastSteering="NONE";
    private long lastUpAt=-1,lastDownAt=-1;
    private Runnable pendingUp,pendingDown;
    private boolean replaying;

    private String carInfoSummary="NOT RUN";
    private String probeSummary="NOT RUN";
    private String diffSummary="NOT RUN";

    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private String obdStage="IDLE",transport="NONE",elmIdentity="UNKNOWN",ecuState="NOT_REACHED",lastError="";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildShell();
        log("APP_START version="+VERSION+" safety="+BuildConfig.SAFETY_PROFILE+" CAN_WRITE="+BuildConfig.CAN_WRITE);
        showPage(0);
    }

    @Override protected void onDestroy(){closeSocket();io.shutdownNow();super.onDestroy();}

    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1);}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setPadding(18,10,18,10);return t;}
    private Button action(String s,android.view.View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);b.setMinHeight(62);return b;}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,10,13));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        status=text("MDC "+VERSION+" • OEM DISPLAY RESTORE • CAN WRITE OFF",14,Color.LTGRAY);top.addView(status,new LinearLayout.LayoutParams(0,58,1));
        steeringChip=text("STEERING: --",12,Color.rgb(255,180,90));steeringChip.setGravity(Gravity.CENTER);top.addView(steeringChip,new LinearLayout.LayoutParams(260,58));
        top.addView(action("OEM PROBE",v->showPage(3)),new LinearLayout.LayoutParams(190,58));root.addView(top);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);ScrollView sc=new ScrollView(this);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);for(int i=0;i<PAGES.length;i++){final int x=i;nav.addView(action(PAGES[i],v->showPage(x)),weight());}root.addView(nav,new LinearLayout.LayoutParams(-1,72));
        setContentView(root);
    }

    private void clear(String title){content.removeAllViews();TextView h=text(title,29,Color.WHITE);h.setGravity(Gravity.CENTER_HORIZONTAL);content.addView(h);}
    private void showPage(int idx){pageIndex=(idx+PAGES.length)%PAGES.length;switch(pageIndex){case 0:showOem();break;case 1:showRetro();break;case 2:showDashboard();break;case 3:showDiagnostics();break;default:showSettings();}}

    private void showOem(){
        clear("OEM DISPLAY RESTORE");
        content.addView(text("Цель internal-3: найти штатный TS10 → RZ-MZD05 → Mazda display API. Непроверенные CAN/OEM команды по-прежнему НЕ отправляются.",17,Color.rgb(255,160,100)));
        content.addView(action("1. ANALYZE TS10 CarInfoService",v->analyzeCarInfoService()));
        content.addView(action("2. OPEN TS10 CarActivity",v->openVendorActivity("com.tw.carinfoservice.CarActivity")));
        content.addView(action("3. OPEN TS10 CarInfo MainActivity",v->openVendorActivity("com.tw.carinfoservice.permission.MainActivity")));
        content.addView(action("4. CAPTURE SNAPSHOT A",v->captureSnapshot(true)));
        content.addView(action("5. CAPTURE SNAPSHOT B + DIFF",v->captureSnapshot(false)));
        content.addView(text("Как пользоваться A/B: сделай Snapshot A → измени температуру/вентилятор/A-C на машине → Snapshot B. MDC покажет, какие car/can/climate состояния реально изменились.",15,Color.LTGRAY));
        content.addView(action("INFO 🔒 protocol not validated",v->blocked("INFO")));
        content.addView(action("CLOCK 🔒 protocol not validated",v->blocked("CLOCK")));
        content.addView(action("RESET 🔒 protocol not validated",v->blocked("RESET")));
        content.addView(action("SET 🔒 protocol not validated",v->blocked("SET")));
    }

    private void showRetro(){
        clear("STEERING CONTROL MAP");
        content.addView(text("Подтверждено на твоей TS10: key 88=UP, key 87=DOWN. Сейчас double UP/DOWN переключают страницы MDC.",17,Color.WHITE));
        content.addView(text("Целевой OEM UX после валидации протокола:\n• double UP → следующий режим штатного БК\n• double DOWN → предыдущий режим\n• triple UP → CLOCK edit\n• triple DOWN → RESET текущего показателя\n• в CLOCK edit VOL+/VOL− меняют значение, UP/DOWN выбирают часы/минуты.",16,Color.LTGRAY));
        content.addView(text("Пока OEM write locked: маршрутизация на красный дисплей включится только после доказанного vendor/API контракта.",15,Color.rgb(255,160,100)));
    }

    private void showDashboard(){
        clear("SECONDARY OBD");
        content.addView(text("OBD теперь вторичный контур. Красный OEM-дисплей — главный приоритет.",17,Color.WHITE));
        content.addView(text("OBD stage: "+obdStage+"\nTransport: "+transport+"\nELM: "+elmIdentity+"\nECU: "+ecuState+"\nError: "+lastError,16,Color.LTGRAY));
        content.addView(action("CONNECT TO PAIRED ELM",v->choosePairedElm()));
    }

    private void showDiagnostics(){
        clear("OEM / TS10 DIAGNOSTICS");
        content.addView(action("ANALYZE CarInfoService",v->analyzeCarInfoService()));
        content.addView(action("RUN DEEP READ-ONLY OEM PROBE",v->runDeepProbe()));
        content.addView(action("SNAPSHOT A",v->captureSnapshot(true)));
        content.addView(action("SNAPSHOT B + DIFF",v->captureSnapshot(false)));
        content.addView(text("[CAR INFO]\n"+carInfoSummary,14,Color.LTGRAY));
        content.addView(text("[DEEP PROBE]\n"+probeSummary,13,Color.LTGRAY));
        content.addView(text("[A/B DIFF]\n"+diffSummary,14,Color.rgb(180,230,180)));
        content.addView(action("COPY SUPPORT REPORT",v->copyReport()));
        content.addView(action("SAVE SUPPORT REPORT",v->saveReport()));
    }

    private void showSettings(){
        clear("SETTINGS");
        content.addView(text("Vehicle: Mazda 3 BK 1.6 facelift\nHead unit: TS10S / UIS7862A\nCAN box: RZ-MZD05\nCarInfoService: "+CAR_PKG+"\nSafety: "+BuildConfig.SAFETY_PROFILE,17,Color.WHITE));
        content.addView(text("Internal-3 не делает неизвестных Binder calls, broadcasts или CAN writes. Открытие exported vendor Activity — только запуск штатного интерфейса TS10 пользователем.",14,Color.LTGRAY));
    }

    private void blocked(String cmd){log("OEM_COMMAND_BLOCKED "+cmd);toast(cmd+": protocol not validated yet");}

    private void analyzeCarInfoService(){
        carInfoSummary="RUNNING";if(pageIndex==3)showDiagnostics();
        io.submit(()->{
            StringBuilder b=new StringBuilder("package="+CAR_PKG+"\nREAD_ONLY=true\n");
            try{
                PackageManager pm=getPackageManager();
                int flags=PackageManager.GET_ACTIVITIES|PackageManager.GET_RECEIVERS|PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS|PackageManager.GET_PERMISSIONS|PackageManager.GET_META_DATA;
                PackageInfo pi=pm.getPackageInfo(CAR_PKG,flags);
                b.append("versionName=").append(pi.versionName).append("\n");
                try{ApplicationInfo ai=pm.getApplicationInfo(CAR_PKG,PackageManager.GET_META_DATA);b.append("sourceDir=").append(ai.sourceDir).append("\n");b.append("process=").append(ai.processName).append("\n");if(ai.metaData!=null)for(String k:ai.metaData.keySet())b.append("meta.").append(k).append("=").append(ai.metaData.get(k)).append("\n");}catch(Exception ignored){}
                if(pi.activities!=null)for(ActivityInfo x:pi.activities)b.append("activity=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
                if(pi.receivers!=null)for(ActivityInfo x:pi.receivers)b.append("receiver=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");
                if(pi.services!=null)for(ServiceInfo x:pi.services)b.append("service=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append(" process=").append(x.processName).append("\n");
                if(pi.requestedPermissions!=null)for(String p:pi.requestedPermissions)b.append("permission=").append(p).append("\n");
            }catch(Exception e){b.append("error=").append(e).append("\n");}
            carInfoSummary=limit(b.toString(),18000);log("CARINFOSERVICE_ANALYSIS chars="+carInfoSummary.length());runOnUiThread(()->showPage(pageIndex));
        });
    }

    private void openVendorActivity(String cls){
        try{
            Intent i=new Intent();i.setComponent(new ComponentName(CAR_PKG,cls));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);log("OPEN_VENDOR_ACTIVITY "+cls);
        }catch(Exception e){log("OPEN_VENDOR_ACTIVITY_FAILED "+cls+" "+e.getClass().getSimpleName());toast("Не удалось открыть "+cls);}
    }

    private void runDeepProbe(){
        probeSummary="RUNNING";showDiagnostics();
        io.submit(()->{
            StringBuilder b=new StringBuilder("MDC_OEM_DISPLAY_PROBE_SCHEMA=1\nREAD_ONLY=true\nCAN_WRITE=false\n");
            appendCommand(b,"getprop",true);
            appendCommand(b,"service list",true);
            appendCommand(b,"pm list packages",true);
            appendCommand(b,"dumpsys package "+CAR_PKG,false);
            appendCommand(b,"dumpsys activity services "+CAR_PKG,false);
            appendCommand(b,"dumpsys activity broadcasts",true);
            appendCommand(b,"logcat -d -v time -t 1200",true);
            probeSummary=limit(b.toString(),30000);log("OEM_DEEP_PROBE chars="+probeSummary.length());runOnUiThread(this::showDiagnostics);
        });
    }

    private void captureSnapshot(boolean first){
        toast(first?"Снимаю Snapshot A…":"Снимаю Snapshot B и сравниваю…");
        io.submit(()->{
            List<String> dst=first?snapshotA:snapshotB;dst.clear();
            collectSnapshotCommand(dst,"getprop");
            collectSnapshotCommand(dst,"dumpsys activity services "+CAR_PKG);
            collectSnapshotCommand(dst,"dumpsys activity broadcasts");
            collectSnapshotCommand(dst,"logcat -d -v brief -t 500");
            if(first){diffSummary="Snapshot A captured: "+dst.size()+" relevant lines. Теперь измени климат/режим и нажми Snapshot B.";log("OEM_SNAPSHOT_A lines="+dst.size());}
            else {diffSummary=buildDiff(snapshotA,snapshotB);log("OEM_SNAPSHOT_B lines="+dst.size()+" diffChars="+diffSummary.length());}
            runOnUiThread(()->showPage(pageIndex));
        });
    }

    private void collectSnapshotCommand(List<String> outList,String cmd){
        try{
            Process p=Runtime.getRuntime().exec(new String[]{"sh","-c",cmd+" 2>&1"});BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;int n=0;
            while((line=r.readLine())!=null&&n<2000){if(relevant(line)){outList.add(cmd+" :: "+sanitize(line));n++;}}r.close();
        }catch(Exception e){outList.add(cmd+" :: ERROR "+e.getClass().getSimpleName());}
    }

    private String buildDiff(List<String> a,List<String> b){
        Set<String> aa=new LinkedHashSet<>(a),bb=new LinkedHashSet<>(b);StringBuilder s=new StringBuilder();
        for(String x:bb)if(!aa.contains(x))s.append("+ ").append(x).append("\n");
        for(String x:aa)if(!bb.contains(x))s.append("- ").append(x).append("\n");
        if(s.length()==0)s.append("NO_RELEVANT_CHANGE_DETECTED\n");return limit(s.toString(),16000);
    }

    private void appendCommand(StringBuilder b,String cmd,boolean filter){
        b.append("\n## ").append(cmd).append("\n");
        try{
            Process p=Runtime.getRuntime().exec(new String[]{"sh","-c",cmd+" 2>&1"});BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;int n=0;
            while((line=r.readLine())!=null&&n<2500){if(!filter||relevant(line))b.append(sanitize(line)).append("\n");n++;}r.close();
        }catch(Exception e){b.append("ERROR ").append(e.getClass().getSimpleName()).append(" ").append(e.getMessage()).append("\n");}
    }

    private boolean relevant(String s){if(s==null)return false;String l=s.toLowerCase(Locale.US);String[] ks={"carinfo","carservice","can","canbus","climate","aircon","air_condition","aircondition","ac_","a/c","temp","fan","vent","raise","rzc","mazda","mcu","tw.","trip","fuel","range","clock","info"};for(String k:ks)if(l.contains(k))return true;return false;}
    private String sanitize(String s){return s.replaceAll("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}","REDACTED_MAC");}
    private String limit(String s,int max){return s.length()>max?s.substring(0,max)+"\n[TRUNCATED]":s;}

    private void choosePairedElm(){
        try{
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!a.isEnabled()){toast("Bluetooth выключен");return;}Set<BluetoothDevice> set=a.getBondedDevices();if(set==null||set.isEmpty()){toast("Нет paired устройств");return;}List<BluetoothDevice> ds=new ArrayList<>(set);String[] labels=new String[ds.size()];for(int i=0;i<ds.size();i++)labels[i]=ds.get(i).getName();new AlertDialog.Builder(this).setTitle("Выбери ELM").setItems(labels,(d,w)->connectObd(ds.get(w))).show();
        }catch(Exception e){lastError=e.getClass().getSimpleName();toast("Bluetooth access failed");}
    }

    private void connectObd(BluetoothDevice d){
        closeSocket();obdStage="TRANSPORT_CONNECT";lastError="";showDashboard();
        io.submit(()->{
            try{
                BluetoothSocket s;try{transport="SPP_SECURE";s=d.createRfcommSocketToServiceRecord(SPP_UUID);s.connect();}catch(Exception e){transport="SPP_INSECURE";s=d.createInsecureRfcommSocketToServiceRecord(SPP_UUID);s.connect();}
                socket=s;in=s.getInputStream();out=s.getOutputStream();obdStage="ELM_HANDSHAKE";elmIdentity=elm("ATI",2500);obdStage="ECU_PROBE";String p=elm("0100",4000);ecuState=(p.contains("4100"))?"CONNECTED":"NO_DATA";obdStage=ecuState.equals("CONNECTED")?"STREAMING":"FAILED";log("OBD_RESULT "+obdStage+" transport="+transport+" elm="+elmIdentity);
            }catch(Exception e){obdStage="FAILED";lastError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();log("OBD_FAILED "+lastError);}runOnUiThread(this::showDashboard);
        });
    }

    private String elm(String c,long timeout)throws Exception{while(in.available()>0)in.read();out.write((c+"\r").getBytes(StandardCharsets.US_ASCII));out.flush();long dl=SystemClock.elapsedRealtime()+timeout;StringBuilder b=new StringBuilder();while(SystemClock.elapsedRealtime()<dl){while(in.available()>0){int x=in.read();if(x<0)throw new Exception("EOF");char ch=(char)x;if(ch=='>')return b.toString().trim();b.append(ch);}SystemClock.sleep(15);}throw new Exception("TIMEOUT_"+c);}
    private void closeSocket(){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;in=null;out=null;}

    @Override public boolean dispatchKeyEvent(KeyEvent e){int k=e.getKeyCode();if((k==KeyEvent.KEYCODE_MEDIA_PREVIOUS||k==KeyEvent.KEYCODE_MEDIA_NEXT)&&!replaying){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)handleSteering(k);return true;}return super.dispatchKeyEvent(e);}
    private void handleSteering(int key){long now=SystemClock.elapsedRealtime();if(key==KeyEvent.KEYCODE_MEDIA_PREVIOUS){if(lastUpAt>0&&now-lastUpAt<=GESTURE_MS&&pendingUp!=null){main.removeCallbacks(pendingUp);pendingUp=null;lastUpAt=-1;steeringDouble(true);return;}lastUpAt=now;pendingUp=()->{pendingUp=null;lastUpAt=-1;steeringSingle(key,true);};main.postDelayed(pendingUp,GESTURE_MS);}else{if(lastDownAt>0&&now-lastDownAt<=GESTURE_MS&&pendingDown!=null){main.removeCallbacks(pendingDown);pendingDown=null;lastDownAt=-1;steeringDouble(false);return;}lastDownAt=now;pendingDown=()->{pendingDown=null;lastDownAt=-1;steeringSingle(key,false);};main.postDelayed(pendingDown,GESTURE_MS);}}
    private void steeringSingle(int key,boolean up){lastSteering=up?"SINGLE UP → MEDIA PREVIOUS":"SINGLE DOWN → MEDIA NEXT";log(lastSteering);updateSteering();replayMedia(key);}
    private void steeringDouble(boolean up){lastSteering=up?"DOUBLE UP → PREVIOUS MDC PAGE":"DOUBLE DOWN → NEXT MDC PAGE";log(lastSteering);updateSteering();showPage(pageIndex+(up?-1:1));}
    private void updateSteering(){if(steeringChip!=null)steeringChip.setText("STEERING: "+lastSteering.replace(" → ","\n"));}
    private void replayMedia(int key){try{replaying=true;AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));main.postDelayed(()->replaying=false,250);}catch(Exception e){replaying=false;}}

    private String report(){StringBuilder b=new StringBuilder();b.append("MDC_SUPPORT_REPORT_SCHEMA=3\nVERSION=").append(VERSION).append("\nMDC_SAFETY_PROFILE=").append(BuildConfig.SAFETY_PROFILE).append("\nCAN_WRITE=false\nREAD_ONLY_DIAGNOSTICS=true\n\n");b.append("[DEVICE]\nmanufacturer=").append(Build.MANUFACTURER).append("\nmodel=").append(Build.MODEL).append("\nandroid=").append(Build.VERSION.RELEASE).append("\nsdk=").append(Build.VERSION.SDK_INT).append("\n\n");b.append("[VEHICLE]\nprofile=Mazda3_BK_1.6_facelift\nheadUnit=TS10S_UIS7862A\ncanBox=RZ-MZD05\n\n");b.append("[CAR_INFO_SERVICE]\n").append(carInfoSummary).append("\n\n[OEM_DEEP_PROBE]\n").append(probeSummary).append("\n\n[OEM_SNAPSHOT_DIFF]\n").append(diffSummary).append("\n\n[OBD]\nstage=").append(obdStage).append("\ntransport=").append(transport).append("\nelm=").append(elmIdentity).append("\necu=").append(ecuState).append("\nerror=").append(lastError).append("\n\n[STEERING]\nlastEvent=").append(lastSteering).append("\n\n[SAFETY]\nCAN_WRITE=false\nOEM_WRITE=false\nUNKNOWN_BINDER_CALL=false\nUNKNOWN_BROADCAST_SEND=false\nDEVICE_NODE_WRITE=false\n\n[RECENT_EVENTS]\n");synchronized(events){for(String e:events)b.append(e).append("\n");}return b.toString();}
    private void copyReport(){ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("MDC Support Report",report()));toast("Support report copied");}
    private void saveReport(){try{File dir=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);if(dir==null)dir=getFilesDir();File f=new File(dir,"MDC-internal3-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".txt");try(FileOutputStream o=new FileOutputStream(f)){o.write(report().getBytes(StandardCharsets.UTF_8));}toast("Saved: "+f.getAbsolutePath());}catch(Exception e){toast("Save failed");}}
    private void log(String s){synchronized(events){events.add(new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+" "+s);if(events.size()>500)events.remove(0);}}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
}
