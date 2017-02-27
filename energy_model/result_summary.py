import sys
import os
import re
import subprocess
import collections


def load_folder(
        mydict,
        foldername,
        folderbase):
    rootfolder = '{0}/{1}'.format(folderbase, foldername)
    folders = [f for f in os.listdir(rootfolder)
               if os.path.isdir('{0}/{1}'.format(rootfolder, f))]
    while len(folders) > 0:
        folder = folders.pop()
        folder_path = '{0}/{1}'.format(rootfolder, folder)
        thrpt_folders = [f for f in os.listdir(folder_path)
                         if os.path.isdir('{0}/{1}'.format(folder_path, f))]
        for thrpt in thrpt_folders:
            thrpt_folder_path = '{0}/{1}'.format(folder_path, thrpt)
            files = [f for f in os.listdir(thrpt_folder_path)
                     if 'result_overview.csv' in f]
            if len(files) == 1:
                mydict[folder][thrpt[:-4]] = 1

# construct dict
mydict = collections.defaultdict(lambda: collections.defaultdict(int))

# folder name
foldername = 'low_thrpt_tests'
folderbase = '/Users/yanzi/GDrive/UCSB/Projects/Offloading_2017/Data/'

# get files
# fetchfile(foldername)

# load
load_folder(mydict, foldername, folderbase)

for method in sorted(mydict.keys()):
    outf = open(
        '{0}/{1}/result_summary_{2}.csv'.format(
            folderbase, foldername, method), 'wb')
    outf.write(
        '#thrpt(Mbps),avg_total_pwr(mW),' +
        'avg_cpu_pwr(mW),avg_cpu_util(%),avg_wifi_pwr(mW)\n')
    thrpts = [float(x) for x in mydict[method].keys()]
    for thrpt in sorted(thrpts):
        filepath = '{0}/{1}/{2}/{3:.1f}Mbps/result_overview.csv'.format(
            folderbase, foldername, method, thrpt)
        if not os.path.isfile(filepath):
            print "something is wrong, {0} does not exist".format(filepath)
        with open(filepath, 'rU') as f:
            contents = f.readlines()
        avg_total_pwr = 0
        avg_cpu_pwr = 0
        avg_cpu_util = 0
        avg_wifi_pwr = 0
        counter = 0
        for line in contents:
            if '#' in line:
                continue
            tmp = line.split(',')
            avg_total_pwr += float(tmp[4])
            avg_cpu_pwr += float(tmp[8])
            avg_cpu_util += float(tmp[13])
            counter += 1
            if len(tmp) > 15:
                avg_wifi_pwr += float(tmp[16])
        if not counter == 0:
            avg_total_pwr /= counter
            avg_cpu_pwr /= counter
            avg_cpu_util /= counter
            avg_wifi_pwr /= counter
        outf.write('{0},{1:.4f},{2:.4f},{3:.4f},{4:.4f}\n'.format(
            thrpt, avg_total_pwr,
            avg_cpu_pwr, avg_cpu_util, avg_wifi_pwr))
    outf.close()
