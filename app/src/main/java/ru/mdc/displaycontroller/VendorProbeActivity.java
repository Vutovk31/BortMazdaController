package ru.mdc.displaycontroller;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
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
 static final String VERSION="1.0.1-internal-7";
 static final String[] PKGS={"com.tw.car","com.tw.carinfoservice","com.tw.service.xt","com.tw.carchoose","com.tw.service"};
 static final String[] CLASSES={"com.tw.car.MazdaPreference","com.tw.car.MazdaRaiseActivity","com.tw.car.MazdaFuleInfo","com.tw.car.MazdaVehicleInfoActivity","c.b.a.a","com.tw.service.xt.CommandService","com.tw.service.xt.aidl.ITWCommandAidl","com.tw.service.xt.aidl.ITWCommandCallbackAidl"};
 final ExecutorService io=Executors.newSingleThreadExecutor(); TextView out; String report="NOT RUN"; Uri lastZip;
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setBackgroundColor(Color.rgb(8,10,13));
  r.addView(t("MDC MAZDA CONTRACT LAB • "+VERSION,22,Color.WHITE));r.addView(t("Evidence sprint. READ-ONLY: no CAN/MCU writes.",14,Color.LTGRAY));
  r.addView(btn("1. DEEP MAZDA CONTROLLER PROBE",v->probe()));
  r.addView(btn("2. EXPORT VENDOR APKS TO DOWNLOAD/MDC",v->exportPublic()));
  r.addView(btn("3. SHARE LAST ZIP",v->share()));
  r.addView(btn("COPY REPORT",v->copy()));
  out=t("Run 1, then 2. Export goes to public Download/MDC and can be shared directly.",12,Color.rgb(185,220,185));ScrollView s=new ScrollView(this);s.addView(out);r.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);
 }
 TextView t(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(18,10,18,10);return v;}
 Button btn(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}
 void probe(){out.setText("Probing…");io.submit(()->{StringBuilder b=new StringBuilder("MDC_MAZDA_DATA_PROBE_SCHEMA=3\nVERSION="+VERSION+"\nREAD_ONLY=true\nCAN_WRITE=false\n");reflect(b,"android.tw.john.TWUtil",getClassLoader());for(String c:CLASSES){String p=c.startsWith("com.tw.service")?"com.tw.service.xt":"com.tw.car";inspect(b,p,c);}for(String p:PKGS)pkg(b,p);report=b.toString();runOnUiThread(()->out.setText(report));});}
 void inspect(StringBuilder b,String pkg,String cn){b.append("\nCLASS=").append(cn).append("\n");try{ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);File od=new File(getCodeCacheDir(),"i7-"+pkg.replace('.','_'));od.mkdirs();ClassLoader cl=new DexClassLoader(ai.sourceDir,od.getAbsolutePath(),ai.nativeLibraryDir,getClassLoader());reflect(b,cn,cl);}catch(Throwable e){b.append("ERROR=").append(e).append("\n");}}
 void reflect(StringBuilder b,String cn,ClassLoader cl){try{Class<?> c=Class.forName(cn,false,cl);b.append("LOADED=true modifiers=").append(Modifier.toString(c.getModifiers())).append("\n");for(Constructor<?> x:c.getDeclaredConstructors())b.append("CTOR=").append(x).append("\n");for(Method m:c.getDeclaredMethods())b.append("METHOD=").append(m).append("\n");for(Field f:c.getDeclaredFields())b.append("FIELD=").append(f).append("\n");}catch(Throwable e){b.append("LOADED=false ").append(e).append("\n");}}
 void pkg(StringBuilder b,String p){try{ApplicationInfo ai=getPackageManager().getApplicationInfo(p,0);b.append("\nPACKAGE=").append(p).append("\nUID=").append(ai.uid).append("\nSOURCE=").append(ai.sourceDir).append("\nREADABLE=").append(new File(ai.sourceDir).canRead()).append("\n");}catch(Throwable e){b.append("\nPACKAGE=").append(p).append(" ERROR=").append(e).append("\n");}}
 void exportPublic(){out.setText("Exporting to Download/MDC…");io.submit(()->{String res;try{
   String name="MDC-vendor-apks-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".zip";
   ContentValues cv=new ContentValues();cv.put(MediaStore.Downloads.DISPLAY_NAME,name);cv.put(MediaStore.Downloads.MIME_TYPE,"application/zip");cv.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MDC");cv.put(MediaStore.Downloads.IS_PENDING,1);
   Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);if(u==null)throw new IOException("MediaStore insert returned null");
   long size;MessageDigest md=MessageDigest.getInstance("SHA-256");try(OutputStream raw=getContentResolver().openOutputStream(u);DigestOutputStream dout=new DigestOutputStream(raw,md);ZipOutputStream z=new ZipOutputStream(dout)){byte[] buf=new byte[65536];for(String p:PKGS){try{ApplicationInfo ai=getPackageManager().getApplicationInfo(p,0);z.putNextEntry(new ZipEntry(p+".apk"));try(InputStream in=new FileInputStream(ai.sourceDir)){int n;while((n=in.read(buf))>0)z.write(buf,0,n);}z.closeEntry();}catch(Throwable e){z.putNextEntry(new ZipEntry(p+"-ERROR.txt"));z.write(e.toString().getBytes());z.closeEntry();}}}
   cv.clear();cv.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,cv,null,null);lastZip=u;size=querySize(u);res="EXPORT_OK\nLOCATION=Download/MDC/"+name+"\nURI="+u+"\nSIZE="+size+"\nSHA256="+hex(md.digest())+"\nTap SHARE LAST ZIP.";
  }catch(Throwable e){res="EXPORT_FAILED "+e;}report=res;final String x=res;runOnUiThread(()->out.setText(x));});}
 long querySize(Uri u){try(android.database.Cursor c=getContentResolver().query(u,new String[]{MediaStore.MediaColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst())return c.getLong(0);}return -1;}
 String hex(byte[] d){StringBuilder s=new StringBuilder();for(byte x:d)s.append(String.format(Locale.US,"%02x",x));return s.toString();}
 void share(){if(lastZip==null){Toast.makeText(this,"Export ZIP first",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/zip");i.putExtra(Intent.EXTRA_STREAM,lastZip);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share MDC vendor APK ZIP"));}
 void copy(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MDC",report));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}
 protected void onDestroy(){io.shutdownNow();super.onDestroy();}
 static class DigestOutputStream extends FilterOutputStream{final MessageDigest d;DigestOutputStream(OutputStream o,MessageDigest d){super(o);this.d=d;}public void write(int b)throws IOException{out.write(b);d.update((byte)b);}public void write(byte[] b,int o,int l)throws IOException{out.write(b,o,l);d.update(b,o,l);}}
}