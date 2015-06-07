import re

def getCpuStat(self):
    with open('/proc/stat') as fd:
        cpustats = []
        while True:
            line = fd.readline()
            if len(line) == 0:
                break;
            if line[0:3] == 'cpu':
                cpustats.append(line.replace('\n', ''))
        return cpustats

def getNetworkStat(self, dev):
    netstats = []
    with open('/proc/net/dev') as fd:
        while True:
            line = fd.readline().strip()
            if len(line) == 0:
                break;
            if line[0:len(dev)] == dev:
                netstats.append(line.replace('\n', ''))
    return netstats

def getDiskStat(self, dev):
    diskstats = []
    with open('/proc/diskstats') as fd:
        while True:
            line = re.sub(' +', ' ', fd.readline().strip()).split(' ')
            line = ' '.join(line[2:len(line)])
            if len(line) == 0:
                break;
            if line[0:len(dev)] == dev:
                diskstats.append(line.replace('\n', ''))
    return diskstats

print getCpuStat(None)
print getNetworkStat(None, 'wlan0')
print getDiskStat(None, 'sda')
