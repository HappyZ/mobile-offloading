package edu.ucsb.cs.sandlab.offloadingdemo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by yanzi on 10/1/15.
 * Updated by yanzi on 01/27/2017
 */
class Utilities {
    private static final String TAG = "Utilities";

    // variables
    static final int oneMB = 1048576;
    static int TCP_port = 4444;
    static int UDP_port = 8888;
    static String myInetIP = null;
    static String myMAC = null;
    private static boolean screenIsOff = false;
    private static int screenBrightness = 1;

    // selections

    // predefined selections
    static CharSequence[] existedItems = new CharSequence[] {
        "Socket_Normal", "Socket_NormalUDP", "Socket_Sendfile", "Socket_Splice", "RawSocket_Normal"
    };
    static CharSequence[] existedItemsThrpt = new CharSequence[]{
        "800Mbps", "760Mbps", "720Mbps", "680Mbps", "640Mbps", "600Mbps", "560Mbps",// 0-6
        "520Mbps", "480Mbps", "440Mbps", "400Mbps", "360Mbps", "320Mbps", "280Mbps",// 7-13
        "240Mbps", "200Mbps", "160Mbps", "120Mbps", "80Mbps",                       // 14-18
        "76Mbps", "72Mbps", "68Mbps", "64Mbps", "60Mbps", "56Mbps", "52Mbps",       // 19-25
        "48Mbps", "44Mbps", "40Mbps", "36Mbps", "32Mbps", "28Mbps", "24Mbps",       // 26-32
        "20Mbps", "16Mbps", "12Mbps", "8Mbps",                                      // 33-36
        "6Mbps", "5Mbps", "4Mbps", "3Mbps", "2Mbps", "1Mbps",                       // 37-42
        "800Kbps", "600Kbps", "400Kbps", "200Kbps",                                 // 43-46
        "Unlimited",                                                                // 47
    };


    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };


    /**
     * switch the screen on/off
     */
    static void switchScreenStatus() {
        String fp_brightness;
        String device_name = getDeviceName();

        Log.d(TAG, "device name: " + device_name);

        if (device_name.equals("shamu")) {
            fp_brightness = "/sys/class/leds/lcd-backlight/brightness";
        } else {
            fp_brightness = "/sys/class/lcd/panel/lcd_power";
        }

        if (!screenIsOff) {
            Process proc;
            String stdout;
            BufferedReader stdout_buf;
            try {
                proc = Runtime.getRuntime().exec(
                        "su -c cat " + fp_brightness);
                proc.waitFor();
                stdout_buf = new BufferedReader(new InputStreamReader(
                        proc.getInputStream()));
                stdout = stdout_buf.readLine();
                if (stdout != null) {
                    screenBrightness = Integer.parseInt(stdout);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "original screen brightness: " + screenBrightness);

        try {
            Runtime.getRuntime().exec(
                    "su -c echo " + (screenIsOff ? screenBrightness : "0") + " > " + fp_brightness)
                    .waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        screenIsOff = !screenIsOff;
    }

    /**
     * get the name of device (product name)
     * @return String
     */
    static String getDeviceName() {
        return Build.PRODUCT;
    }

    /**
     * Android 6.0 + required
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param activity:
     */
    static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // We don't have permission so prompt the user
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


    /**
     * check if we can write on external storage
     * @return true/false
     */
    static boolean canWriteOnExternalStorage() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    /**
     * get the ip and mac addresses
     */
    static void getSelfIdentity(String interface_name, boolean useIPv4) {
        String name;
        Enumeration<NetworkInterface> networks;
        Enumeration<InetAddress> inetAddresses;
        NetworkInterface network;
        myInetIP = null;
        myMAC = null;

        try {
            networks = NetworkInterface.getNetworkInterfaces();

            while (networks.hasMoreElements()) {
                network = networks.nextElement();

                // check if the interface matches the desired one
                name = network.getDisplayName();
                if (!name.equals(interface_name))
                    continue;
                Log.d(TAG, "myInterface: " + interface_name);

                // get the mac address
                byte[] mac = network.getHardwareAddress();

                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i],
                                (i < mac.length - 1) ? ":" : ""));
                    }
                    myMAC = sb.toString();
                }

                // get the ip address
                inetAddresses = network.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String sAddr = inetAddress.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        // check if we only want ipv4
                        if (useIPv4) {
                            if (isIPv4)
                                myInetIP = sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                myInetIP =
                                        (delim < 0) ? sAddr.toUpperCase() : sAddr.substring(
                                            0, delim).toUpperCase();
                            }
                        }
                    }
                }

            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (myMAC == null) {
            Log.d(TAG, "Failed to get MAC from interface " + interface_name);
            myMAC = "00:00:00:00:00:00";
        }

        if (myInetIP == null) {
            Log.d(TAG, "Failed to get IP from interface " + interface_name);
            myInetIP = "127.0.0.1";
        }

        Log.d(TAG, "myMAC: " + myMAC);
        Log.d(TAG, "myIP: " + myInetIP);
    }


    /**
     * parse binary file output
     * @param output:
     * @return double
     */
    static double parseBinOutput(String output) {
        String[] toks = output.trim().split(":");
        if (toks.length == 2) {
            return Double.parseDouble(toks[1]);
        }
        return -1;
    }


    /**
     * get the number of cores of device
     * @return int > 0
     */
    static int getNumCores() {
        Process proc;
        BufferedReader stdout_buf;
        String stdout;

        try {
            proc = Runtime.getRuntime().exec("grep -c processor /proc/cpuinfo");
            proc.waitFor();

            // read std out
            stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));

            stdout = stdout_buf.readLine();
            Log.d(TAG, "Number of cores: " + stdout);
            stdout_buf.close();

            if (stdout == null) {
                Log.w(TAG, "cannot fetch number of cores!");
                return 1;
            } else {
                return Integer.parseInt(stdout);
            }

        } catch(Exception e) {
            Log.w(TAG, "cannot fetch number of cores!");
            MainActivity.myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.append("Failed to get # of cores\n");
                }
            });
            //Default to return 1 core
            return 1;
        }
    }


    /**
     * get pid of the binary process
     * @param inName    the name of process
     * @return true/false - succeed or not
     */
    protected static int getMyPID(String inName, boolean flag) {
        String commd;
        Process proc;
        BufferedReader stdout_buf;
        String stdout;

        // get commd ready
        if (flag)
            commd = "su -c busybox ps | grep "
                    + inName + " | grep -v grep | head -1 | awk '{print $1}'";
        else
            commd = "su -c busybox ps | grep "
                    + inName + " | grep -v grep | head -1 | awk '{print $3}'";

        try {
            proc = Runtime.getRuntime().exec(commd);
            proc.waitFor();

            // read std out
            stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));

            stdout = stdout_buf.readLine();
            stdout_buf.close();

            if (! stdout.equals("")) {
                Log.d(TAG, inName + "PID: " + stdout);
                return Integer.parseInt(stdout);
            }
        } catch (InterruptedException | IOException ignored) {
            Log.d(TAG, "Faild to fetch PID for " + inName);
        }
        return -1;
    }


    /**
     * check if a file exists
     * @param myFile:
     * @return true/false
     */
    static boolean fileExist(String myFile) {
        File file = new File(myFile);
        return file.exists() && file.isFile();
    }


    /**
     * check if a directory exists
     * @param myDirectory:
     * @param createIfNot: try to create the folder if directory does not exist
     * @return true/false
     */
    static boolean dirExist(String myDirectory, boolean createIfNot) {
        File file = new File(myDirectory);
        return (file.exists() && file.isDirectory()) || (createIfNot && file.mkdirs());
    }


    /**
     * post parse CPU for a folder
     * @param folderName:
     * @return true/false
     */
    static boolean parseCPUforFolder(String folderName) {
        Process proc;
        BufferedReader stdout_buf, br;
        FileOutputStream os_cpu;
        String cpuFile, line, tmp;
        int i, offset;

        try {
            proc = Runtime.getRuntime().exec(
                    "su && cd " + MainActivity.outFolderPath + "/"
                            + folderName + " && ls *.cpuRaw");
            proc.waitFor();

            // read std out
            stdout_buf = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));

            while ((cpuFile = stdout_buf.readLine()) != null) {
                br = new BufferedReader(new FileReader(
                                MainActivity.outFolderPath + "/"
                                        + folderName + "/" + cpuFile));

                os_cpu = new FileOutputStream(new File(
                                MainActivity.outFolderPath + "/"
                                        + folderName + "/" + cpuFile.replace("cpuRaw", "cpu")));

                while ((line = br.readLine()) != null) {

                    String[] s = line.split("\\s+");

                    /*
                     * each line format as the following
                     * [ // 0        1      2     3     4      5      6      7      8       9-11
                     *  timestamp, "cpu", user, nice, system, idle, iowait, irq, softirq, 0, 0, 0,
                     *   // 12   13    14     15     16     17     18    19      20-22      23
                     *  "cpu0", user, nice, system, idle, iowait, irq, softirq, 0, 0, 0, frequency,
                     *  ...
                     * ]
                     */

                    tmp = s[0] + " "                // timestamp
                            + s[5] + " "            // cpu_total idle
                            + parseUsedCPU(s, 1);   // cpu_total used

                    for (i = 0; i < MainActivity.coreNum; ++i) {
                        offset = (i + 1) * 12;
                        tmp += " " + s[4 + offset]                  // cpu_i idle
                                + " " + parseUsedCPU(s, offset)     // cpu_i used
                                + " " + s[offset + 11];     // cpu_i frequency
                    }
                    tmp += "\n";

                    /*
                     * convert to parsed format (each line):
                     * [
                     *  timestamp, cpu_total idle, cpu_total used,
                     *  cpu_0 idle, cpu_0 used, cpu_0 frequency,
                     *  ...
                     * ]
                     */
                    os_cpu.write(tmp.getBytes());


                    // format for Ana's script:
                    //      time
                    //      cpuTotal idle
                    //      cpuTotal used
                    //      cpu0 idle
                    //      cpu0 used
                    //      cpu0 freq
                    //      cpu1 freq
                    //      cpuTotal normal process user mode
                    //      cpuTotal niced process in user mode
                    //      cpuTotal kernal mode
                    //      cpuTotal IO
                    //      cpuTotal hardware interrupts
                    //      cpuTotal software interrupts
                    //      cpu0 normal process user mode
                    //      cpu0 niced process in user mode
                    //      cpu0 kernal mode
                    //      cpu0 IO
                    //      cpu0 hardware interrupts
                    //      cpu0 software interrupts
//                    os_cpu.write((toks[0] + " "     // time
//                            // cpuTotal toks[1-11]
//                            + toks[5] + " "         // cpuTotal idle
//                            // cpuTotal used
//                            + (Long.parseLong(toks[2]) + Long.parseLong(toks[3])
//                            + Long.parseLong(toks[4]) + Long.parseLong(toks[6])
//                            + Long.parseLong(toks[7]) + Long.parseLong(toks[8])) + " "
//                            // cpu 0 toks[12-22]
//                            + toks[16] + " "        // cpu0 idle
//                            // cpu0 used
//                            + (Long.parseLong(toks[13]) + Long.parseLong(toks[14])
//                            + Long.parseLong(toks[15]) + Long.parseLong(toks[17])
//                            + Long.parseLong(toks[18]) + Long.parseLong(toks[19])) + " "
//                            // cpu 0 freq toks[23]
//                            + toks[23] + " "
//                            // cpu 1 freq toks[35]
//                            + toks[35] + " "
//                            // cpuTotal details
//                            + toks[2] + " " + toks[3] + " " + toks[4] + " "
//                            + toks[6] + " " + toks[7] + " " + toks[8] + " "
//                            // cpu0 details
//                            + toks[13] + " " + toks[14] + " " + toks[15] + " "
//                            + toks[17] + " " + toks[18] + " " + toks[19]).getBytes());
//                    // cpu 1 toks[24-34]
//                    os_cpu.write("\n".getBytes());
                }

                br.close();
                os_cpu.close();
            }

            stdout_buf.close();

        } catch (IOException | InterruptedException ignore) {
            return false;
        }
        return true;
    }


    /**
     * parse the cpu usage (used)
     * @param tmp: cpu usage
     * @return long: used cpu usage
     */
    private static Long parseUsedCPU(String[] tmp, int offset) {
        return (Long.parseLong(tmp[1 + offset])
                + Long.parseLong(tmp[2 + offset])
                + Long.parseLong(tmp[3 + offset])
                + Long.parseLong(tmp[5 + offset])
                + Long.parseLong(tmp[6 + offset])
                + Long.parseLong(tmp[7 + offset]));
    }


    /**
     * Translate the selection index into throughput setup
     * @param myI:
     * @return integer
     */
    static int findCorrespondingThrpt(int myI) {
        if (myI < 19) {
            return (800 - (myI * 40)) * 1000000;
        } else if (myI < 37) {
            return (76 - ((myI - 19) * 4)) * 1000000;
        } else if (myI < 43) {
            return (6 - ((myI - 37))) * 1000000;
        } else if (myI < 47) {
            return (800 - ((myI - 43) * 200)) * 1000;
        } else { // default unlimited
            if (MainActivity.isLocal)
                // for loopback, the unlimited shouldn't be really unlimited..
                return 8 * 100000000;
            else
                return -1;
        }
    }


    /**
     * Estimate how much time left
     * @param numRepeats:
     * @param numSelectedItems:
     * @param totalBytes:
     * @param selectedItemsThrpt:
     */
    static void estimateTime(
            int numRepeats, int numSelectedItems, int totalBytes,
            ArrayList<Integer> selectedItemsThrpt) {
        int time = 0;

        if (MainActivity.isLocal) {
            for (int k = 0; k < selectedItemsThrpt.size(); ++k)
                time += (Math.max(totalBytes / findCorrespondingThrpt(selectedItemsThrpt.get(k))
                        + 20, 20));
        } else {
            for (int k = 0; k < selectedItemsThrpt.size(); ++k)
                time += (Math.max(totalBytes / findCorrespondingThrpt(selectedItemsThrpt.get(k))
                        + 20, 60));
        }

        time = (time + 15) * numSelectedItems * numRepeats * 1000;

        final String estimatedTime =
                new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US).format(
                        new Date(System.currentTimeMillis() + time));

        MainActivity.myHandler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.txt_results.append("Estimated ending @ " + estimatedTime + "\n");
            }
        });
    }

    /**
     * check if is a valid IP (pingable)
     * @param ip: ip address as string
     * @return boolean
     */
    static boolean validIP(String ip) {
        // only check the ip ad ipv4
        if ( ip == null || ip.isEmpty() ) return false;
        int i;
        String[] parts;
        try {
            parts = ip.split( "\\." );
        } catch (Exception ignored) {
            return false;
        }
        if ( parts.length != 4 ) return false;
        for ( String s : parts ) {
            i = Integer.parseInt(s);
            if ((i < 0) || (i > 255)) return false;
        }
        if (ip.endsWith(".")) return false;
        // ping the ip and see if it is reachable
        try {
            return InetAddress.getByName(ip).isReachable(5);
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * check if is a valid MAC
     * @param mac: mac address as string
     * @return boolean
     */
    static boolean validMAC(String mac) {
        // use regular expression to validate a mac address
        // the only valid format is xx:xx:xx:xx:xx:xx
        Pattern p = Pattern.compile("^([a-fA-F0-9][:]){5}[a-fA-F0-9][:]$");
        Matcher m = p.matcher(mac);
        return m.find();
    }
}
