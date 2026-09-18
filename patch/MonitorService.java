package com.monitoringcenter.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MonitorService extends Service {
    public static final String ACTION_START = "mc.START";
    public static final String ACTION_SILENCE = "mc.SILENCE";
    public static final String ACTION_REMOTE = "mc.REMOTE";
    public static final String EXTRA_ACTION = "remote_action";
    public static final String EXTRA_ID = "remote_id";
    private static final String CH_STATUS = "monitor_status";
    private static final String CH_ALERT = "monitor_alerts";
    private ScheduledExecutorService executor;
    private SharedPreferences prefs;
    private int failures = 0;

    @Override public void onCreate() {
        super.onCreate(); prefs=getSharedPreferences("mc",MODE_PRIVATE); createChannels(); startForeground(100,statusNotification("Подключение к ПК…",false));
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        ensureExecutor();
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_SILENCE.equals(action)){executor.execute(()->{postSilence();pollOnce();});return START_STICKY;}
        if(ACTION_REMOTE.equals(action)){
            String ra=intent.getStringExtra(EXTRA_ACTION), id=intent.getStringExtra(EXTRA_ID);
            executor.execute(()->{postAction(ra,id);try{Thread.sleep(500);}catch(Exception ignored){}pollOnce();}); return START_STICKY;
        }
        return START_STICKY;
    }

    private synchronized void ensureExecutor(){
        if(executor==null||executor.isShutdown()){
            executor=Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(this::pollOnce,0,2,TimeUnit.SECONDS);
        }
    }

    private void pollOnce() {
        String base=normalizedBase(), token=prefs.getString("token","").trim();
        if(base.isEmpty()||token.isEmpty()){updateStatus("Не настроено",false);return;}
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(base+"/api/v1/state").openConnection(); c.setConnectTimeout(2500);c.setReadTimeout(3500);c.setRequestMethod("GET");c.setRequestProperty("X-Monitoring-Token",token);
            int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);String json=readAll(c.getInputStream());JSONObject root=new JSONObject(json);
            failures=0;prefs.edit().putString("last_state",json).putLong("last_ok",System.currentTimeMillis()).putString("last_error","").apply();handleEvents(root.optJSONArray("events"));
            boolean alarm=root.optBoolean("alarm_active",false);String pc=root.optString("pc_name","ПК");updateStatus((alarm?"ТРЕВОГА · ":"Подключено · ")+pc,alarm);
        }catch(Exception e){failures++;prefs.edit().putString("last_error",e.getMessage()==null?e.toString():e.getMessage()).apply();updateStatus("Нет связи с ПК"+(failures>1?" · "+failures:""),false);if(failures==3)notifyConnectionLost();}
        finally{if(c!=null)c.disconnect();}
    }

    private void handleEvents(JSONArray a)throws JSONException{
        if(a==null||a.length()==0)return;String last=prefs.getString("last_event_key","");String newest=eventKey(a.getJSONObject(0));if(last.isEmpty()){prefs.edit().putString("last_event_key",newest).apply();return;}if(newest.equals(last))return;
        ArrayList<JSONObject> fresh=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);String k=eventKey(e);if(k.equals(last))break;fresh.add(e);}Collections.reverse(fresh);for(JSONObject e:fresh)notifyEvent(e);prefs.edit().putString("last_event_key",newest).apply();
    }
    private String eventKey(JSONObject e){return e.optString("at")+"|"+e.optString("source_id")+"|"+e.optString("text");}

    private void notifyEvent(JSONObject e){
        String source=e.optString("source","Monitoring center"),text=e.optString("text","Сработала тревога");
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent sil=new Intent(this,MonitorService.class).setAction(ACTION_SILENCE);PendingIntent spi=PendingIntent.getService(this,7,sil,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CH_ALERT).setSmallIcon(R.drawable.ic_monitor).setContentTitle("ТРЕВОГА — "+source).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).setPriority(Notification.PRIORITY_MAX).addAction(new Notification.Action.Builder(null,"ВЫКЛЮЧИТЬ ТРЕВОГУ",spi).build()).build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.nanoTime()&0x7fffffff),n);
    }
    private void notifyConnectionLost(){Notification n=new Notification.Builder(this,CH_ALERT).setSmallIcon(R.drawable.ic_monitor).setContentTitle("Monitoring center — потеря связи").setContentText("Телефон не может подключиться к ПК. Проверь Wi‑Fi и Monitoring center.").setPriority(Notification.PRIORITY_HIGH).build();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(901,n);}

    private void postSilence(){postSimple("/api/v1/silence",null);}
    private void postAction(String action,String id){
        if(action==null||action.trim().isEmpty())return;
        try{JSONObject q=new JSONObject();q.put("Action",action);q.put("ID",id==null?"":id);postSimple("/api/v1/action",q.toString());}catch(Exception ignored){}
    }
    private void postSimple(String path,String body){
        HttpURLConnection c=null;try{String b=normalizedBase();if(b.isEmpty())return;c=(HttpURLConnection)new URL(b+path).openConnection();c.setConnectTimeout(2500);c.setReadTimeout(3000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("X-Monitoring-Token",prefs.getString("token",""));if(body!=null){c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.getOutputStream().write(bytes);}else c.getOutputStream().write(new byte[0]);int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);}catch(Exception e){prefs.edit().putString("last_error",e.getMessage()==null?e.toString():e.getMessage()).apply();}finally{if(c!=null)c.disconnect();}
    }

    private String normalizedBase(){String b=prefs.getString("base","").trim();if(b.endsWith("/"))b=b.substring(0,b.length()-1);return b;}
    private static String readAll(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toString(StandardCharsets.UTF_8.name());}
    private void updateStatus(String text,boolean alarm){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(100,statusNotification(text,alarm));}
    private Notification statusNotification(String text,boolean alarm){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CH_STATUS).setSmallIcon(R.drawable.ic_monitor).setContentTitle("Monitoring center").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).build();}
    private void createChannels(){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel st=new NotificationChannel(CH_STATUS,"Состояние Monitoring center",NotificationManager.IMPORTANCE_LOW);st.setDescription("Постоянное соединение с Monitoring center на ПК");nm.createNotificationChannel(st);NotificationChannel al=new NotificationChannel(CH_ALERT,"Тревоги Monitoring center",NotificationManager.IMPORTANCE_HIGH);al.setDescription("Срабатывания Grafana, Telegram и Mattermost");al.enableVibration(true);nm.createNotificationChannel(al);}
    @Override public void onDestroy(){if(executor!=null)executor.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
