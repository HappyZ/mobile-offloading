package edu.ucsb.cs.sandlab.offloadingdemo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.plinzen.rttmanager.RttManagerCompat;

public class MainActivity extends Activity {
    // unchanged stuff
    protected static final String binaryFolderPath = "/data/local/tmp/";
    protected static final String binary_tcpdump = "tcpdump";
    private static final String TAG = "MainActivity";
    private static final int mVersion = Build.VERSION.SDK_INT;
    // the configs
    protected static String remoteIP = "192.168.2.1";
    protected static String remoteMAC = "4e:32:75:f8:7e:64";
    // default variables
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
    private Button btn_startTransmit, btn_startReceive, btn_measureBg;
    private Button btn_setByte2send, btn_setRepeatTimes, btn_setTCPDumpInterface,
            btn_clearStatus, btn_setLogFreq, btn_setOthers, btn_ranging;
    private WifiManager wm;
    private Intent intentSSLogger;
    static BroadcastReceiver my_recv;
    protected static int coreNum = 1;
    protected static int perProcPID = -1;
    protected static int UDPfinishTime = 0;
    protected static double reportedFinishTime = 0.0;
    protected static int repeatCounts = 3;
    protected static int bytes2send = 100 * Utilities.oneMB; // default 100MB
    protected static int currentBandwidth = -1; // bps, default is -1, indicating unlimited
    protected static TextView txt_results;
    protected static Handler myHandler;
    protected static String outFolderPath;
    protected static String btn_click_time;
    protected static String tcpdumpInterface = "wlan0"; // default "wlan0"
    protected static String binary_TX_Normal;
    protected static String binary_TX_NormalUDP;
    protected static String binary_TX_Sendfile;
    protected static String binary_TX_UDPSendfile;
    protected static String binary_TX_Splice;
    protected static String binary_TX_RawNormal;
    protected static final String binary_TX_RawSplice = "";
    protected static String binary_RX_Normal;
    protected static String binary_RX_NormalUDP;
    protected static final String binary_RX_Sendfile = "";
    protected static String binary_RX_Splice;
    protected static String binary_RX_RawNormal;
    protected static boolean isUsingWifi = true;
    protected static boolean isRunning_TX_Normal = false;
    protected static boolean isRunning_TX_NormalUDP = false;
    protected static boolean isRunning_TX_Sendfile = false;
    protected static boolean isRunning_TX_UDPSendfile = false;
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
     * @param serviceClass:
     * @return true/false
     */
    protected boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
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

