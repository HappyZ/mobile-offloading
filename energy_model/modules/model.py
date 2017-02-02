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

    def __init__(self, isDebuging=False, use_uAh=False):
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
        self.DEBUG = isDebuging
        self.logger = None
        if isDebuging:
            self.logger = EmptyLogger("Model", printout=True)
        if use_uAh:
            self._ratio_uAh_over_mAs = 5 / 18.0
        else:
            self._ratio_uAh_over_mAs = 1.0

    def load(self, productname, dir="../models/"):
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
                        int(pst.attrib['prompwr']),
                        int(pst.attrib['promlen'])]

        active = node.find("active")
        if active is None:
            net_node['active'] = None
        else:
            net_node['active'] = {}
            for ast in active.findall("activestate"):
                net_node['active'][int(ast.attrib['rssi'])] = \
                    [int(ast.attrib['rxpwr']),
                        int(ast.attrib['txpwr']),
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
                        int(tst.attrib['tailpwr']),
                        int(tst.attrib['taillen'])]

        # print net_node

    def parseCPUMultiCore(self, node):
        for core in node.findall("core"):
            if core.attrib['mode'] == 'multicore':
                myid = int(core.attrib['id'])
                if myid in self.cpu_multi_core:
                    myfreq = int(core.attrib['freq'])
                    self.cpu_multi_core[myid][myfreq] = \
                        [int(core.attrib['active']),
                            int(core.attrib['idle'])]
                else:
                    self.cpu_multi_core[myid] = \
                        {int(core.attrib['freq']):
                            [int(core.attrib['active']),
                                int(core.attrib['idle'])]}
        # print self.cpu_multi_core

    def parseCPUSingleCore(self, node):
        for core in node.findall("core"):
            if core.attrib['mode'] == 'singlecore':
                myid = int(core.attrib['id'])
                if myid in self.cpu_single_core:
                    myfreq = int(core.attrib['freq'])
                    self.cpu_single_core[myid][myfreq] = \
                        [int(core.attrib['active']),
                            int(core.attrib['idle'])]
                else:
                    self.cpu_single_core[myid] = \
                        {int(core.attrib['freq']):
                            [int(core.attrib['active']),
                                int(core.attrib['idle'])]}
        # print self.cpu_single_core

    def parseFreqs(self, node):
        for freq in node.findall("freq"):
            self.freqs.append(int(freq.attrib['val']))
        # if self.DEBUG:
        #     self.logger.debug(self.freqs)

    def get_cpu_energy(self, time_diff, freq, util):
        '''
        @param freq: list of cpu frequencies
        @param util: list of cpu utilization
        '''
        if len(freq) != len(util) or len(freq) < 1:
            self.logger.error("freq & util have different length!")
            sys.exit(-1)
        current = 0
        if len(freq) > 1:
            db = self.cpu_multi_core
            for i in xrange(len(freq)):
                if freq[i] <= 0 or freq[i] not in db[i]:
                    self.logger.error("freq outlier: {0}".format(freq[i]))
                    self.logger.debug(db[i])
                    continue
                active_current = db[i][freq[i]][0]
                idle_current = db[i][freq[i]][1]
                current += util[i] * (active_current - idle_current) + \
                    idle_current
        else:
            db = self.cpu_single_core
            if freq[0] <= 0 or freq[0] not in db[0]:
                self.logger.error("freq outlier: {0}".format(f))
                self.logger.debug(db[i])
            else:
                active_current = db[0][freq[0]][0]
                idle_current = db[0][freq[0]][1]
                current = util[0] * (active_current - idle_current) + \
                    idle_current
        # derive energy
        energy = current * time_diff * self._ratio_uAh_over_mAs
        if self.DEBUG:
            self.logger.debug("cpu_energy: {0:.4f}".format(energy))
        return energy

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
                    rssi_diff = table_rssi[i-1] - table_rssi[i]
                    current = (endp + 1.0 * (endp - startp) *
                               (rssi - table_rssi[i-1]) / rssi_diff)
                    break
        if current is None:
            self.logger.error("Current {0} is nothing!".format(current))
            sys.exit(-1)
        # derive energy
        energy = current * time_diff * self._ratio_uAh_over_mAs
        if self.DEBUG:
            self.logger.debug("wifi_active_energy: {0:.4f}".format(energy))
        return energy

    def get_wifi_tail_energy(self, time_diff):
        energy = (time_diff * self.net_wifi['tail']['0'][1] *
                  self._ratio_uAh_over_mAs)
        if self.DEBUG:
            self.logger.debug("wifi_tail_energy: {0:.4f}".format(energy))
        return energy

if __name__ == "__main__":
    print "Usage: from model import *"
    # debugging..
    myObj = Model(isDebuging=True)
    # myObj.load(sys.argv[1])
    myObj.load("shamu")
    myObj.get_wifi_tail_energy(1)
    myObj.get_wifi_active_energy(1, -60, isTX=False)
    myObj.get_cpu_energy(1, [1036800, 422400], [0, 1])
