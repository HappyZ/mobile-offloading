import sys
import os
import re
import subprocess

# sys.path.append("modules")
try:
    from model import *
except:
    raise


class EnergyAnalyzer():
    '''
    Energy analyzer
    '''
    def __init__(self, productname,
                 isDebugging=False, unit="mW", output_path=None, logger=None):
        self.myModel = Model(isDebugging=isDebugging, unit=unit)
        self.myModel.load(productname)
        self.num_of_cores = getCoreNum(productname)
        # first time initialization
        self.clean_up_cpu()
        self.clean_up_net()
        # define output path, if not set then will not output to file
        self.output_path = output_path

        self.DEBUG = isDebugging
        if logger is None:
            self.logger = EmptyLogger(
                "EnergyAnalyzer", isDebugging=self.DEBUG, printout=True)
        else:
            self.logger = logger

    '''
    Variable clean up functions
    '''
    def clean_up_cpu(self):
        self.clean_up_cpu_data()
        self.clean_up_cpu_result()

    def clean_up_cpu_data(self):
        self.data_cpu = []  # (sorted) cpu results of logs
        self.data_cpu_details = []
        self.data_cpu_d = []  # deltas between pair of results
        self.data_cpu_details_d = []  # deltas between pair of results
        self.avg_log_freq = 0

    def clean_up_cpu_result(self):
        self.instant_freqs = []  # freq of each core at time unit
        self.instant_utils = []  # util perc of each core at time unit
        self.instant_power = []  # power spent at time unit
        self.cpu_idle = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.cpu_used = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
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
        self.net_start_time = float('-inf')
        self.net_end_time = float('inf')
        self.data_size = 0
        self.wifi_avg_thrpt = 0
        # WiFi
        self.wifi_rssi = []
        # LTE
        self.lte_rsrp = []
        # GSM
        self.gsm_rssi = []

    def clean_up_wifi_result(self):
        self.wifi_instant_thrpt = []
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
    def parse_cpu_raw(self, filepath, details=True):
        def get_user(tmp, offset):
            return int(tmp[1 + offset])

        def get_nice(tmp, offset):
            return int(tmp[2 + offset])

        def get_system(tmp, offset):
            return int(tmp[3 + offset])

        def get_idle(tmp, offset):
            return int(tmp[4 + offset])

        def get_iowait(tmp, offset):
            return int(tmp[5 + offset])

        def get_irq(tmp, offset):
            return int(tmp[6 + offset])

        def get_softirq(tmp, offset):
            return int(tmp[7 + offset])

        def get_freq(tmp, offset):
            return int(tmp[11 + offset])

        def get_busy(tmp, offset):
            return get_user(tmp, offset) + get_nice(tmp, offset) +\
                get_system(tmp, offset) + get_iowait(tmp, offset) +\
                get_irq(tmp, offset) + get_softirq(tmp, offset)

        if not os.path.isfile(filepath):
            self.logger.error(
                "cpu raw log file {0} does not exist".format(filepath))
            sys.exit(-1)

        self.logger.debug("parse_cpu_raw started")
        with open(filepath, 'rU') as f:
            contents = f.readlines()
        f = open(filepath[:-3], 'wb')
        if details:
            f2 = open(filepath[:-3] + 'Detail', 'wb')
        for line in contents:
            tmp = line.split()
            f.write("{0} {1} {2}".format(
                tmp[0], get_idle(tmp, 1), get_busy(tmp, 1)))
            if details:
                f2.write("{0}:{1} {2} {3} {4} {5} {6} {7}".format(
                    tmp[0], get_busy(tmp, 1) + get_idle(tmp, 1),
                    get_user(tmp, 1), get_nice(tmp, 1),
                    get_system(tmp, 1), get_iowait(tmp, 1),
                    get_irq(tmp, 1), get_softirq(tmp, 1)))
            for i in xrange(self.num_of_cores):
                offset = (i + 1) * 12
                f.write(" {0} {1} {2}".format(
                    get_idle(tmp, offset),
                    get_busy(tmp, offset),
                    get_freq(tmp, offset)))
                if details:
                    f2.write(":{0} {1} {2} {3} {4} {5} {6}".format(
                        get_busy(tmp, offset) + get_idle(tmp, offset),
                        get_user(tmp, offset), get_nice(tmp, offset),
                        get_system(tmp, offset), get_iowait(tmp, offset),
                        get_irq(tmp, offset), get_softirq(tmp, offset)))
            f.write('\n')
            if details:
                f2.write('\n')
        f.close()
        if details:
            f2.close()
        self.logger.debug("parse_cpu_raw ended")

    def read_cpu_log(self, filepath,
                     startT=float('-inf'), endT=float('inf'),
                     details=True):
        if '.cpuRaw' in filepath or not os.path.isfile(filepath):
            if '.cpuRaw' in filepath:
                self.parse_cpu_raw(filepath)
                filepath = filepath[:-3]
            else:
                self.parse_cpu_raw(filepath + 'Raw')
        if not os.path.isfile(filepath):
            self.logger.error(
                "cpu log file {0} does not exist".format(filepath))
            sys.exit(-1)
        self.logger.debug("clean up cpu data")
        self.clean_up_cpu_data()
        self.logger.debug("read_cpu_log started")
        contents = []
        skipFirstTime = True
        timeGap_sum_buff = 0
        timeGap = 0.1 * 2
        with open(filepath, 'rU') as f:
            contents = f.readlines()
        if details:
            with open(filepath + 'Detail', 'rU') as f:
                contents2 = f.readlines()
        for line_idx in xrange(len(contents)):
            line = contents[line_idx]
            line_details = contents2[line_idx]
            tmp = line.rstrip().split(' ')
            if details:
                tmp2 = line_details.rstrip().split(':')
                tmp2_cpu = tmp2[1].split(' ')
            if len(tmp) < 3:
                print "something is wrong at splitting the line for cpu_log"
                sys.exit(-1)
            timestamp = int(tmp[0]) / 1000.0  # ms -> s
            # self.logger.debug(
            #     "time: {0}, startT: {1}, endT: {2}".format(
            #         timestamp, startT, endT))
            if timestamp < (startT - timeGap) or timestamp > (endT + timeGap):
                continue
            timeGap_sum_buff += timeGap
            # self.logger.debug("passed")
            cpu_cpu_idle = int(tmp[1])
            cpu_cpu_used = int(tmp[2])
            cpu_per_core = []
            if details:
                cpu_cpu_user = int(tmp2_cpu[1])
                cpu_cpu_nice = int(tmp2_cpu[2])
                cpu_cpu_system = int(tmp2_cpu[3])
                cpu_cpu_iowait = int(tmp2_cpu[4])
                cpu_cpu_irq = int(tmp2_cpu[5])
                cpu_cpu_softirq = int(tmp2_cpu[6])
            if not skipFirstTime:
                delta_t = timestamp - self.data_cpu[-1][0]
                timeGap = delta_t * 2
                # self.logger.debug(timeGap)
                delta_cpu_idle = cpu_cpu_idle - self.data_cpu[-1][1]
                delta_cpu_used = cpu_cpu_used - self.data_cpu[-1][2]
                delta_per_core = []
                if details:
                    delta_cpu_user = \
                        cpu_cpu_user - self.data_cpu_details[-1][0]
                    delta_cpu_nice = \
                        cpu_cpu_nice - self.data_cpu_details[-1][1]
                    delta_cpu_system = \
                        cpu_cpu_system - self.data_cpu_details[-1][2]
                    delta_cpu_iowait = \
                        cpu_cpu_iowait - self.data_cpu_details[-1][3]
                    delta_cpu_irq = cpu_cpu_irq - self.data_cpu_details[-1][4]
                    delta_cpu_softirq = \
                        cpu_cpu_softirq - self.data_cpu_details[-1][5]
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
                    [delta_t, delta_cpu_idle,
                     delta_cpu_used, delta_per_core])
                self.data_cpu_details_d.append(
                    [delta_cpu_user, delta_cpu_nice, delta_cpu_system,
                     delta_cpu_iowait, delta_cpu_irq, delta_cpu_softirq])
            self.data_cpu.append(
                [timestamp, cpu_cpu_idle, cpu_cpu_used, cpu_per_core])
            self.data_cpu_details.append(
                [cpu_cpu_user, cpu_cpu_nice, cpu_cpu_system,
                 cpu_cpu_iowait, cpu_cpu_irq, cpu_cpu_softirq])
            skipFirstTime = False
        if len(self.data_cpu_d) < 1:
            self.logger.error("parse_cpu_energy finds delta empty")
            self.logger.error(self.data_cpu)
            self.logger.error("check the file and see why")
        # calculate the logging frequency
        self.avg_log_freq = timeGap_sum_buff / 2 / len(self.data_cpu_d)
        self.logger.debug("read_cpu_log ended")

    def parse_cpu_energy(self,
                         power_base=0, details=True):
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
        self.cpu_idle = [0 for i in xrange(num_of_cores + 1)]
        self.cpu_used = [0 for i in xrange(num_of_cores + 1)]
        self.cpu_utils_avg = [0 for i in xrange(num_of_cores + 1)]
        if details:
            self.cpu_user_util = 0
            self.cpu_nice_util = 0
            self.cpu_system_util = 0
            self.cpu_iowait_util = 0
            self.cpu_irq_util = 0
            self.cpu_softirq_util = 0
        for result_idx in xrange(len(self.data_cpu_d)):
            result = self.data_cpu_d[result_idx]
            # allocate memory
            freqs = [0 for i in xrange(num_of_cores)]
            utils = [0 for i in xrange(num_of_cores + 1)]
            for i in xrange(num_of_cores):
                if result[3][i][0] + result[3][i][1] > 0:
                    utils[i] = 1.0 * \
                        result[3][i][1] / (result[3][i][0] + result[3][i][1])
                    self.cpu_idle[i] += result[3][i][0]
                    self.cpu_used[i] += result[3][i][1]
                else:
                    utils[i] = 0
                freqs[i] = result[3][i][-1]
            # calculate total
            if result[1] + result[2] > 0:
                utils[-1] = 1.0 * result[2] / (result[1] + result[2])
                self.cpu_idle[-1] += result[1]
                self.cpu_used[-1] += result[2]
            else:
                utils[-1] = 0
            if details:
                self.cpu_user_util += self.data_cpu_details_d[result_idx][0]
                self.cpu_nice_util += self.data_cpu_details_d[result_idx][1]
                self.cpu_system_util += self.data_cpu_details_d[result_idx][2]
                self.cpu_iowait_util += self.data_cpu_details_d[result_idx][3]
                self.cpu_irq_util += self.data_cpu_details_d[result_idx][4]
                self.cpu_softirq_util += self.data_cpu_details_d[result_idx][5]
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
        if details:
            self.cpu_user_util = 1.0 * \
                self.cpu_user_util / (self.cpu_used[-1] + self.cpu_idle[-1])
            self.cpu_nice_util = 1.0 * \
                self.cpu_nice_util / (self.cpu_used[-1] + self.cpu_idle[-1])
            self.cpu_system_util = 1.0 * \
                self.cpu_system_util / (self.cpu_used[-1] + self.cpu_idle[-1])
            self.cpu_iowait_util = 1.0 * \
                self.cpu_iowait_util / (self.cpu_used[-1] + self.cpu_idle[-1])
            self.cpu_irq_util = 1.0 * \
                self.cpu_irq_util / (self.cpu_used[-1] + self.cpu_idle[-1])
            self.cpu_softirq_util = 1.0 * \
                self.cpu_softirq_util / (self.cpu_used[-1] + self.cpu_idle[-1])
        self.cpu_power_avg = self.cpu_energy_total / self.cpu_time_total
        for i in xrange(num_of_cores + 1):
            self.cpu_utils_avg[i] = 1.0 * \
                self.cpu_used[i] / (self.cpu_used[i] + self.cpu_idle[i])
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
                    # [timestamp (ms), wifi rssi]
                    # or
                    # [timestamp (ms), gsm rssi, lte ss, lte rsrp, lte rsrq]
                    tmp = myMatch.groups()
                    break
            if tmp is None:
                self.logger.debug("nothing found in {0}".format(line.rstrip()))
                continue
            if len(tmp) > 2:
                # [timestamp (ss), lte rsrp]
                self.lte_rsrp.append([int(tmp[0]) / 1000.0, int(tmp[3])])
                # [timestamp (ss), gsm rssi]
                self.gsm_rssi.append([int(tmp[0]) / 1000.0, int(tmp[1])])
            else:
                # [timestamp (s), wifi rssi]
                self.wifi_rssi.append([int(tmp[0]) / 1000.0, int(tmp[1])])
        # self.logger.debug(self.lte_rsrp)
        # self.logger.debug(self.wifi_rssi)

    def read_tcpdump_file(self, fp_tcpdump,
                          size_limit=None,
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
                      'Flags \[([\w\.]+)\], seq (\d+),',
                        # UDP
                      '([\d\.]+) IP ([\d\.]+) \> ([\d\.]+): ' +
                      'UDP, length (\d+)',
                        # offloading
                      '([\d\.]+) IP0 bad-hlen 0']
        with open(fp_tcpdump, 'rU') as f:
            content = f.readlines()
        firstLine = True
        total_bytes = 0
        # check the network status: idle, active, or tail
        net_state = 'a'  # active
        for line in content:
            tmp = None
            for pattern_i in xrange(len(myPatterns)):
                myMatch = re.search(myPatterns[pattern_i], line)
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
            # if pattern_i == 1:
            #     data_type = 'ack'
            # else:
            #     data_type = 'seq'
            data_len = 0
            if len(tmp) > 5 or pattern_i > 2:
                if pattern_i == 3:  # UDP
                    data_len = int(tmp[3])
                elif pattern_i == 4:  # offloading
                    data_len = 1488
                else:
                    data_len = int(tmp[5]) - int(tmp[4])
                total_bytes += data_len
                # self.logger.debug("{0}".format(total_bytes))
                if size_limit is not None and total_bytes > size_limit:
                    break
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
            sys.exit(1)
        # the last one must be tail
        self.data_tcpdump[-1][1] = 't'
        # after self.myModel.wifi_timeout, it will become idle
        self.data_tcpdump.append(
            [self.data_tcpdump[-1][0] + self.myModel.wifi_timeout, 'i', 0])
        # self.logger.debug(self.data_tcpdump)
        # get the start and end time of network
        self.net_start_time = self.data_tcpdump[0][0]
        self.net_end_time = self.data_tcpdump[-2][0]  # true net end time
        self.data_size = total_bytes
        self.wifi_avg_thrpt = \
            total_bytes / (self.net_end_time - self.net_start_time)

    def read_wifi_log(self, fp_tcpdump, tcpdump_filter="",
                      size_limit=None,
                      fp_sslogger=None, delete_ori_tcpdump=True):
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
                if delete_ori_tcpdump:
                    subprocess.call("rm {0}".format(fp_tcpdump), shell=True)
            fp_tcpdump += '.tcpdump'
        # read the file
        self.read_tcpdump_file(fp_tcpdump, size_limit=size_limit, isWiFi=True)
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
        curRSSI_max = sorted(
            self.myModel.net_wifi['active'].keys(), reverse=True)[0]
        curRSSI = curRSSI_max
        curRSSI_idx = None
        if len(self.wifi_rssi) > 0:
            curRSSI_idx = 0
            curRSSI = self.wifi_rssi[0][1]
        # for convenience the first throughput point is initially set to 0
        self.wifi_instant_thrpt.append(
            [self.data_tcpdump[0][0], 0])
        tmp_data_size = 0
        # derive energy for wifi network
        for i in xrange(len(self.data_tcpdump) - 1):
            # find my current rssi (only if sslogger has the data)
            if curRSSI_idx is not None and \
                    curRSSI_idx < len(self.wifi_rssi) - 1:
                while self.wifi_rssi[
                        curRSSI_idx + 1][0] < self.data_tcpdump[i][0]:
                    curRSSI_idx += 1
                    if curRSSI_idx >= len(self.wifi_rssi) - 1:
                        curRSSI_idx -= 1
                        break
                curRSSI = self.wifi_rssi[curRSSI_idx][1]
            # self.logger.debug("myRSSI: {0}dB".format(curRSSI))
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
                tmp_data_size += self.data_tcpdump[i][2]
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
            # only calculate throughput if time difference is large enough
            diffT = self.data_tcpdump[i][0] - self.wifi_instant_thrpt[-1][0]
            if diffT > 0.1:
                thrpt = 1.0 * tmp_data_size / diffT
                # reset the previous thrpt
                self.wifi_instant_thrpt[-1][1] = thrpt
                # add a new one
                self.wifi_instant_thrpt.append(
                    [self.data_tcpdump[i][0], 0])
                tmp_data_size = 0
        # the last thrpt
        if diffT > 0:
            self.wifi_instant_thrpt[-1][1] = 1.0 * tmp_data_size / diffT
            self.wifi_instant_thrpt.append(
                [self.data_tcpdump[-2][0], 0])
            self.wifi_instant_thrpt.append(
                [self.data_tcpdump[-1][0], 0])
        # self.logger.debug(self.wifi_instant_thrpt)
        # energy
        self.wifi_energy = self.wifi_active_energy + self.wifi_tail_energy
        self.wifi_time = self.wifi_active_time + self.wifi_tail_time
        try:
            self.wifi_power = self.wifi_energy / self.wifi_time
        except:
            self.wifi_power = -1
        self.logger.debug("parse_wifi_energy ended")

    def generate_result_summary(
            self,
            cpu=True, wifi=True, f_suffix="", details=True):
        '''
        Generate summary of the results
        '''
        total_energy = self.cpu_energy_total + self.wifi_energy
        total_time = self.wifi_time if wifi else self.cpu_time_total
        avg_power = total_energy / total_time

        self.logger.info(
            "total energy: {0:.4f}mJ; time: {1:.4f}s; power: {2:.4f}mW".format(
                total_energy, total_time, avg_power))
        self.logger.info(
            "avg logging freq: {0:.4f}s/record".format(self.avg_log_freq))

        # if write to file, first generate overview
        f = None
        if self.output_path is not None:
            overview_fn = "{0}/result_overview.csv".format(self.output_path)
            if os.path.isfile(overview_fn):
                f = open(overview_fn, 'ab')
            else:
                f = open(overview_fn, 'wb')
                # first line description
                f.write('#data_size(MB),avg_thrpt(Mbps),' +
                        'total_energy(mJ),total_time(s),avg_total_pwr(mW),' +
                        'avg_logging_freq(s/record)')
                if cpu:
                    f.write(',cpu_energy(mJ),' +
                            'cpu_time(s),avg_cpu_pwr(mW),' +
                            ','.join(
                                ['avg_cpu{0}_util(%)'.format(
                                    x) for x in xrange(
                                    len(self.cpu_utils_avg) - 1)]) +
                            ',avg_cpu_util(%)')
                if wifi:
                    f.write(',wifi_energy(mJ),wifi_time(s),avg_wifi_pwr(mW),' +
                            'wifi_active_energy(mJ),wifi_idle_energy(mJ)')
                if details:
                    f.write(',cpu_user_util(%),cpu_nice_util(%)' +
                            ',cpu_system_util(%),cpu_iowait_util(%)' +
                            ',cpu_irq_util(%),cpu_softirq_util(%)')
                f.write('\n')
            f.write('{0:.2f},'.format(self.data_size / 1000000.0))
            f.write('{0:.2f},'.format(self.wifi_avg_thrpt / 1000000.0 * 8))
            f.write('{0:.8f},{1:.8f},{2:.8f},'.format(
                total_energy, total_time, avg_power))
            f.write('{0:.8f}'.format(self.avg_log_freq))

        # if output cpu
        if cpu:
            self.logger.info(
                "total cpu energy: {0:.4f}mJ".format(self.cpu_energy_total))
            self.logger.info(
                "total cpu time: {0:.4f}s".format(self.cpu_time_total))
            self.logger.info(
                "average cpu power: {0:.4f}mW".format(self.cpu_power_avg))
            tmp = ",".join(["{0:.2f}".format(x * 100)
                            for x in self.cpu_utils_avg])
            self.logger.info(
                "average cpu util (%): {0}".format(tmp))
            # write to file
            if f is not None:
                f.write(',{0:.8f},{1:.8f},{2:.8f},{3}'.format(
                    self.cpu_energy_total, self.cpu_time_total,
                    self.cpu_power_avg, tmp))

        # if output wifi
        if wifi:
            self.logger.info(
                "total wifi energy: {0:.4f}mJ".format(self.wifi_energy))
            self.logger.info(
                "total wifi time: {0:.4f}s".format(self.wifi_time))
            self.logger.info(
                "avg wifi power: {0:.4f}mW".format(self.wifi_power))
            self.logger.info(
                "active wifi {0:.4f}mJ vs. idle {1:.4f}mJ".format(
                    self.wifi_active_energy, self.wifi_tail_energy))
            # write to file
            if f is not None:
                f.write(',{0:.8f},{1:.8f},{2:.8f},{3:.8f},{4:.8f}'.format(
                    self.wifi_energy, self.wifi_time, self.wifi_power,
                    self.wifi_active_energy, self.wifi_tail_energy))

        if details:
            self.logger.info(
                "cpu user util: {0:.2f}%".format(self.cpu_user_util * 100))
            self.logger.info(
                "cpu nice util: {0:.2f}%".format(self.cpu_nice_util * 100))
            self.logger.info(
                "cpu system util: {0:.2f}%".format(self.cpu_system_util * 100))
            self.logger.info(
                "cpu iowait util: {0:.2f}%".format(self.cpu_iowait_util * 100))
            self.logger.info(
                "cpu irq util: {0:.2f}%".format(self.cpu_irq_util * 100))
            self.logger.info(
                "cpu softirq util: {0:.2f}%".format(
                    self.cpu_softirq_util * 100))
            if f is not None:
                f.write(
                    ',{0:.4f},{1:.4f},{2:.4f},{3:.4f},{4:.4f},{5:.4f}'.format(
                        self.cpu_user_util * 100, self.cpu_nice_util * 100,
                        self.cpu_system_util * 100, self.cpu_iowait_util * 100,
                        self.cpu_irq_util * 100, self.cpu_softirq_util * 100))

        if f is not None:
            f.write('\n')
            self.logger.info(
                "Wrote to file {0}".format(overview_fn))
            f.close()

        # now generate instant wifi thrpt
        if wifi and self.output_path is not None:
            fn = "{0}/result_wifi_instant_{1:.2f}MB{2}.csv".format(
                self.output_path, self.data_size / 1000000.0, f_suffix)
            f = open(fn, 'wb')
            # description
            f.write('#time(s),time_delta(s),throughput(Mbps)\n')
            for i in xrange(len(self.wifi_instant_thrpt) - 1):
                f.write('{0:.2f},{1:.2f},{2:.2f}\n'.format(
                    self.wifi_instant_thrpt[i][0],
                    self.wifi_instant_thrpt[i][0] -
                    self.wifi_instant_thrpt[0][0],
                    self.wifi_instant_thrpt[i][1] / 1000000.0 * 8))
            # f.write('{0:.2f},{1:.2f},{2:.2f}\n'.format(
            #     self.wifi_instant_thrpt[-1][0],
            #     self.wifi_instant_thrpt[-1][0] -
            #     self.wifi_instant_thrpt[0][0],
            #     0))
            f.close()
            self.logger.info("Wrote to file {0}".format(fn))

        # now generate instant cpu
        if cpu and self.output_path is not None:
            fn = "{0}/result_cpu_instant_{1:.2f}MB{2}.csv".format(
                self.output_path, self.data_size / 1000000.0, f_suffix)
            f = open(fn, 'wb')
            # description
            num_of_cores = len(self.instant_freqs[0])
            f.write('#time(s),time_delta(s),' +
                    ','.join(['freq_cpu{0}'.format(x)
                              for x in xrange(num_of_cores)]) +
                    ',' +
                    ','.join(['util_cpu{0}'.format(x)
                              for x in xrange(num_of_cores)]) +
                    ',util_cpu,' +
                    'power\n')
            for i in xrange(len(self.instant_freqs)):
                f.write('{0:.2f},'.format(self.data_cpu[i+1][0]))
                f.write('{0:.2f},'.format(
                    self.data_cpu[i][0] - self.data_cpu[0][0]))
                for freq in self.instant_freqs[i]:
                    f.write('{0:d},'.format(freq))
                for util in self.instant_utils[i]:
                    f.write('{0:.2f},'.format(util * 100))
                f.write('{0:.8f}\n'.format(self.instant_power[i]))
            f.close()
            self.logger.info("Wrote to file {0}".format(fn))

if __name__ == "__main__":
    # cpuFile = sys.argv[1]
    # cpuFile = "./models/test/1485560673559.cpu"
    # tcpdumpFile = "./models/test/tcpdump_wifionly_1485560673559"
    # ssFile = "./models/test/1485560673559.ss"
    cpuFile = "./models/test2/1487031992798.cpu"
    tcpdumpFile = "./models/test2/tcpdump_wifionly_1487031992798"
    ssFile = "./models/test2/1487031992798.ss"
    myObj = EnergyAnalyzer(
        "shamu", isDebugging=False, unit="mW",
        output_path="./models/test2/")
    myObj.read_wifi_log(
        tcpdumpFile,
        size_limit=None,  # 1000*1000*90,
        fp_sslogger=ssFile, tcpdump_filter="host 192.168.10.1")
    myObj.parse_wifi_energy()
    myObj.read_cpu_log(
        cpuFile,
        startT=myObj.net_start_time, endT=myObj.net_end_time)
    myObj.parse_cpu_energy()
    myObj.generate_result_summary(f_suffix="_test")
