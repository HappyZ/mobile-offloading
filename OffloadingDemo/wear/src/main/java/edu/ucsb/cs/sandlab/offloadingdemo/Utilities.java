package edu.ucsb.cs.sandlab.offloadingdemo;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by yanzi on 10/1/15.
 */
public class Utilities {
    private static final String TAG = "Utilities";

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
    protected static int getMyPID(String inName) {
        String commd = "su -c busybox ps | grep " + inName + " | grep -v grep | head -1 | awk '{print $1}'";
        try {
            Process proc = Runtime.getRuntime().exec(commd);
            proc.waitFor();
            String line;
            StringBuilder out = new StringBuilder();
            BufferedReader is = new BufferedReader(new InputStreamReader(proc.getInputStream()));
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
     * parse CPU for a folder
     * @param folderName
     * @return true/false
     */
    protected static boolean parseCPUforFolder(String folderName) {
        try {
            Process proc = Runtime.getRuntime().exec("su && cd " + MainActivity.outFolderPath + "/" + folderName
                    + " && ls *.cpuRaw");
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
                                new FileReader(MainActivity.outFolderPath + "/" + folderName + "/" + cpuFiles[i]));
                        FileOutputStream os_cpu = new FileOutputStream(
                                new File(MainActivity.outFolderPath + "/" + folderName, cpuFiles[i].replace("cpuRaw", "cpu")));
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

    protected static int findCorrespondingThrpt(int myI) {
        if (myI == 0) {
            return 50000;
        } else if (myI == 1) {
            return 100000;
        } else if (myI < 6) {
            return 250000 * (myI - 1);
        } else if (myI < 24) {
            return 1500000 + 500000 * (myI - 6);
        } else if (myI > 24) {
            return 15000000 + 5000000 * (myI - 25);
        } else { // default unlimited
            if (MainActivity.isLocal)
                return 100000000;
            else
                return 20000000;
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
