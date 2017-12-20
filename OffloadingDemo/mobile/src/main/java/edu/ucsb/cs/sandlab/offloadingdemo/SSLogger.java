package edu.ucsb.cs.sandlab.offloadingdemo;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * Created by yanzi on 9/20/15.
 * Updated by yanzi on 01/27/2017
 * support multiple cores now
 */
public class SSLogger extends Service {
    private static final String TAG = "SSLogger";
    private boolean isRunning = false;
    private boolean isRunningPollingThread = false;
    private String  ssFileName, cpuFileName,
                    cpuWiFiDriverPIDFileName, cpuPerProcPIDFileName,
                    cpuTCPDumpFileName, cpuAppSelfFileName;
    private int wifiRSS, gsmRSS;
    private static int tcpdumpPID = -1, appSelfPID = -1;
    private WifiManager wm;
    private FileOutputStream os_ss = null, os_cpu = null,
            os_cpuWiFiDriverPID = null, os_cpuPerProcPID = null,
            os_cpuTCPDump = null, os_cpuAppSelf = null;

    public class PollingThread extends Thread {
        public void run() {
            if (isRunningPollingThread) {
                Log.d(TAG, "already running polling thread");
                return;
            }
            // prevent multiple polling threads
            isRunningPollingThread = true;

            // variables
            int i;
            byte[] cpuRawStuff, ssStuff = null,
                   cpuWiFiDriverPIDStuff = null,
                   cpuPerPIDStuff = null,
                   cpuTCPDumpStuff = null,
                   cpuAppSelfStuff = null;
            String mTime; // holder for system time
            String tmp; // placeholder for constructed string
            String[] cpuUsage = new String[MainActivity.coreNum + 1]; // placeholder for each core
            String[] cpuFreq = new String[MainActivity.coreNum]; // placeholder for each core

            // if SSLogger is set to run
            while(isRunning) {

                // get current system time
                mTime = Long.toString(System.currentTimeMillis());

                // read current cpu usage
                readUsage(cpuUsage);

                // read current cpu frequency
                readFrequency(cpuFreq);

                // construct bytes for cpuRaw log
                tmp = mTime + " " + cpuUsage[0];
                for (i = 0; i < MainActivity.coreNum; ++i) {
                    tmp += " " + cpuUsage[i + 1] + " " + cpuFreq[i];
                }
                tmp += "\n";
                cpuRawStuff = tmp.getBytes();

                // construct bytes for PID log
                if (MainActivity.wifiDriverPID != -1) {
                    cpuWiFiDriverPIDStuff = (mTime + " "
                            + parseProcPIDStat(readUsagePID(MainActivity.wifiDriverPID))
                            + "\n").getBytes();
                }

                // construct bytes for per process pid (just running process)
                if (MainActivity.isLoggingPerProcPID) {
                    cpuPerPIDStuff = (mTime + " "
                            + parseProcPIDStat(readUsagePID(MainActivity.perProcPID))
                            + "\n").getBytes();
                }

                // construct bytes for tcpdump
                if (tcpdumpPID != -1) {
                    cpuTCPDumpStuff = (mTime + " "
                            + parseProcPIDStat(readUsagePID(tcpdumpPID)) + "\n").getBytes();
                }

                // construct bytes for logging app
                if (MainActivity.isLoggingAppSelf) {
                    cpuAppSelfStuff = (mTime + " "
                            + parseProcPIDStat(readUsagePID(appSelfPID)) + "\n").getBytes();
                }

                // construct bytes for wifi rss
                if (wifiRSS != 0) {
                    ssStuff = (mTime + " wifi " + wifiRSS + "\n").getBytes();
                }

                // write results into file
                try {
                    if (isRunning) {
                        if (wifiRSS != 0) {
                            os_ss.write(ssStuff);
                        }

                        os_cpu.write(cpuRawStuff);

                        if (MainActivity.wifiDriverPID != -1)
                            os_cpuWiFiDriverPID.write(cpuWiFiDriverPIDStuff);

                        if (MainActivity.isLoggingPerProcPID)
                            os_cpuPerProcPID.write(cpuPerPIDStuff);

                        if (tcpdumpPID != -1)
                            os_cpuTCPDump.write(cpuTCPDumpStuff);

                        if (MainActivity.isLoggingAppSelf)
                            os_cpuAppSelf.write(cpuAppSelfStuff);
                    }
                } catch (IOException unimportant) {
                    Log.w(TAG, "IO error at SSLogger");
                }

                // sleep for a while and then log to prevent high IO (and cpu)
                try {
                    Thread.sleep(MainActivity.time_wait_for);
                } catch (Exception unimportant) {
                    Log.w(TAG, "Can't wait.. something wrong?");
                }
            }

            // end of polling thread
            isRunningPollingThread = false;
        }
    }

