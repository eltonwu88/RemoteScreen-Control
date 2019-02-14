package com.example.eltonwu.remotescreen;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

@SuppressLint("DefaultLocale")
public class MainActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = "TESTR";

    private SurfaceView mRemoteScreen;
    private int         mRemoteScreenWidth = 1080;
    private int         mRemoteScreenHeight= 1920;
    private HandlerThread mRenderThread;
    private HandlerThread mControlSenderThread;
    private ControlThread mControlThread;
    private ConnectThread mConnectThread;
    private Handler       mHandler;
    private Handler       mControlHandler;
    private GestureDetector mGestureDetector;

    private String mConnectIPAdrres;

    private static final int MSG_DRAW = 1000;
    private Handler.Callback mRenderCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(msg.what == MSG_DRAW){
                int    len  = msg.arg1;
                byte[] rgba = (byte[]) msg.obj;
                drawScreen(rgba,len);
            }
            return true;
        }
    };
    private static final int MSG_RC = 2000;
    private Handler.Callback mControlCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(msg.what == MSG_RC){
                String cmd = (String) msg.obj;
                synchronized (TAG){
                    if(mControlThread != null){
                        if(mControlThread.mSender != null){
                            mControlThread.mSender.println(cmd);
                        }
                    }
                }
            }
            return true;
        }
    };


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if(intent != null){
            String ipaddr = intent.getStringExtra("ipaddr");
            if(ipaddr != null){
                mConnectIPAdrres = ipaddr;
            }else{
                finish();
            }
        }else{
            finish();
        }

        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        mRemoteScreen = findViewById(R.id.remoteView);
        mRemoteScreen.setOnTouchListener(this);
        mGestureDetector = new GestureDetector(this,mGestureListener);
        mConnectThread = new ConnectThread();
        mConnectThread.start();
        mControlThread = new ControlThread();
        mControlThread.start();

        mRenderThread = new HandlerThread("MY Render Thread");
        mRenderThread.start();
        mControlSenderThread = new HandlerThread("Sender Thread");
        mControlSenderThread.start();
        mHandler       = new Handler(mRenderThread.getLooper(),mRenderCallback);
        mControlHandler= new Handler(mControlSenderThread.getLooper(),mControlCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mRenderThread != null){
            mRenderThread.quit();
        }

        if(mControlSenderThread != null){
            mControlSenderThread.quit();
        }

        if(mConnectThread != null){
            mConnectThread.interrupt();
            mConnectThread.isRunning = false;
            try {
                mConnectThread.join();
            } catch (InterruptedException ignored) {}
        }

        if(mControlThread != null){
            mControlThread.interrupt();
            try {
                mControlThread.mSocket.close();
            } catch (IOException ignored) {}
            mControlThread.isRunning = false;
            try {
                mControlThread.join();
            } catch (InterruptedException ignored) {}
            synchronized (TAG){
                mControlThread = null;
            }
        }
    }

    private void rcTap(int x,int y){
        String cmd = String.format("input tap %d %d",x,y);
        Message message = Message.obtain();
        message.what = MSG_RC;
        message.obj  = cmd;
        mControlHandler.sendMessage(message);
    }
    private void rcScroll(int x1,int y1,int x2, int y2){
//        String debug = String.format("input swipe {%d %d} -- {%d,%d}",x1,y1,x2,y2);
//        Log.i(TAG,debug);
        String cmd = String.format("input swipe %d %d %d %d",x1,y1,x2,y2);
        Message message = Message.obtain();
        message.what = MSG_RC;
        message.obj  = cmd;
        mControlHandler.sendMessage(message);
    }

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            SurfaceHolder holder;
            if((holder = mRemoteScreen.getHolder()) != null){
                float X = e.getX();
                float Y = e.getY();
                float ratio = mRemoteScreenWidth / (holder.getSurfaceFrame().width() * 1.0f);
                int rX= (int) (X * ratio);
                int rY= (int) (Y * ratio);
                rcTap(rX,rY);
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//            Log.i(TAG,"e1:"+MotionEvent.actionToString(e1.getAction()) + " e2:"+MotionEvent.actionToString(e2.getAction()));
//            SurfaceHolder holder;
//            if((holder = mRemoteScreen.getHolder()) != null){
//                float X1 = e1.getX();
//                float Y1 = e1.getY();
//                float X2 = e2.getX();
//                float Y2 = e2.getY();
//                float ratio = mRemoteScreenWidth / (holder.getSurfaceFrame().width() * 1.0f);
//                int rX1= (int) (X1 * ratio);
//                int rY1= (int) (Y1 * ratio);
//                int rX2= (int) (X2 * ratio);
//                int rY2= (int) (Y2 * ratio);
//                rcScroll(rX1,rY1,rX2,rY2);
//            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            SurfaceHolder holder;
            if((holder = mRemoteScreen.getHolder()) != null){
                float X1 = e1.getX();
                float Y1 = e1.getY();
                float X2 = e2.getX();
                float Y2 = e2.getY();
                float ratio = mRemoteScreenWidth / (holder.getSurfaceFrame().width() * 1.0f);
                int rX1= (int) (X1 * ratio);
                int rY1= (int) (Y1 * ratio);
                int rX2= (int) (X2 * ratio);
                int rY2= (int) (Y2 * ratio);
                rcScroll(rX1,rY1,rX2,rY2);
            }
            return true;
        }

    };

    private PaintFlagsDrawFilter mPaintFlagsDrawFilter = new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private void drawScreen2(int width,int height,byte[] bb){
        SurfaceHolder holder = mRemoteScreen.getHolder();
        if(holder != null){
            Canvas canvas = holder.lockCanvas();
            if(canvas != null){
                Bitmap bitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bb));
                int widthScreen = holder.getSurfaceFrame().width();
                float ratioW = (widthScreen * 1.0f) / width;
                canvas.save();
                canvas.scale(ratioW,ratioW);
                canvas.setDrawFilter(mPaintFlagsDrawFilter);
                canvas.drawBitmap(bitmap,0,0,null);
                canvas.restore();
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }
    private void drawScreen(byte[] bb,int len){
        SurfaceHolder holder = mRemoteScreen.getHolder();
        if(holder != null){
            Canvas canvas = holder.lockCanvas();
            if(canvas != null){
                Bitmap bitmap = BitmapFactory.decodeByteArray(bb, 0, len);
                if(bitmap != null){
                    int widthScreen = holder.getSurfaceFrame().width();
                    float ratioW = (widthScreen * 1.0f) / bitmap.getWidth();
                    canvas.save();
                    canvas.scale(ratioW,ratioW);
                    canvas.setDrawFilter(mPaintFlagsDrawFilter);
                    canvas.drawBitmap(bitmap,0,0,null);
                    canvas.restore();
                }
                holder.unlockCanvasAndPost(canvas);

            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    private class ControlThread extends Thread {
        private volatile boolean isRunning = true;
        private PrintWriter mSender;
        private Socket      mSocket;
        @Override
        public void run() {
            InetSocketAddress address = new InetSocketAddress(mConnectIPAdrres, 56665);
            mSocket = new Socket();
            try {
                mSocket.connect(address, 3000);
                Log.i(TAG, "connected");
                InputStream  inputStream = mSocket.getInputStream();
                OutputStream outputStream= mSocket.getOutputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                PrintWriter    pw = new PrintWriter(outputStream,true);
                mSender = pw;
                pw.println("shell");
                while (isRunning){
                    String line = br.readLine();
                    if(line != null){
                        Log.i(TAG,line);
                    }else{
                        break;
                    }
                }

            } catch (IOException e){
                Log.i(TAG,"control exception :"+e.getMessage());
            } finally {
                try {
                    mSocket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private class ConnectThread extends Thread {
        private int mRWidth;
        private int mRHeight;
        private int mRSize;
        private byte[] mRData;
        private byte[] mRenderData; // a copy of mRData
        private volatile boolean isRunning = true;

        private int receiveScreen(InputStream is){
            int recvSize = 0;
            long now = System.currentTimeMillis();
            while(true){
                try{
                    int offset = recvSize;
                    int len    = mRSize - recvSize;
                    int actualRecv = is.read(mRData,offset,len);
                    if(actualRecv == -1){
                        throw new IOException("remote reach end");
                    }
                    recvSize += actualRecv;
//                    Log.i(TAG,"recv progress:"+recvSize+", actual: "+actualRecv);
                    if(recvSize > 6){
                        char a = (char) mRData[recvSize - 6];
                        char b = (char) mRData[recvSize - 6 +1];
                        char c = (char) mRData[recvSize - 6 +2];
                        char d = (char) mRData[recvSize - 6 +3];
                        char e = (char) mRData[recvSize - 6 +4];
                        char f = (char) mRData[recvSize - 6 +5];
                        if(a == 'A' && b== 'B' && c=='C' && d=='D' && e=='E' && f=='F'){
//                            Log.i(TAG,"match end code");
                            break;
                        }
                    }
                }catch (IOException e){
                    Log.i(TAG,"read stream exception :"+e.getMessage());
                    isRunning = false;
                    break;
                }
            }
            long delta = System.currentTimeMillis() - now;
            Log.i(TAG,"recv bitmap finished :"+delta);
            return recvSize;
        }

        private void receiveScreen2(InputStream is){
            int recvSize = 0;
            long now = System.currentTimeMillis();
            while(recvSize != mRSize){
                try{
                    int offset = recvSize;
                    int len    = mRSize - recvSize;
                    int actualRecv = is.read(mRData,offset,len);
                    if(actualRecv == -1){
                        throw new IOException("remote reach end");
                    }
                    recvSize += actualRecv;
//                                    Log.i(TAG,"recv progress :"+recvSize +", act :"+actualRecv);
                }catch (IOException e){
                    Log.i(TAG,"read stream exception :"+e.getMessage());
                    isRunning = false;
                    break;
                }
            }
            long delta = System.currentTimeMillis() - now;
            Log.i(TAG,"recv bitmap finished :"+delta);
        }

        @Override
        public void run() {
            InetSocketAddress address = new InetSocketAddress(mConnectIPAdrres, 56668);
            final Socket socket = new Socket();
            try {
                socket.connect(address,3000);
                Log.i(TAG,"connected");
                InputStream is = socket.getInputStream();
                final BufferedReader br = new BufferedReader(new InputStreamReader(is));
                PrintWriter    		 pw = new PrintWriter(socket.getOutputStream(),true);
                BufferedInputStream bis = new BufferedInputStream(is);
                String line = br.readLine();
                if(line != null){
                    Log.i(TAG,"from server :"+line);
                    String[] spilt = line.split("x");
                    try{
                        mRWidth = Integer.parseInt(spilt[0]);
                        mRHeight= Integer.parseInt(spilt[1]);
                    }catch (NumberFormatException e){
                        throw new IOException("not width and height :"+e.getMessage());
                    }
                    mRSize = mRWidth * mRHeight * 4;
                    mRData = new byte[mRSize];
                    mRenderData = new byte[mRSize];
                    pw.println("ready");

                    while (isRunning){
                        int size = receiveScreen(bis);
                        if(isRunning){
                            Message message = Message.obtain();
                            System.arraycopy(mRData,0,mRenderData,0,size);
                            message.what = MSG_DRAW;
                            message.arg1 = size;
                            message.obj  = mRenderData;
                            mHandler.sendMessage(message);
                        }
                        pw.println("next");
//                            drawScreen(mRWidth,mRHeight,mRData);
                    }
                }else{
                    throw new IOException("server message end");
                }
            } catch (IOException e) {
                Log.i(TAG,"client exception :"+e.getMessage());
            }finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.i(TAG,"close failed:"+e.getMessage());
                }
            }
        }
    }
}
