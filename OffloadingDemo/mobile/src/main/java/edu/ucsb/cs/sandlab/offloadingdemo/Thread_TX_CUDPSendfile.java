package edu.ucsb.cs.sandlab.offloadingdemo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by yanzi on 9/19/15.
 * Updated on 01/27/17
 */

class Thread_TX_CUDPSendfile implements Runnable {
    private double sentBytes = 0.0;
    private double duration = 0.0;
    private double throughput = 0.0;

    @Override
    public void run() {
        // prevent multiple runs
        if (MainActivity.isRunning_TX_UDPSendfile)
            return;
        MainActivity.isRunning_TX_UDPSendfile = true;

        // variables
        Process proc;
        String stdout;
        BufferedReader stdout_buf, error_buf;
        String[] commd = new String[3];

        // get the right command
        commd[0] = "su";
        commd[1] = "-c";
        // ./client_send_normaludp_sendfile <bytes2send/file2send> <ip> <port>
        // <[optional] bandwidth (bps)> <[optional] sendsize (Bytes)>
        commd[2] = (MainActivity.isForcingCPU0?"taskset 1 ":"")
                + MainActivity.binaryFolderPath + MainActivity.binary_TX_UDPSendfile + " "
                + MainActivity.bytes2send + " "
                + (MainActivity.isLocal ? Utilities.myInetIP : MainActivity.remoteIP) + " "
                + Utilities.UDP_port + " "
                + ((MainActivity.currentBandwidth < 0) ? "" : String.valueOf(
                MainActivity.currentBandwidth));

        Log.d("TX_UDPSendfile", "Start TX UDP Sendfile");
        try {
            // run process
            proc = Runtime.getRuntime().exec(commd);

            // if config to log per process
            while (MainActivity.isLoggingPerProcPID && MainActivity.perProcPID == -1) {
                MainActivity.perProcPID = Utilities.getMyPID(
                        MainActivity.binary_TX_UDPSendfile, false);
            }
            proc.waitFor();

            // read error
            error_buf = new BufferedReader(new InputStreamReader(
                    proc.getErrorStream()));
            final String error = error_buf.readLine(); // only one line error

            // read std out
            stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));

            // get sent bytes
            stdout = stdout_buf.readLine();
            if (stdout == null) {
                // error happens
                MainActivity.myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.txt_results.append("Err in TX_UDP_Sendfile: " + error + "\n");
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
                            MainActivity.txt_results.append("Sent " +
                                    String.format("%.4f", sentBytes/Utilities.oneMB) +
                                    " MB in " +
                                    String.format("%.4f", duration) +
                                    "s (" +
                                    String.format("%.4f", throughput/Utilities.oneMB) +
                                    "Mbps)\n");
                        }
                    });
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        Log.d("TX_UDPSendfile", "Stop TX UDP Sendfile");

        MainActivity.isRunning_TX_UDPSendfile = false;
        MainActivity.perProcPID = -1;
    }
}
