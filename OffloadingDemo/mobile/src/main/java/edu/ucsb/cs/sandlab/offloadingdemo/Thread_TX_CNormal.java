package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by yanzi on 9/18/15.
 * Updated on 01/25/17
 */

public class Thread_TX_CNormal implements Runnable {
    private double sentBytes = 0.0;
    private double duration = 0.0;
    private double throughput = 0.0;

    @Override
    public void run() {
        if (MainActivity.isRunning_TX_Normal)
            return;
        if (MainActivity.isVerbose)
            Log.d("TX_Normal", "Start TX Normal");
        MainActivity.isRunning_TX_Normal = true;
        Process proc;
        String[] commd = new String[3];

        // get the right command
        commd[0] = "su";
        commd[1] = "-c";
        // ./client_send_normaltcp <bytes2send/file2send> <ip> <port>
        // <[optional] bandwidth (bps)> <[optional] sendsize (bytes)>
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_TX_Normal + " "
                + MainActivity.bytes2send + " "
                + (MainActivity.isLocal ? MainActivity.myInetIP : MainActivity.remoteIP) + " "
                + MainActivity.RXportNum + " "
                + ((MainActivity.currentBandwidth < 0) ? "" : String.valueOf(
                        MainActivity.currentBandwidth));

        try {
            // run process
            proc = Runtime.getRuntime().exec(commd);

            // if config to log per process
            while (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1) {
                MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_TX_Normal, false);
            }
            proc.waitFor();

            // read error
            BufferedReader error_buf = new BufferedReader(new InputStreamReader(
                    proc.getErrorStream()));
            final String error = error_buf.readLine(); // only one line error

            // read std out
            BufferedReader stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));
            String stdout;

            // get sent bytes
            stdout = stdout_buf.readLine();
            if (stdout == null) {
                // error happens
                MainActivity.myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.txt_results.append("Err in TX_Normal: " + error + "\n");
                    }
                });
            } else {
                // sent bytes
                sentBytes = Utilities.parseBinOutput(stdout);

                // duration
                stdout = stdout_buf.readLine();
                duration = Utilities.parseBinOutput(stdout);
                MainActivity.reportedFinishTime = duration;
                if (MainActivity.isVerbose) {
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.append("Time: " + duration + "ms\n");
                        }
                    });
                }

                // throughput
                stdout = stdout_buf.readLine();
                throughput = Utilities.parseBinOutput(stdout);
            }

//            InputStream stdout = proc.getInputStream();
//            byte[] buffer = new byte[20];
//            int read;
//            StringBuilder out = new StringBuilder();
//            while(true){
//                read = stdout.read(buffer);
//                if(read<0){
//                    MainActivity.myHandler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            MainActivity.txt_results.append("Err in TX_Normal: " + error + "\n");
//                        }
//                    });
//                    break;
//                }
//                out.append(new String(buffer, 0, read));
//                if(read<20){
//                    break;
//                }
//            }
//            final String mOut = out.toString().trim();
//            if (!mOut.equals(""))
//                MainActivity.reportedFinishTime = Double.parseDouble(mOut);
//            else
//                MainActivity.reportedFinishTime = 0.0;
//            if (MainActivity.isVerbose) {
//                MainActivity.myHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        MainActivity.txt_results.append("Time: " + mOut + "ms\n");
//                    }
//                });
//            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (MainActivity.isVerbose)
            Log.d("TX_Normal", "Stop TX Normal");
        MainActivity.isRunning_TX_Normal = false;
        MainActivity.perProcPID = -1;
    }
}
