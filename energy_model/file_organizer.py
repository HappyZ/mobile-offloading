import sys
import os
import re
import subprocess
import collections

from energy_analyzer import *
sys.path.append("modules")
try:
    from analyzer import *
except:
    raise


def fetchfile(foldername):
    subprocess.call(
        './pullTarsFromPhone.sh {0}'.format(foldername), shell=True)


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
                     if '.tar.gz' in f]
            mydict[folder][thrpt] = files

    # reorganize files
    files = os.listdir(rootfolder)
    files = sorted([file for file in files if '.tar.gz' in file])

    for file in files:
        # print file
        tmp = file.split('_')
        mode = tmp[0]
        method = '{0}_{1}'.format(tmp[1], tmp[2])
        filesize = tmp[3]
        repeats = tmp[4][:-7]
        thrpt = tmp[6]
        mydict[method][thrpt].append(file)

    for method in mydict.keys():
        method_folder = '{0}/{1}'.format(rootfolder, method)
        if not os.path.isdir(method_folder):
            os.mkdir(method_folder)
        for thrpt in mydict[method].keys():
            thrpt_folder = '{0}/{1}'.format(method_folder, thrpt)
            if not os.path.isdir(thrpt_folder):
                os.mkdir(thrpt_folder)
            subprocess.call('rm {0}/*.csv'.format(thrpt_folder),
                            shell=True)
            for file in mydict[method][thrpt]:
                if os.path.isfile('{0}/{1}'.format(rootfolder, file)):
                    subprocess.call(
                        'mv {0}/{1} {2}'.format(
                            rootfolder, file, thrpt_folder),
                        shell=True)
                    subprocess.call(
                        'cd {0} && tar -xzf {0}/{1}'.format(
                            thrpt_folder, file),
                        shell=True)

# construct dict
mydict = collections.defaultdict(lambda: collections.defaultdict(list))

# folder name
foldername = 'initial_comparison'
folderbase = '/Users/yanzi/GDrive/UCSB/Projects/Offloading_2017/Data/'

# get files
# fetchfile(foldername)

# load
load_folder(mydict, foldername, folderbase)

DEBUG = False
logger = EmptyLogger("App", isDebugging=DEBUG, printout=True)
# remoteIP = '128.111.68.220'
remoteIP = '192.168.2.1'
# sizeOptions = [1, 5, 10, 20, 50, 100]  # MB
sizeOptions = [None]  # MB

myAnalyzer = EnergyAnalyzer(
    "shamu", isDebugging=DEBUG, unit="mW", logger=logger)

for method in mydict.keys():
    for thrpt in mydict[method].keys():
        folder = '{0}/{1}/{2}/{3}'.format(
            folderbase, foldername, method, thrpt)
        # analyzer obj
        myAnalyzer.output_path = folder
        analyzeit(logger, folder, myAnalyzer, remoteIP, sizeOptions)
