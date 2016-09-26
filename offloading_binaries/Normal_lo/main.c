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
//#include "/media/Lucifer/android/ndk-profiler/jni/prof.h"

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
	int sockfd;
	struct ifreq if_idx;
	struct ifreq if_mac;
	int tx_len = 0;
	char sendbuf[BUF_SIZ];
	struct ether_header *eh = (struct ether_header *) sendbuf;
	struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
	struct sockaddr_ll socket_address;
	char ifName[IFNAMSIZ];
	int i, j, ret, sendsize=1488, packet_num, offset = 0;
	int fd;                    /* file descriptor for file to send */
	struct timeval t_start,t_end,t_now;
    
    struct sockaddr_in servaddr;
	
//    monstartup("normal");

    if (argc > 1)
		packet_num = atoi(argv[1]);
	else
		packet_num = 166666*sendsize;
    
	if (argc > 2)
		quota = atoi(argv[2]) / (1000000 / slotLength);
	
    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    bzero(&servaddr,sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr=inet_addr("192.168.1.15");
    servaddr.sin_port=htons(4444);



    fd = open("/data/local/tmp/bigfile", O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "unable to open the file.\n");
        exit(1);
    }
    
   	gettimeofday(&t_start, NULL);
    if (connect(sockfd, (struct sockaddr *)&servaddr, sizeof(servaddr)) < 0)
    {
        fprintf(stderr, "unable to connect the server.\n");
        exit(1);
    }
    while (offset < packet_num)
    {
		if ((packet_num - offset) < quota)
		{
			quota = packet_num - offset;			
		}
		while (sentInSlot < quota)
		{	
			read(fd, sendbuf, (quota - sentInSlot < 4096) ? quota - sentInSlot : 4096);
			ret = send(sockfd, sendbuf, (quota - sentInSlot < 4096) ? quota - sentInSlot : 4096, 0);
//			printf("%d\t%d\n", ret, errno);
			if (ret <= 0)
			{
				printf("send fail\n");
				usleep(100);
				continue;
			}
			offset += ret;
			sentInSlot = sentInSlot + ret;
		}
		gettimeofday(&t_now, NULL);
		elapsedTime = (t_now.tv_sec-t_start.tv_sec)*1000000.0+(t_now.tv_usec-t_start.tv_usec);
		if (elapsedTime < slotLength * slot)
		{
			//printf("sent %d, quota %d, packet_num %d, usleep %lf\n", offset, quota, packet_num, slotLength * slot - elapsedTime);
			usleep((int)(slotLength * slot - elapsedTime));
		}
		sentInSlot = 0;
		slot++;

    }
    close(sockfd);
    gettimeofday(&t_end, NULL);
    printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);
    
    close(fd);
    
//    moncleanup();
	return 0;
}
