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
 static final String VERSION="1.0.1-internal-10";
 static final String[] PKGS={"com.tw.car","com.tw.carinfoservice","com.tw.service.xt","com.tw.carchoose","com.tw.service"};
 static final String[] CLASSES={"com.tw.car.MazdaPreference","com.tw.car.MazdaRaiseActivity","com.tw.car.MazdaFuleInfo","com.tw.car.MazdaVehicleInfoActivity","c.b.a.a","com.tw.service.xt.CommandService","com.tw.service.xt.aidl.ITWCommandAidl","com.tw.service.xt.aidl.ITWCommandCallbackAidl"};
 final ExecutorService io=Executors.newSingleThreadExecutor(); TextView out; String report="NOT RUN"; Uri lastZip; final StringBuilder liveLog=new StringBuilder(); BroadcastReceiver mazdaRx; boolean monitorOn=false;
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setBackgroundColor(Color.rgb(8,10,13));
  r.addView(t("MDC MAZDA CONTRACT LAB • "+VERSION,22,Color.WHITE));r.addView(t("Evidence sprint. READ-ONLY: no CAN/MCU writes.",14,Color.LTGRAY));
  r.addView(btn("1. MAZDA VENDOR DATA BRIDGE PROBE",v->vendorDataBridge()));
  r.addView(btn("2. SMART VENDOR REPORT (TXT + JSON)",v->smartReport()));
  r.addView(btn("3. DEEP MAZDA CONTROLLER PROBE",v->probe()));
  r.addView(btn("4. EXPORT VENDOR APKS TO DOWNLOAD/MDC",v->exportPublic()));
  r.addView(btn("5. SHARE LAST ZIP",v->share()));
  r.addView(btn("COPY REPORT",v->copy()));
  out=t("Internal-10: read-only vendor data bridge probe. No broadcasts, Binder commands, TWUtil writes, CAN or MCU writes are sent.",12,Color.rgb(185,220,185));ScrollView s=new ScrollView(this);s.addView(out);r.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);
 }
 TextView t(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(18,10,18,10);return v;}
 Button btn(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}


 void vendorDataBridge(){out.setText("Probing vendor Mazda data path…");io.submit(()->{StringBuilder b=new StringBuilder();b.append("MDC_VENDOR_DATA_BRIDGE=1\\nVERSION=").append(VERSION).append("\\nREAD_ONLY=true\\nCAN_WRITE=false\\nOEM_WRITE=false\\n");String[] cs={"com.tw.car.MazdaVehicleInfoActivity","com.tw.car.MazdaFuleInfo","com.tw.car.MazdaPreference","c.b.a.a","com.tw.service.xt.CommandService","com.tw.service.xt.aidl.ITWCommandAidl","com.tw.service.xt.aidl.ITWCommandCallbackAidl","android.tw.john.TWUtil"};for(String cn:cs){try{String p=cn.startsWith("com.tw.service")?"com.tw.service.xt":cn.startsWith("com.tw.car")||cn.equals("c.b.a.a")?"com.tw.car":null;if(p==null)reflect(b,cn,getClassLoader());else inspect(b,p,cn);}catch(Throwable e){b.append("PROBE_ERROR ").append(cn).append("=").append(e).append("\\n");}}b.append("\\nNOTE=No vendor methods invoked. This probe only resolves classes/members and APK evidence.\\n");report=b.toString();runOnUiThread(()->out.setText(report));});}

 void startPassiveMonitor(){if(monitorOn){out.setText("PASSIVE_MONITOR_ALREADY_RUNNING\n"+liveLog);return;}liveLog.append("MDC_PASSIVE_MAZDA_MONITOR=1\nVERSION=").append(VERSION).append("\nCAN_WRITE=false\nOEM_WRITE=false\n");
  mazdaRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){StringBuilder x=new StringBuilder();x.append("\nEVENT action=").append(i.getAction()).append(" package=").append(i.getPackage()).append("\n");Bundle e=i.getExtras();if(e==null)x.append("EXTRAS=<none>\n");else for(String k:e.keySet()){Object v;try{v=e.get(k);}catch(Throwable z){v="<error "+z+">";}x.append("EXTRA ").append(k).append("=").append(String.valueOf(v)).append(" type=").append(v==null?"null":v.getClass().getName()).append("\n");}liveLog.append(x);report=liveLog.toString();runOnUiThread(()->out.setText(report));}};
  IntentFilter q=new IntentFilter();q.addAction("ACTION_CAR_INFO_RECIEVE");q.addAction("CAR_RemainKON");q.addAction("CAR_WATER_TEMP");
  try{if(Build.VERSION.SDK_INT>=33)registerReceiver(mazdaRx,q,Context.RECEIVER_EXPORTED);else registerReceiver(mazdaRx,q);monitorOn=true;liveLog.append("STATUS=LISTENING\nACTIONS=ACTION_CAR_INFO_RECIEVE,CAR_RemainKON,CAR_WATER_TEMP\n");report=liveLog.toString();out.setText(report);}catch(Throwable e){report="PASSIVE_MONITOR_FAILED "+e;out.setText(report);}}

 void smartReport(){out.setText("Building smart vendor report…");io.submit(()->{StringBuilder b=new StringBuilder();
  b.append("MDC_SMART_VENDOR_SCHEMA=1\nVERSION=").append(VERSION).append("\nREAD_ONLY=true\nCAN_WRITE=false\nOEM_WRITE=false\n");
  b.append("TARGET=TS10/RZ-MZD05/Mazda3BK\n");
  reflect(b,"android.tw.john.TWUtil",getClassLoader());
  for(String cn:CLASSES){String p=cn.startsWith("com.tw.service")?"com.tw.service.xt":"com.tw.car";inspect(b,p,cn);}
  String[] keys={"Mazda","Fuel","Fule","Consumption","Remain","Range","Time","Clock","INFO","RESET","sendKeyCode","sendCarSettingsType","extendedInterface","ACTION_CAR_INFO","CAR_RemainKON","CAR_WATER_TEMP","TWUtil","RZC","mCanId","time_setting_key","fuel_info_key"};
  for(String p:PKGS){pkg(b,p);scanApkStrings(b,p,keys);}
  String json="{\n  \"version\":\""+VERSION+"\",\n  \"read_only\":true,\n  \"can_write\":false,\n  \"oem_write\":false,\n  \"targets\":[\"c.b.a.a\",\"android.tw.john.TWUtil\",\"com.tw.service.xt.CommandService\"],\n  \"evidence_keys\":[\"sendKeyCode\",\"sendCarSettingsType\",\"extendedInterface\",\"time_setting_key\",\"fuel_info_key\",\"ACTION_CAR_INFO_REQUEST\",\"ACTION_CAR_INFO_RECIEVE\"]\n}\n";
  try{String a=saveText("MDC_vendor_analysis-"+stamp()+".txt",b.toString());String j=saveText("MDC_command_map-"+stamp()+".json",json);report=b.toString();final String x="SMART_REPORT_OK\n"+a+"\n"+j+"\n\n"+report;runOnUiThread(()->out.setText(x));}catch(Throwable e){final String x="SMART_REPORT_FAILED "+e;report=x;runOnUiThread(()->out.setText(x));}
 });}
 String stamp(){return new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());}
 String saveText(String name,String data)throws Exception{ContentValues cv=new ContentValues();cv.put(MediaStore.Downloads.DISPLAY_NAME,name);cv.put(MediaStore.Downloads.MIME_TYPE,name.endsWith(".json")?"application/json":"text/plain");cv.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MDC");Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);if(u==null)throw new IOException("MediaStore insert null");try(OutputStream o=getContentResolver().openOutputStream(u)){o.write(data.getBytes("UTF-8"));}return "SAVED=Download/MDC/"+name+" URI="+u;}
 void scanApkStrings(StringBuilder b,String p,String[] keys){b.append("\nSTATIC_STRING_SCAN=").append(p).append("\n");try{ApplicationInfo ai=getPackageManager().getApplicationInfo(p,0);byte[] raw=readLimited(ai.sourceDir,16*1024*1024);String s=new String(raw,"ISO-8859-1");for(String k:keys){int from=0,count=0;while(count<12){int i=s.indexOf(k,from);if(i<0)break;int lo=Math.max(0,i-90),hi=Math.min(s.length(),i+k.length()+140);String x=s.substring(lo,hi).replaceAll("[^\\x20-\\x7E]"," ");x=x.replaceAll(" +"," ");b.append("HIT[").append(k).append("]=").append(x).append("\n");from=i+k.length();count++;}}}catch(Throwable e){b.append("SCAN_ERROR=").append(e).append("\n");}}
 byte[] readLimited(String path,int max)throws Exception{try(InputStream in=new FileInputStream(path);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] q=new byte[65536];int n,total=0;while((n=in.read(q))>0&&total<max){int w=Math.min(n,max-total);o.write(q,0,w);total+=w;}return o.toByteArray();}}

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
 protected void onDestroy(){if(monitorOn&&mazdaRx!=null)try{unregisterReceiver(mazdaRx);}catch(Throwable ignored){}io.shutdownNow();super.onDestroy();}
 static class DigestOutputStream extends FilterOutputStream{final MessageDigest d;DigestOutputStream(OutputStream o,MessageDigest d){super(o);this.d=d;}public void write(int b)throws IOException{out.write(b);d.update((byte)b);}public void write(byte[] b,int o,int l)throws IOException{out.write(b,o,l);d.update(b,o,l);}}
}