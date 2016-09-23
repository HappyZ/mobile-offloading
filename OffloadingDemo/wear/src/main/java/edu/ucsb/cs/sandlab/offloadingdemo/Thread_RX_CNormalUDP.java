package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by yanzi on 9/18/15.
 */
public class Thread_RX_CNormalUDP implements Runnable {
    @Override
    public void run() {
        if (MainActivity.isRunning_RX_NormalUDP)
            return;
        if (MainActivity.isVerbose)
            Log.d("RX_NormalUDP", "Start RX NormalUDP");
        MainActivity.isRunning_RX_NormalUDP = true;
        Process proc;
        String[] commd = new String[3];
        commd[0] = "su";
        commd[1] = "-c";
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_RX_NormalUDP + " "
                + MainActivity.bytes2send + " 32000";
        try {
            proc = Runtime.getRuntime().exec(commd);
            proc.waitFor();
            InputStream stdout = proc.getInputStream();
            byte[] buffer = new byte[20];
            int read;
            StringBuilder out = new StringBuilder();
            while(true){
                if (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1)
                    MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_RX_NormalUDP);
                read = stdout.read(buffer);
                if(read<0){
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.append("Failed in RX_NormalUDP at port 32000\n");
                        }
                    });
                    break;
                }
                out.append(new String(buffer, 0, read));
                if(read<20){
                    break;
                }
            }
            if (out.length() > 0) {
                MainActivity.UDPfinishTime = (int)Float.parseFloat(out.toString().trim());
            }
            if (MainActivity.isVerbose) {
                final String mOut = out.toString().trim();
                Log.d("Thread_RX_CNormalUDP", mOut + "ms");
                MainActivity.myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.txt_results.append("Time: " + mOut + "ms\n");
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (MainActivity.isVerbose)
            Log.d("RX_NormalUDP", "Stop RX NormalUDP");
        MainActivity.isRunning_RX_NormalUDP = false;
        MainActivity.perProcPID = -1;
    }
}
