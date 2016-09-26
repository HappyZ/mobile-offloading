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
#include <sys/socket.h>
#include <netinet/in.h>
#include <signal.h>

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
#define BUF_SIZ		8192
 
int main(int argc, char *argv[])
{
	int slotLength = 10000; // in microseconds
	int quota = 1000000000; // Bytes per slot, default 1GB/slot
	int sentInSlot = 0, slot = 1, defaulttimeperiod = 500000000;
	double elapsedTime;
	
	struct iovec iov;
	int sockfd, listenfd;
	struct ifreq if_idx;
	struct ifreq if_mac;
	int tx_len = 0;
	char sendbuf[BUF_SIZ];
	struct ether_header *eh = (struct ether_header *) sendbuf;
	struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
	struct sockaddr_ll socket_address;
	char ifName[IFNAMSIZ];
	int i, j, ret, sendsize=1458, packet_num=1000000000, offset = 0, port;
	int fd;                    /* file descriptor for file to send */
	struct sockaddr_in servaddr,cliaddr;
	socklen_t clilen;
	struct timeval t_start,t_end,t_now;
    	
	signal(SIGPIPE, SIG_IGN);
	
	if (argc > 1)
		port  = atoi(argv[1]);
	else
	{
		printf("Usage: UDPServer port rate");
		exit(1);
    }
	
	if (argc > 2)
		quota = atoi(argv[2]) / (1000000 / slotLength);
	
	if (argc > 3)
		defaulttimeperiod = atoi(argv[3])*1000000;
	
	listenfd = socket(AF_INET, SOCK_DGRAM, 0);

// 	printf("listen");
	bzero(&servaddr,sizeof(servaddr));
	servaddr.sin_family = AF_INET;
	servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
	servaddr.sin_port = htons(port);
	bind(listenfd, (struct sockaddr *)&servaddr, sizeof(servaddr));
//	listen(listenfd, 1);
// 	printf("bind");
	for(;;)
	{
		clilen=sizeof(cliaddr);
		printf("start receiving..\n");
		ret = recvfrom(listenfd,sendbuf,4096, 0, (struct sockaddr *)&cliaddr, &clilen);
// 		printf("msg: %s\n",sendbuf);
		if (sendbuf[0] == '0') {
			printf("Accepted.\n");
			gettimeofday(&t_start, NULL);
			for (i = 0; i < BUF_SIZ; i++) sendbuf[i] = '0';
			while (offset < packet_num)
			{
				if ((packet_num - offset) < quota)
				{
					quota = packet_num - offset;			
				}
				while (sentInSlot < quota)
				{	
					ret = sendto(listenfd, sendbuf, sendsize, 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
					if (ret < sendsize)
					{
						printf("send fail\n");
						slot = 1;
						sentInSlot = 0;
						offset = 0;
						goto RESTART;
					}
					offset += ret;
					sentInSlot = sentInSlot + ret;
				}
				gettimeofday(&t_now, NULL);
				elapsedTime = (t_now.tv_sec-t_start.tv_sec)*1000000.0+(t_now.tv_usec-t_start.tv_usec);
				if (elapsedTime > defaulttimeperiod) {
					slot = 1;
					sentInSlot = 0;
					offset = 0;
					goto AUTOCLOSE;
				}
				//printf("sent %d, quota %d, packet_num %d, usleep %lf\n", offset, quota, packet_num, slotLength * slot - elapsedTime);
				if (elapsedTime < slotLength * slot)
				{
					usleep((int)(slotLength * slot - elapsedTime));
				}
				sentInSlot = 0;
				slot++;

			}
// 			sockfd = accept(listenfd,(struct sockaddr *)&cliaddr, &clilen);
		}
		
		
	RESTART:
	// 		close(sockfd);
		gettimeofday(&t_end, NULL);
		printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);
	}
	AUTOCLOSE:
		gettimeofday(&t_end, NULL);
		printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);
	close(fd);

	return 0;
}
