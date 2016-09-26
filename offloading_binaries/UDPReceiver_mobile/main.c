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
	int sentInSlot = 0, slot = 1;
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
	int i, j, ret, sendsize=1488, packet_num=1000000000, offset = 0, port;
	int fd;                    /* file descriptor for file to send */
	struct sockaddr_in servaddr,cliaddr;
	socklen_t clilen;
	struct timeval t_start,t_end,t_now;
    	
	signal(SIGPIPE, SIG_IGN);
	
	if (argc > 1)
		port  = atoi(argv[1]);
	else
	{
		printf("Usage: TCPServer port rate");
		exit(1);
    }
	
	if (argc > 2)
		packet_num = atoi(argv[2]);
	
	
	listenfd = socket(AF_INET, SOCK_DGRAM, 0);

	
	bzero(&servaddr,sizeof(servaddr));
	servaddr.sin_family = AF_INET;
	servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
	servaddr.sin_port = htons(port);
	bind(listenfd, (struct sockaddr *)&servaddr, sizeof(servaddr));
// 	listen(listenfd, 1);
	
	for(;;)
	{
		clilen=sizeof(cliaddr);
		ret = recvfrom(listenfd,sendbuf, 4096, 0, (struct sockaddr *)&cliaddr, &clilen);
// 		sockfd = accept(listenfd,(struct sockaddr *)&cliaddr, &clilen);
		printf("Accepted.\n");
		
	}
    
    close(fd);
    
	return 0;
}
