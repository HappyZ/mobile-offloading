#!/usr/bin/env python
import sys
# Yanzi

wifiRSS = []
gsmRSS = []

with open(sys.argv[1], 'rU') as f: # it must be sorted
	for line in f:
		tmp = line.split(" ")
		time = int(tmp[0]) # ms
		stuff = tmp[1:]
		if "wifi" in stuff:
			wifiRSS.append( (time, stuff) )
		elif "gsm" in stuff:
			gsmRSS.append( (time, stuff) )

wifilen = len(wifiRSS)
gsmlen = len(gsmRSS)
print "WiFi has {0} records, GSM has {1} records".format(wifilen, gsmlen)

for i in range(wifilen-1):
	start = False
	for j in range(wifiRSS[i][0], wifiRSS[i+1][0], 50):
		if not start:
			start = True
			continue
		wifiRSS.append((j, wifiRSS[i][1]))

wifiRSS.sort(key=lambda tup: tup[0])
# print wifiRSS

for i in range(gsmlen-1):
	start = False
	for j in range(gsmRSS[i][0], gsmRSS[i+1][0], 50):
		if not start:
			start = True
			continue
		gsmRSS.append((j, gsmRSS[i][1]))

gsmRSS.sort(key=lambda tup: tup[0])
# print gsmRSS



