package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by yanzi on 9/18/15.
 * Updated on 01/27/17
 */

class Thread_TX_CRawSplice implements Runnable {
    private double sentBytes = 0.0;
    private double duration = 0.0;
    private double throughput = 0.0;

    @Override
    public void run() {
        if (MainActivity.isRunning_TX_RawSplice)
            return;
        if (MainActivity.isVerbose)
            Log.d("TX_RawSplice", "Start TX RawSplice");
        MainActivity.isRunning_TX_RawSplice = true;
        Process proc;
        String[] commd = new String[3];

        // get the right command
        commd[0] = "su";
        commd[1] = "-c";
        // ./client_send_bypassl3_splice <bytes2send/file2send> <ip> <port>
        // <[optional] bandwidth (bps)> <[optional] sendsize (Bytes)>
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_TX_RawSplice + " "
                + MainActivity.bytes2send + " "
                + (MainActivity.isLocal ? Utilities.myInetIP : MainActivity.remoteIP) + " "
                + Utilities.TCP_port + " "
                + ((MainActivity.currentBandwidth < 0) ? "" : String.valueOf(
                MainActivity.currentBandwidth));

        try {
            // run process
            proc = Runtime.getRuntime().exec(commd);

            // if config to log per process
            while (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1) {
                MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_TX_RawSplice, false);
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
                        MainActivity.txt_results.append("Err in TX_Raw_Splice: " + error + "\n");
                    }
                });
            } else {
                // sent bytes
                sentBytes = Utilities.parseBinOutput(stdout);

                // duration
                stdout = stdout_buf.readLine();
                duration = Utilities.parseBinOutput(stdout);
                MainActivity.reportedFinishTime = duration;

                // throughput
                stdout = stdout_buf.readLine();
                throughput = Utilities.parseBinOutput(stdout);

                if (MainActivity.isVerbose) {
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.append("Sent " + sentBytes +
                                    "bytes in " + duration +
                                    "s (" +throughput/Utilities.oneMB +"Mbps)\n");
                        }
                    });
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        Log.d("TX_RawSplice", "Stop TX RawSplice");

        MainActivity.isRunning_TX_RawSplice = false;
        MainActivity.perProcPID = -1;
    }
}
