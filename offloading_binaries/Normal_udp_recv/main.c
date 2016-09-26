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
//#include <sys/fcntl.h>

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
	int sockfd, listenfd;
	socklen_t clilen;
	struct ifreq if_idx;
	struct ifreq if_mac;
	int tx_len = 0;
	char sendbuf[BUF_SIZ];
//	struct ether_header *eh = (struct ether_header *) sendbuf;
//	struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
//	struct sockaddr_ll socket_address;
//	char ifName[IFNAMSIZ];
	int i, j, ret, sendsize=1458, packet_num, offset = 0, port = 32000;
	int fd;                    /* file descriptor for file to send */
	struct timeval t_start,t_end;
    
    struct sockaddr_in servaddr,cliaddr;
//printf("init");
    if (argc > 1)
		packet_num = atoi(argv[1]);
	else
		packet_num = 166666*sendsize;
	
	if (argc > 2)
		port = atoi(argv[2]);
    
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
//printf("sockfd");
    bzero(&servaddr,sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr=inet_addr("128.111.68.220");
    servaddr.sin_port=htons(port);
//printf("servaddr");
//    bind(sockfd, (struct sockaddr *)&servaddr,sizeof(servaddr));
    fd = open("/data/local/tmp/bigfile_w", O_WRONLY | O_CREAT | O_TRUNC);
    if (fd == -1) {
        fprintf(stderr, "unable to open the file.\n");
        exit(1);
    }
    
   	gettimeofday(&t_start, NULL);
//printf("gettime");
   //  if (bind(sockfd, (struct sockaddr *)&servaddr, sizeof(servaddr)) < 0)
//     {
//         fprintf(stderr, "unable to connect the server.\n");
//         exit(1);
//     }
// 	printf("bind");
	sendto(sockfd, "0\n",strlen("0\n"),0, (struct sockaddr *)&servaddr, sizeof(servaddr));
     
	while (offset < packet_num)
    {
	  // clilen= sizeof(cliaddr);
        ret = recvfrom(sockfd, sendbuf, sendsize, 0, NULL, NULL);
//        printf("%d\t%d\n", ret, errno);
	if (ret <= 0)
        {
            printf("recv fail\n");
            usleep(100);
            continue;
        }
	if (sendbuf[50] != '0') continue;
        write(fd, sendbuf, ret);
        offset += ret;
//	printf("%d\n", offset);
    }
//    close(sockfd);
    gettimeofday(&t_end, NULL);
    printf("%lf\n", (t_end.tv_sec-t_start.tv_sec)*1000.0+(t_end.tv_usec-t_start.tv_usec)/1000.0);
    
    close(fd);
    
	return 0;
}
