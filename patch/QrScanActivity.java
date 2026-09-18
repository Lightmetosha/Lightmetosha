package com.monitoringcenter.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.*;
import java.util.*;

public class QrScanActivity extends Activity {
    private static final int REQ_CAMERA=9101;
    private DecoratedBarcodeView scanner;
    private boolean started=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{
            buildUi();
            if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
                startScanner();
            }else{
                requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
            }
        }catch(Throwable t){
            Toast.makeText(this,"Ошибка QR-сканера: "+t.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9,11,18));
        root.setPadding(dp(16),dp(18),dp(16),dp(18));

        TextView title=new TextView(this);
        title.setText("Сканирование QR");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        TextView hint=new TextView(this);
        hint.setText("Наведи камеру на QR-код в Monitoring center на ПК");
        hint.setTextColor(Color.rgb(160,168,188));
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.setMargins(0,dp(8),0,dp(14));
        root.addView(hint,hp);

        scanner=new DecoratedBarcodeView(this);
        scanner.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        root.addView(scanner,new LinearLayout.LayoutParams(-1,0,1));

        Button cancel=new Button(this);
        cancel.setText("ОТМЕНА");
        cancel.setTextColor(Color.WHITE);
        cancel.setBackgroundColor(Color.rgb(35,40,55));
        cancel.setOnClickListener(v->{setResult(RESULT_CANCELED);finish();});
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(48));
        cp.setMargins(0,dp(14),0,0);
        root.addView(cancel,cp);

        setContentView(root);
    }

    private void startScanner(){
        if(started||scanner==null)return;
        started=true;
        scanner.decodeSingle(new BarcodeCallback(){
            @Override public void barcodeResult(BarcodeResult result){
                if(result==null||result.getText()==null)return;
                Intent data=new Intent();
                data.putExtra("qr",result.getText());
                setResult(RESULT_OK,data);
                finish();
            }
            @Override public void possibleResultPoints(List<ResultPoint> points){}
        });
        scanner.resume();
    }

    @Override protected void onResume(){
        super.onResume();
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && scanner!=null){
            try{scanner.resume();}catch(Throwable ignored){}
        }
    }

    @Override protected void onPause(){
        if(scanner!=null){try{scanner.pause();}catch(Throwable ignored){}}
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_CAMERA){
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startScanner();
            else{
                Toast.makeText(this,"Без доступа к камере QR-код нельзя сканировать",Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}
