package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by yanzi on 9/18/15.
 */
public class Thread_RX_CNormal implements Runnable {
    @Override
    public void run() {
        if (MainActivity.isRunning_RX_Normal)
            return;
        if (MainActivity.isVerbose)
            Log.d("RX_Normal", "Start RX Normal");
        MainActivity.isRunning_RX_Normal = true;
        Process proc;
        String[] commd = new String[3];
        commd[0] = "su";
        commd[1] = "-c";
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_RX_Normal + " "
                + MainActivity.bytes2send + " " + MainActivity.RXportNum;
        try {
            proc = Runtime.getRuntime().exec(commd);
            while (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1) {
                MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_RX_Normal, false);
            }
            proc.waitFor();
            InputStream stdout = proc.getInputStream();
            byte[] buffer = new byte[20];
            int read;
            StringBuilder out = new StringBuilder();
            while(true){
                read = stdout.read(buffer);
                if(read<0){
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.append("Failed in RX_Normal at port " + MainActivity.RXportNum + "\n");
                        }
                    });
                    break;
                }
                out.append(new String(buffer, 0, read));
                if(read<20){
                    break;
                }
            }
            final String mOut = out.toString().trim();
            MainActivity.reportedFinishTime = Double.parseDouble(mOut);
            if (MainActivity.isVerbose) {
                Log.d("Thread_RX_CNormal", mOut + "ms");
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
            Log.d("RX_Normal", "Stop RX Normal");
        MainActivity.isRunning_RX_Normal = false;
        MainActivity.perProcPID = -1;
    }
}
