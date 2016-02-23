package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by yanzi on 9/18/15.
 */
public class Thread_RX_CRawNormal implements Runnable {
    @Override
    public void run() {
        if (MainActivity.isRunning_RX_RawNormal)
            return;
        if (MainActivity.isVerbose)
            Log.d("RX_RawNormal", "Start RX RawNormal");
        MainActivity.isRunning_RX_RawNormal = true;
        Process proc;
        String[] commd = new String[3];
        commd[0] = "su";
        commd[1] = "-c";
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_RX_RawNormal + " "
                + MainActivity.bytes2send;
        try {
            proc = Runtime.getRuntime().exec(commd);
            while (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1) {
                MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_RX_RawNormal, false);
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
                            MainActivity.txt_results.append("Failed in RX_RawNormal at port " + MainActivity.RXportNum + "\n");
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
            final String mOut = out.toString().trim();
            MainActivity.reportedFinishTime = Double.parseDouble(mOut);
            if (MainActivity.isVerbose) {
                Log.d("Thread_RX_CRawNormal", mOut + "ms");
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
            Log.d("RX_RawNormal", "Stop RX RawNormal");
        MainActivity.isRunning_RX_RawNormal = false;
        MainActivity.perProcPID = -1;
    }
}
