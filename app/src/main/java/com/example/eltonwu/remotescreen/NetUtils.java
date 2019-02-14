package com.example.eltonwu.remotescreen;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetUtils {
    private static final String TAG = "NetUtils";
    private static final int MAX_SCAN_THREAD = 253;

    public static void scanNetwork(ArrayList<InetAddress> result) {
        result.clear();
        ScanThread.openedIP = result;
        ArrayList<InetAddress> toBeScanned = new ArrayList<InetAddress>();
        int maxHost = (int) (Math.pow(2f, (32-24))) - 3;//(0,255,256)
        int eachThreadScanNum = maxHost/MAX_SCAN_THREAD;
        int remainder = maxHost % MAX_SCAN_THREAD; //余数
//		Log.w(TAG,"each :"+eachThreadScanNum+" reminder:"+remainder+" max:"+maxHost);
        if(eachThreadScanNum == 0) {
            eachThreadScanNum = 1;
        }else {
            if(remainder != 0) {
                eachThreadScanNum += 1;
            }
        }
//		Log.w(TAG,"eachThreadScanNum:"+eachThreadScanNum);
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while(nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
//				Log.w(TAG,"ni name:"+ni.getDisplayName());
                List<InterfaceAddress> list = ni.getInterfaceAddresses();
                for(InterfaceAddress address : list) {
                    //make sure its IPv4 Network and start with 192.168
                    short mask_length = address.getNetworkPrefixLength();
                    byte[] baddr = address.getAddress().getAddress();
                    if(baddr.length != 4) {
                        continue;
                    }
                    if(mask_length != 24) {
                        continue;
                    }
                    if(toUnsignedInt(baddr[0]) != 192 || toUnsignedInt(baddr[1]) != 168) {
                        continue;
                    }
                    byte[] naddr = new byte[4];
                    naddr[0] = baddr[0];
                    naddr[1] = baddr[1];
                    naddr[2] = baddr[2];
                    naddr[3] = 0;
                    try {
                        InetAddress iaddr = InetAddress.getByAddress(naddr);
                        toBeScanned.add(iaddr);
                    } catch (UnknownHostException e) {
                        Log.w(TAG,"failed to address:"+address+" reason:"+e.getMessage());
                    }
                }
            }
            for(InetAddress addr : toBeScanned) {
                Log.w(TAG,addr.getHostAddress()+" will be scaned");
                ScanThread[] threads = new ScanThread[MAX_SCAN_THREAD];
                for(int i=0; i<MAX_SCAN_THREAD; i++) {
                    int from  = (i*eachThreadScanNum) + 1;
                    int to	  = (i+1)*eachThreadScanNum;
                    if(from > maxHost) {
                        break;
                    }
                    if(to > maxHost) {
                        to = maxHost;
                    }
                    ScanThread thread = new ScanThread(toUnsignedInt(addr.getAddress()[2]),from, to);
                    threads[i] = thread;
                    thread.start();
                }
                for(ScanThread thread : threads) {
                    if(thread != null) {
                        try {
                            thread.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.w(TAG,"all scan thread finished");
            for(InetAddress addr : result) {
                Log.w(TAG,"avaliable:"+addr.getHostAddress());
            }
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static final int toUnsignedInt(byte x){
        return ((int) x) & 0xff;
    }

    private static class ScanThread extends Thread {
        private int fromIP;
        private int toIP;
        private int netID;
        private static ArrayList<InetAddress> openedIP;

        public ScanThread(int netID,int fromIP, int toIP) {
            super();
            this.fromIP = fromIP;
            this.toIP 	= toIP;
            this.netID	= netID;
        }

        @Override
        public void run() {
//			Log.w(TAG,"from :"+fromIP+" to :"+toIP);
            for(int i=fromIP; i<=toIP;i++) {
                Socket socket = null;
                try {
                    byte[] baddr = new byte[4];
                    baddr[0] = (byte) 192;
                    baddr[1] = (byte) 168;
                    baddr[2] = (byte) netID;
                    baddr[3] = (byte) i;
                    InetAddress addr = InetAddress.getByAddress(baddr);
                    socket = new Socket();
                    InetSocketAddress dest = new InetSocketAddress(addr, 56668);
//					Log.w(TAG,"scaning :"+addr.getHostAddress());
                    socket.connect(dest, 1000);
                    Log.w(TAG,"---------------------"+addr.getHostAddress()+"----------------------");
                    synchronized (openedIP) {
                        openedIP.add(addr);
                    }
                } catch (UnknownHostException e) {
                    Log.w(TAG,"ip :"+i+" scan failed"+" reason:"+e.getMessage());
                } catch (IOException e) {
                    //probably timeout
//					Log.w(TAG,"ip :"+i+" scan failed"+" reason:"+e.getMessage());
                } finally {
                    if(socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            Log.w(TAG,"close scan socket failed :"+e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
