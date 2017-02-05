import sys
import os
import re
import subprocess

sys.path.append("modules")
try:
    from model import *
except:
    raise


class EnergyAnalyzer():
    '''
    Energy analyzer
    '''
    def __init__(self, productname, isDebugging=False, unit="mW"):
        self.myModel = Model(isDebugging=isDebugging, unit=unit)
        self.myModel.load(productname)
        # first time initialization
        self.clean_up_cpu()
        self.clean_up_net()

        self.DEBUG = isDebugging
        self.logger = EmptyLogger(
            "EnergyAnalyzer", isDebugging=self.DEBUG, printout=True)

    def clean_up_cpu(self):
        self.data_cpu = []  # (sorted) cpu results of logs
        self.data_cpu_d = []  # deltas between pair of results
        self.instant_freqs = []  # freq of each core at time unit
        self.instant_utils = []  # util perc of each core at time unit
        self.instant_power = []  # power spent at time unit
        self.total_idle = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_used = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_energy = 0  # unit as defined in unit
        self.avg_power = 0  # average power across time
        self.avg_utils = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_time = 0

    def clean_up_net(self):
        self.data_tcpdump = []
        self.net_start_time = -1
        self.net_end_time = -1
        self.wifi_rssi = []
        self.lte_rsrp = []

    def read_cpu_log(self, filepath,
                     startT=None, endT=None):
        self.logger.debug("re-initialize variables")
        self.clean_up_cpu()
        self.logger.debug("read_cpu_log started")
        contents = []
        skipFirstTime = True
        with open(filepath, 'rU') as f:
            contents = f.readlines()
        for line in contents:
            tmp = line.rstrip().split(' ')
            if len(tmp) < 3:
                print "something is wrong at splitting the line for cpu_log"
                sys.exit(-1)
            timestamp = int(tmp[0]) / 1000.0  # ms -> s
            if (startT is not None and timestamp < startT) \
                    or (endT is not None and timestamp > endT):
                continue
            cpu_total_idle = int(tmp[1])
            cpu_total_used = int(tmp[2])
            cpu_per_core = []
            if not skipFirstTime:
                delta_t = timestamp - self.data_cpu[-1][0]
                delta_total_idle = cpu_total_idle - self.data_cpu[-1][1]
                delta_total_used = cpu_total_used - self.data_cpu[-1][2]
                delta_per_core = []
            for i in xrange(3, len(tmp), 3):
                cpu_i_idle = int(tmp[i])
                cpu_i_used = int(tmp[i + 1])
                cpu_i_freq = int(tmp[i + 2])
                cpu_per_core.append([cpu_i_idle, cpu_i_used, cpu_i_freq])
                if len(self.data_cpu) != 0:
                    delta_per_core.append(
                        [cpu_i_idle - self.data_cpu[-1][3][i / 3 - 1][0],
                         cpu_i_used - self.data_cpu[-1][3][i / 3 - 1][1],
                         cpu_i_freq])  # self.data_cpu[-1][3][i / 3 - 1][2])
            if not skipFirstTime:
                self.data_cpu_d.append(
                    [delta_t, delta_total_idle,
                     delta_total_used, delta_per_core])
            self.data_cpu.append(
                [timestamp, cpu_total_idle, cpu_total_used, cpu_per_core])
            skipFirstTime = False
        self.logger.debug("read_cpu_log ended")

    def parse_cpu_energy(self,
                         power_base=0):
        if len(self.data_cpu_d) < 1:
            self.logger.error("parse_cpu_energy finds delta empty")
            self.logger.error(self.data_cpu)
            self.logger.error("did you call read_cpu_log?")
            return
        self.logger.debug("parse_cpu_energy started")
        num_of_cores = len(self.data_cpu_d[0][3])
        # allocate memory
        self.total_idle = [0 for i in xrange(num_of_cores + 1)]
        self.total_used = [0 for i in xrange(num_of_cores + 1)]
        self.avg_utils = [0 for i in xrange(num_of_cores + 1)]
        for result in self.data_cpu_d:
            # allocate memory
            freqs = [0 for i in xrange(num_of_cores)]
            utils = [0 for i in xrange(num_of_cores + 1)]
            for i in xrange(num_of_cores):
                if result[3][i][0] + result[3][i][1] > 0:
                    utils[i] = 1.0 * \
                        result[3][i][1] / (result[3][i][0] + result[3][i][1])
                    self.total_idle[i] += result[3][i][0]
                    self.total_used[i] += result[3][i][1]
                else:
                    utils[i] = 0
                freqs[i] = result[3][i][-1]
            # calculate total
            if result[1] + result[2] > 0:
                utils[-1] = 1.0 * result[2] / (result[1] + result[2])
                self.total_idle[-1] += result[1]
                self.total_used[-1] += result[2]
            else:
                utils[-1] = 0
            # store the results
            self.instant_freqs.append(freqs)
            self.instant_utils.append(utils)
            # get power (only if the unit is in Watts or mWatts)
            instant_power = self.myModel.get_cpu_energy(
                result[0], freqs, utils[:-1])
            # check if returned is power (watt) or energy (joule)
            if self.myModel.using_power:
                # subtract base (background) if desired
                instant_power -= power_base
                self.instant_power.append(instant_power)
                energy = instant_power * result[0]
            else:
                self.instant_power.append(instant_power / result[0])
                instant_power -= power_base * result[0]
                energy = instant_power
            self.total_time += result[0]
            self.total_energy += energy
        self.avg_power = self.total_energy / self.total_time
        for i in xrange(num_of_cores + 1):
            self.avg_utils[i] = 1.0 * \
                self.total_used[i] / (self.total_used[i] + self.total_idle[i])

        self.logger.info(
            "total energy: {0:.4f}mJ".format(self.total_energy))
        self.logger.info(
            "total time: {0:.4f}s".format(self.total_time))
        self.logger.info(
            "average power: {0:.4f}mW".format(self.avg_power))
        self.logger.info(
            "average util: {0}".format(
                ",".join(
                    ["{0:.2f}%".format(x * 100) for x in self.avg_utils])))
        # self.logger.debug(self.instant_power)
        self.logger.debug("parse_cpu_energy ended")

    def read_sslogger_file(self, fp_sslogger):
        if not os.path.isfile(fp_sslogger):
            self.logger.error(
                "sslogger file {0} does not exist".format(fp_sslogger))
            sys.exit(-1)
        myPatterns = [  # wifi
                      '(\d+) wifi ([-\d]+)',
                        # gsm, LTE
                      '(\d+) gsm ([-\d]+) lte ([-\d]+) ([-\d]+) ([-\d]+)']
        with open(fp_sslogger, 'rU') as f:
            content = f.readlines()
        for line in content:
            tmp = None
            for pattern in myPatterns:
                myMatch = re.search(pattern, line)
                if myMatch is not None:
                    # [timestamp (us), wifi rssi]
                    # or
                    # [timestamp (us), gsm rssi, lte ss, lte rsrp, lte rsrq]
                    tmp = list(myMatch.groups())
                    break
            if tmp is None:
                self.logger.debug("nothing found in {0}".format(line.rstrip()))
                continue
            if len(tmp) > 2:
                self.lte_rsrp.append([int(tmp[0]) / 1000.0, int(tmp[3])])
            else:
                self.wifi_rssi.append([int(tmp[0]) / 1000.0, int(tmp[1])])
        # self.logger.debug(self.lte_rsrp)
        # self.logger.debug(self.wifi_rssi)

    def read_tcpdump_file(self, fp_tcpdump,
                          isWiFi=False, isLTE=False, is3G=False):
        # TODO: currently WIFi only
        if not os.path.isfile(fp_tcpdump):
            self.logger.error(
                "tcpdump file {0} does not exist".format(fp_tcpdump))
            sys.exit(-1)
        myPatterns = [  # DATA packet, with seq range
                      '([\d\.]+) IP ([\d\.]+) \> ([\d\.]+): ' +
                      'Flags \[([\w\.]+)\], seq (\d+):(\d+),',
                        # ACK
                      '([\d\.]+) IP ([\d\.]+) \> ([\d\.]+): ' +
                      'Flags \[([\w\.]+)\], ack (\d+),',
                        # SYN/SYNACK or FIN/FINACK packet, with one seq
                      '([\d\.]+) IP ([\d\.]+) \> ([\d\.]+): ' +
                      'Flags \[([\w\.]+)\], seq (\d+),']
        with open(fp_tcpdump, 'rU') as f:
            content = f.readlines()
        firstLine = True
        # check the network status: idle, active, or tail
        net_state = 'i'  # idle
        for line in content:
            tmp = None
            for pattern in myPatterns:
                myMatch = re.search(pattern, line)
                if myMatch is not None:
                    # [timestamp (s), src_ip, dst_ip, flag, seq_start, seq_end]
                    # or
                    # [timestamp (s), src_ip, dst_ip, flag, seq]
                    tmp = list(myMatch.groups())
                    tmp[0] = float(tmp[0])  # timestamp
                    break
            if tmp is None:
                self.logger.debug("nothing found in {0}".format(line.rstrip()))
                continue
            data_len = 0
            if len(tmp) > 5:
                data_len = int(tmp[5]) - int(tmp[4])
            if firstLine:
                firstLine = False
            elif self.data_tcpdump[-1][1] == 'i' \
                    or self.data_tcpdump[-1][1] == 't':
                net_state = 'a'  # active
            elif self.data_tcpdump[-1][1] == 'a':
                prevT = self.data_tcpdump[-1][0]
                time_diff = tmp[0] - prevT
                # self.logger.debug("time_diff: {0:.4f}".format(time_diff))
                if time_diff > self.myModel.wifi_min_spike_interval:
                    self.data_tcpdump[-1][1] = 't'  # prev is tail
                if time_diff > self.myModel.wifi_timeout:
                    self.data_tcpdump.append(
                        [prevT + self.myModel.wifi_timeout,
                         'i',
                         self.data_tcpdump[-1][2]])
            self.data_tcpdump.append(
                [tmp[0], net_state, data_len])
        # check result
        if len(self.data_tcpdump) < 2:
            self.logger.error("tcpdump file has weird size...")
            sys.exit(-1)
        # the last one must be tail
        self.data_tcpdump[-1][1] = 't'
        # after self.myModel.wifi_timeout, it will become idle
        self.data_tcpdump.append(
            [self.data_tcpdump[-1][0] + self.myModel.wifi_timeout, 'i', 0])
        # self.logger.debug(self.data_tcpdump)
        # get the start and end time of network
        self.net_start_time = self.data_tcpdump[0][0]
        self.net_end_time = self.data_tcpdump[-2][0]  # true net end time

    def read_wifi_log(self, fp_tcpdump, tcpdump_filter="",
                      fp_sslogger=None):
        self.logger.debug("read_wifi_log started")
        self.logger.debug("re-initialize variables")
        self.clean_up_net()
        # parse tcpdump file
        self.logger.debug("parse tcpdump")
        if not os.path.isfile(fp_tcpdump):
            self.logger.error(
                "tcpdump file {0} does not exist".format(fp_tcpdump))
            sys.exit(-1)
        if ".tcpdump" not in fp_tcpdump:
            # generate the parsed tcpdump file
            subprocess.call(
                "tcpdump -tt -n -r {0} {1} > {0}.tcpdump".format(
                    fp_tcpdump, tcpdump_filter),
                shell=True)
            fp_tcpdump += '.tcpdump'
        # read the file
        self.read_tcpdump_file(fp_tcpdump, isWiFi=True)
        # parse sslogger
        self.logger.debug("parse sslogger file")
        if fp_sslogger is None:
            self.logger.info(
                "no sslogger file, will assume the strongest")
            return
        elif not os.path.isfile(fp_sslogger):
            self.logger.info(
                "sslogger file {0} does not exist".format(fp_sslogger))
            sys.exit(-1)
        self.read_sslogger_file(fp_sslogger)
        self.logger.debug("read_wifi_log ended")

    def parse_wifi_energy(self, power_base=0):
        self.logger.debug("parse_wifi_energy started")

        self.logger.debug("parse_wifi_energy ended")


if __name__ == "__main__":
    # cpuFile = sys.argv[1]
    cpuFile = "./models/test/1485560673559.cpu"
    tcpdumpFile = "./models/test/tcpdump_wifionly_1485560673559"
    ssFile = "./models/test/1485560673559.ss"
    if not os.path.isfile(cpuFile):
        print ".....!"
        sys.exit(-1)
    myObj = EnergyAnalyzer("shamu", isDebugging=True, unit="mW")
    myObj.read_cpu_log(cpuFile)
    myObj.parse_cpu_energy()
    myObj.read_wifi_log(
        tcpdumpFile,
        fp_sslogger=ssFile, tcpdump_filter="host 128.111.68.220")
    myObj.parse_wifi_energy()
    # for i in xrange(1, len(myObj.freqs)):
    #     print myObj.freqs[i] - myObj.freqs[i-1]
    # myObj.get_wifi_tail_energy(1)
    # myObj.get_wifi_active_energy(1, -60, isTX=False)
    # myObj.get_cpu_energy(1, [1036800, 422400], [0, 1])