package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by yanzi on 9/19/15.
 */
public class Thread_TX_CRawNormal implements Runnable {
    @Override
    public void run() {
        if (MainActivity.isRunning_TX_RawNormal)
            return;
        if (MainActivity.isVerbose)
            Log.d("TX_RawNormal", "Start TX RawNormal");
        MainActivity.isRunning_TX_RawNormal = true;
        Process proc;
        String[] commd = new String[3];
        commd[0] = "su";
        commd[1] = "-c";
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_TX_RawNormal + " "
                + MainActivity.bytes2send + " " + String.valueOf(MainActivity.currentBandwidth);
        try {
            proc = Runtime.getRuntime().exec(commd);
            proc.waitFor();
            InputStream stdout = proc.getInputStream();
            byte[] buffer = new byte[20];
            int read;
            StringBuilder out = new StringBuilder();
            while(true){
                if (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1)
                    MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_TX_RawNormal);
                read = stdout.read(buffer);
                if(read<0){
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.append("Failed in TX_RawNormal\n");
                        }
                    });
                    break;
                }
                out.append(new String(buffer, 0, read));
                if(read<20){
                    break;
                }
            }
            if (MainActivity.isVerbose) {
                final String mOut = out.toString().trim();
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
            Log.d("TX_RawNormal", "Stop TX RawNormal");
        MainActivity.isRunning_TX_RawNormal = false;
        MainActivity.perProcPID = -1;
    }
}
