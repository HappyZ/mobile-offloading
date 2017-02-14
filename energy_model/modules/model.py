import sys
import os
import xml.etree.cElementTree as ET
from misc import *


def getVoltage(productname):
    if productname == "shamu":
        return 4.2
    return 1


class Model():
    '''
    The energy model module
    '''

    def __init__(self, isDebugging=False, unit="mW"):
        self.freqs = []
        self.cpu_single_core = {}
        self.cpu_multi_core = {}
        '''
        cpu format: { cpu num:
                        { freq: [active current, idle current], ...}
                    }
        '''
        self.net_wifi = {}
        self.net_LTE = {}
        self.net_3G = {}
        '''
        net format: { 'prom':
                        { 'index': [rssi, current, length], ...},
                      'active':
                        { rssi: [rx current, tx current, rx xput, tx xput]}
                      'tail':
                        { 'index': [rssi, current, length], ...},
                    }
        '''
        self.wifi_min_spike_interval = 0.03
        self.wifi_timeout = 0.21
        self.voltage = 1
        self.unit = unit

        self.DEBUG = isDebugging
        self.logger = EmptyLogger(
            "Model", isDebugging=self.DEBUG, printout=True)

        if 'A' in self.unit:
            tmp = self.unit.replace('A', 'W')
            self.logger.info(
                "Will use power instead: {0} -> {1}".format(tmp, unit))
            self.unit = tmp
        if 'W' in self.unit:
            self.using_power = True
        else:
            self.using_power = False

    def load(self, productname, dir="./models/"):
        self.voltage = getVoltage(productname)
        filepath = "{0}/{1}.xml".format(dir, productname)
        if not os.path.isfile(filepath):
            self.logger.error("File {0} does not exist.".format(filepath))
            sys.exit(-1)
        tree = ET.parse(filepath)
        root = tree.getroot()
        cpumodel = root.find("cpumodel")
        self.parseFreqs(cpumodel.find("freqs"))
        cores = cpumodel.find("cores")
        self.parseCPUSingleCore(cores)
        self.parseCPUMultiCore(cores)
        netmodel = root.find("netmodel")
        for net in netmodel.findall("net"):
            if net.attrib['id'] == 'WIFI':
                self.parseNet(net, self.net_wifi)
            elif net.attrib['id'] == 'LTE':
                self.parseNet(net, self.net_LTE)
            elif net.attrib['id'] == '3G':
                self.parseNet(net, self.net_3G)

    def parseNet(self, node, net_node):
        prom = node.find("prom")
        if prom is None or prom.attrib['numstates'] == '0':
            net_node['prom'] = None
        else:
            net_node['prom'] = {}
            for pst in prom.findall("promstate"):
                net_node['prom'][pst.attrib['index']] = \
                    [int(pst.attrib['index']),
                        float(pst.attrib['prompwr']),
                        float(pst.attrib['promlen'])]

        active = node.find("active")
        if active is None:
            net_node['active'] = None
        else:
            net_node['active'] = {}
            for ast in active.findall("activestate"):
                net_node['active'][int(ast.attrib['rssi'])] = \
                    [float(ast.attrib['rxpwr']),
                        float(ast.attrib['txpwr']),
                        float(ast.attrib['rxxput']),
                        float(ast.attrib['txxput'])]

        tail = node.find("tail")
        if tail is None or tail.attrib['numstates'] == '0':
            net_node['tail'] = None
        else:
            net_node['tail'] = {}
            for tst in tail.findall("tailstate"):
                net_node['tail'][tst.attrib['index']] = \
                    [int(tst.attrib['index']),
                        float(tst.attrib['tailpwr']),
                        float(tst.attrib['taillen'])]

    def parseCPUMultiCore(self, node):
        for core in node.findall("core"):
            if core.attrib['mode'] == 'multicore':
                myid = int(core.attrib['id'])
                if myid in self.cpu_multi_core:
                    myfreq = int(core.attrib['freq'])
                    self.cpu_multi_core[myid][myfreq] = \
                        [float(core.attrib['active']),
                            float(core.attrib['idle'])]
                else:
                    self.cpu_multi_core[myid] = \
                        {int(core.attrib['freq']):
                            [float(core.attrib['active']),
                                float(core.attrib['idle'])]}

    def parseCPUSingleCore(self, node):
        for core in node.findall("core"):
            if core.attrib['mode'] == 'singlecore':
                myid = int(core.attrib['id'])
                if myid in self.cpu_single_core:
                    myfreq = int(core.attrib['freq'])
                    self.cpu_single_core[myid][myfreq] = \
                        [float(core.attrib['active']),
                            float(core.attrib['idle'])]
                else:
                    self.cpu_single_core[myid] = \
                        {int(core.attrib['freq']):
                            [float(core.attrib['active']),
                                float(core.attrib['idle'])]}

    def parseFreqs(self, node):
        for freq in node.findall("freq"):
            self.freqs.append(int(freq.attrib['val']))

    def get_final_energy(self, current, time):
        '''
        @param current: mA
        @param time: s
        @return defined energy with unit conversion
        '''
        # self.logger.debug("current: {0}, time_diff: {1:.8f}".format(
        #    current, time))
        if 'W' in self.unit:
            tmp = current * self.voltage
        elif 'J' in self.unit:
            tmp = current * self.voltage * time
        else:
            self.logger.error(
                "Unit {0} not supported!".format(self.unit))
            sys.exit(-1)
        # self.logger.debug("tmp: {0}".format(tmp, current * self.voltage))
        if 'm' == self.unit[0]:
            return tmp
        else:
            return tmp / 1000.0

    def get_cpu_energy(self, time_diff, freq, util):
        '''
        @param freq: list of cpu frequencies
        @param util: list of cpu utilization
        @return: energy in desired unit, default is mW
        '''
        if len(freq) != len(util) or len(freq) < 1:
            self.logger.error("freq & util have different length!")
            sys.exit(-1)
        current = 0
        if len(freq) > 1:
            db = self.cpu_multi_core
        else:
            db = self.cpu_single_core
        for i in xrange(len(freq)):
            if freq[i] <= 0:
                continue
            if freq[i] not in db[i]:
                minDiff = float("inf")
                myJ = None
                for j in xrange(len(self.freqs)):
                    tmp = abs(self.freqs[j] - freq[i])
                    if tmp < minDiff:
                        minDiff = tmp
                        myJ = j
                closestFreq = self.freqs[j]
                self.logger.debug("Freq outlier: {0}. ".format(freq[i]) +
                                  "Use {0} instead.".format(closestFreq))
                freq[i] = closestFreq
            active_current = db[i][freq[i]][0]
            idle_current = db[i][freq[i]][1]
            current += util[i] * active_current + (1 - util[i]) * idle_current
        # derive power or energy
        result = self.get_final_energy(current, time_diff)
        self.logger.debug(
            "duration: {0:.4f}s, cpu_energy: {1:.4f}{2}".format(
                time_diff, result, self.unit))
        return result

    def get_lte_prom_energy(self, time_diff, rssi, isTX=True):
        self.logger.error('TODO: not implemented yet')
        return None

    def get_lte_tail_energy(self, time_diff, rssi, isTX=True):
        self.logger.error('TODO: not implemented yet')
        return None

    def get_lte_active_energy(self, time_diff, rssi, isTX=True):
        self.logger.error('TODO: not implemented yet')
        return None

    def get_wifi_active_energy(self, time_diff, rssi, isTX=True):
        table_rssi = sorted(self.net_wifi['active'].keys(), reverse=True)
        if isTX:
            currentIdx = 1
        else:
            currentIdx = 0
        # fetch current
        current = None
        if rssi >= table_rssi[0]:
            current = self.net_wifi['active'][table_rssi[0]][currentIdx]
        elif rssi < table_rssi[-1]:
            current = self.net_wifi['active'][table_rssi[-1]][currentIdx]
        else:
            for i in xrange(1, len(table_rssi)):
                if rssi >= table_rssi[i]:
                    endp = self.net_wifi['active'][table_rssi[i-1]][currentIdx]
                    startp = self.net_wifi['active'][table_rssi[i]][currentIdx]
                    rssi_diff = table_rssi[i - 1] - table_rssi[i]
                    current = (endp + 1.0 * (endp - startp) *
                               (rssi - table_rssi[i-1]) / rssi_diff)
                    break
        if current is None:
            self.logger.error("Current {0} is nothing!".format(current))
            sys.exit(-1)
        # derive power or energy
        result = self.get_final_energy(current, time_diff)
        # self.logger.debug(
        #     "cpu_energy: {0:.4f}{1}".format(result, self.unit))
        return result

    def get_wifi_tail_energy(self, time_diff):
        current = self.net_wifi['tail']['0'][1]
        result = self.get_final_energy(current, time_diff)
        # self.logger.debug(
        #     "cpu_energy: {0:.4f}{1}".format(result, self.unit))
        return result

if __name__ == "__main__":
    print "Usage: from model import *"
