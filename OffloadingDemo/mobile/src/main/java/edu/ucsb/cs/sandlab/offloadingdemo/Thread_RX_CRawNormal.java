package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by yanzi on 9/18/15.
 * Updated on 01/31/17
 */

class Thread_RX_CRawNormal implements Runnable {
    private double recvBytes = 0.0;
    private double duration = 0.0;
    private double throughput = 0.0;

    @Override
    public void run() {

        // prevent multiple runs
        if (MainActivity.isRunning_RX_RawNormal)
            return;
        MainActivity.isRunning_RX_RawNormal = true;

        // variables
        Process proc;
        String stdout;
        BufferedReader stdout_buf, error_buf;
        String[] commd = new String[3];

        // get the right command
        commd[0] = "su";
        commd[1] = "-c";
        // ./client_recv_bypassl3 <ip> <port>
        // <[optional] recvsize (bytes)> <[optional] interface> <[optional] filepath>
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_RX_RawNormal
                + " " + (MainActivity.isLocal ? Utilities.myInetIP : MainActivity.remoteIP)
                + " " + MainActivity.RXportNum
                + " " + MainActivity.bytes2send;

        Log.d("RX_RawNormal", "Start RX RawNormal");

        try {
            // run process
            proc = Runtime.getRuntime().exec(commd);

            // if config to log per process
            while (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1) {
                MainActivity.perProcPID = Utilities.getMyPID(MainActivity.binary_RX_Splice, false);
            }
            proc.waitFor();

            // read error
            error_buf = new BufferedReader(new InputStreamReader(
                    proc.getErrorStream()));
            final String error = error_buf.readLine(); // only one line error

            // read std out
            stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));

            // get received bytes
            stdout = stdout_buf.readLine();
            if (stdout == null) {
                // error happens
                MainActivity.myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.txt_results.append("Err in RX_RawNormal: " + error + "\n");
                    }
                });
            } else {
                // sent bytes
                recvBytes = Utilities.parseBinOutput(stdout);

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

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        Log.d("RX_RawNormal", "Stop RX RawNormal");

        MainActivity.isRunning_RX_RawNormal = false;
        MainActivity.perProcPID = -1;
    }
}
