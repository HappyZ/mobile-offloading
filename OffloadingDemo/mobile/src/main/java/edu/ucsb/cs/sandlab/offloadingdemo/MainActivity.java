package edu.ucsb.cs.sandlab.offloadingdemo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends Activity {
    // unchanged stuff
    protected static final String sshlinklab = "ssh linklab@hotcrp.cs.ucsb.edu -i /data/.ssh/id_rsa -o StrictHostKeyChecking=no";
    protected static final String sshlinklablocal = "ssh linklab@128.111.68.220 -i /data/.ssh/id_rsa -o StrictHostKeyChecking=no";
    protected static final String udpserver_pathport = "~/mobileRDMABeach/UDPServer 32000 ";
    protected static final String binaryFolderPath = "/data/local/tmp/";
    protected static final String binary_tcpdump = "tcpdump";
    protected static final int oneMB = 1048576;
    private static final String TAG = "MainActivity";
    private static final int mVersion = Build.VERSION.SDK_INT;
    // the configs
    protected static boolean isForcingCPU0 = false;
    protected static boolean isVerbose = true;
    protected static boolean isLocal = false;
    protected static boolean isLoggingTCPDump = false;
    protected static boolean isUsingTCPDump = true;
    protected static boolean isLoggingPerProcPID = false;
    protected static boolean isLoggingAppSelf = false;
    protected static int time_wait_for = 100; // ms
    protected static int wifiDriverPID = -1;
    // maintained variables
    private Button btn_startTransmit, btn_startReceive;
    private Button btn_setByte2send, btn_setRepeatTimes, btn_setTCPDumpInterface,
            btn_clearStatus, btn_setLogFreq, btn_setOthers;
    private CharSequence[] existedItems;
    private CharSequence[] existedItemsThrpt;
    private WifiManager wm;
    private Intent intentSSLogger;
    protected static int coreNum = 1;
    protected static int perProcPID = -1;
    protected static int UDPfinishTime = 0;
    protected static double reportedFinishTime = 0.0;
    protected static int repeatCounts = 3;
    protected static int bytes2send = 10*oneMB; // default 10MB
    protected static int currentBandwidth = 20000000; // bytes per sec, default unlimited (not for loopback)
    protected static TextView txt_results;
    protected static Handler myHandler;
    protected static String RXportNum = "4445";
    protected static String outFolderPath;
    protected static String btn_click_time;
    protected static String tcpdumpInterface = "wlan0";
    protected static String binary_TX_Normal = "normal";
    protected static String binary_TX_NormalUDP = "normal_udp";
    protected static String binary_TX_Sendfile = "sendfile";
    protected static String binary_TX_Splice = "splice";
    protected static String binary_TX_RawNormal = "bypassl3";
    protected static final String binary_TX_RawSplice = "";
    protected static String binary_RX_Normal = "normal_recv";
    protected static String binary_RX_NormalUDP = "normal_udp_recv";
    protected static final String binary_RX_Sendfile = "";
    protected static String binary_RX_Splice = "splice_recv";
    protected static String binary_RX_RawNormal = "bypassl3_recv";
    protected static boolean isUsingWifi = true;
    protected static boolean isRunning_TX_Normal = false;
    protected static boolean isRunning_TX_NormalUDP = false;
    protected static boolean isRunning_TX_Sendfile = false;
    protected static boolean isRunning_TX_Splice = false;
    protected static boolean isRunning_TX_RawNormal = false;
    protected static boolean isRunning_TX_RawSplice = false;
    protected static boolean isRunning_RX_Normal = false;
    protected static boolean isRunning_RX_NormalUDP = false;
    protected static boolean isRunning_RX_Sendfile = false;
    protected static boolean isRunning_RX_Splice = false;
    protected static boolean isRunning_RX_RawNormal = false;
    /**
     * Check whether a service is running
     * @param serviceClass
     * @return true/false
     */
    protected boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if service is still running in the background, if so tell me in logcat
     */
    private void myServiceCheck() {
        if (!isVerbose) return;
        if (isServiceRunning(SSLogger.class)) {
            Log.d(TAG, "SSLogger running..");
        } else {
            Log.d(TAG, "SSLogger stopped..");
        }
    }

    private boolean checkBinaryFilesExist() {
        String missingFiles = "";
        if (!Utilities.fileExist(binaryFolderPath + binary_tcpdump))
            missingFiles += binary_tcpdump;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Normal))
            missingFiles += " " + binary_TX_Normal;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Normal + "_lo"))
            missingFiles += " " + binary_TX_Normal + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_NormalUDP))
            missingFiles += " " + binary_TX_NormalUDP;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_NormalUDP + "_lo"))
            missingFiles += " " + binary_TX_NormalUDP + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Sendfile))
            missingFiles += " " + binary_TX_Sendfile;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Sendfile + "_lo"))
            missingFiles += " " + binary_TX_Sendfile + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Splice))
            missingFiles += " " + binary_TX_Splice;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_RawNormal))
            missingFiles += " " + binary_TX_RawNormal;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_RawNormal + "_lo"))
            missingFiles += " " + binary_TX_RawNormal + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_Normal))
            missingFiles += " " + binary_RX_Normal;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_Normal + "_lo"))
            missingFiles += " " + binary_RX_Normal + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_NormalUDP))
            missingFiles += " " + binary_RX_NormalUDP;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_NormalUDP + "_lo"))
            missingFiles += " " + binary_RX_NormalUDP + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_Splice))
            missingFiles += " " + binary_RX_Splice;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_Splice + "_lo"))
            missingFiles += " " + binary_RX_Splice + "_lo";
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_RawNormal))
            missingFiles += " " + binary_RX_RawNormal;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_RawNormal + "_lo"))
            missingFiles += " " + binary_RX_RawNormal + "_lo";

        if (!missingFiles.equals("")) {
            final String mFiles = missingFiles;
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    txt_results.setText("Failed to find following files:\n" + mFiles);
                    btn_startTransmit.setEnabled(false);
                    btn_startReceive.setEnabled(false);
                }
            });
            return false;
        } else {
            btn_startTransmit.setEnabled(true);
            btn_startReceive.setEnabled(true);
        }
        return true;
    }

    /**
     * start the record
     * @param myflag
     */
    protected void startRecording(boolean myflag) {
        final boolean flagRecv = myflag;
        final ArrayList<Integer> selectedItems = new ArrayList<>();
        AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
        adb.setMultiChoiceItems(existedItems, null, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if (which == 5 || (flagRecv && which == 2) || (mVersion < 21 && which == 3)) {
                    Toast.makeText(MainActivity.this, "not working", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isChecked) {
                    selectedItems.add(which);
                } else if (selectedItems.contains(which)) {
                    selectedItems.remove(Integer.valueOf(which));
                }
            }
        });
        adb.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            //            Process su = null;
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selectedItems.size() < 1) {
                    Toast.makeText(MainActivity.this, "Nothing is selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                final ArrayList<Integer> selectedItemsThrpt = new ArrayList<>();
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setMultiChoiceItems(existedItemsThrpt, null, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            selectedItemsThrpt.add(which);
                        } else if (selectedItemsThrpt.contains(which)) {
                            selectedItemsThrpt.remove(Integer.valueOf(which));
                        }
                    }
                });
                adb.setPositiveButton("Go!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (selectedItemsThrpt.size() < 1) {
                            Toast.makeText(MainActivity.this, "Nothing is selected", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (isVerbose) {
                            Log.d(TAG, "selected variations " + selectedItemsThrpt);
                        }
                        Utilities.estimateTime(repeatCounts, selectedItems.size(), bytes2send, selectedItemsThrpt);

                        // power management
                        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                        final PowerManager.WakeLock wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "MyWakelockTag");

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                String[] commd = new String[3];
                                commd[0] = "su";
                                commd[1] = "&&";
//                                wakelock.acquire();
                                // change screen brightness to 0
//                                Settings.System.putInt(MainActivity.this.getContentResolver(),
//                                        Settings.System.SCREEN_BRIGHTNESS, 0);
//                                final WindowManager.LayoutParams lp = getWindow().getAttributes();
//                                lp.screenBrightness = 0.0f;// 100 / 100.0f;
                                try {
                                    Runtime.getRuntime().exec("su -c echo 0 > /sys/class/lcd/panel/lcd_power").waitFor();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                myHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.txt_results.append("Begin..\n");
//                                        getWindow().setAttributes(lp);
                                    }
                                });
                                // prepare
                                try {
                                    Runtime.getRuntime().exec("su -c killall -9 normal").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_udp").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_udp_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 sendfile").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 sendfile_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 splice").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 splice_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 bypassl3").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 bypassl3_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_recv").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_recv_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_udp_recv").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 normal_udp_recv_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 splice_recv").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 splice_recv_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 bypassl3_recv").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 bypassl3_recv_lo").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 tcpdump").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 TCPReceiver_mobile").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 TCPSender_mobile").waitFor();
                                    Runtime.getRuntime().exec("su -c killall -9 UDPServer_mobile").waitFor();
                                    if (isLocal) {
                                        if (flagRecv)
                                            Runtime.getRuntime().exec("su && /data/local/tmp/Run_for_Download.sh").waitFor();
                                        else
                                            Runtime.getRuntime().exec("su && /data/local/tmp/Run_for_Upload.sh").waitFor();
                                    } else {
                                        myHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                MainActivity.txt_results.append("In case you forget, remember to run " + ((flagRecv) ? "Run_for_Download.sh" : "Run_for_Upload.sh") + "\n");
                                            }
                                        });
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                // start iteration
                                for (int k = 0; k < selectedItemsThrpt.size(); ++k) {
                                    int myI = selectedItemsThrpt.get(k);
                                    currentBandwidth = Utilities.findCorrespondingThrpt(myI);
                                    RXportNum = Integer.toString(4445 - myI + 24);
                                    if (isVerbose) {
                                        Log.d(TAG, "bandwidth is set to " + currentBandwidth
                                                + "\nRXportNum is set to " + RXportNum);
                                    }
                                    // start
                                    try {
                                        commd[2] = "cd " + outFolderPath
                                                + " && ls | grep -v '.tar.gz' | busybox xargs rm -rf";
                                        Runtime.getRuntime().exec(commd).waitFor();
                                        commd[2] = "mkdir -p";
                                        for (int i = 0; i < selectedItems.size(); ++i) {
                                            commd[2] += " " + outFolderPath + "/" + existedItems[selectedItems.get(i)];
                                        }
                                        Runtime.getRuntime().exec(commd).waitFor();
                                        Thread.sleep(1000);
                                        // start repeating
                                        int waitTimeSec = 0;
                                        for (int i = 0; i < repeatCounts; ++i) {
                                            for (int j = 0; j < selectedItems.size(); ++j) {
                                                if (flagRecv && (selectedItems.get(j) == 1 || selectedItems.get(j) == 4)) {
                                                    if (isLocal) {
                                                        waitTimeSec = (Math.max(bytes2send / currentBandwidth + 20, 20));
                                                        Runtime.getRuntime().exec("su -c /data/local/tmp/UDPServer_mobile 32000 "
                                                                + currentBandwidth + " " + waitTimeSec + " &").waitFor();
                                                    } else {
                                                        waitTimeSec = (Math.max(bytes2send / currentBandwidth + 20, 60));
                                                        Process proc = Runtime.getRuntime().exec("su");
                                                        DataOutputStream os = new DataOutputStream(proc.getOutputStream());
                                                        if (isUsingWifi) {
                                                            os.writeBytes(sshlinklablocal + "\n");
                                                            os.flush();
                                                            Thread.sleep(5001);
                                                        } else {
                                                            os.writeBytes(sshlinklab + "\n");
                                                            os.flush();
                                                            Thread.sleep(10001);
                                                        }
                                                        os.writeBytes(udpserver_pathport + currentBandwidth + " " + waitTimeSec + " &\n");
                                                        os.flush();
                                                        Thread.sleep(1001);
                                                        os.writeBytes("exit\n");
                                                        os.flush();
                                                        os.writeBytes("exit\n");
                                                        os.flush();
                                                        Thread.sleep(501);
                                                        os.close();
                                                        proc.destroy();
                                                        Thread.sleep(805);
                                                    }
                                                }
                                                Thread.sleep(1000);
                                                btn_click_time = Long.toString(System.currentTimeMillis());
                                                startService(intentSSLogger);
                                                Thread.sleep(1000);
                                                myServiceCheck();
                                                if (flagRecv) { // Starting Receiving
                                                    switch (selectedItems.get(j)) {
                                                        case 0: // normal
                                                            new Thread(new Thread_RX_CNormal()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_RX_Normal) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 1: // normal udp
                                                            new Thread(new Thread_RX_CNormalUDP()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_RX_NormalUDP) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 2: // sendfile unimplemented
                                                            break;
                                                        case 3: // splice
                                                            new Thread(new Thread_RX_CSplice()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_RX_Splice) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 4: // rawsocket
                                                            new Thread(new Thread_RX_CRawNormal()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_RX_RawNormal) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        default: // do nothing
                                                            break;
                                                    }
                                                } else { // Starting Transmitting
                                                    switch (selectedItems.get(j)) {
                                                        case 0: // normal
                                                            new Thread(new Thread_TX_CNormal()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_TX_Normal) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 1: // normal udp
                                                            new Thread(new Thread_TX_CNormalUDP()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_TX_NormalUDP) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 2: // sendfile
                                                            new Thread(new Thread_TX_CSendfile()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_TX_Sendfile) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 3: // splice
                                                            new Thread(new Thread_TX_CSplice()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_TX_Splice) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 4: // rawsocket
                                                            new Thread(new Thread_TX_CRawNormal()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_TX_RawNormal) {
                                                                Thread.sleep(1005);
                                                            }
                                                            break;
                                                        case 5: // rawsocket splice unimplemented
                                                            break;
                                                        default: // do nothing
                                                            break;
                                                    }
                                                }
                                                stopService(intentSSLogger);
                                                myServiceCheck();
                                                Thread.sleep(1000);
                                                Runtime.getRuntime().gc();
                                                System.gc();
                                                if (flagRecv && (selectedItems.get(j) == 1 || selectedItems.get(j) == 4)) {
                                                    Thread.sleep(Math.abs(waitTimeSec*1000 - UDPfinishTime));
                                                }
                                                commd[2] = "cd " + outFolderPath + " && mv *" + btn_click_time
                                                        + "* " + existedItems[selectedItems.get(j)] + "/";
                                                Runtime.getRuntime().exec(commd).waitFor();
                                                Log.d(TAG, "Finished " + (currentBandwidth / 1000000.0) + "MBps, "
                                                        + i + "th repeat on " + existedItems[selectedItems.get(j)]
                                                        + ", t="+reportedFinishTime+"ms");
                                                Thread.sleep(5000);
                                            }
                                        }
                                        // parse and zip it
                                        for (int i = 0; i < selectedItems.size(); ++i) {
                                            if (Utilities.parseCPUforFolder((String) existedItems[selectedItems.get(i)])) {
                                                String tarName = ((flagRecv) ? "download_" : "upload_")
                                                        + existedItems[selectedItems.get(i)] + "_"
                                                        + (bytes2send / 1024) + "KB_" + repeatCounts + "repeats_thrpt_"
                                                        + (currentBandwidth / 1000000.0) + "MBps_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
                                                        + ".tar.gz";
                                                commd[2] = "cd " + outFolderPath + "/"
                                                        + existedItems[selectedItems.get(i)]
                                                        + " && busybox tar -czf ../" + tarName + " *";
                                                Runtime.getRuntime().exec(commd).waitFor();
                                            } else {
                                                final CharSequence failedFolderName = existedItems[selectedItems.get(i)];
                                                myHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        txt_results.append("Failed for folder "
                                                                + failedFolderName + "\n");
                                                    }
                                                });
                                            }
                                        }
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
//                                wakelock.release();
                                // change screen brightness back
