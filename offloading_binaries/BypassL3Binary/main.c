/*
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
 
#include <arpa/inet.h>
#include <linux/if_packet.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <net/if.h>
#include <netinet/ether.h>
#include <sys/sendfile.h>
#include <errno.h>
#include <sys/time.h>
#include <math.h>

#define ETH_P_IP	0x0800		/* Internet Protocol packet	*/
#define ETH_ALEN	6		/* from <net/ethernet.h> */
#define ETH_P_ALL       0x0003

#define MY_DEST_MAC0	0xba
#define MY_DEST_MAC1	0xf6
#define MY_DEST_MAC2	0xb1
#define MY_DEST_MAC3	0x71
#define MY_DEST_MAC4	0x09
#define MY_DEST_MAC5	0x64
 
#define DEFAULT_IF	"wlan0"
//#define DEFAULT_IF	"eth1"
#define BUF_SIZ		8192
 
int main(int argc, char *argv[])
{
	int slotLength = 10000; // in microseconds
	int quota = 1000000000; // Bytes per slot, default 1GB/slot
	int sentInSlot = 0, slot = 1;
	double elapsedTime;
	double packetPerSlot;
	
	struct iovec iov;
	int sockfd;
	struct ifreq if_idx;
	struct ifreq if_mac;
	int tx_len = 0;
	char sendbuf[BUF_SIZ];
	struct ether_header *eh = (struct ether_header *) sendbuf;
	struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
	struct sockaddr_ll socket_address;
	char ifName[IFNAMSIZ];
	int i, j, ret, sendsize=1500, packet_num, offset = 0;
	int fd;                    /* file descriptor for file to send */
	struct timeval t_start, t_end, t_now;

	if (argc > 4)
		sendsize = atoi(argv[4]);

	if (argc > 1)
		packet_num = atoi(argv[1])/sendsize;
	else
		packet_num = 166666;
	
	if (argc > 2)
		quota = atoi(argv[2]) / (1000000 / slotLength);
	else {
		printf("Usage: ./bypassl3 <bytes> <datarate> <optional:interface> <optional:sendsize>");
		exit(-1);
	}
	
	if (argc > 3) {
		/* get the name of the interface */
		strcpy(ifName, argv[3]);
	} else {
		strcpy(ifName, DEFAULT_IF);
	}
		
	// fix packet size problem
	packetPerSlot = ceil(((double)quota) / sendsize);
	slotLength = (int)(packetPerSlot * sendsize / quota * slotLength);
	quota = (int)packetPerSlot * sendsize;
	
 
	/* Open RAW socket to send on */
	//sendsize -= 14;
	//if ((sockfd = socket(AF_PACKET, SOCK_DGRAM, IPPROTO_RAW)) == -1) {
	if ((sockfd = socket(AF_PACKET, SOCK_RAW, IPPROTO_RAW)) == -1) {
	    perror("socket");
	}
 
	/* Get the index of the interface to send on */
	memset(&if_idx, 0, sizeof(struct ifreq));
	strncpy(if_idx.ifr_name, ifName, IFNAMSIZ-1);
	if (ioctl(sockfd, SIOCGIFINDEX, &if_idx) < 0)
	    perror("SIOCGIFINDEX");
	/* Get the MAC address of the interface to send on */
	memset(&if_mac, 0, sizeof(struct ifreq));
	strncpy(if_mac.ifr_name, ifName, IFNAMSIZ-1);
	if (ioctl(sockfd, SIOCGIFHWADDR, &if_mac) < 0)
	    perror("SIOCGIFHWADDR");
 
	/* Construct the Ethernet header */
	memset(sendbuf, 0, BUF_SIZ);
	/* Ethernet header */
	
	eh->ether_shost[0] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[0];
	eh->ether_shost[1] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[1];
	eh->ether_shost[2] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[2];
	eh->ether_shost[3] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[3];
	eh->ether_shost[4] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[4];
	eh->ether_shost[5] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[5];
	eh->ether_dhost[0] = MY_DEST_MAC0;
	eh->ether_dhost[1] = MY_DEST_MAC1;
	eh->ether_dhost[2] = MY_DEST_MAC2;
	eh->ether_dhost[3] = MY_DEST_MAC3;
	eh->ether_dhost[4] = MY_DEST_MAC4;
	eh->ether_dhost[5] = MY_DEST_MAC5;
	eh->ether_type = htons(ETH_P_IP);
	tx_len += sizeof(struct ether_header);
 	
 
	/* Packet data */
	/*
	sendbuf[tx_len++] = 0xde;
	sendbuf[tx_len++] = 0xad;
	sendbuf[tx_len++] = 0xbe;
	sendbuf[tx_len++] = 0xef;
 	*/
 	
	/* Index of the network device */
	socket_address.sll_ifindex = if_idx.ifr_ifindex;
	/* Address length*/
	socket_address.sll_halen = ETH_ALEN;
	/* Destination MAC */
	socket_address.sll_addr[0] = MY_DEST_MAC0;
	socket_address.sll_addr[1] = MY_DEST_MAC1;
	socket_address.sll_addr[2] = MY_DEST_MAC2;
	socket_address.sll_addr[3] = MY_DEST_MAC3;
	socket_address.sll_addr[4] = MY_DEST_MAC4;
	socket_address.sll_addr[5] = MY_DEST_MAC5;
	//test
	//socket_address.sll_family = AF_PACKET;
	//socket_address.sll_protocol = htons(ETH_P_ALL);
 
//    fd = open("bigfile");
    fd = open("/data/local/tmp/bigfile", O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "unable to open the file.\n");
        exit(1);
    }
    
	/*
 	if (bind(sockfd,(struct sockaddr*)&socket_address, sizeof(struct sockaddr_ll)) == -1)
	{
    	perror("bind error.\n");
    	exit(1);
	}
	*/
	/* Send packet */
	gettimeofday(&t_start, NULL);
	read(fd, sendbuf+tx_len, sendsize-tx_len);
	for (i = 0; i < packet_num;)
	{	
		if (((packet_num - i) * sendsize) < quota)
		{
			quota = (packet_num - i) * sendsize;			
		}

		while (sentInSlot < quota)
		{
			//ret = sendfile(sockfd, fd, (off_t *)&offset, sendsize);
			//printf("%d\t%d\n", ret, errno);
			//read(fd, sendbuf, sendsize);
			ret = sendto(sockfd, sendbuf, sendsize, MSG_DONTWAIT, (struct sockaddr*)&socket_address, sizeof(struct sockaddr_ll));
//			printf("%d\t%d\n", ret, errno);
			if (ret == sendsize)
			{
				read(fd, sendbuf+tx_len, sendsize-tx_len);
				i++;
				sentInSlot = sentInSlot + ret;
			}
			else
			{
				//printf("sendto error\n");
				usleep(100);
			}
		}
		gettimeofday(&t_now, NULL);
		elapsedTime = (t_now.tv_sec-t_start.tv_sec)*1000000.0+(t_now.tv_usec-t_start.tv_usec);
		if (elapsedTime < slotLength * slot)
		{
			//printf("sent %d, quota %d, packet_num %d, usleep %lf\n", i, quota, packet_num, slotLength * slot - elapsedTime);
			usleep((int)(slotLength * slot - elapsedTime));
		}
		sentInSlot = 0;
		slot++;
	}
	gettimeofday(&t_end, NULL);
	printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);

	
	return 0;
}
