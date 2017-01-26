package edu.ucsb.cs.sandlab.offloadingdemo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by yanzi on 10/1/15.
 */
public class Utilities {
    private static final String TAG = "Utilities";
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Android 6.0 + required
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
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
    public static boolean canWriteOnExternalStorage() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * get the ip and mac addresses
     */
    protected static void getSelfIdentity(String interface_name, boolean useIPv4) {
        try {
            Enumeration<NetworkInterface> networks =
                    NetworkInterface.getNetworkInterfaces();

            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();

                // check if the interface matches the desired one
                String name = network.getDisplayName();
                if (!name.equals(interface_name))
                    continue;

                // get the ip address
                Enumeration<InetAddress> inetAddresses = network.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String sAddr = inetAddress.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        // check if we only want ipv4
                        if (useIPv4) {
                            if (isIPv4)
                                MainActivity.myInetIP = sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                MainActivity.myInetIP =
                                        (delim < 0) ? sAddr.toUpperCase() : sAddr.substring(
                                            0, delim).toUpperCase();
                            }
                        }
                    }
                }

                // get the mac address
                byte[] mac = network.getHardwareAddress();

                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i],
                                (i < mac.length - 1) ? ":" : ""));
                    }
                    MainActivity.myMAC = sb.toString();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * parse binary file output
     * @param output
     * @return double
     */
    protected static double parseBinOutput(String output) {
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
    protected static int getNumCores() {
        Process proc;
        try {
            proc = Runtime.getRuntime().exec("grep -c processor /proc/cpuinfo");
            InputStream stdout = proc.getInputStream();
            byte[] buff = new byte[20];
            int read;
            StringBuilder out = new StringBuilder();
            while(true){
                read = stdout.read(buff);
                if(read<0){
                    MainActivity.myHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.txt_results.setText("Failed to get cores\n");
                        }
                    });
                    return 1;
                }
                out.append(new String(buff, 0, read));
                if(read<20){
                    break;
                }
            }
            proc.waitFor();
            stdout.close();
            //Return the number of cores (virtual CPU devices)
            return Integer.parseInt(out.toString().trim());
        } catch(Exception e) {
            MainActivity.myHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.txt_results.setText("Failed to get cores\n");
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
        if (flag)
            commd = "su -c busybox ps | grep "
                    + inName + " | grep -v grep | head -1 | awk '{print $1}'";
        else
            commd = "su -c busybox ps | grep "
                    + inName + " | grep -v grep | head -1 | awk '{print $3}'";
        try {
            Process proc = Runtime.getRuntime().exec(commd);
            proc.waitFor();
            String line;
            StringBuilder out = new StringBuilder();
            BufferedReader is = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            while ((line = is.readLine()) != null) {
                out.append(line).append("\n");
            }
            String tmp = out.toString().trim();
            if (!tmp.equals("")) {
                return Integer.parseInt(tmp);
            }
        } catch (InterruptedException unimportant) {
            Log.d(TAG, "InterruptedException but unimportant");
        } catch (IOException e) {
            Log.d(TAG, "IOException but unimportant");
        }
        return -1;
    }

    /**
     * check if a file exists
     * @param myFile
     * @return true/false
     */
    protected static boolean fileExist(String myFile) {
        File file = new File(myFile);
        if (file.exists() && file.isFile())
            return true;
        return false;
    }

    /**
     * check if a directory exists
     * @param myDirectory
     * @param createIfNot: try to create the folder if directory does not exist
     * @return true/false
     */
    protected static boolean dirExist(String myDirectory, boolean createIfNot) {
        File file = new File(myDirectory);
        if (file.exists() && file.isDirectory())
            return true;
        if (createIfNot) {
            try{
                file.mkdirs();
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * parse CPU for a folder
     * @param folderName
     * @return true/false
     */
    protected static boolean parseCPUforFolder(String folderName) {
        try {
            Process proc = Runtime.getRuntime().exec(
                    "su && cd " + MainActivity.outFolderPath + "/"
                            + folderName + " && ls *.cpuRaw");
            proc.waitFor();
            InputStream stdout = proc.getInputStream();
            byte[] buffer = new byte[20];
            int read;
            StringBuilder out = new StringBuilder();
            while(true){
                read = stdout.read(buffer);
                if(read<0){
                    Log.d(TAG, "Failed in parseCPUforFolder: ls nothing");
                    break;
                }
                out.append(new String(buffer, 0, read));
                if(read<20){
                    break;
                }
            }
            if (!out.toString().equals("")) {
                String[] cpuFiles = out.toString().split("\\n");
                for (int i = 0; i < cpuFiles.length; ++i) {
                    try {
                        BufferedReader br = new BufferedReader(
                                new FileReader(
                                        MainActivity.outFolderPath + "/"
                                                + folderName + "/" + cpuFiles[i]));
                        FileOutputStream os_cpu = new FileOutputStream(
                                new File(
                                        MainActivity.outFolderPath + "/"
                                                + folderName,
                                        cpuFiles[i].replace("cpuRaw", "cpu")));
                        String line;
                        while ((line = br.readLine()) != null) {
                            String[] toks = line.split("\\s+");
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
                            os_cpu.write((toks[0] + " "     // time
                                    // cpuTotal toks[1-11]
                                    + toks[5] + " "         // cpuTotal idle
                                    // cpuTotal used
                                    + (Long.parseLong(toks[2]) + Long.parseLong(toks[3])
                                    + Long.parseLong(toks[4]) + Long.parseLong(toks[6])
                                    + Long.parseLong(toks[7]) + Long.parseLong(toks[8])) + " "
                                    // cpu 0 toks[12-22]
                                    + toks[16] + " "        // cpu0 idle
                                    // cpu0 used
                                    + (Long.parseLong(toks[13]) + Long.parseLong(toks[14])
                                    + Long.parseLong(toks[15]) + Long.parseLong(toks[17])
                                    + Long.parseLong(toks[18]) + Long.parseLong(toks[19])) + " "
                                    // cpu 0 freq toks[23]
                                    + toks[23] + " "
                                    // cpu 1 freq toks[35]
                                    + toks[35] + " "
                                    // cpuTotal details
                                    + toks[2] + " " + toks[3] + " " + toks[4] + " "
                                    + toks[6] + " " + toks[7] + " " + toks[8] + " "
                                    // cpu0 details
                                    + toks[13] + " " + toks[14] + " " + toks[15] + " "
                                    + toks[17] + " " + toks[18] + " " + toks[19]).getBytes());
                            // cpu 1 toks[24-34]
                            os_cpu.write("\n".getBytes());
                            os_cpu.flush();
                        }
                        os_cpu.close();
                    } catch (IOException unimportant) {
                        return false;
                    }
                }
            }
        } catch (IOException e) {
//            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
//            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Translate the selection index into throughput setup
     * @param myI
     * @return
     */
    protected static int findCorrespondingThrpt(int myI) {
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
                return 8 * 100000000; // for loopback, the unlimited shouldn't be really unlimited..
            else
                return -1;
        }
    }

    protected static void estimateTime(int numRepeats, int numSelectedItems, int totalBytes, ArrayList<Integer> selectedItemsThrpt) {
        int time = 0;
        if (MainActivity.isLocal) {
            for (int k = 0; k < selectedItemsThrpt.size(); ++k)
                time += (Math.max(totalBytes / findCorrespondingThrpt(selectedItemsThrpt.get(k)) + 20, 20));
        } else {
            for (int k = 0; k < selectedItemsThrpt.size(); ++k)
                time += (Math.max(totalBytes / findCorrespondingThrpt(selectedItemsThrpt.get(k)) + 20, 60));
        }
        time = (time + 15) * numSelectedItems * numRepeats * 1000;
        final String estimatedTime = new SimpleDateFormat("MM/dd HH:mm:ss").format(new Date(System.currentTimeMillis() + time));
        MainActivity.myHandler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.txt_results.append("Estimated ending @ " + estimatedTime + "\n");
            }
        });
    }
}