    /**
     * my initialization
     */
    public void initialization() {
        // no need to check the directory again since main activity already did
        File mDir = new File(MainActivity.outFolderPath);

        // run tcpdump
        if (MainActivity.isUsingTCPDump) {
            try {
                Runtime.getRuntime().exec(
                        "su -c " + MainActivity.binaryFolderPath
                                + "tcpdump -i " + MainActivity.tcpdumpInterface
                                + " -w " + MainActivity.outFolderPath + "/tcpdump_wifionly_"
                                + MainActivity.btn_click_time + " &"
                ).waitFor();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }

            // get the tcpdump pid if requested
            if (MainActivity.isLoggingTCPDump)
                tcpdumpPID = Utilities.getMyPID(MainActivity.binary_tcpdump, true);
        }

        // get the logging app pid if requested
        if (MainActivity.isLoggingAppSelf)
            appSelfPID = Utilities.getMyPID("offloading", true);

        // permission error
        if (!Utilities.canWriteOnExternalStorage()) {
            onDestroy();
        }

        // get the initial WiFi signal strength
        wm = (WifiManager) this.getSystemService(WIFI_SERVICE);
        if (!wm.isWifiEnabled() && MainActivity.isVerbose) {
            MainActivity.myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append("WiFi should be ON! Check.\n");
                }
            });
            wifiRSS = 0;
        } else {
            wifiRSS = wm.getConnectionInfo().getRssi();
            // register to fetch rssi upon change
            this.registerReceiver(this.myWifiReceiver,
                    new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        }

        // signal strength file string
        if (wifiRSS != 0) {
            ssFileName = MainActivity.btn_click_time.concat(".ss");
        }

        // cpu raw usage & frequency file string
        cpuFileName = MainActivity.btn_click_time.concat(".cpuRaw");

        // file handler for cpu usage of wifi driver
        if (MainActivity.wifiDriverPID != -1)
            cpuWiFiDriverPIDFileName = MainActivity.btn_click_time.concat(".cpuDriver");

        // file string for cpu usage of my process
        if (MainActivity.isLoggingPerProcPID)
            cpuPerProcPIDFileName = MainActivity.btn_click_time.concat(".cpuProcPID");

        // file string for cpu usage of tcpdump
        if (tcpdumpPID != -1)
            cpuTCPDumpFileName = MainActivity.btn_click_time.concat(".cpuTCPDump");

        // file string for cpu usage of logging app
        if (MainActivity.isLoggingAppSelf)
            cpuAppSelfFileName = MainActivity.btn_click_time.concat(".cpuAppSelf");

        // create file output stream
        try {
            if (wifiRSS != 0) {
                os_ss = new FileOutputStream(new File(mDir, ssFileName));
            }
            os_cpu = new FileOutputStream(new File(mDir, cpuFileName));
            if (MainActivity.wifiDriverPID != -1)
                os_cpuWiFiDriverPID = new FileOutputStream(new File(mDir, cpuWiFiDriverPIDFileName));
            if (MainActivity.isLoggingPerProcPID)
                os_cpuPerProcPID = new FileOutputStream(new File(mDir, cpuPerProcPIDFileName));
            if (tcpdumpPID != -1)
                os_cpuTCPDump = new FileOutputStream(new File(mDir, cpuTCPDumpFileName));
            if (MainActivity.isLoggingAppSelf)
                os_cpuAppSelf = new FileOutputStream(new File(mDir, cpuAppSelfFileName));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        initialization();
        // collect signals
        PollingThread pt = new PollingThread();
        pt.start();
        return START_STICKY;
    }

    @Override
    public void onCreate(){
        super.onCreate();
    }

    @Override
    public void onDestroy(){
        isRunning = false;

        // get rid of wifi rssi monitor
        if (wifiRSS != 0)
            this.unregisterReceiver(this.myWifiReceiver);

        // kill tcpdump
        if (MainActivity.isUsingTCPDump) {
            try {
                Runtime.getRuntime().exec("su -c killall -9 tcpdump").waitFor();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
            if (MainActivity.isLoggingTCPDump)
                tcpdumpPID = -1;
        }

        // close all file handler
        try {
            if (os_ss != null)
                os_ss.close();
            if (os_cpu != null)
                os_cpu.close();
            if (os_cpuWiFiDriverPID != null)
                os_cpuWiFiDriverPID.close();
            if (os_cpuPerProcPID != null)
                os_cpuPerProcPID.close();
            if (os_cpuTCPDump != null)
                os_cpuTCPDump.close();
            if (os_cpuAppSelf != null)
                os_cpuAppSelf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * get wifi rssi
     */
    private BroadcastReceiver myWifiReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent arg1){
            wifiRSS = arg1.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
            Log.d(TAG, "WiFi RSSI: " + wifiRSS);
            if (MainActivity.isVerbose) {
                MainActivity.myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.txt_results.append("wifiRSS: " + wifiRSS + "\n");
                    }
                });
            }
        }
    };

    /**
     * parse one string line of proc (faster)
     * @param toks  each part
     * @return  string that's been aggregated
     */
    private static String parseProcStat(String[] toks) {
        // the columns are:
        //      0 "cpu": the string "cpu" that identifies the line
        //      1 user: normal processes executing in user mode
        //      2 nice: niced processes executing in user mode
        //      3 system: processes executing in kernel mode
        //      4 idle: twiddling thumbs
        //      5 iowait: waiting for I/O to complete
        //      6 irq: servicing interrupts
        //      7 softirq: servicing softirqs
        long idle = Long.parseLong(toks[4]);
        long cpu = Long.parseLong(toks[1]) + Long.parseLong(toks[2]) + Long.parseLong(toks[3])
                + Long.parseLong(toks[5]) + Long.parseLong(toks[6]) + Long.parseLong(toks[7]);
        return idle + " " + cpu;
    }

    /**
     * parse one string line of procpid
     * @param line  the line
     * @return string that's been parsed
     */
    private static String parseProcPIDStat(String line) {
        if (line == null) return "-1 -1 -1"; // -1 means it does not exist
        String[] toks = line.split("\\s+");
        long idle = Long.parseLong(toks[15]) + Long.parseLong(toks[16]);
        long cpu = Long.parseLong(toks[13]) + Long.parseLong(toks[14]);
        long whichcpu = Long.parseLong(toks[38]);
        return idle + " " + cpu + " " + whichcpu;
    }

    /**
     * read the cpu usage (faster)
     * @param raws  String[]
     */
    private static void readUsage(String[] raws) {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            for (int i = 0; i <= MainActivity.coreNum; ++i) {
                raws[i] = reader.readLine();
            }
            reader.close();
        } catch (IOException unimportant) {
            Log.w(TAG, "exception on readUsage (faster)");
        }
    }

    private static String readUsagePID(int currentPID) {
        if (currentPID == -1) return null;
        // changed by Yanzi

        Process proc;
        BufferedReader stdout_buf;
        String load;
        try {
            proc = Runtime.getRuntime().exec("su -c cat /proc/" + currentPID + "/stat");
            proc.waitFor();

            // read std out
            stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));

            load = stdout_buf.readLine();
//            RandomAccessFile reader = new RandomAccessFile("/proc/" + currentPID +"/stat", "r");
//            String load = reader.readLine();
//            reader.close();
            return load;
        } catch (IOException | InterruptedException unimportant) {
            Log.w(TAG, "exception on readUsagePID on pid: " + currentPID);
        }
        return null;
    }

    /**
     * get the frequency of all cpu cores
     * @param raws
     */
    private static void readFrequency(String[] raws) {
        String filepath;
        RandomAccessFile reader;

        try {
            for (int cpuid = 0; cpuid < MainActivity.coreNum; ++cpuid) {
                filepath = "/sys/devices/system/cpu/cpu" + cpuid + "/cpufreq/scaling_cur_freq";
                reader = new RandomAccessFile(filepath, "r");
                String[] toks = reader.readLine().split("\\s+");
                raws[cpuid] = toks[0];
                reader.close();
            }
        } catch (IOException unimportant) {
            Log.w(TAG, "exception on cpuFrequency");
        }

    }

}
