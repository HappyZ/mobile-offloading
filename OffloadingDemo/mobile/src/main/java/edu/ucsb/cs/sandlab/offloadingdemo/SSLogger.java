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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * Created by yanzi on 9/20/15.
 * two core only right now
 */
public class SSLogger extends Service {
    private static final String TAG = "SSLogger";
    private boolean isRunning = false;
    private boolean isRunningPollingThread = false;
    private String ssFileName, cpuFileName,
            cpuWiFiDriverPIDFileName, cpuPerProcPIDFileName, cpuTCPDumpFileName, cpuAppSelfFileName;
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
            isRunningPollingThread = true;
            while(isRunning) {
                // get current system time
                String mTime = Long.toString(System.currentTimeMillis());
                String[] raws = new String[5]; // assume 4 cores, [0] is total
                // read current cpu usage
                readUsage(raws);
                String myTmp = mTime + " " + raws[0] + " ";
                for (int i = 0; i < MainActivity.coreNum-1; ++i) {
                    myTmp += raws[i+1] + " " + cpuFrequency(i) + " ";
                }
                myTmp += raws[MainActivity.coreNum] + " " + cpuFrequency(MainActivity.coreNum-1) + "\n";
//                // cpuTotal
//                String[] cpuTotal = raws[0].split("\\s+");
//                String cpuTotalsum = parseProcStat(cpuTotal);
//                // cpu 1 - N (N = coreNum)
//                String[] cpu1 = raws[1].split("\\s+");
//                String cpu1sum = parseProcStat(cpu1);
//                String[] cpu2 = null, cpu3 = null, cpu4 = null;
//                String cpu2sum = null, cpu3sum = null, cpu4sum = null;
//                // parse lines
//                if (MainActivity.coreNum > 1) {
//                    cpu2 = raws[2].split("\\s+");
//                    cpu2sum = parseProcStat(cpu2);
//                }
//                if (MainActivity.coreNum > 2) {
//                    cpu3 = raws[3].split("\\s+");
//                    cpu3sum = parseProcStat(cpu3);
//                }
//                if (MainActivity.coreNum > 3) {
//                    cpu4 = raws[4].split("\\s+");
//                    cpu4sum = parseProcStat(cpu4);
//                }
                byte[] cpuStuff, ssStuff = null,
                        cpuWiFiDriverPIDStuff = null, cpuPerPIDStuff = null, cpuTCPDumpStuff = null,
                        cpuAppSelfStuff = null;
                cpuStuff = myTmp.getBytes();
                if (MainActivity.wifiDriverPID != -1)
                    cpuWiFiDriverPIDStuff = (mTime + " " + parseProcPIDStat(readUsagePID(MainActivity.wifiDriverPID))  + "\n").getBytes();
                if (MainActivity.isLoggingPerProcPID)
                    cpuPerPIDStuff = (mTime + " " + parseProcPIDStat(readUsagePID(MainActivity.perProcPID)) + "\n").getBytes();
                if (tcpdumpPID != -1)
                    cpuTCPDumpStuff = (mTime + " " + parseProcPIDStat(readUsagePID(tcpdumpPID)) + "\n").getBytes();
                if (MainActivity.isLoggingAppSelf)
                    cpuAppSelfStuff = (mTime + " " + parseProcPIDStat(readUsagePID(appSelfPID)) + "\n").getBytes();
                if (wifiRSS != 0)
                    ssStuff = (mTime + " wifi " + wifiRSS + "\n").getBytes();
                try {
                    if (isRunning) {
                        if (wifiRSS != 0) {
//                            Log.d(TAG, "Wrote stuff: " + ssStuff);
                            os_ss.write(ssStuff);
                        }
                        os_cpu.write(cpuStuff);
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
                try {
                    Thread.sleep(MainActivity.time_wait_for);
                } catch (Exception unimportant) {
                    Log.w(TAG, "Can't wait..");
                }
            }
            isRunningPollingThread = false;
        }
    }