//                                Settings.System.putInt(MainActivity.this.getContentResolver(),
//                                        Settings.System.SCREEN_BRIGHTNESS, 200);
//                                lp.screenBrightness = 50;// 50 / 100.0f;
                                try {
                                    Runtime.getRuntime().exec("su -c echo 1 > /sys/class/lcd/panel/lcd_power").waitFor();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                myHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        txt_results.append("All Done\n");
//                                        getWindow().setAttributes(lp);
                                    }
                                });
                            }
                        }).start();
                    }
                });
                adb.setNegativeButton("Cancel", null);
                adb.create().show();
            }
        });
        adb.setNegativeButton("Cancel", null);
        adb.create().show();
    }

    /**
     * Intialize parameters etc.
     */
    protected void initialization() {
        // must have root privilege in order to run
        try {
            Runtime.getRuntime().exec("su");
        } catch (Throwable e) {
            Toast.makeText(this, R.string.warn_root, Toast.LENGTH_LONG).show();
        }
        // handler that updates the ui at main thread
        // it's used in sslogger thus will be modded in receiver activity also
        // do not modify this
        myHandler = new Handler();
        existedItems = new CharSequence[] {
                "Socket_Normal", "Socket_NormalUDP", "Socket_Sendfile",
                "Socket_Splice", "RawSocket_Normal"
        };
        existedItemsThrpt = new CharSequence[]{
                "50KB", "100KB", "250KB", "0.5MB", "0.75MB", "1MB", "1.5MB", "2MB", // 0-7
                "2.5MB", "3MB", "3.5MB", "4MB", "4.5MB", "5MB", "5.5MB", "6MB",     // 8-15
                "6.5MB", "7MB", "7.5MB", "8MB", "8.5MB", "9MB", "9.5MB", "10MB",    // 16-23
                "Unlimited",                                                        // 24
                "15MB", "20MB", "25MB", "30MB", "35MB", "40MB", "45MB", "50MB",     // 25-32
                "55MB", "60MB", "65MB", "70MB", "75MB", "80MB", "85MB", "90MB",     // 33-40
                "95MB", "100MB",                                                    // 41-42
                "11MB", "13MB"                                                      // 43-44
        };
        intentSSLogger = new Intent(this, SSLogger.class);
        wm = (WifiManager) this.getSystemService(WIFI_SERVICE);
        // assignments
        binary_TX_Normal = "normal";
        binary_TX_NormalUDP = "normal_udp";
        binary_TX_Sendfile = "sendfile";
        binary_TX_RawNormal = "bypassl3";
        binary_TX_Splice = "splice";
        binary_RX_Normal = "normal_recv";
        binary_RX_NormalUDP = "normal_udp_recv";
        binary_RX_Splice = "splice_recv";
        binary_RX_RawNormal = "bypassl3_recv";
        coreNum = Utilities.getNumCores();
        outFolderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SSLogger";
        isUsingWifi = (wm.isWifiEnabled())?true:false;
        // find view elements
        txt_results = (TextView) findViewById(R.id.txt_results);
        btn_startTransmit = (Button) findViewById(R.id.btn_startTransmit);
        btn_startReceive = (Button) findViewById(R.id.btn_startReceive);
        btn_setByte2send = (Button) findViewById(R.id.btn_setByte2send);
        btn_setRepeatTimes = (Button) findViewById(R.id.btn_setRepeatTimes);
        btn_setTCPDumpInterface = (Button) findViewById(R.id.btn_setTCPDumpInterface);
        btn_setOthers = (Button) findViewById(R.id.btn_setOthers);
        btn_setLogFreq = (Button) findViewById(R.id.btn_setLogFreq);
        btn_clearStatus = (Button) findViewById(R.id.btn_clearStatus);
        if (coreNum > 2) {
            txt_results.append("Only support 2 cores now! Contact Yanzi to add "+coreNum+" cores support!\n");
        }
        txt_results.append(isUsingWifi?getString(R.string.stat_wifion):getString(R.string.stat_wifioff));
        // click listener
        btn_startTransmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording(false);
            }
        });
        btn_startReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording(true);
            }
        });
        btn_setByte2send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CharSequence[] mItems={
                        (bytes2send == (oneMB/2))?"Current: 0.5MB":"0.5MB",
                        (bytes2send == (oneMB))?"Current: 1MB":"1MB",
                        (bytes2send == (2*oneMB))?"Current: 2MB":"2MB",
                        (bytes2send == (5*oneMB))?"Current: 5MB":"5MB",
                        (bytes2send == (10*oneMB))?"Current: 10MB":"10MB",
                        (bytes2send == (20*oneMB))?"Current: 20MB":"20MB",
                        (bytes2send == (50*oneMB))?"Current: 50MB":"50MB",
                        (bytes2send == (100*oneMB))?"Current: 100MB":"100MB",
                        (bytes2send == (200*oneMB))?"Current: 200MB":"200MB"};
                AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
                mDialog.setItems(mItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 3) {
                            bytes2send = oneMB/2;
                            for (int i = 0; i < which; ++i)
                                bytes2send *= 2;
                        } else if (which < 6) {
                            bytes2send = 5 * oneMB;
                            for (int i = 3; i < which; ++i)
                                bytes2send *= 2;
                        } else if (which < 9) {
                            bytes2send = 50 * oneMB;
                            for (int i = 6; i < which; ++i)
                                bytes2send *= 2;
                        } else {
                            bytes2send = 10 * oneMB; // default 10MB
                        }
                        if (isVerbose) {
                            Log.d(TAG, "outgoing/incoming bytes is set to " + bytes2send);
                        }
                    }
                });
                mDialog.create().show();
            }
        });
        btn_setRepeatTimes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CharSequence[] mItems={
                        (repeatCounts == 1)?"Current: once (1)":"once (1)",
                        (repeatCounts == 2)?"Current: twice (2)":"twice (2)",
                        (repeatCounts == 3)?"Current: tripple (3)":"tripple (3)",
                        (repeatCounts == 4)?"Current: quad (4)":"quad (4)",
                        (repeatCounts == 5)?"Current: quint (5)":"quint (5)",
                        (repeatCounts == 10)?"Current: 10":"10",
                        (repeatCounts == 20)?"Current: 20":"20"};
                AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
                mDialog.setItems(mItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        repeatCounts = which + 1;
                        if (which == 5) repeatCounts = 10;
                        else if (which == 6) repeatCounts = 20;
                        if (isVerbose) {
                            Log.d(TAG, "repeatCounts is set to " + repeatCounts);
                        }
                    }
                });
                mDialog.create().show();
            }
        });
        btn_setTCPDumpInterface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> out = new ArrayList<>();
                if (!isUsingTCPDump) {
                    out.add("Current: TCPDump OFF");
                } else {
                    out.add("Disable TCPDump");
                }
                try {
                    Process proc = Runtime.getRuntime().exec("ls /sys/class/net");
                    proc.waitFor();
                    String line;
                    BufferedReader is = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    while ((line = is.readLine()) != null) {
                        if (isUsingTCPDump && line.equals(tcpdumpInterface)) {
                            out.add("Current: " + line);
                        } else {
                            out.add(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final CharSequence mTmp[] = out.toArray(new CharSequence[out.size()]);
                AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
                mDialog.setItems(mTmp, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            isUsingTCPDump = false;
                            if (isVerbose) {
                                myHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.txt_results.append("TCPDump is disabled\n");
                                    }
                                });
                                Log.d(TAG, "TCPDump is disabled");
                            }
                        } else {
                            tcpdumpInterface = ((String) mTmp[which]).replace("Current: ", "");
                            isUsingTCPDump = true;
                            Toast.makeText(MainActivity.this, "TCPDump interface is changed to " + tcpdumpInterface, Toast.LENGTH_SHORT).show();
                            if (isVerbose) {
                                Log.d(TAG, "TCPDump interface is set to " + tcpdumpInterface);
                            }
                        }
                    }
                });
                mDialog.create().show();
            }
        });
        btn_setOthers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CharSequence[] items = {"Verbose Mode", "Only Run Locally", "CPULog (Per Proc)", "CPULog (WiFi Driver)", "CPULog (This App)", "CPULog (TCPDump)", "Force on CPU0"};
                boolean[] checkedItems = {isVerbose, isLocal, isLoggingPerProcPID, (wifiDriverPID!=-1), isLoggingAppSelf, isLoggingTCPDump, isForcingCPU0};
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        switch (which) {
                            case 0:
                                isVerbose = isChecked;
                                Toast.makeText(MainActivity.this, "Set to be " + (isVerbose ? "verbose" : "NOT verbose"), Toast.LENGTH_SHORT).show();
                                break;
                            case 1:
                                isLocal = isChecked;
                                binary_TX_Normal = isLocal ? "normal_lo" : "normal";
                                binary_TX_NormalUDP = isLocal ? "normal_udp_lo" : "normal_udp";
                                binary_TX_Sendfile = isLocal ? "sendfile_lo" : "sendfile";
                                binary_TX_Splice = isLocal ? "splice_lo" : "splice";
                                binary_TX_RawNormal = isLocal ? "bypassl3_lo" : "bypassl3";
                                binary_RX_Normal = isLocal ? "normal_recv_lo" : "normal_recv";
                                binary_RX_NormalUDP = isLocal ? "normal_udp_recv_lo" : "normal_udp_recv";
                                binary_RX_Splice = isLocal ? "splice_recv_lo" : "splice_recv";
                                binary_RX_RawNormal = isLocal ? "bypassl3_recv_lo" : "bypassl3_recv";
                                if (isLocal) {
                                    isUsingTCPDump = false;
                                    Toast.makeText(MainActivity.this, "Remember to set IP to 192.168.1.15\n" +
                                            "Will start TCPSdr/Rcvr locally\ntcpdump disabled", Toast.LENGTH_LONG).show();
                                } else {
                                    isUsingTCPDump = true;
                                    tcpdumpInterface = "wlan0";
                                    Toast.makeText(MainActivity.this, "Back to original\ntcpdump enabled to wlan0", Toast.LENGTH_LONG).show();
                                }
                                break;
                            case 2:
                                isLoggingPerProcPID = isChecked;
                                Toast.makeText(MainActivity.this, isLoggingPerProcPID?"Will log per process cpu":"Will NOT log per process cpu", Toast.LENGTH_SHORT).show();
                                break;
                            case 3:
                                wifiDriverPID = isChecked?Utilities.getMyPID("dhd_dpc", true):-1;
                                Toast.makeText(MainActivity.this, (wifiDriverPID!=-1)?"Will log wifi driver cpu":"Will NOT log wifi driver cpu", Toast.LENGTH_SHORT).show();
                                break;
                            case 4:
                                isLoggingAppSelf = isChecked;
                                Toast.makeText(MainActivity.this, isLoggingAppSelf?"Will log app itself cpu":"Will NOT log app itself cpu", Toast.LENGTH_SHORT).show();
                                break;
                            case 5:
                                isLoggingTCPDump = isChecked;
                                Toast.makeText(MainActivity.this, isLoggingTCPDump?"Will log tcpdump cpu":"Will NOT log tcpdump cpu", Toast.LENGTH_SHORT).show();
                                break;
                            case 6:
                                isForcingCPU0 = isChecked;
                                Toast.makeText(MainActivity.this, (isForcingCPU0?"Force running on cpu 0":"Will run on any cpu"), Toast.LENGTH_SHORT).show();
                                break;
                        }
                    }
                });
                adb.setPositiveButton("OK", null);
                adb.create().show();
            }
        });
        btn_clearStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txt_results.setText("");
                    }
                });
            }
        });
        btn_setLogFreq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CharSequence[] mItems={
                        (time_wait_for == 50)?"Current: every 50ms":"every 50ms",
                        (time_wait_for == 100)?"Current: every 100ms":"every 100ms",
                        (time_wait_for == 200)?"Current: every 200ms":"every 200ms",
                        (time_wait_for == 500)?"Current: every 500ms":"every 500ms",
                        (time_wait_for == 1000)?"Current: every 1s":"every 1s"};
                AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
                mDialog.setItems(mItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                time_wait_for = 50;
                                break;
                            case 1:
                                time_wait_for = 100;
                                break;
                            case 2:
                                time_wait_for = 200;
                                break;
                            case 3:
                                time_wait_for = 500;
                                break;
                            case 4:
                                time_wait_for = 1000;
                                break;
                            default:
                                time_wait_for = 100; // default 100ms
                        }
                        if (isVerbose) {
                            myHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MainActivity.txt_results.append("time_wait_for is set to " + time_wait_for + "ms\n");
                                }
                            });
                            Log.d(TAG, "time_wait_for is set to " + time_wait_for + "ms");
                        }
                    }
                });
                mDialog.create().show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialization();
        checkBinaryFilesExist();
    }

}
