/*
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
 
#include <arpa/inet.h>
#include <linux/if_packet.h>
#include <linux/ip.h>
#include <linux/udp.h>
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

#define ETH_P_IP	0x0800		/* Internet Protocol packet	*/
#define ETH_ALEN	6		/* from <net/ethernet.h> */
#define ETH_P_ALL       0x0003

#define ETHER_TYPE 0x0800 /* Customize */

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
	struct iovec iov;
	int sockfd, socktrigger;
	int sockopt;
	struct ifreq ifopts;	/* set promiscuous mode */
	struct ifreq if_ip;	/* get ip addr */
	ssize_t numbytes;
	int tx_len = 0;
	char sendbuf[BUF_SIZ];
	struct ether_header *eh = (struct ether_header *) sendbuf;
	struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
	struct sockaddr_ll socket_address;
	struct sockaddr_in servaddr;
	char ifName[IFNAMSIZ];
	int i, j, ret, sendsize=1500, packet_num, offset = 0, port = 32000;
	int fd;                    /* file descriptor for file to send */
	struct timeval t_start,t_end;

	/* Get interface name */
	strcpy(ifName, DEFAULT_IF);
	
	if (argc > 1)
		packet_num = atoi(argv[1])/sendsize;
	else
		packet_num = 166666;
 
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    bzero(&servaddr,sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr=inet_addr("192.168.1.15");
    servaddr.sin_port=htons(port);
    sendto(sockfd, "0\n",strlen("0\n"),0, (struct sockaddr *)&servaddr, sizeof(servaddr));
 

	if ((sockfd = socket(PF_PACKET, SOCK_RAW, htons(ETH_P_ALL))) == -1) {
		perror("listener: socket");	
		return -1;
	}
 
	strncpy(ifopts.ifr_name, ifName, IFNAMSIZ-1);
	ioctl(sockfd, SIOCGIFFLAGS, &ifopts);
	ifopts.ifr_flags |= IFF_PROMISC;
	ioctl(sockfd, SIOCSIFFLAGS, &ifopts);
 
	if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &sockopt, sizeof sockopt) == -1) {
		perror("setsockopt");
		close(sockfd);
		exit(EXIT_FAILURE);
	}
	
	if (setsockopt(sockfd, SOL_SOCKET, SO_BINDTODEVICE, ifName, IFNAMSIZ-1) == -1)	{
		perror("SO_BINDTODEVICE");
		close(sockfd);
		exit(EXIT_FAILURE);
	}
	

    fd = open("/data/local/tmp/bigfile_w", O_WRONLY | O_CREAT | O_TRUNC);
    if (fd == -1) {
        fprintf(stderr, "unable to open the file.\n");
        exit(1);
    }
    
	/* Recv packet */
	gettimeofday(&t_start, NULL);
	for (i = 0; i < packet_num;)
	{	
	
		numbytes = recvfrom(sockfd, sendbuf, sendsize, 0, NULL, NULL);
		//printf("listener: got packet %lu bytes\n", numbytes);
	
		if (sendbuf[50] == '0')
		{
			write(fd, sendbuf, sendsize);
			i++;
		}
		else
        {
			usleep(100);
        }
	}
	gettimeofday(&t_end, NULL);
	printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);

	return 0;
}
