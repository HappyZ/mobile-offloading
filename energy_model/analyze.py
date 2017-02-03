import sys
import os

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
        self.results = []
        self.deltas = []
        self.instant_freqs = []
        self.instant_utils = []
        self.instant_power = []
        self.total_idle = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_used = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_energy = 0  # unit as defined in unit
        self.avg_power = 0
        self.avg_utils = []  # [cpu0, cpu1, ..., cpuN, cpuOverall]
        self.total_time = 0

        self.DEBUG = isDebugging
        if self.DEBUG:
            self.logger = EmptyLogger(
                "EnergyAnalyzer", isDebugging=self.DEBUG, printout=True)

    def read_cpu_log(self, filepath,
                     startT=None, endT=None):
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
                delta_t = timestamp - self.results[-1][0]
                delta_total_idle = cpu_total_idle - self.results[-1][1]
                delta_total_used = cpu_total_used - self.results[-1][2]
                delta_per_core = []
            for i in xrange(3, len(tmp), 3):
                cpu_i_idle = int(tmp[i])
                cpu_i_used = int(tmp[i + 1])
                cpu_i_freq = int(tmp[i + 2])
                cpu_per_core.append([cpu_i_idle, cpu_i_used, cpu_i_freq])
                if len(self.results) != 0:
                    delta_per_core.append(
                        [cpu_i_idle - self.results[-1][3][i / 3 - 1][0],
                         cpu_i_used - self.results[-1][3][i / 3 - 1][1],
                         cpu_i_freq])
            if not skipFirstTime:
                self.deltas.append(
                    [delta_t, delta_total_idle,
                     delta_total_used, delta_per_core])
            self.results.append(
                [timestamp, cpu_total_idle, cpu_total_used, cpu_per_core])
            skipFirstTime = False
        self.logger.debug("read_cpu_log ended")

    def get_cpu_energy(self):
        if len(self.deltas) < 1:
            self.logger.error("get_cpu_energy finds delta empty")
            self.logger.error(self.results)
            return
        self.logger.debug("get_cpu_energy started")
        num_of_cores = len(self.deltas[0][3])
        # allocate memory
        self.total_idle = [0 for i in xrange(num_of_cores + 1)]
        self.total_used = [0 for i in xrange(num_of_cores + 1)]
        self.avg_utils = [0 for i in xrange(num_of_cores + 1)]
        for result in self.deltas:
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
            if self.myModel.using_power:
                self.instant_power.append(instant_power)
                energy = instant_power * result[0]
            else:
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
        self.logger.debug("get_cpu_energy ended")


if __name__ == "__main__":
    # cpuFile = sys.argv[1]
    cpuFile = "./models/test/1485560673559.cpu"
    if not os.path.isfile(cpuFile):
        print ".....!"
        sys.exit(-1)
    myObj = EnergyAnalyzer("shamu", isDebugging=True, unit="mW")
    myObj.read_cpu_log(cpuFile)
    myObj.get_cpu_energy()
    # for i in xrange(1, len(myObj.freqs)):
    #     print myObj.freqs[i] - myObj.freqs[i-1]
    # myObj.get_wifi_tail_energy(1)
    # myObj.get_wifi_active_energy(1, -60, isTX=False)
    # myObj.get_cpu_energy(1, [1036800, 422400], [0, 1])