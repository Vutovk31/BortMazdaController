package ru.mdc.displaycontroller;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT = 31;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long GESTURE_MS = 420L;
    private static final String[] PAGES = {"OEM", "RETRO", "DASHBOARD", "DIAGNOSTICS", "SETTINGS"};
    private static final String[] DISCOVERY_KEYS = {"can","canbus","mcu","vehicle","carservice","carinfo","steer","raise","rzc","mazda","ts10","topway","fyt","syu","radio"};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> events = new ArrayList<>();
    private final Telemetry telemetry = new Telemetry();
    private final Map<String,BluetoothDevice> btCandidates = new LinkedHashMap<>();

    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean polling;
    private BluetoothDevice pendingBondDevice;
    private boolean receiverRegistered;

    private String obdStage="IDLE", transport="NONE", elmIdentity="UNKNOWN", ecuState="NOT_REACHED", supportedPids="UNKNOWN", lastError="";
    private String discoverySummary="NOT RUN", carInfoSummary="NOT RUN", lastSteering="NONE";
    private long tripLastMs;
    private double tripKm,tripFuelL;
    private long lastUpAt=-1,lastDownAt=-1;
    private Runnable pendingUp,pendingDown;
    private boolean replaying;
    private int pageIndex=2;

    private LinearLayout content;
    private TextView status;
    private TextView steeringChip;
    private Button obdTopButton;

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(action) && d != null) {
                addBtCandidate(d);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action) && d != null) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (pendingBondDevice != null && sameDevice(d,pendingBondDevice)) {
                    if (state == BluetoothDevice.BOND_BONDED) {
                        log("BT_BONDED " + safeDevice(d));
                        BluetoothDevice target = pendingBondDevice;
                        pendingBondDevice = null;
                        toast("Сопряжение готово. Подключаю ELM…");
                        connectObd(target);
                    } else if (state == BluetoothDevice.BOND_NONE) {
                        log("BT_BOND_FAILED " + safeDevice(d));
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                log("BT_SCAN_FINISHED candidates="+btCandidates.size());
                showBluetoothPicker(false);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        registerBtReceiver();
        buildShell();
        log("APP_START safety="+BuildConfig.SAFETY_PROFILE+" CAN_WRITE="+BuildConfig.CAN_WRITE);
        ensureBluetoothPermission();
        showPage(2);
    }

    @Override protected void onDestroy(){
        polling=false;
        closeSocket();
        if(receiverRegistered){try{unregisterReceiver(btReceiver);}catch(Exception ignored){}}
        io.shutdownNow();
        super.onDestroy();
    }

    private void registerBtReceiver(){
        IntentFilter f=new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btReceiver,f);
        receiverRegistered=true;
    }

    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1);}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setPadding(18,10,18,10);return t;}
    private Button action(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);b.setMinHeight(62);return b;}
    private Button nav(String s,View.OnClickListener l){Button b=action(s,l);b.setTextSize(14);return b;}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,10,13));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        status=text("MDC 1.0.1 internal-2 • OBD IDLE • CAN WRITE OFF",15,Color.LTGRAY);
        top.addView(status,new LinearLayout.LayoutParams(0,58,1));
        steeringChip=text("STEERING: --",13,Color.rgb(255,180,90));
        steeringChip.setGravity(Gravity.CENTER);
        top.addView(steeringChip,new LinearLayout.LayoutParams(220,58));
        obdTopButton=action("OBD / ELM",v->openBluetoothLab());
        top.addView(obdTopButton,new LinearLayout.LayoutParams(210,58));
        root.addView(top);

        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc=new ScrollView(this);
        sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout n=new LinearLayout(this);
        for(int i=0;i<PAGES.length;i++){
            final int idx=i;
            n.addView(nav(PAGES[i],v->showPage(idx)),weight());
        }
        root.addView(n,new LinearLayout.LayoutParams(-1,72));
        setContentView(root);
    }

    private void clear(String title){content.removeAllViews();TextView h=text(title,30,Color.WHITE);h.setGravity(Gravity.CENTER_HORIZONTAL);content.addView(h);}
    private void showPage(int idx){pageIndex=(idx+PAGES.length)%PAGES.length;switch(pageIndex){case 0:showOem();break;case 1:showRetro();break;case 2:showDashboard();break;case 3:showDiagnostics();break;default:showSettings();}}

    private void showOem(){
        clear("OEM COMPUTER");
        content.addView(text("OEM bridge пока READ-ONLY/LOCKED: протокол команд штатного БК ещё не подтверждён.",17,Color.rgb(255,150,100)));
        content.addView(text("LOCKED_UNVALIDATED_PROTOCOL = кнопка известна по функции Mazda, но MDC намеренно НЕ отправляет непроверенную команду в автомобиль.",15,Color.LTGRAY));
        content.addView(action("INFO — OEM protocol not validated",v->blocked("INFO_NEXT")));
        content.addView(action("CLOCK — Android clock settings",v->{log("CLOCK_LOCAL_SETTINGS");try{startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));}catch(Exception e){toast("Настройки часов Android недоступны");}}));
        content.addView(action("RESET TRIP — MDC only",v->new AlertDialog.Builder(this).setTitle("Сбросить локальный MDC Trip?").setMessage("Сбросит только расчёты MDC. OEM/CAN команда не отправляется.").setPositiveButton("RESET",(d,w)->{tripKm=0;tripFuelL=0;tripLastMs=0;log("MDC_TRIP_RESET");showOem();}).setNegativeButton("Отмена",null).show()));
        content.addView(action("SET — OEM protocol not validated",v->blocked("SET")));
        content.addView(action("TIME + — OEM protocol not validated",v->blocked("TIME_PLUS")));
        content.addView(action("TIME − — OEM protocol not validated",v->blocked("TIME_MINUS")));
        content.addView(text("Найден TS10-компонент com.tw.carinfoservice. Его read-only структура анализируется в Diagnostics.",15,Color.LTGRAY));
    }

    private void showRetro(){
        clear("RETRO");
        content.addView(text("Локальная ретро-панель MDC. OEM-команды не маскируются под рабочие.",16,Color.LTGRAY));
        LinearLayout r=new LinearLayout(this);
        r.addView(action("INFO 🔒",v->blocked("INFO_NEXT")),weight());
        r.addView(action("CLOCK",v->{try{startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));}catch(Exception e){toast("Clock settings unavailable");}}),weight());
        r.addView(action("RESET MDC",v->{tripKm=0;tripFuelL=0;tripLastMs=0;log("MDC_TRIP_RESET_RETRO");toast("MDC Trip reset");}),weight());
        r.addView(action("SET 🔒",v->blocked("SET")),weight());
        content.addView(r);
        LinearLayout m=new LinearLayout(this);
        m.addView(action("◀ PREV MEDIA",v->replayMedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS)),weight());
        m.addView(action("NEXT MEDIA ▶",v->replayMedia(KeyEvent.KEYCODE_MEDIA_NEXT)),weight());
        content.addView(m);
        content.addView(text("Двойной UP/DOWN на руле теперь реально переключает экраны MDC. Одинарный UP/DOWN отправляет штатный Android media previous/next.",15,Color.LTGRAY));
    }

    private void showDashboard(){
        clear("DASHBOARD");
        content.addView(text(metric("SPEED",telemetry.speed,"km/h")+"     "+metric("RPM",telemetry.rpm,"rpm"),24,Color.WHITE));
        content.addView(text(metric("COOLANT",telemetry.coolant,"°C")+"     "+metric("VOLT",telemetry.voltage,"V"),22,Color.WHITE));
        content.addView(text(metric("MAF",telemetry.maf,"g/s")+"     "+metric("FUEL",telemetry.fuelPct,"%"),20,Color.WHITE));
        content.addView(text(String.format(Locale.US,"TRIP %.2f km     FUEL %.3f L     AVG %s",tripKm,tripFuelL,tripKm>.2?String.format(Locale.US,"%.1f L/100",tripFuelL/tripKm*100):"--"),20,Color.WHITE));
        content.addView(text("OBD state: "+obdStage+" • transport: "+transport+" • error: "+(lastError.isEmpty()?"--":lastError),15,Color.LTGRAY));
        content.addView(action("OBD / ELM CONNECTION LAB",v->openBluetoothLab()));
        content.addView(action("REFRESH DASHBOARD",v->showDashboard()));
    }

    private String metric(String n,Double v,String unit){return n+" "+(v==null?"--":String.format(Locale.US,v%1==0?"%.0f":"%.1f",v))+" "+unit;}

    private void showDiagnostics(){
        clear("DIAGNOSTICS");
        content.addView(text("OBD: "+obdStage+"\nTransport: "+transport+"\nELM: "+elmIdentity+"\nECU: "+ecuState+"\nPIDs: "+supportedPids+"\nLast error: "+lastError,16,Color.WHITE));
        content.addView(text("Steering: UP=88 / DOWN=87 • double=420 ms\nLast steering: "+lastSteering+"\nCAN_WRITE=false",15,Color.LTGRAY));
        content.addView(action("OBD / ELM CONNECTION LAB",v->openBluetoothLab()));
        content.addView(action("ANALYZE com.tw.carinfoservice (READ-ONLY)",v->analyzeCarInfoService()));
        content.addView(action("RUN READ-ONLY TS10 DISCOVERY",v->runDiscovery()));
        content.addView(text("CarInfoService:\n"+carInfoSummary,14,Color.LTGRAY));
        content.addView(text("TS10 discovery:\n"+discoverySummary,14,Color.LTGRAY));
        content.addView(action("COPY SUPPORT REPORT",v->copyReport()));
        content.addView(action("SAVE SUPPORT REPORT",v->saveReport()));
    }

    private void showSettings(){
        clear("SETTINGS");
        content.addView(text("Vehicle: Mazda 3 BK 1.6 facelift\nHead unit: TS10S / UIS7862A\nCAN box: RZ-MZD05\nSafety: "+BuildConfig.SAFETY_PROFILE,18,Color.WHITE));
        content.addView(text("OBD flow: paired devices → active Classic Bluetooth scan → pairing → secure SPP → insecure RFCOMM SPP fallback.",15,Color.LTGRAY));
        content.addView(action("OPEN OBD / ELM CONNECTION LAB",v->openBluetoothLab()));
        content.addView(action("OPEN ANDROID BLUETOOTH SETTINGS",v->openBtSettings()));
        content.addView(action("RUN DISCOVERY",v->runDiscovery()));
    }

    private void blocked(String c){log("OEM_COMMAND_BLOCKED "+c);toast(c+": LOCKED_UNVALIDATED_PROTOCOL");}

    private boolean hasBtPermissions(){
        if(Build.VERSION.SDK_INT>=31){
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    private void ensureBluetoothPermission(){if(Build.VERSION.SDK_INT>=31&&!hasBtPermissions())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},REQ_BT);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_BT){if(hasBtPermissions())toast("Bluetooth permissions OK");else fail("BT_PERMISSION_DENIED");}}

    private void openBluetoothLab(){
        if(!hasBtPermissions()){ensureBluetoothPermission();return;}
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(a==null){fail("BT_ADAPTER_UNAVAILABLE");return;}
        if(!a.isEnabled()){
            new AlertDialog.Builder(this).setTitle("Bluetooth выключен").setMessage("Включи Bluetooth магнитолы и повтори.").setPositiveButton("ВКЛЮЧИТЬ",(d,w)->{try{startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));}catch(Exception e){openBtSettings();}}).setNegativeButton("ОТМЕНА",null).show();
            return;
        }
        btCandidates.clear();
        try{Set<BluetoothDevice> bonded=a.getBondedDevices();if(bonded!=null)for(BluetoothDevice d:bonded)addBtCandidate(d);}catch(Exception e){log("BT_BONDED_ENUM_ERROR "+e.getClass().getSimpleName());}
        showBluetoothPicker(true);
    }

    private void showBluetoothPicker(boolean initial){
        if(isFinishing())return;
        List<BluetoothDevice> ds=new ArrayList<>(btCandidates.values());
        String[] labels=new String[ds.size()+3];
        labels[0]="🔎 СКАНИРОВАТЬ Bluetooth устройства";
        labels[1]="⚙ Открыть настройки Bluetooth TS10";
        labels[2]="ℹ Если ELM есть только внутри Car Scanner — всё равно запусти СКАН";
        for(int i=0;i<ds.size();i++){
            BluetoothDevice d=ds.get(i);
            String bond=d.getBondState()==BluetoothDevice.BOND_BONDED?"PAIRED":"FOUND";
            labels[i+3]=safeName(d)+"  • "+bond+"  • "+redactMac(d.getAddress());
        }
        new AlertDialog.Builder(this).setTitle(initial?"OBD / ELM CONNECTION LAB":"Найденные устройства: "+ds.size()).setItems(labels,(dialog,which)->{
            if(which==0){startBtScan();return;}
            if(which==1){openBtSettings();return;}
            if(which==2){toast("Запусти скан и выбери ELM/OBD из списка");return;}
            BluetoothDevice d=ds.get(which-3);
            if(d.getBondState()==BluetoothDevice.BOND_BONDED)connectObd(d);else pairThenConnect(d);
        }).setNegativeButton("Закрыть",null).show();
    }

    private void startBtScan(){
        if(!hasBtPermissions()){ensureBluetoothPermission();return;}
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(a==null){fail("BT_ADAPTER_UNAVAILABLE");return;}
        try{
            if(a.isDiscovering())a.cancelDiscovery();
            boolean ok=a.startDiscovery();
            log("BT_SCAN_START ok="+ok);
            toast(ok?"Сканирование ~12 секунд…":"TS10 не запустил стандартный Bluetooth scan");
            if(!ok)openBtSettings();
        }catch(Exception e){fail("BT_SCAN_FAILED_"+e.getClass().getSimpleName());openBtSettings();}
    }

    private void pairThenConnect(BluetoothDevice d){
        try{
            pendingBondDevice=d;
            boolean started=d.createBond();
            log("BT_CREATE_BOND "+safeDevice(d)+" started="+started);
            if(started)toast("Подтверди сопряжение ELM на магнитоле. После pairing подключение продолжится автоматически.");
            else {pendingBondDevice=null;toast("TS10 не разрешил стандартное pairing. Открываю настройки Bluetooth.");openBtSettings();}
        }catch(Exception e){pendingBondDevice=null;fail("BT_PAIR_FAILED_"+e.getClass().getSimpleName());openBtSettings();}
    }

    private void openBtSettings(){try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}catch(Exception ignored){toast("Настройки Bluetooth недоступны");}}}
    private void addBtCandidate(BluetoothDevice d){try{String a=d.getAddress();if(a!=null)btCandidates.put(a,d);}catch(Exception ignored){}}
    private boolean sameDevice(BluetoothDevice a,BluetoothDevice b){try{return a.getAddress().equals(b.getAddress());}catch(Exception e){return false;}}
    private String safeName(BluetoothDevice d){try{String n=d.getName();return n==null||n.trim().isEmpty()?"Unknown Bluetooth":n;}catch(Exception e){return "Unknown Bluetooth";}}
    private String safeDevice(BluetoothDevice d){return safeName(d)+"/"+redactMac(d.getAddress());}
    private String redactMac(String a){if(a==null)return "--";String[] p=a.split(":");return p.length==6?p[0]+":"+p[1]+":**:**:**:"+p[5]:"REDACTED";}

    private void connectObd(BluetoothDevice device){
        polling=false;closeSocket();
        obdStage="TRANSPORT_CONNECT";ecuState="NOT_REACHED";lastError="";updateStatus();
        io.submit(()->{
            try{
                BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null&&a.isDiscovering())a.cancelDiscovery();
                BluetoothSocket s;
                try{
                    transport="SPP_SECURE";logBg("OBD_TRANSPORT_ATTEMPT SPP_SECURE "+safeDevice(device));
                    s=device.createRfcommSocketToServiceRecord(SPP_UUID);s.connect();
                }catch(Exception first){
                    logBg("SPP_SECURE_FAILED "+first.getClass().getSimpleName());
                    transport="SPP_INSECURE";
                    s=device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);s.connect();
                }
                socket=s;in=s.getInputStream();out=s.getOutputStream();
                obdStage="ELM_HANDSHAKE";updateStatusBg();elmHandshake();
                obdStage="ECU_PROBE";updateStatusBg();
                String p=elm("0100",4500);
                if(p.contains("UNABLETOCONNECT")||p.contains("NODATA")){ecuState="UNAVAILABLE";throw new Exception("ECU_UNABLE_TO_CONNECT: "+p);}
                ecuState="CONNECTED";supportedPids=p;obdStage="STREAMING";updateStatusBg();polling=true;tripLastMs=SystemClock.elapsedRealtime();
                pollLoop();
            }catch(Exception e){
                lastError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();obdStage="FAILED";logBg("OBD_FAILED "+lastError);updateStatusBg();closeSocket();
            }
        });
    }

    private void elmHandshake() throws Exception{
        String[] cs={"ATZ","ATE0","ATL0","ATS0","ATH0","ATSP0","ATAT1"};
        for(String c:cs){String r=elm(c,c.equals("ATZ")?4500:2200);logBg("ELM "+c+" => "+clip(r));if(c.equals("ATZ")&&(r.isEmpty()||r.contains("TIMEOUT")))throw new Exception("ELM_NO_PROMPT");}
        try{elmIdentity=elm("ATI",1800);}catch(Exception ignored){}
    }

    private void pollLoop() throws Exception{
        while(polling&&socket!=null&&socket.isConnected()){
            long st=SystemClock.elapsedRealtime();
            telemetry.rpm=parsePid(elm("010C",1800),0x0C);
            telemetry.speed=parsePid(elm("010D",1800),0x0D);
            telemetry.coolant=parsePid(elm("0105",1800),0x05);
            telemetry.maf=parsePid(elm("0110",1800),0x10);
            telemetry.fuelPct=parsePid(elm("012F",1800),0x2F);
            Double df=parsePid(elm("015E",1800),0x5E);
            telemetry.fuelRate=df!=null?df:deriveFuelRate(telemetry.maf);
            telemetry.voltage=parseVoltage(elm("ATRV",1800));
            integrateTrip();
            runOnUiThread(()->{updateStatus();if(pageIndex==2)showDashboard();});
            SystemClock.sleep(Math.max(100,1000-(SystemClock.elapsedRealtime()-st)));
        }
    }

    private String elm(String command,long timeout)throws Exception{
        if(out==null||in==null)throw new Exception("TRANSPORT_NOT_CONNECTED");
        while(in.available()>0)in.read();
        out.write((command+"\r").getBytes(StandardCharsets.US_ASCII));out.flush();
        long dl=SystemClock.elapsedRealtime()+timeout;StringBuilder b=new StringBuilder();
        while(SystemClock.elapsedRealtime()<dl){while(in.available()>0){int x=in.read();if(x<0)throw new Exception("TRANSPORT_EOF");char c=(char)x;if(c=='>')return normalize(b.toString());b.append(c);}SystemClock.sleep(15);}throw new Exception("ELM_TIMEOUT_"+command);
    }
    private static String normalize(String s){return s.toUpperCase(Locale.US).replace(" ","").replace("\r","").replace("\n","");}
    private static String clip(String s){return s.length()>80?s.substring(0,80):s;}

    private Double parsePid(String r,int pid){
        try{String mark=String.format(Locale.US,"41%02X",pid);int i=r.indexOf(mark);if(i<0)return null;String d=r.substring(i+4);switch(pid){case 0x0C:return Integer.parseInt(d.substring(0,4),16)/4.0;case 0x0D:return (double)Integer.parseInt(d.substring(0,2),16);case 0x05:return Integer.parseInt(d.substring(0,2),16)-40.0;case 0x10:return Integer.parseInt(d.substring(0,4),16)/100.0;case 0x2F:return Integer.parseInt(d.substring(0,2),16)*100.0/255.0;case 0x5E:return Integer.parseInt(d.substring(0,4),16)/20.0;}}catch(Exception ignored){}return null;
    }
    private Double parseVoltage(String r){try{String x=r.replace("V","");int i=0;while(i<x.length()&&!(Character.isDigit(x.charAt(i))||x.charAt(i)=='.'))i++;int j=i;while(j<x.length()&&(Character.isDigit(x.charAt(j))||x.charAt(j)=='.'))j++;return Double.parseDouble(x.substring(i,j));}catch(Exception e){return null;}}
    private Double deriveFuelRate(Double maf){return maf==null?null:maf*3600.0/(14.7*745.0);}
    private void integrateTrip(){long now=SystemClock.elapsedRealtime();if(tripLastMs==0){tripLastMs=now;return;}double h=(now-tripLastMs)/3600000.0;tripLastMs=now;if(h<=0||h>5.0/3600.0)return;if(telemetry.speed!=null)tripKm+=telemetry.speed*h;if(telemetry.fuelRate!=null)tripFuelL+=telemetry.fuelRate*h;}

    private void analyzeCarInfoService(){
        carInfoSummary="RUNNING";showDiagnostics();
        io.submit(()->{
            StringBuilder b=new StringBuilder("package=com.tw.carinfoservice\nREAD_ONLY=true\n");
            try{
                PackageManager pm=getPackageManager();
                int flags=PackageManager.GET_ACTIVITIES|PackageManager.GET_RECEIVERS|PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS|PackageManager.GET_PERMISSIONS|PackageManager.GET_META_DATA;
                PackageInfo pi=pm.getPackageInfo("com.tw.carinfoservice",flags);
                b.append("versionName=").append(pi.versionName).append("\n");
                appendActivities(b,"activity",pi.activities);
                appendActivities(b,"receiver",pi.receivers);
                appendServices(b,pi.services);
                appendProviders(b,pi.providers);
                if(pi.requestedPermissions!=null)for(String p:pi.requestedPermissions)if(matchesDiscovery(p))b.append("permission=").append(p).append("\n");
            }catch(Exception e){b.append("error=").append(e.getClass().getSimpleName()).append(":").append(e.getMessage()).append("\n");}
            carInfoSummary=limit(b.toString(),12000);logBg("CARINFOSERVICE_ANALYSIS chars="+carInfoSummary.length());runOnUiThread(this::showDiagnostics);
        });
    }
    private void appendActivities(StringBuilder b,String type,ActivityInfo[] xs){if(xs!=null)for(ActivityInfo x:xs)b.append(type).append("=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");}
    private void appendServices(StringBuilder b,ServiceInfo[] xs){if(xs!=null)for(ServiceInfo x:xs)b.append("service=").append(x.name).append(" exported=").append(x.exported).append(" permission=").append(x.permission).append("\n");}
    private void appendProviders(StringBuilder b,ProviderInfo[] xs){if(xs!=null)for(ProviderInfo x:xs)b.append("provider=").append(x.name).append(" exported=").append(x.exported).append(" authority=").append(x.authority).append(" readPerm=").append(x.readPermission).append(" writePerm=").append(x.writePermission).append("\n");}

    private void runDiscovery(){
        discoverySummary="RUNNING";showDiagnostics();
        io.submit(()->{
            StringBuilder b=new StringBuilder("MDC_TS10_DISCOVERY_SCHEMA=3\nREAD_ONLY=true\nCAN_WRITE=false\n");
            collectCommand(b,"getprop");collectCommand(b,"service list");collectCommand(b,"pm list packages");
            collectFiles(b,new File("/dev"));collectFiles(b,new File("/vendor/lib64"));collectFiles(b,new File("/vendor/lib"));collectFiles(b,new File("/system/lib64"));
            discoverySummary=limit(b.toString(),16000);logBg("DISCOVERY_COMPLETE chars="+discoverySummary.length());runOnUiThread(this::showDiagnostics);
        });
    }

    private void collectCommand(StringBuilder b,String cmd){
        b.append("\n## ").append(cmd).append("\n");
        try{Process p=Runtime.getRuntime().exec(cmd);java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));String line;int n=0;while((line=r.readLine())!=null&&n<800){if(matchesDiscovery(line))b.append(sanitizeLine(line)).append("\n");n++;}r.close();}catch(Exception e){b.append("error=").append(e.getClass().getSimpleName()).append("\n");}
    }
    private boolean matchesDiscovery(String s){if(s==null)return false;String l=s.toLowerCase(Locale.US);for(String k:DISCOVERY_KEYS)if(l.contains(k))return true;return false;}
    private String sanitizeLine(String s){if(s==null)return "";return s.replaceAll("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}","REDACTED_MAC");}
    private void collectFiles(StringBuilder b,File dir){
        b.append("\n## files ").append(dir.getAbsolutePath()).append("\n");
        try{File[] fs=dir.listFiles();if(fs==null)return;int n=0;for(File f:fs){if(n>=300)break;String name=f.getName();if(matchesDiscovery(name)){b.append(f.getAbsolutePath()).append(" readable=").append(f.canRead()).append("\n");n++;}}}catch(Exception e){b.append("error=").append(e.getClass().getSimpleName()).append("\n");}
    }
    private String limit(String s,int max){return s.length()>max?s.substring(0,max)+"\n[TRUNCATED]":s;}

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int k=e.getKeyCode();
        if((k==KeyEvent.KEYCODE_MEDIA_PREVIOUS||k==KeyEvent.KEYCODE_MEDIA_NEXT)&&!replaying){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)handleSteering(k);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void handleSteering(int key){
        long now=SystemClock.elapsedRealtime();
        if(key==KeyEvent.KEYCODE_MEDIA_PREVIOUS){
            if(lastUpAt>0&&now-lastUpAt<=GESTURE_MS&&pendingUp!=null){main.removeCallbacks(pendingUp);pendingUp=null;lastUpAt=-1;steeringDouble(true);return;}
            lastUpAt=now;pendingUp=()->{pendingUp=null;lastUpAt=-1;steeringSingle(KeyEvent.KEYCODE_MEDIA_PREVIOUS,true);};main.postDelayed(pendingUp,GESTURE_MS);
        }else{
            if(lastDownAt>0&&now-lastDownAt<=GESTURE_MS&&pendingDown!=null){main.removeCallbacks(pendingDown);pendingDown=null;lastDownAt=-1;steeringDouble(false);return;}
            lastDownAt=now;pendingDown=()->{pendingDown=null;lastDownAt=-1;steeringSingle(KeyEvent.KEYCODE_MEDIA_NEXT,false);};main.postDelayed(pendingDown,GESTURE_MS);
        }
    }
    private void steeringSingle(int key,boolean up){lastSteering=up?"SINGLE UP → MEDIA PREVIOUS":"SINGLE DOWN → MEDIA NEXT";log(up?"STEERING_SINGLE_UP_REPLAY":"STEERING_SINGLE_DOWN_REPLAY");updateSteeringChip();replayMedia(key);}
    private void steeringDouble(boolean up){lastSteering=up?"DOUBLE UP → PREVIOUS MDC PAGE":"DOUBLE DOWN → NEXT MDC PAGE";log(up?"STEERING_DOUBLE_UP":"STEERING_DOUBLE_DOWN");updateSteeringChip();showPage(pageIndex+(up?-1:1));toast(lastSteering);}
    private void updateSteeringChip(){if(steeringChip!=null)steeringChip.setText("STEERING: "+lastSteering.replace(" → ","\n"));}

    private void replayMedia(int key){
        try{replaying=true;AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));main.postDelayed(()->replaying=false,250);}catch(Exception e){replaying=false;log("STEERING_REPLAY_FAILED "+e.getClass().getSimpleName());toast("Media replay failed");}
    }

    private void updateStatus(){if(status!=null)status.setText("MDC 1.0.1 internal-2 • OBD "+obdStage+" • "+transport+" • CAN WRITE OFF");if(obdTopButton!=null)obdTopButton.setText(obdStage.equals("STREAMING")?"OBD ✓ STREAMING":"OBD / ELM");}
    private void updateStatusBg(){runOnUiThread(this::updateStatus);}
    private void fail(String e){lastError=e;obdStage="FAILED";transport="NONE";log(e);updateStatus();toast(e);}
    private void log(String s){synchronized(events){events.add(new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+" "+s);if(events.size()>500)events.remove(0);}}
    private void logBg(String s){log(s);}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    private void closeSocket(){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;in=null;out=null;}

    private String report(){
        StringBuilder b=new StringBuilder();
        b.append("MDC_SUPPORT_REPORT_SCHEMA=2\nVERSION=1.0.1-internal-2\nMDC_SAFETY_PROFILE=").append(BuildConfig.SAFETY_PROFILE).append("\nCAN_WRITE=").append(BuildConfig.CAN_WRITE).append("\nREAD_ONLY_DIAGNOSTICS=true\n\n");
        b.append("[DEVICE]\nmanufacturer=").append(Build.MANUFACTURER).append("\nmodel=").append(Build.MODEL).append("\nandroid=").append(Build.VERSION.RELEASE).append("\nsdk=").append(Build.VERSION.SDK_INT).append("\n\n");
        b.append("[VEHICLE]\nprofile=Mazda3_BK_1.6_facelift\nheadUnit=TS10S_UIS7862A\ncanBox=RZ-MZD05\n\n");
        b.append("[OBD]\nstage=").append(obdStage).append("\ntransport=").append(transport).append("\nerror=").append(lastError).append("\n\n");
        b.append("[ELM]\nidentity=").append(elmIdentity).append("\n\n[ECU]\nstate=").append(ecuState).append("\nsupportedPids=").append(supportedPids).append("\n\n");
        b.append("[STEERING]\nphysicalUpKey=88\nphysicalDownKey=87\ndoubleWindowMs=420\nlastEvent=").append(lastSteering).append("\n\n");
        b.append(String.format(Locale.US,"[TRIP]\ndistanceKm=%.3f\nfuelL=%.4f\n\n",tripKm,tripFuelL));
        b.append("[OEM]\nbridge=LOCKED\nINFO=LOCKED_UNVALIDATED_PROTOCOL\nCLOCK_OEM=LOCKED\nRESET_OEM=LOCKED\n\n");
        b.append("[CAR_INFO_SERVICE]\n").append(carInfoSummary).append("\n\n[DISCOVERY]\n").append(discoverySummary).append("\n\n");
        b.append("[SAFETY]\nCAN_WRITE=false\nOBD_WRITE=false\nOEM_WRITE=false\nUNKNOWN_BINDER_CALL=false\nUNKNOWN_BROADCAST_SEND=false\nDEVICE_NODE_WRITE=false\n\n[RECENT_EVENTS]\n");
        synchronized(events){for(String e:events)b.append(e).append("\n");}
        return b.toString();
    }
    private void copyReport(){ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("MDC Support Report",report()));toast("Support report copied");}
    private void saveReport(){try{File dir=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);if(dir==null)dir=getFilesDir();File f=new File(dir,"MDC-support-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".txt");try(FileOutputStream o=new FileOutputStream(f)){o.write(report().getBytes(StandardCharsets.UTF_8));}toast("Saved: "+f.getAbsolutePath());log("SUPPORT_REPORT_SAVED");}catch(Exception e){log("EXPORT_FAILED "+e.getClass().getSimpleName());toast("Save failed");}}

    private static class Telemetry{Double speed,rpm,coolant,voltage,maf,fuelPct,fuelRate;}
}