    /**
     * my initialization
     */
    public void initialization() {
        // run tcpdump
        if (MainActivity.isUsingTCPDump) {
            try {
                Runtime.getRuntime().exec("su -c " + MainActivity.binaryFolderPath
                                + "tcpdump -i " + MainActivity.tcpdumpInterface
                                + " -w " + MainActivity.outFolderPath + "/tcpdump_wifionly_"
                                + MainActivity.btn_click_time + " &"
                ).waitFor();
                if (MainActivity.isVerbose) {
                    Log.d(TAG, "TCPDump started");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (MainActivity.isLoggingTCPDump)
                tcpdumpPID = Utilities.getMyPID(MainActivity.binary_tcpdump, true);
        }
        if (MainActivity.isLoggingAppSelf)
            appSelfPID = Utilities.getMyPID("offloading", true);
        if (!Utilities.canWriteOnExternalStorage()) {
            MainActivity.myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append("Can't write sdcard\n");
                }
            });
            onDestroy();
        }
        // get the initial WiFi signal strength
        wm = (WifiManager) this.getSystemService(WIFI_SERVICE);
        if (!wm.isWifiEnabled() && MainActivity.isVerbose) {
            MainActivity.myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append("WiFi remains OFF!\n");
                }
            });
            wifiRSS = 0;
        } else {
            wifiRSS = wm.getConnectionInfo().getRssi();
            this.registerReceiver(this.myWifiReceiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        }
        // create folder
        File mDir = new File(MainActivity.outFolderPath);
//        mDir.mkdir();
        if (wifiRSS != 0) {
//            Log.d(TAG, "wifi rss is not 0");
            ssFileName = MainActivity.btn_click_time.concat(".ss");
        }
        cpuFileName = MainActivity.btn_click_time.concat(".cpuRaw");
        if (MainActivity.wifiDriverPID != -1)
            cpuWiFiDriverPIDFileName = MainActivity.btn_click_time.concat(".cpuPID");
        if (MainActivity.isLoggingPerProcPID)
            cpuPerProcPIDFileName = MainActivity.btn_click_time.concat(".cpuProcPID");
        if (tcpdumpPID != -1)
            cpuTCPDumpFileName = MainActivity.btn_click_time.concat(".cpuTCPDump");
        if (MainActivity.isLoggingAppSelf)
            cpuAppSelfFileName = MainActivity.btn_click_time.concat(".cpuAppSelf");
        try {
            if (wifiRSS != 0) {
//                Log.d(TAG, "create os_ss handler");
                File tmp = new File(mDir, ssFileName);
                os_ss = new FileOutputStream(tmp);
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
//        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy(){
        isRunning = false;
        if (wifiRSS != 0)
            this.unregisterReceiver(this.myWifiReceiver);
        // kill tcpdump
        if (MainActivity.isUsingTCPDump) {
            try {
                Runtime.getRuntime().exec("su -c killall -9 tcpdump").waitFor();
                if (MainActivity.isVerbose)
                    Log.d(TAG, "TCPDump ended");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (MainActivity.isLoggingTCPDump)
                tcpdumpPID = -1;
        }
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

    private BroadcastReceiver myWifiReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent arg1){
            wifiRSS = arg1.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
            if (MainActivity.isVerbose) {
                Log.d(TAG, "WiFi RSSI: " + wifiRSS);
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
            String load = reader.readLine();
            raws[0] = load;
            load = reader.readLine();
            raws[1] = load;
            if (MainActivity.coreNum > 1) {
                load = reader.readLine();
                raws[2] = load;
            }
            if (MainActivity.coreNum > 2) {
                load = reader.readLine();
                raws[3] = load;
            }
            if (MainActivity.coreNum > 3) {
                load = reader.readLine();
                raws[4] = load;
            }
            reader.close();
        } catch (IOException unimportant) {
            Log.w(TAG, "exception on readUsage (faster)");
        }
    }

    private static String readUsagePID(int currentPID) {
        if (currentPID == -1) return null;
        // changed by Yanzi
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/" + currentPID +"/stat", "r");
            String load = reader.readLine();
            reader.close();
            return load;
        } catch (IOException unimportant) {
            Log.w(TAG, "exception on readUsagePID on pid: " + currentPID);
        }
        return null;
    }

    /**
     * get the frequency of the cpu
     * @param cpuid which cpu
     * @return long
     */
    private static long cpuFrequency(int cpuid) {
        try {
            String pathFile = "/sys/devices/system/cpu/cpu" + cpuid + "/cpufreq/scaling_cur_freq";
            RandomAccessFile reader = new RandomAccessFile(pathFile, "r");
            String load = reader.readLine();
            String[] toks = load.split("\\s+");
            long cpuScaling = Long.parseLong(toks[0]);
            reader.close();
            return cpuScaling;
        } catch (FileNotFoundException unimportant) {
//            Log.w(TAG, "exception on cpuFreq");
            return -1;
        } catch (IOException unimportant) {
//            Log.w(TAG, "exception on cpuFreq");
            return -1;
        }
    }
}
