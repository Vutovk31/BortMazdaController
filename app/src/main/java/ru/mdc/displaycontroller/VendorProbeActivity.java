package ru.mdc.displaycontroller;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.*;
import android.view.View;
import android.widget.*;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class VendorProbeActivity extends Activity {
  static final String VERSION="1.0.1-internal-6";
  static final String[] PKGS={"com.tw.car","com.tw.carinfoservice","com.tw.service.xt","com.tw.carchoose","com.tw.service"};
  static final String[] CLASSES={"com.tw.car.MazdaPreference","com.tw.car.MazdaRaiseActivity","com.tw.car.MazdaFuleInfo","com.tw.car.MazdaVehicleInfoActivity","c.b.a.a","com.tw.service.xt.CommandService","com.tw.service.xt.aidl.ITWCommandAidl","com.tw.service.xt.aidl.ITWCommandCallbackAidl"};
  final ExecutorService io=Executors.newSingleThreadExecutor(); TextView out; String report="NOT RUN";
  public void onCreate(Bundle b){super.onCreate(b); LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setBackgroundColor(Color.rgb(8,10,13));
    r.addView(t("MDC MAZDA DATA LAB • "+VERSION,22,Color.WHITE));
    r.addView(t("Goal: expose the data already decoded by the stock Mazda app. READ-ONLY: no CAN/MCU writes.",14,Color.LTGRAY));
    r.addView(btn("1. LIVE MAZDA DATA MIRROR",v->openStock("com.tw.car.MazdaVehicleInfoActivity")));
    r.addView(btn("2. STOCK FUEL INFO",v->openStock("com.tw.car.MazdaFuleInfo")));
    r.addView(btn("3. STOCK TIME SETTING",v->openStock("com.tw.car.MazdaPreference")));
    r.addView(btn("4. DEEP MAZDA CONTROLLER PROBE",v->probe()));
    r.addView(btn("5. EXPORT VENDOR APKS (APP STORAGE)",v->export()));
    r.addView(btn("COPY REPORT",v->copy()));
    out=t("Ready. Buttons 1–3 open the proven stock Mazda screens; button 4 maps the controller contract; button 5 fixes the failed MediaStore export by using MDC app storage.",12,Color.rgb(185,220,185)); ScrollView s=new ScrollView(this);s.addView(out);r.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);
  }
  TextView t(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(18,10,18,10);return v;}
  Button btn(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}
  void openStock(String cls){try{Intent i=new Intent().setComponent(new ComponentName("com.tw.car",cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Throwable e){Toast.makeText(this,"Stock screen unavailable: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}}
  void probe(){out.setText("Probing…");io.submit(()->{StringBuilder b=new StringBuilder("MDC_MAZDA_DATA_PROBE_SCHEMA=2\nVERSION="+VERSION+"\nREAD_ONLY=true\nCAN_WRITE=false\n"); reflect(b,"android.tw.john.TWUtil",getClassLoader()); for(String c:CLASSES){String p=c.startsWith("com.tw.service")?"com.tw.service.xt":"com.tw.car"; inspect(b,p,c);} for(String p:PKGS) pkg(b,p); report=b.toString();runOnUiThread(()->out.setText(report));});}
  void inspect(StringBuilder b,String pkg,String cn){b.append("\nCLASS=").append(cn).append("\n");try{ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);File od=new File(getCodeCacheDir(),"i6-"+pkg.replace('.','_'));od.mkdirs();ClassLoader cl=new DexClassLoader(ai.sourceDir,od.getAbsolutePath(),ai.nativeLibraryDir,getClassLoader());reflect(b,cn,cl);}catch(Throwable e){b.append("ERROR=").append(e).append("\n");}}
  void reflect(StringBuilder b,String cn,ClassLoader cl){try{Class<?> c=Class.forName(cn,false,cl);b.append("LOADED=true modifiers=").append(Modifier.toString(c.getModifiers())).append("\n");for(Constructor<?> x:c.getDeclaredConstructors())b.append("CTOR=").append(x).append("\n");for(Method m:c.getDeclaredMethods())b.append("METHOD=").append(m).append("\n");for(Field f:c.getDeclaredFields())b.append("FIELD=").append(f).append("\n");for(Class<?> x:c.getDeclaredClasses())b.append("INNER=").append(x.getName()).append("\n");}catch(Throwable e){b.append("LOADED=false ").append(e.getClass().getSimpleName()).append(":").append(e.getMessage()).append("\n");}}
  void pkg(StringBuilder b,String p){try{ApplicationInfo ai=getPackageManager().getApplicationInfo(p,0);b.append("\nPACKAGE=").append(p).append("\nUID=").append(ai.uid).append("\nSOURCE=").append(ai.sourceDir).append("\nREADABLE=").append(new File(ai.sourceDir).canRead()).append("\n");}catch(Throwable e){b.append("\nPACKAGE=").append(p).append(" ERROR=").append(e).append("\n");}}
  void export(){out.setText("Exporting…");io.submit(()->{String res;try{File base=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(base==null)base=getFilesDir();File dir=new File(base,"MDC");dir.mkdirs();File zip=new File(dir,"MDC-vendor-apks-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".zip");byte[] buf=new byte[65536];try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(zip))){for(String p:PKGS){try{ApplicationInfo ai=getPackageManager().getApplicationInfo(p,0);File f=new File(ai.sourceDir);z.putNextEntry(new ZipEntry(p+".apk"));try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(buf))>0)z.write(buf,0,n);}z.closeEntry();}catch(Throwable e){z.putNextEntry(new ZipEntry(p+"-ERROR.txt"));z.write(e.toString().getBytes());z.closeEntry();}}}res="EXPORT_OK\n"+zip.getAbsolutePath()+"\nSIZE="+zip.length()+"\nSHA256="+sha(zip);}catch(Throwable e){res="EXPORT_FAILED "+e;}report=res;final String x=res;runOnUiThread(()->{out.setText(x);Toast.makeText(this,x,Toast.LENGTH_LONG).show();});});}
  String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}
  void copy(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MDC",report));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}
  protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}