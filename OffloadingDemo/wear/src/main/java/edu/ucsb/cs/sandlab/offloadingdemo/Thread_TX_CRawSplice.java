package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by yanzi on 9/18/15.
 */
public class Thread_TX_CRawSplice implements Runnable {
    @Override
    public void run() {
        if (MainActivity.isRunning_TX_RawSplice)
            return;
        if (MainActivity.isVerbose)
            Log.d("TX_RawSplice", "Start TX RawSplice");
        MainActivity.isRunning_TX_RawSplice = true;
        Process proc;
        String[] commd = new String[3];
        commd[0] = "su";
        commd[1] = "-c";
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_TX_RawSplice + " "
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
                    MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_TX_RawSplice);
                read = stdout.read(buffer);
                if(read<0){
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.append("Failed in TX_RawSplice\n");
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
            Log.d("TX_RawSplice", "Stop TX RawSplice");
        MainActivity.isRunning_TX_RawSplice = false;
        MainActivity.perProcPID = -1;
    }
}
