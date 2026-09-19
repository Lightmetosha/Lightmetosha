package com.monitoringcenter.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int REQ_QR=7001;
    private final int BG=Color.rgb(9,11,18), CARD=Color.rgb(20,24,36), TEXT=Color.rgb(245,247,255), MUTED=Color.rgb(143,152,172), ACCENT=Color.rgb(124,92,255), ACCENT2=Color.rgb(49,215,255), GOOD=Color.rgb(55,214,122), BAD=Color.rgb(255,73,106), WARN=Color.rgb(255,184,77);
    private SharedPreferences prefs;
    private EditText base, token;
    private TextView conn;
    private LinearLayout monitors, events, summaryPage, eventsPage;
    private Button tabSummary, tabEvents;
    private Handler handler=new Handler(Looper.getMainLooper());
    private Runnable refreshTask=new Runnable(){public void run(){renderState();handler.postDelayed(this,1000);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("mc",MODE_PRIVATE);
        buildUi();
        requestNotificationPermission();
        handler.post(refreshTask);
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refreshTask);super.onDestroy();}

    private void buildUi(){
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(20),dp(18),dp(30)); sc.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView title=txt("Monitoring center",26,TEXT,true); root.addView(title);
        TextView sub=txt("Удалённая панель",12,MUTED,false); sub.setPadding(0,dp(2),0,dp(14)); root.addView(sub);

        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,0,0,dp(16));
        tabSummary=button("СВОДКА",ACCENT); tabEvents=button("СОБЫТИЯ",Color.rgb(35,40,55));
        tabSummary.setOnClickListener(v->showPage(false)); tabEvents.setOnClickListener(v->showPage(true));
        tabs.addView(tabSummary,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(44),1); ep.setMargins(dp(8),0,0,0); tabs.addView(tabEvents,ep); root.addView(tabs);

        summaryPage=new LinearLayout(this); summaryPage.setOrientation(LinearLayout.VERTICAL); root.addView(summaryPage,new LinearLayout.LayoutParams(-1,-2));
        eventsPage=new LinearLayout(this); eventsPage.setOrientation(LinearLayout.VERTICAL); eventsPage.setVisibility(View.GONE); root.addView(eventsPage,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout setup=card(); summaryPage.addView(setup); setup.addView(txt("Подключение к ПК",15,TEXT,true));
        base=input("http://192.168.1.50:8765"); base.setText(prefs.getString("base","")); setup.addView(field("Адрес",base));
        token=input("Токен"); token.setText(prefs.getString("token","")); setup.addView(field("Токен",token));

        LinearLayout pairButtons=new LinearLayout(this); pairButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button qr=button("СКАНИРОВАТЬ QR",Color.rgb(35,40,55)); qr.setOnClickListener(v->scanQr()); pairButtons.addView(qr,new LinearLayout.LayoutParams(0,dp(46),1));
        Button connect=button("ПОДКЛЮЧИТЬ",ACCENT); connect.setOnClickListener(v->connect()); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1); cp.setMargins(dp(8),0,0,0); pairButtons.addView(connect,cp); setup.addView(pairButtons);
        Button disconnect=button("ОТКЛЮЧИТЬСЯ ОТ ПК",Color.rgb(35,40,55)); disconnect.setOnClickListener(v->disconnect()); LinearLayout.LayoutParams dpb=new LinearLayout.LayoutParams(-1,dp(44)); dpb.setMargins(0,dp(8),0,0); setup.addView(disconnect,dpb);
        conn=txt("Не подключено",12,MUTED,false); conn.setPadding(0,dp(12),0,0); setup.addView(conn);

        LinearLayout global=card(); summaryPage.addView(global); global.addView(txt("Управление",15,TEXT,true));
        LinearLayout line1=new LinearLayout(this); line1.setOrientation(LinearLayout.HORIZONTAL);
        Button startAll=button("ЗАПУСТИТЬ ВСЕ",ACCENT); startAll.setOnClickListener(v->remoteAction("start_all","")); line1.addView(startAll,new LinearLayout.LayoutParams(0,dp(46),1));
        Button stopAll=button("ОСТАНОВИТЬ ВСЕ",Color.rgb(35,40,55)); stopAll.setOnClickListener(v->remoteAction("stop_all","")); LinearLayout.LayoutParams sap=new LinearLayout.LayoutParams(0,dp(46),1); sap.setMargins(dp(8),0,0,0); line1.addView(stopAll,sap); global.addView(line1);
        Button silence=button("ВЫКЛЮЧИТЬ ТРЕВОГУ НА ПК",BAD); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(46)); slp.setMargins(0,dp(8),0,0); global.addView(silence,slp); silence.setOnClickListener(v->silence());

        summaryPage.addView(section("Мониторинги")); monitors=new LinearLayout(this); monitors.setOrientation(LinearLayout.VERTICAL); summaryPage.addView(monitors);
        TextView hint=txt("Телефон и ПК должны быть в одной локальной сети. Фоновая служба держит соединение и присылает уведомления о новых тревогах.",11,MUTED,false); hint.setPadding(0,dp(12),0,0); summaryPage.addView(hint);

        eventsPage.addView(section("История событий")); events=new LinearLayout(this); events.setOrientation(LinearLayout.VERTICAL); eventsPage.addView(events);
        TextView eHint=txt("Здесь отображаются последние события, которые Monitoring center хранит на ПК.",11,MUTED,false); eHint.setPadding(0,dp(8),0,0); eventsPage.addView(eHint);

        setContentView(sc);
    }

    private void showPage(boolean eventsTab){
        summaryPage.setVisibility(eventsTab?View.GONE:View.VISIBLE);
        eventsPage.setVisibility(eventsTab?View.VISIBLE:View.GONE);
        tabSummary.setBackground(round(eventsTab?Color.rgb(35,40,55):ACCENT,13,eventsTab?Color.rgb(35,40,55):ACCENT));
        tabEvents.setBackground(round(eventsTab?ACCENT:Color.rgb(35,40,55),13,eventsTab?ACCENT:Color.rgb(35,40,55)));
    }

    private void scanQr(){
        try{
            Intent i=new Intent(this,QrScanActivity.class);
            startActivityForResult(i,REQ_QR);
        }catch(Exception e){
            Toast.makeText(this,"Не удалось открыть камеру: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode==REQ_QR){
            if(resultCode==RESULT_OK && data!=null){
                String raw=data.getStringExtra("qr");
                if(raw!=null && !raw.trim().isEmpty()){applyConnectionString(raw);connect();}
            }else{
                Toast.makeText(this,"Сканирование отменено",Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    private void applyConnectionString(String raw){
        String v=raw==null?"":raw.trim(); if(v.isEmpty())return;
        String b=v,t="";
        if(v.contains("|")){String[] p=v.split("\\|",2);b=p[0].trim();if(p.length>1)t=p[1].trim();}
        if(!b.startsWith("http://")&&!b.startsWith("https://"))b="http://"+b;
        while(b.endsWith("/"))b=b.substring(0,b.length()-1);
        base.setText(b); if(!t.isEmpty())token.setText(t);
        prefs.edit().putString("base",b).putString("token",token.getText().toString().trim()).putString("last_event_key","").apply();
        Toast.makeText(this,"QR считан",Toast.LENGTH_SHORT).show();
    }

    private void connect(){
        String b=base.getText().toString().trim(); String t=token.getText().toString().trim();
        if(b.contains("|")){String[] parts=b.split("\\|",2);b=parts[0].trim();if(t.isEmpty()&&parts.length>1)t=parts[1].trim();}
        if(!b.startsWith("http://")&&!b.startsWith("https://"))b="http://"+b; while(b.endsWith("/"))b=b.substring(0,b.length()-1);
        prefs.edit().putString("base",b).putString("token",t).putString("last_event_key","").putBoolean("manual_disconnected",false).apply(); base.setText(b); token.setText(t);
        Intent i=new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_START); startSvc(i); Toast.makeText(this,"Подключение запущено",Toast.LENGTH_SHORT).show();
    }
    private void disconnect(){
        prefs.edit().putBoolean("manual_disconnected",true).putLong("last_ok",0).putString("last_error","").apply();
        Intent i=new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_DISCONNECT);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        conn.setText("Отключено от ПК"); conn.setTextColor(MUTED);
        Toast.makeText(this,"Соединение с ПК остановлено",Toast.LENGTH_SHORT).show();
    }
    private void silence(){ Intent i=new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_SILENCE); startSvc(i); Toast.makeText(this,"Команда отправлена",Toast.LENGTH_SHORT).show(); }
    private void remoteAction(String action,String id){
        Intent i=new Intent(this,MonitorService.class).setAction(MonitorService.ACTION_REMOTE).putExtra(MonitorService.EXTRA_ACTION,action).putExtra(MonitorService.EXTRA_ID,id); startSvc(i);
        Toast.makeText(this,action.startsWith("start")?"Запуск отправлен":"Остановка отправлена",Toast.LENGTH_SHORT).show();
    }
    private void startSvc(Intent i){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    private void renderState(){
        boolean manual=prefs.getBoolean("manual_disconnected",false);
        long ok=prefs.getLong("last_ok",0); String js=prefs.getString("last_state",""); String err=prefs.getString("last_error","");
        if(manual){conn.setText("Отключено от ПК");conn.setTextColor(MUTED);}
        else if(ok>0){long age=(System.currentTimeMillis()-ok)/1000; conn.setText(age<8?"● ПК подключён · обновлено "+age+" сек назад":"● Нет свежих данных · "+age+" сек");conn.setTextColor(age<8?GOOD:BAD);}
        else {conn.setText(err.isEmpty()?"Не подключено":"Ошибка: "+err);conn.setTextColor(MUTED);}
        if(js.isEmpty())return; try{JSONObject r=new JSONObject(js); renderMonitors(r); renderEvents(r.optJSONArray("events"));}catch(Exception ignored){}
    }
    private void renderMonitors(JSONObject root)throws JSONException{
        monitors.removeAllViews();
        JSONObject states=root.optJSONObject("states"); if(states==null)return;
        JSONArray defs=root.optJSONArray("monitors");
        if(defs==null){defs=new JSONArray(); JSONObject labels=root.optJSONObject("labels"); Iterator<String> it=states.keys(); while(it.hasNext()){String id=it.next();JSONObject d=new JSONObject();d.put("id",id);d.put("label",labels==null?id:labels.optString(id,id));defs.put(d);}}
        for(int i=0;i<defs.length();i++){
            JSONObject d=defs.optJSONObject(i); if(d==null)continue;
            final String id=d.optString("id",""); if(id.isEmpty())continue;
            String name=d.optString("label",id);
            JSONObject s=states.optJSONObject(id); if(s==null)s=new JSONObject(); boolean al=s.optBoolean("alert"),ready=s.optBoolean("ready"),running=s.optBoolean("running");
            LinearLayout c=card(); c.setBackground(round(al?Color.rgb(50,17,28):CARD,16,al?BAD:Color.rgb(43,48,65)));
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView n=txt(name,14,TEXT,true);head.addView(n,new LinearLayout.LayoutParams(0,-2,1));
            TextView pill=txt(al?"ТРЕВОГА":ready?"ГОТОВ":running?"ЗАПУСК":"СТОП",11,al?Color.rgb(255,190,204):ready?GOOD:running?WARN:MUTED,true);head.addView(pill);c.addView(head);
            TextView st=txt(s.optString("status",""),12,MUTED,false);st.setPadding(0,dp(10),0,0);c.addView(st);
            String le=s.optString("last_event","");if(!le.isEmpty()){TextView e=txt(s.optString("last_event_at","")+" · "+le,11,TEXT,false);e.setPadding(0,dp(8),0,0);c.addView(e);}
            LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setPadding(0,dp(10),0,0);
            Button start=button("СТАРТ",ACCENT); start.setOnClickListener(v->remoteAction("start",id)); controls.addView(start,new LinearLayout.LayoutParams(0,dp(40),1));
            Button stop=button("СТОП",Color.rgb(35,40,55)); stop.setOnClickListener(v->remoteAction("stop",id)); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(40),1);sp.setMargins(dp(8),0,0,0);controls.addView(stop,sp);c.addView(controls);
            monitors.addView(c);
        }
    }
    private void renderEvents(JSONArray a)throws JSONException{
        events.removeAllViews();if(a==null||a.length()==0){events.addView(txt("Событий пока нет",12,MUTED,false));return;}
        for(int i=0;i<Math.min(100,a.length());i++){JSONObject e=a.getJSONObject(i);LinearLayout c=card();TextView h=txt(e.optString("source",""),12,ACCENT2,true);c.addView(h);TextView body=txt(e.optString("at","")+"  "+e.optString("text",""),12,TEXT,false);body.setPadding(0,dp(6),0,0);c.addView(body);events.addView(c);}
    }

    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);}
    private TextView section(String s){TextView v=txt(s,14,TEXT,true);v.setPadding(0,dp(22),0,dp(10));return v;}
    private LinearLayout field(String label,EditText e){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(0,dp(12),0,0);x.addView(txt(label,11,MUTED,true));x.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));return x;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(85,94,112));e.setTextColor(TEXT);e.setTextSize(13);e.setSingleLine(true);e.setPadding(dp(13),0,dp(13),0);e.setBackground(round(Color.rgb(8,10,17),12,Color.rgb(47,52,70)));return e;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(round(CARD,17,Color.rgb(43,48,65)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));c.setLayoutParams(lp);return c;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setPadding(dp(8),0,dp(8),0);b.setBackground(round(color,13,color));return b;}
    private TextView txt(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(0,1.1f);return t;}
    private GradientDrawable round(int fill,int r,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(r));g.setStroke(dp(1),stroke);return g;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}