    private void killAllBinaries() {
        try {
            Runtime.getRuntime().exec("su -c killall -9 " + binary_TX_Normal).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_TX_NormalUDP).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_TX_Sendfile).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_TX_UDPSendfile).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_TX_Splice).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_TX_RawNormal).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_RX_Normal).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_RX_NormalUDP).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_RX_Splice).waitFor();
            Runtime.getRuntime().exec("su -c killall -9 " + binary_RX_RawNormal).waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkBinaryFilesExist() {
        String missingFiles = "";
        if (!Utilities.fileExist(binaryFolderPath + binary_tcpdump))
            missingFiles += binary_tcpdump;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Normal))
            missingFiles += " " + binary_TX_Normal;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_NormalUDP))
            missingFiles += " " + binary_TX_NormalUDP;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Sendfile))
            missingFiles += " " + binary_TX_Sendfile;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_UDPSendfile))
            missingFiles += " " + binary_TX_UDPSendfile;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_Splice))
            missingFiles += " " + binary_TX_Splice;
        if (!Utilities.fileExist(binaryFolderPath + binary_TX_RawNormal))
            missingFiles += " " + binary_TX_RawNormal;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_Normal))
            missingFiles += " " + binary_RX_Normal;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_NormalUDP))
            missingFiles += " " + binary_RX_NormalUDP;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_Splice))
            missingFiles += " " + binary_RX_Splice;
        if (!Utilities.fileExist(binaryFolderPath + binary_RX_RawNormal))
            missingFiles += " " + binary_RX_RawNormal;
        if (!Utilities.fileExist(binaryFolderPath + "bigfile")) {
            try {
                Runtime.getRuntime().exec(
                        "su && dd if=/dev/zero of="
                                + binaryFolderPath + "bigfile"
                                + " count=1 bs=1 seek=$((2 * 1024 * 1024 * 1024 - 1)) "
                                + "&& chmod 755 " + binaryFolderPath + "bigfile").waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            Toast.makeText(this, R.string.txt_created_bigfile, Toast.LENGTH_LONG).show();
        }
        if (!missingFiles.equals("")) {
            final String mFiles = getString(R.string.err_filecheck_failed) + missingFiles;
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    txt_results.setText(mFiles);
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
     * @param myflag:
     */
    protected void startRecording(boolean myflag) {
        AlertDialog.Builder adb;
        final boolean flagRecv = myflag;
        final ArrayList<Integer> selectedItems = new ArrayList<>();

        // first initialize the target ip and mac
        EditText edit_remote_ip = (EditText) findViewById(R.id.remote_ip);
        EditText edit_remote_mac = (EditText) findViewById(R.id.remote_mac);
        String string_remote_ip = edit_remote_ip.getText().toString();
        String string_remote_mac = edit_remote_mac.getText().toString();
        // check if ip is in valid format
        if (Utilities.validIP(string_remote_ip)) {
            remoteIP = string_remote_ip;
        } else {
            Log.d(TAG, string_remote_ip);
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append(
                            "Entered IP is not right, will use " + remoteIP + " instead");
                }
            });
        }
        // check if mac is in valid format
        if (Utilities.validMAC(string_remote_mac)) {
            remoteMAC = string_remote_mac;
        } else {
            Log.d(TAG, string_remote_mac);
            myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append(
                            "Entered MAC is not right, will use " + remoteMAC + " instead");
                }
            });
        }
        Log.d(TAG, "remote IP is set to " + remoteIP);
        Log.d(TAG, "remote MAC is set to " + remoteMAC);

        // then create a dialog for options
        adb = new AlertDialog.Builder(MainActivity.this);
        adb.setMultiChoiceItems(Utilities.existedItems, null,
                new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if ((flagRecv && which == 2) || (mVersion < 21 && which == 3)) {
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

        adb.setPositiveButton(R.string.txt_continue, new DialogInterface.OnClickListener() {
            //            Process su = null;
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selectedItems.size() < 1) {
                    Toast.makeText(MainActivity.this, R.string.err_no_selection, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                final ArrayList<Integer> selectedItemsThrpt = new ArrayList<>();
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setMultiChoiceItems(Utilities.existedItemsThrpt, null,
                        new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            selectedItemsThrpt.add(which);
                        } else if (selectedItemsThrpt.contains(which)) {
                            selectedItemsThrpt.remove(Integer.valueOf(which));
                        }
                    }
                });
                adb.setPositiveButton(R.string.txt_go, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (selectedItemsThrpt.size() < 1) {
                            Toast.makeText(
                                    MainActivity.this,
                                    R.string.err_no_selection,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (isLocal) {
                            Toast.makeText(
                                    MainActivity.this,
                                    R.string.err_unimplemented,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (isVerbose) {
                            Log.d(TAG, "selected variations " + selectedItemsThrpt);
                        }

                        Utilities.estimateTime(
                                repeatCounts, selectedItems.size(), bytes2send, selectedItemsThrpt);

                        // TODO: take out the thread
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                String[] commd = new String[3];
                                commd[0] = "su";
                                commd[1] = "&&";

                                // change screen brightness to 0
                                Utilities.switchScreenStatus();

                                myHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.txt_results.append("Begin..\n");
//                                        getWindow().setAttributes(lp);
                                    }
                                });

                                // prepare
                                try {
                                    killAllBinaries();
                                    Runtime.getRuntime().exec(
                                            "su -c killall -9 " + binary_tcpdump).waitFor();
                                    Runtime.getRuntime().exec(
                                            "su -c killall -9 TCPReceiver_mobile").waitFor();
                                    Runtime.getRuntime().exec(
                                            "su -c killall -9 TCPSender_mobile").waitFor();
                                    Runtime.getRuntime().exec(
                                            "su -c killall -9 UDPServer_mobile").waitFor();
//                                    if (isLocal) {
//                                        if (flagRecv)
//                                            Runtime.getRuntime().exec("su && /data/local/tmp/Run_for_Download.sh").waitFor();
//                                        else
//                                            Runtime.getRuntime().exec("su && /data/local/tmp/Run_for_Upload.sh").waitFor();
//                                    } else {
//                                        myHandler.post(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                MainActivity.txt_results.append("In case you forget, remember to run " + ((flagRecv) ? "Run_for_Download.sh" : "Run_for_Upload.sh") + "\n");
//                                            }
//                                        });
//                                    }
                                } catch (InterruptedException | IOException e) {
                                    e.printStackTrace();
                                }

                                // start iteration
                                for (int k = 0; k < selectedItemsThrpt.size(); ++k) {
                                    int myI = selectedItemsThrpt.get(k);
                                    currentBandwidth = Utilities.findCorrespondingThrpt(myI);

                                    Log.d(TAG, "bandwidth is set to " + currentBandwidth
                                            + "\nTCP_port is set to " + Utilities.TCP_port
                                            + "\nUDP_port is set to " + Utilities.UDP_port);

                                    // start
                                    try {
                                        commd[2] = "cd " + outFolderPath
                                                + " && ls | grep -v '.tar.gz' | busybox xargs rm -rf";
                                        Runtime.getRuntime().exec(commd).waitFor();
                                        commd[2] = "mkdir -p";
                                        for (int i = 0; i < selectedItems.size(); ++i) {
                                            commd[2] += " " + outFolderPath + "/"
                                                    + Utilities.existedItems[selectedItems.get(i)];
                                        }

                                        Runtime.getRuntime().exec(commd).waitFor();
                                        Thread.sleep(1000);

                                        // start repeating
                                        int wait_time_sec = 0;
                                        for (int i = 0; i < repeatCounts; ++i) {
                                            for (int j = 0; j < selectedItems.size(); ++j) {
                                                int whichItem = selectedItems.get(j);
                                                if (flagRecv) {
                                                    if (isLocal) {
                                                        wait_time_sec = Math.max(
                                                                bytes2send / currentBandwidth + 20,
                                                                20);
//                                                        Runtime.getRuntime().exec("su -c /data/local/tmp/UDPServer_mobile 32000 "
//                                                                + currentBandwidth + " " + waitTimeSec + " &").waitFor();
                                                    } else {
                                                        wait_time_sec = Math.max(
                                                                bytes2send / currentBandwidth + 20,
                                                                60);
                                                        Process proc = Runtime.getRuntime().exec(
                                                                "su");
                                                        DataOutputStream os = new DataOutputStream(
                                                                proc.getOutputStream());
                                                        os.writeBytes("ssh root@" +
                                                                remoteIP + "\n");
                                                        os.flush();
                                                        Thread.sleep(1000);
                                                        os.writeBytes("x\n");
                                                        os.flush();
                                                        Thread.sleep(1000);
                                                        if (whichItem == 1 || whichItem == 4) {
                                                            os.writeBytes(
                                                                    "" +
                                                                            "\n");
                                                        } else {
                                                            os.writeBytes(
                                                                    "" +
                                                                    "\n");
                                                        }
                                                        os.flush();
                                                        Thread.sleep(1000);
                                                        os.writeBytes("exit\n");
                                                        os.flush();
                                                        os.writeBytes("exit\n");
                                                        os.flush();
                                                        Thread.sleep(500);
                                                        os.close();
                                                        proc.destroy();
                                                        Thread.sleep(500);
                                                    }
                                                }
                                                Thread.sleep(1000);
                                                btn_click_time = Long.toString(
                                                        System.currentTimeMillis());
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
                                                        case 5: // udp sendfile
                                                            new Thread(new Thread_TX_CUDPSendfile()).start();
                                                            Thread.sleep(1005);
                                                            while (isRunning_TX_UDPSendfile) {
                                                                Thread.sleep(1005);
                                                            }
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
                                                    Thread.sleep(Math.abs(wait_time_sec*1000 - UDPfinishTime));
                                                }
                                                commd[2] = "cd " + outFolderPath + " && mv *" + btn_click_time
                                                        + "* " + Utilities.existedItems[selectedItems.get(j)] + "/";
                                                Runtime.getRuntime().exec(commd).waitFor();
                                                Log.d(TAG, "Finished " + (currentBandwidth / 1000000.0) + "Mbps, "
                                                        + (i + 1) + "th repeat on " + Utilities.existedItems[selectedItems.get(j)]
                                                        + ", t="+reportedFinishTime+"s");
                                                Thread.sleep(5000);
                                            }
                                        }
                                        // parse and zip it
                                        for (int i = 0; i < selectedItems.size(); ++i) {
                                            if (Utilities.parseCPUforFolder(
                                                    (String) Utilities.existedItems[selectedItems.get(i)])) {
                                                String tarName = (
                                                        (flagRecv) ? "download_" : "upload_")
                                                        + Utilities.existedItems[selectedItems.get(i)] + "_"
                                                        + (bytes2send / 1024) + "KB_"
                                                        + repeatCounts + "repeats_thrpt_"
                                                        + (currentBandwidth == -1 ? "Unlimited" :
                                                          (currentBandwidth / 1000000.0) + "Mbps_")
                                                        + (new SimpleDateFormat(
                                                                "yyyyMMdd_HHmmss", Locale.US)
                                                            .format(new Date()))
                                                        + ".tar.gz";
                                                commd[2] = "cd " + outFolderPath + "/"
                                                        + Utilities.existedItems[selectedItems.get(i)]
                                                        + " && busybox tar -czf ../"
                                                        + tarName + " *";
                                                Runtime.getRuntime().exec(commd).waitFor();
                                            } else {
                                                final CharSequence failedFolderName =
                                                        Utilities.existedItems[selectedItems.get(i)];
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
                                    } catch (InterruptedException | IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                // change screen brightness back
                                Utilities.switchScreenStatus();

                                // msg indicating all done
                                myHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        txt_results.append("All Done\n");
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


    public void startRanging(ScanResult wifiConfig,
                             RttManagerCompat.RttListener rttListener) throws Throwable {
        final RttManagerCompat.RttParams params = new RttManagerCompat.RttParams();
        params.bssid = wifiConfig.BSSID;
        params.requestType = RttManagerCompat.RTT_TYPE_TWO_SIDED;
        params.frequency = wifiConfig.frequency;
        params.centerFreq0 = wifiConfig.centerFreq0;
        params.centerFreq1 = wifiConfig.centerFreq1;
        params.channelWidth = wifiConfig.channelWidth;
        RttManagerCompat rttManagerCompat = new RttManagerCompat(getApplicationContext());
        final RttManagerCompat.RttCapabilities capabilities = rttManagerCompat.getRttCapabilities();
        myHandler.post(new Runnable() {
            @Override
            public void run() {
                txt_results.append("\n\n" + capabilities.toString() + "\n\n");
            }
        });
        if (capabilities != null) {
            params.LCIRequest = capabilities.lciSupported;
            params.LCRRequest = capabilities.lcrSupported;
        }
        rttManagerCompat.startRanging(new RttManagerCompat.RttParams[]{params}, rttListener);
    }

    protected void wifiRanging(List<ScanResult> results){
        final CharSequence mTmp[] = new CharSequence[results.size()];
        for (int which = 0; which < results.size(); which++) {
            mTmp[which] = results.get(which).BSSID + " " + results.get(which).SSID;
            Log.d("ScanBSSID", (String) mTmp[which]);
        }
        AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
        mDialog.setItems(mTmp, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d("ChosenBSSID", results.get(which).BSSID);
                try {
                    startRanging(results.get(which), new RttManagerCompat.RttListener() {
                        @Override
                        public void onAborted() {}

                        @Override
                        public void onFailure(int reason, String description) {
                            myHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    txt_results.append("failed: " + description);
                                }
                            });
                        }

                        @Override
                        public void onSuccess(RttManagerCompat.RttResult[] results) {
                            myHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    for (int i = 0; i < results.length; i++)
                                        txt_results.append(results[i].toString() + "\n");
                                }
                            });
                        }
                    });
                } catch (Throwable e) {}
            }
        });
        mDialog.create().show();
    }

    /**
     * Initialize parameters etc.
     */
    protected void initialization() {

        // must have root privilege in order to run
        try {
            Runtime.getRuntime().exec("su");
            Toast.makeText(MainActivity.this, R.string.txt_silentsu, Toast.LENGTH_SHORT)
                    .show();
        } catch (Throwable e) {
            Toast.makeText(this, R.string.warn_root, Toast.LENGTH_LONG).show();
        }

        // must have storage permission
        Utilities.verifyStoragePermissions(this);

        // permission error
        if (!Utilities.canWriteOnExternalStorage()) {
            Log.d(TAG, getString(R.string.err_writepermission));
            MainActivity.myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append(getString(R.string.err_writepermission) + "\n");
                }
            });
        }

        // handler that updates the ui at main thread
        // it's used in sslogger thus will be modded in receiver activity also
        // do not modify this
        myHandler = new Handler();

        // sslogger intent
        intentSSLogger = new Intent(this, SSLogger.class);

        Utilities.getSelfIdentity(tcpdumpInterface, true);

        // binary executables to run
        binary_TX_Normal = "client_send_normaltcp";
        binary_TX_NormalUDP = "client_send_normaludp";
        binary_TX_Sendfile = "client_send_normaltcp_sendfile";
        binary_TX_UDPSendfile = "client_send_normaludp_sendfile";
        binary_TX_RawNormal = "client_send_bypassl3";
        binary_TX_Splice = "client_send_normaltcp_splice";
        binary_RX_Normal = "client_recv_normaltcp";
        binary_RX_NormalUDP = "client_recv_normaludp";
        binary_RX_Splice = "client_recv_normaltcp_splice";
        binary_RX_RawNormal = "client_recv_bypassl3";

        // get number of cores
        coreNum = Utilities.getNumCores();

        // output folder for SSLogger
        outFolderPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SSLogger";
        if (!Utilities.dirExist(outFolderPath, true)) {
            // checked and cannot create this folder
            Toast.makeText(this, R.string.err_mkdir_failed, Toast.LENGTH_LONG).show();
        }

        // elements in the page
        txt_results = (TextView) findViewById(R.id.txt_results);
        btn_startTransmit = (Button) findViewById(R.id.btn_startTransmit);
        btn_startReceive = (Button) findViewById(R.id.btn_startReceive);
        btn_measureBg = (Button) findViewById(R.id.btn_measureBg);
        btn_setByte2send = (Button) findViewById(R.id.btn_setByte2send);
        btn_setRepeatTimes = (Button) findViewById(R.id.btn_setRepeatTimes);
        btn_setTCPDumpInterface = (Button) findViewById(R.id.btn_setTCPDumpInterface);
        btn_setOthers = (Button) findViewById(R.id.btn_setOthers);
        btn_setLogFreq = (Button) findViewById(R.id.btn_setLogFreq);
        btn_clearStatus = (Button) findViewById(R.id.btn_clearStatus);
        btn_ranging = (Button) findViewById(R.id.btn_ranging);

        // grab WiFi service and check if wifi is enabled
        wm = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        isUsingWifi = wm.isWifiEnabled();
        txt_results.append(
                (isUsingWifi ? getString(R.string.stat_wifion) : getString(R.string.stat_wifioff)));

        // click listener
        btn_ranging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isUsingWifi) return;
                wm.startScan();
                Log.d("ScanResult", "Start scanning");
                my_recv = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent intent) {
                        List<ScanResult> results = wm.getScanResults();
                        if (results.isEmpty()) {
                            Log.d("ScanResult", "result is empty");
                        } else {
                            wifiRanging(results);
                        }
                        c.unregisterReceiver(my_recv);
                        Log.d("ScanResult", "receiver unregistered");
                    }
                };
                registerReceiver(my_recv, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            }
        });
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
        btn_measureBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // msg indicating starting
                        myHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                txt_results.append("Starting.. will come back after 1min\n");
                            }
                        });
