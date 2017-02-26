//package com.mobeymarker;
//
//import android.app.Service;
//import android.content.Intent;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Message;
//import android.os.Messenger;
//import android.support.annotation.Nullable;
//import android.util.Log;
//
//import com.google.android.gms.maps.model.LatLng;
//
//import java.util.ArrayList;
//
//public class bg extends Service {
//    ArrayList<LatLng> latlngs = new ArrayList<LatLng>();
//    final Messenger m0 = new Messenger(new h0);
//
//    class h0 extends Handler {
//        @Override
//        public void handleMessage(Message m){
//            switch (m.what) {
//
//            }
//        }
//    }
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    @Override
//    public void onCreate(){
//        Log.i("XXX", "===== m0 - onCreate() =====");
//    }
//
//    @Override
//    public void onDestroy(){
//        Log.i("XXX", "===== m0 - onDestroy() =====");
//    }
//
//    @Override
//    public IBinder
//    }
//}
