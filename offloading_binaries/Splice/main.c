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
#define _GNU_SOURCE         /* See feature_test_macros(7) */
#include <fcntl.h>
#include <sys/uio.h>
#include <netinet/tcp.h>

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
	int i, j, ret=0, sendsize=1488, packet_num, offset = 0;
	int fd;                    /* file descriptor for file to send */
	int outstanding;
	struct timeval t_start,t_end,t_now;
	ssize_t bytes, bytes_sent, bytes_in_pipe;
	size_t total_bytes_sent = 0;
    
    int filedes [2];
    ret = pipe (filedes);
    
    struct sockaddr_in servaddr;

    if (argc > 1)
		packet_num = atoi(argv[1]);
	else
		packet_num = 166666*sendsize;
    
	if (argc > 2)
		quota = atoi(argv[2]) / (1000000 / slotLength);
    
    sockfd = socket(AF_INET, SOCK_STREAM, 0);

    bzero(&servaddr,sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr=inet_addr("128.111.68.220");
    servaddr.sin_port=htons(4444);

    connect(sockfd, (struct sockaddr *)&servaddr, sizeof(servaddr));

    fd = open("/data/local/tmp/bigfile", O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "unable to open the file.\n");
        exit(1);
    }
    
    //setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, (char *) &ret, sizeof(int)); 
   	//ioctl(sockfd, SIOCOUTQ, &outstanding);
   	//printf("outstanding:%d\n", outstanding);
   
	gettimeofday(&t_start, NULL);
    while (total_bytes_sent < packet_num)
    {
		if ((packet_num - total_bytes_sent) < quota)
		{
			quota = packet_num - offset;			
		}
		
		while (sentInSlot < quota)
		{	
			// Splice the data from in_fd into the pipe
			if ((bytes_sent = splice(fd, NULL, filedes[1], NULL,
					quota - sentInSlot, 
					SPLICE_F_MORE | SPLICE_F_MOVE)) <= 0) {
				if (errno == EINTR || errno == EAGAIN) {
					// Interrupted system call/try again
					// Just skip to the top of the loop and try again
					continue;
				}
				perror("splice");
				return -1;
			}

			// Splice the data from the pipe into out_fd
			bytes_in_pipe = bytes_sent;
			while (bytes_in_pipe > 0) {
				if ((bytes = splice(filedes[0], NULL, sockfd, NULL, bytes_in_pipe,
						SPLICE_F_MORE | SPLICE_F_MOVE)) <= 0) {
					if (errno == EINTR || errno == EAGAIN) {
						// Interrupted system call/try again
						// Just skip to the top of the loop and try again
						continue;
					}
					perror("splice");
					return -1;
				}
				bytes_in_pipe -= bytes;
			}
			total_bytes_sent += bytes_sent;
			sentInSlot += bytes_sent;
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
		
        /*
        for(;;)
        {
			ioctl(sockfd, SIOCOUTQ, &outstanding);
			printf("outstanding:%d\n", outstanding);
			if (outstanding>0)
			{
				usleep(100);
			}
			else
			{
				break;
			}
		}
        */
    }
    close(sockfd);
    gettimeofday(&t_end, NULL);
    printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);
    
    close(fd);
    
	return 0;
}
