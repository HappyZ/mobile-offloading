import sys
import os

sys.path.append("modules")
try:
    from model import *
except:
    raise


def read_cpu_log(filepath, startT=None, endT=None, isDelta=True):
    contents = []
    results = []
    deltas = []
    skipFirstTime = True
    with open(filepath, 'rU') as f:
        contents = f.readlines()
    for line in contents:
        tmp = line.rstrip().split(' ')
        if len(tmp) < 3:
            print "something is wrong at splitting the line for cpu_log"
            sys.exit(-1)
        timestamp = int(tmp[0])  # ms
        if (startT is not None and timestamp < startT) \
                or (endT is not None and timestamp > endT):
            continue
        cpu_total_idle = int(tmp[1])
        cpu_total_used = int(tmp[2])
        cpu_per_core = []
        if isDelta and not skipFirstTime:
            delta_t = timestamp - results[-1][0]
            delta_total_idle = cpu_total_idle - results[-1][1]
            delta_total_used = cpu_total_used - results[-1][2]
            delta_per_core = []
        for i in xrange(3, len(tmp), 3):
            cpu_i_idle = int(tmp[i])
            cpu_i_used = int(tmp[i + 1])
            cpu_i_freq = int(tmp[i + 2])
            cpu_per_core.append([cpu_i_idle, cpu_i_used, cpu_i_freq])
            if isDelta and len(results) != 0:
                delta_per_core.append(
                    [cpu_i_idle - results[-1][3][i / 3 - 1][0],
                     cpu_i_used - results[-1][3][i / 3 - 1][1],
                     cpu_i_freq])
        if isDelta and not skipFirstTime:
            deltas.append(
                [delta_t, delta_total_idle, delta_total_used, delta_per_core])
        results.append(
            [timestamp, cpu_total_idle, cpu_total_used, cpu_per_core])
        skipFirstTime = False
    if isDelta:
        return deltas, results
    return results


def get_cpu_energy(results, model):
    energy = 0
    if len(results) < 1:
        return energy
    num_of_cores = len(results[0][3])
    for result in results:
        freqs = [0 for i in xrange(num_of_cores)]
        utils = [0 for i in xrange(num_of_cores + 1)]
        for i in xrange(num_of_cores):
            if result[3][i][0] + result[3][i][1] > 0:
                utils[i] = 1.0 * \
                    result[3][i][1] / (result[3][i][0] + result[3][i][1])
            else:
                utils[i] = 0
            freqs[i] = result[3][i][-1]
        # calculate total
        if result[1] + result[2] > 0:
            utils[-1] = 1.0 * result[1] / (result[1] + result[2])
        else:
            utils[-1] = 0
        energy += model.get_cpu_energy(result[0] / 1000.0, freqs, utils[:-1])


if __name__ == "__main__":
    # cpuFile = sys.argv[1]
    cpuFile = "1485560673559.cpu"
    if not os.path.isfile(cpuFile):
        print ".....!"
        sys.exit(-1)
    deltas, results = read_cpu_log(cpuFile, isDelta=True)
    myObj = Model(isDebuging=True, unit="mW")
    # myObj.load(sys.argv[1])
    myObj.load("shamu")
    get_cpu_energy(deltas, myObj)
    # for i in xrange(1, len(myObj.freqs)):
    #     print myObj.freqs[i] - myObj.freqs[i-1]
    # myObj.get_wifi_tail_energy(1)
    # myObj.get_wifi_active_energy(1, -60, isTX=False)
    # myObj.get_cpu_energy(1, [1036800, 422400], [0, 1])