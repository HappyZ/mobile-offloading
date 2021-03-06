import sys
import os
import re
import subprocess

sys.path.append("modules")
try:
    from analyzer import *
except:
    raise


def checkFiles(folderpath, timestamp):
    cpu = "{0}/{1}.cpu".format(folderpath, timestamp)
    cpuRaw = "{0}/{1}.cpuRaw".format(folderpath, timestamp)
    ss = "{0}/{1}.ss".format(folderpath, timestamp)
    tcpdump = "{0}/tcpdump_wifionly_{1}.tcpdump".format(folderpath, timestamp)
    status = {'cpu': cpu, 'cpuRaw': cpuRaw, 'ss': ss, 'tcpdump': tcpdump}
    # .cpuRaw file
    if not os.path.isfile(cpuRaw):
        del status['cpuRaw']
    # tcpdump file
    if not os.path.isfile(tcpdump):
        if os.path.isfile(tcpdump[:-8]):
            status['tcpdump'] = status['tcpdump'][:-8]
        else:
            del status['tcpdump']
    # .cpu file
    # if not os.path.isfile(cpu):
    del status['cpu']
    # .ss file
    if not os.path.isfile(ss):
        status['ss'] = None
    return status


def analyzeit(logger, folder, myAnalyzer, remoteIP, sizeOptions=[None]):
    # check if is folder
    if os.path.isdir(folder):
        files = [x for x in os.listdir(folder) if '.cpuRaw' in x]
    else:
        logger.error('{0} does not exist (or is not a folder)'.format(folder))
        return False
    for i in xrange(len(files)):
        file = files[i]
        logger.debug(file)
        try:
            timestamp, ext = file.split('.')
        except:
            logger.error("{0}'s name is not a valid one".format(file))
            sys.exit(1)
        status = checkFiles(folder, timestamp)
        logger.debug(status)
        for size_limit in sizeOptions:
            if size_limit is not None:
                size_limit = size_limit * 1000 * 1000
            # myAnalyzer.clean_up_cpu()
            # myAnalyzer.clean_up_net()
            # parse wifi
            if 'tcpdump' in status:
                logger.debug('reading tcpdump...')
                if 'bypass' in folder or 'RawSocket' in folder:
                    myfilter = ""
                else:
                    myfilter = "host {0}".format(remoteIP)
                myAnalyzer.read_wifi_log(
                    status['tcpdump'],
                    size_limit=size_limit,
                    fp_sslogger=status['ss'],
                    tcpdump_filter=myfilter)
                logger.debug('analyzing tcpdump...')
                myAnalyzer.parse_wifi_energy()
            # parse cpu
            if 'cpu' in status:
                cpuFile = status['cpu']
            else:
                cpuFile = status['cpuRaw']
            logger.debug('reading cpu...')
            logger.debug(
                "net start time {0}".format(myAnalyzer.net_start_time))
            logger.debug(
                "net end time {0}".format(myAnalyzer.net_end_time))
            myAnalyzer.read_cpu_log(
                cpuFile,
                startT=myAnalyzer.net_start_time,
                endT=myAnalyzer.net_end_time)
            logger.debug('analyzing cpu...')
            myAnalyzer.parse_cpu_energy()
            # generate result
            myAnalyzer.generate_result_summary(
                wifi='tcpdump' in status,
                f_suffix="_{0}".format(i))

if __name__ == "__main__":
    DEBUG = False
    logger = EmptyLogger("App", isDebugging=DEBUG, printout=True)
    # remoteIP = '128.111.68.220'
    remoteIP = '192.168.2.1'
    # sizeOptions = [1, 5, 10, 20, 50, 100]  # MB
    sizeOptions = [None]  # MB
    # folder = sys.argv[1]
    # folder = './models/bypass/120MBps/'
    folder = '/Users/yanzi/GDrive/UCSB/Projects/Offloading_2017/Data/' +\
        'bg_measurement_test'
        # 'low_thrpt_tests/Socket_Normal/2.0Mbps'
    # 'initial_comparison/udp_mtu20k/160Mbps'
    # create analyzer obj
    myAnalyzer = EnergyAnalyzer(
        "shamu", isDebugging=DEBUG, unit="mW", output_path=folder)

    analyzeit(logger, folder, myAnalyzer, remoteIP, sizeOptions)