//                        // disable tcpdump
//                        boolean isUsingTCPDump_backup = isUsingTCPDump;
//                        isUsingTCPDump = false;
                        String[] commd = new String[3];
                        commd[0] = "su";
                        commd[1] = "&&";
                        commd[2] = "cd " + outFolderPath
                                + " && rm *.cpu *.cpuRaw *.ss tcpdump*";
                        // change screen brightness to 0
                        Utilities.switchScreenStatus();

                        btn_click_time = Long.toString(
                                System.currentTimeMillis());
                        startService(intentSSLogger);
                        try {
                            Thread.sleep(60000);  // sleep for 60s
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        stopService(intentSSLogger);
                        myServiceCheck();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // change screen back on
                        Utilities.switchScreenStatus();
                        Runtime.getRuntime().gc();
                        System.gc();
                        String tarName = "bg_measure_"
                                + (new SimpleDateFormat(
                                "yyyyMMdd_HHmmss", Locale.US)
                                .format(new Date()))
                                + ".tar.gz";
                        commd[2] = "cd " + outFolderPath
                                + " && busybox tar -czf "
                                + tarName + " *.cpu *.cpuRaw *.ss tcpdump*";
                        try {
                            Runtime.getRuntime().exec(commd).waitFor();
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                        commd[2] = "cd " + outFolderPath
                                + " && rm *.cpu *.cpuRaw *.ss tcpdump*";
                        try {
                            Runtime.getRuntime().exec(commd).waitFor();
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
//                        isUsingTCPDump = isUsingTCPDump_backup;
                        // msg indicating all done
                        myHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                txt_results.append("Done\n");
                            }
                        });
                    }
                }).start();

            }
        });
        btn_setByte2send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CharSequence[] mItems={
                        (bytes2send == (Utilities.oneMB/2))?"Current: 0.5MB":"0.5MB",
                        (bytes2send == (Utilities.oneMB))?"Current: 1MB":"1MB",
                        (bytes2send == (2*Utilities.oneMB))?"Current: 2MB":"2MB",
                        (bytes2send == (5*Utilities.oneMB))?"Current: 5MB":"5MB",
                        (bytes2send == (10*Utilities.oneMB))?"Current: 10MB":"10MB",
                        (bytes2send == (20*Utilities.oneMB))?"Current: 20MB":"20MB",
                        (bytes2send == (50*Utilities.oneMB))?"Current: 50MB":"50MB",
                        (bytes2send == (100*Utilities.oneMB))?"Current: 100MB":"100MB",
                        (bytes2send == (200*Utilities.oneMB))?"Current: 200MB":"200MB",
                        (bytes2send == (500*Utilities.oneMB))?"Current: 500MB":"500MB",
                        (bytes2send == (1000*Utilities.oneMB))?"Current: 1GB":"1GB"};
                AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
                mDialog.setItems(mItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 3) {
                            bytes2send = Utilities.oneMB/2;
                            for (int i = 0; i < which; ++i)
                                bytes2send *= 2;
                        } else if (which < 6) {
                            bytes2send = 5 * Utilities.oneMB;
                            for (int i = 3; i < which; ++i)
                                bytes2send *= 2;
                        } else if (which < 9) {
                            bytes2send = 50 * Utilities.oneMB;
                            for (int i = 6; i < which; ++i)
                                bytes2send *= 2;
                        } else if (which == 9) {
                            bytes2send = 500 * Utilities.oneMB;
                        } else if (which == 10) {
                            bytes2send = 1000 * Utilities.oneMB;
                        } else {
                            bytes2send = 10 * Utilities.oneMB; // default 10MB
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
                    Process proc = Runtime.getRuntime().exec("su -c ls /sys/class/net");
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
                } catch (IOException | InterruptedException e) {
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
                            Toast.makeText(MainActivity.this, "TCPDump interface is changed to "
                                    + tcpdumpInterface, Toast.LENGTH_SHORT).show();
                            if (isVerbose) {
                                Log.d(TAG, "TCPDump interface is set to " + tcpdumpInterface);
                            }
                            // based on the selected interface, get corresponding IP and MAC address
                            Utilities.getSelfIdentity(tcpdumpInterface, true);
                        }
                    }
                });
                mDialog.create().show();
            }
        });

        btn_setOthers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CharSequence[] items = {
                        "Verbose Mode", "Only Run Locally", "CPULog (Per Proc)",
                        "CPULog (WiFi Driver)", "CPULog (This App)", "CPULog (TCPDump)",
                        "Force on CPU0", "20kMTU for UDP"};
                boolean[] checkedItems = {
                        isVerbose, isLocal, isLoggingPerProcPID, (wifiDriverPID != -1),
                        isLoggingAppSelf, isLoggingTCPDump, isForcingCPU0,
                        (Utilities.udpsendsize != -1)};
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        switch (which) {
                            case 0:
                                isVerbose = isChecked;
                                Toast.makeText(MainActivity.this, "Set to be " +
                                        (isVerbose ? "verbose" : "NOT verbose"),
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case 1:
                                isLocal = isChecked;
                                if (isLocal) {
                                    isUsingTCPDump = false;
                                    Toast.makeText(MainActivity.this, ""
//                                            "Remember to set IP to 192.168.1.15\n"
                                                    + "Will start locally\n"
                                                    + "tcpdump disabled", Toast.LENGTH_LONG).show();
                                } else {
                                    isUsingTCPDump = true;
                                    tcpdumpInterface = "wlan0";
                                    Toast.makeText(MainActivity.this,
                                            "Back to original\ntcpdump enabled to wlan0",
                                            Toast.LENGTH_LONG).show();
                                }
                                break;
                            case 2:
                                isLoggingPerProcPID = isChecked;
                                Toast.makeText(MainActivity.this,
                                        isLoggingPerProcPID ?
                                                "Will log cpu/process" : "cpu/process disabled",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case 3:
                                // TODO: wifi driver for Nexus build not working
                                wifiDriverPID = isChecked ?
                                        Utilities.getMyPID("dhd_dpc", true) : -1;
                                Toast.makeText(MainActivity.this,
                                        (wifiDriverPID != -1) ?
                                                "Will log wifi driver cpu" : "wifi driver cpu disabled",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case 4:
                                isLoggingAppSelf = isChecked;
                                Toast.makeText(MainActivity.this,
                                        isLoggingAppSelf ?
                                                "Will log app cpu" : "Will NOT log app cpu",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case 5:
                                isLoggingTCPDump = isChecked;
                                Toast.makeText(MainActivity.this,
                                        isLoggingTCPDump ?
                                                "Will log tcpdump cpu" : "Will NOT log tcpdump cpu",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case 6:
                                isForcingCPU0 = isChecked;
                                Toast.makeText(MainActivity.this,
                                        (isForcingCPU0 ? "Force on cpu 0" : "Will run on any cpu"),
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case 7:
                                Utilities.udpsendsize = isChecked ? 20000: -1;
                                Toast.makeText(MainActivity.this,
                                        (Utilities.udpsendsize != -1) ?
                                                "udp mtu to 20k" : "1.5k mtu for udp",
                                        Toast.LENGTH_SHORT).show();
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
                                    MainActivity.txt_results.append(
                                            "time_wait_for is set to " + time_wait_for + "ms\n");
                                }
                            });
                        }
                        Log.d(TAG, "time_wait_for is set to " + time_wait_for + "ms");
                    }
                });
                mDialog.create().show();
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialization();
        checkBinaryFilesExist();
    }

}
