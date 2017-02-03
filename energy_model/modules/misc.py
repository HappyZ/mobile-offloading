'''
Created by Yanzi @ 06/16/2016
Last updated by Yanzi @ 02/02/2017
misc module: all sorts of uncategorized functions
'''

import logging
import os
import random
import time
import threading
import numpy as np
from math import sqrt
from TerminalColors import *


class MyTimer(threading.Thread):
    '''
    Threaded timer so it is non-blocking and we can do additional stuff
    '''
    def __init__(self, timeInSec):
        threading.Thread.__init__(self)
        self.time = timeInSec

    def run(self):
        print "Timer thread kicked in.. START.."
        time.sleep(self.time)
        print "Timer thread kicked in.. STOP.."


class EmptyLogger:
    '''
    logger base
    '''
    def __init__(
        self, loggerTag, isDebugging=False, logPath=None, printout=True
    ):
        self.myLogger = logging.getLogger(loggerTag)
        if isDebugging:
            level = logging.DEBUG
        else:
            level = logging.INFO
        self.myLogger.setLevel(level)
        self.ch_file = None
        self.ch_stream = None
        formatter = logging.Formatter(
            '%(asctime)s %(name)s %(levelname)s, %(message)s')
        if logPath is not None:
            self.ch_file = logging.FileHandler(logPath, 'w')
            self.ch_file.setLevel(logging.DEBUG)
            self.ch_file.setFormatter(formatter)
            self.myLogger.addHandler(self.ch_file)
        if printout:
            self.ch_stream = logging.StreamHandler()
            self.ch_stream.setLevel(level)
            self.ch_stream.setFormatter(formatter)
            self.myLogger.addHandler(self.ch_stream)
        self.myLogger.info('logging started')

    def info(self, string):
        self.myLogger.info(string)

    def debug(self, string):
        self.myLogger.debug(string)

    def error(self, string):
        self.myLogger.error(colorString(string))

    def note(self, string):
        self.myLogger.info(colorString(string, color='blue'))

    def enable(self):
        if self.ch_file is not None:
            self.myLogger.addHandler(self.ch_file)
        if self.ch_stream is not None:
            self.myLogger.addHandler(self.ch_stream)

    def disable(self):
        if self.ch_file is not None:
            self.myLogger.removeHandler(self.ch_file)
        if self.ch_stream is not None:
            self.myLogger.removeHandler(self.ch_stream)


def convert2Bool(stuff):
    '''
    convert string or integer 0/1 to bool
    supported ['True', 'true', 'yes', 'y', '1', 1] and vice versa
    '''
    return stuff in ['True', 'true', 'yes', 'y', '1', 1]


def abs(num):
    '''
    compute absolute number
    '''
    return np.abs(num)


def nanratio(lst):
    '''
    compute the ratio of nan over all elements in list
    '''
    if len(lst) is 0:
        return 0
    try:
        nan_count = np.count_nonzero(np.isnan(lst))
    except:
        nan_count = np.sum(np.isnan(lst))
    if np.isnan(nan_count):
        return float('nan')
    return 1.0 * nan_count / len(lst)


def max(lst):
    '''
    compute max (excluding nan) of a list of numbers
    '''
    if len(lst) is 0:
        return float('nan')
    return np.nanmax(lst)


def mean(lst):
    '''
    compute mean (excluding nan) of a list of numbers
    '''
    if len(lst) is 0:
        return float('nan')
    try:
        tmp = np.count_nonzero(~np.isnan(lst))
    except:
        tmp = np.sum(~np.isnan(lst))
    tmp2 = np.nansum(lst)
    if tmp is 0 or np.isnan(tmp2):
        return float('nan')
    return tmp2 / tmp


def median(lst):
    '''
    compute median (excluding nan) of a list of numbers
    '''
    if len(lst) is 0:
        return float('nan')
    lst = np.array(lst)
    newlst = lst[~np.isnan(lst)]
    if len(newlst) is 0:
        return float('nan')
    return np.median(newlst)


def std(lst):
    '''
    compute std (excluding nan) of a list of numbers
    '''
    if len(lst) is 0:
        return float('nan')
    try:
        return np.nanstd(lst)
    except:
        pass
    lst = np.array(lst)
    tmp = mean(lst)
    if np.isnan(tmp):
        return float('nan')
    return np.sqrt(mean(abs(lst - tmp)**2))


def getRandomIPAddr():
    '''
    derive a random ip address
    '''
    return '192.168.1.'+str(random.randint(1, 255))


def getRandomMacAddr():
    '''
    derive a random MAC address
    '''
    mac = [
        0x02, 0x08, 0x02,
        random.randint(0x00, 0x7f),
        random.randint(0x00, 0xff),
        random.randint(0x00, 0xff)]
    return ':'.join(map(lambda x: "%02x" % x, mac))


def which(program):
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    fpath, fname = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
            path = path.strip('"')
            exe_file = os.path.join(path, program)
            if is_exe(exe_file):
                return exe_file

    return None


if __name__ == '__main__':
    print 'Usage: from misc import *'
