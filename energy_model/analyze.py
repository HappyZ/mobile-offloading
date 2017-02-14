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

    '''
    Variable clean up functions
    '''
    def clean_up_cpu(self):
        self.clean_up_cpu_data()
        self.clean_up_cpu_result()

    def clean_up_cpu_data(self):
        self.data_cpu = []  # (sorted) cpu results of logs
        self.data_cpu_d = []  # deltas between pair of results

    def clean_up_cpu_result(self):
        self.instant_freqs = []  # freq of each core at time unit
        self.instant_utils = []  # util perc of each core at time unit
        self.instant_power = []  # power spent at time unit
        self.total_idle = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_used = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.cpu_energy_total = 0  # unit as defined in unit
        self.cpu_power_avg = 0  # average power across time
        self.cpu_utils_avg = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.cpu_time_total = 0

    def clean_up_net(self):
        self.clean_up_net_data()
        self.clean_up_wifi_result()
        self.clean_up_lte_result()

    def clean_up_net_data(self):
        self.data_tcpdump = []
        # [[time, net_state, data_len], ...]
        # net_state: 'i': 'idle', 'a': 'active', 't': 'tail'
        self.net_start_time = -1
        self.net_end_time = -1
        # WiFi
        self.wifi_rssi = []
        # LTE
        self.lte_rsrp = []

    def clean_up_wifi_result(self):
        self.wifi_energy = 0
        self.wifi_time = 0
        self.wifi_power = 0
        self.wifi_active_time = 0
        self.wifi_tail_time = 0
        self.wifi_tail_energy = 0
        self.wifi_active_energy = 0

    def clean_up_lte_result(self):
        pass

    '''
    CPU
    '''
    def read_cpu_log(self, filepath,
                     startT=float('-inf'), endT=float('inf')):
        self.logger.debug("clean up cpu data")
        self.clean_up_cpu_data()
        self.logger.debug("read_cpu_log started")
        contents = []
        skipFirstTime = True
        timeGap = 0.1
        with open(filepath, 'rU') as f:
            contents = f.readlines()
        for line in contents:
            tmp = line.rstrip().split(' ')
            if len(tmp) < 3:
                print "something is wrong at splitting the line for cpu_log"
                sys.exit(-1)
            timestamp = int(tmp[0]) / 1000.0  # ms -> s
            # self.logger.debug(
            #     "time: {0}, startT: {1}, endT: {2}".format(
            #         timestamp, startT, endT))
            if timestamp < (startT - timeGap) or timestamp > (endT + timeGap):
                continue
            # self.logger.debug("passed")
            cpu_total_idle = int(tmp[1])
            cpu_total_used = int(tmp[2])
            cpu_per_core = []
            if not skipFirstTime:
                delta_t = timestamp - self.data_cpu[-1][0]
                timeGap = delta_t
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
        self.logger.debug("clean up cpu result")
        self.clean_up_cpu_result()
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
        self.cpu_utils_avg = [0 for i in xrange(num_of_cores + 1)]
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
                self.instant_power.append(
                    instant_power / result[0] - power_base)
                instant_power -= power_base * result[0]
                energy = instant_power
            self.cpu_time_total += result[0]
            self.cpu_energy_total += energy
        self.cpu_power_avg = self.cpu_energy_total / self.cpu_time_total
        for i in xrange(num_of_cores + 1):
            self.cpu_utils_avg[i] = 1.0 * \
                self.total_used[i] / (self.total_used[i] + self.total_idle[i])
        # self.logger.debug(self.instant_power)
        self.logger.debug("parse_cpu_energy ended")

    '''
    Network
    '''
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
                    tmp = myMatch.groups()
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
        net_state = 'a'  # active
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
        self.logger.debug("clean up network data")
        self.clean_up_net_data()
        self.logger.debug("read_wifi_log started")
        # parse tcpdump file
        self.logger.debug("parse tcpdump")
        if ".tcpdump" not in fp_tcpdump:
            if not os.path.isfile("{0}.tcpdump".format(fp_tcpdump)):
                # check if raw file exists
                if not os.path.isfile(fp_tcpdump):
                    self.logger.error(
                        "tcpdump file {0} does not exist".format(fp_tcpdump))
                    sys.exit(-1)
                # generate the parsed tcpdump file
                subprocess.call(
                    "tcpdump -tt -n -r {0} {1} > {0}.tcpdump".format(
                        fp_tcpdump, tcpdump_filter),
                    shell=True)
                # remove the big file as it is too big
                subprocess.call("rm {0}".format(fp_tcpdump), shell=True)
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

    def parse_wifi_energy(self, power_base=0, isTX=True):
        self.logger.debug("clean up wifi result")
        self.clean_up_wifi_result()
        if len(self.data_tcpdump) < 2:
            self.logger.error("parse_wifi_energy finds data_tcpdump empty")
            self.logger.error(self.data_tcpdump)
            self.logger.error("did you call read_wifi_log?")
            return
        self.logger.debug("parse_wifi_energy started")
        # by default assume the rssi is the max
        curRSSI = sorted(
            self.myModel.net_wifi['active'].keys(), reverse=True)[0]
        # derive energy for wifi network
        for i in xrange(len(self.data_tcpdump) - 1):
            diffT = self.data_tcpdump[i + 1][0] - self.data_tcpdump[i][0]
            # self.logger.debug("diffT: {0:.8f}".format(diffT))
            # self.logger.debug("{0}".format(self.data_tcpdump[i]))
            if self.data_tcpdump[i][1] == 'a':
                curPower = self.myModel.get_wifi_active_energy(
                    diffT, curRSSI)
                # check if returned is power (watt) or energy (joule)
                if self.myModel.using_power:
                    # subtract base (background) if desired
                    curPower -= power_base
                    energy = curPower * diffT
                else:
                    curPower -= power_base * diffT
                    energy = curPower
                self.wifi_active_energy += energy
                self.wifi_active_time += diffT
            elif self.data_tcpdump[i][1] == 't':
                curPower = self.myModel.get_wifi_tail_energy(diffT)
                # check if returned is power (watt) or energy (joule)
                if self.myModel.using_power:
                    # subtract base (background) if desired
                    curPower -= power_base
                    energy = curPower * diffT
                else:
                    curPower -= power_base * diffT
                    energy = curPower
                self.wifi_tail_energy += energy
                self.wifi_tail_time += diffT
            else:
                self.logger.debug("{0}".format(self.data_tcpdump[i]))
                self.logger.error(
                    "net_state is not recognized: {0}".format(
                        self.data_tcpdump[i][1]))
        self.wifi_energy = self.wifi_active_energy + self.wifi_tail_energy
        self.wifi_time = self.wifi_active_time + self.wifi_tail_time
        try:
            self.wifi_power = self.wifi_energy / self.wifi_time
        except:
            self.wifi_power = -1
        self.logger.debug("parse_wifi_energy ended")

    def generate_result_summary(self, cpu=True, wifi=True):
        '''
        Print summary of the results
        '''

        self.logger.info("total energy: {0:.4f}mJ".format(
            self.cpu_energy_total + self.wifi_energy))

        if cpu:
            self.logger.info(
                "total cpu energy: {0:.4f}mJ".format(self.cpu_energy_total))
            self.logger.info(
                "total cpu time: {0:.4f}s".format(self.cpu_time_total))
            self.logger.info(
                "average cpu power: {0:.4f}mW".format(self.cpu_power_avg))
            self.logger.info(
                "average cpu util: {0}".format(
                    ",".join(
                        ["{0:.2f}%".format(x * 100)
                         for x in self.cpu_utils_avg])))
        if wifi:
            self.logger.info(
                "total wifi energy: {0:.4f}mJ".format(self.wifi_energy))
            self.logger.info(
                "total wifi time: {0:.4f}s".format(self.wifi_time))
            self.logger.info(
                "total wifi power: {0:.4f}mW".format(self.wifi_power))
            self.logger.info(
                "active wifi {0:.4f}mW vs. idle {1:.4f}mW".format(
                    self.wifi_active_energy, self.wifi_tail_energy))

if __name__ == "__main__":
    # cpuFile = sys.argv[1]
    cpuFile = "./models/test/1485560673559.cpu"
    tcpdumpFile = "./models/test/tcpdump_wifionly_1485560673559"
    ssFile = "./models/test/1485560673559.ss"
    if not os.path.isfile(cpuFile):
        print ".....!"
        sys.exit(-1)
    myObj = EnergyAnalyzer("shamu", isDebugging=True, unit="mW")
    myObj.read_wifi_log(
        tcpdumpFile,
        fp_sslogger=ssFile, tcpdump_filter="host 128.111.68.220")
    myObj.parse_wifi_energy()
    myObj.read_cpu_log(
        cpuFile,
        startT=myObj.net_start_time, endT=myObj.net_end_time)
    myObj.parse_cpu_energy()
    myObj.generate_result_summary()
