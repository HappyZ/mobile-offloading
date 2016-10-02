/*
 * Initial commit by Yibo @ Jul. 28, 2015
 * Last update by Yanzi @ Sept. 28, 2016
 */

#include <stdio.h>
#include <string.h>
#include <arpa/inet.h>
#include <linux/if_packet.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <net/if.h>
#include <netinet/ether.h>
#include <sys/sendfile.h>
#include <errno.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <fcntl.h>

#define ETH_P_IP        0x0800      /* Internet Protocol packet */
#define ETH_ALEN        6       /* from <net/ethernet.h> */
#define ETH_P_ALL       0x0003

#define MY_DEST_MAC0    0x18
#define MY_DEST_MAC1    0x03
#define MY_DEST_MAC2    0x73
#define MY_DEST_MAC3    0xc8
#define MY_DEST_MAC4    0x86
#define MY_DEST_MAC5    0x52
 
#define DEFAULT_IF      "wlan0"
#define BUF_SIZ         65535

char isNumber(char number[])
{
    int i = 0;

    //checking for negative numbers
    if (number[0] == '-')
        i = 1;
    for (; number[i] != 0; i++)
    {
        //if (number[i] > '9' || number[i] < '0')
        if (!isdigit(number[i]))
            return 0;
    }
    return 1;
}

int main(int argc, char *argv[])
{
    // defaults
    uint total_bytes_recv = 0;
    // for timing
    double elapsedTime;
    struct timeval t_start, t_end, t_now;
    // for socket
    int i, j, fd; // file descriptor of file to send
    int sockfd; // socket
    int sockopt;
    char recvbuf[BUF_SIZ];
    struct ifreq ifopts;
    struct ifreq if_mac;
    struct sockaddr_in servaddr;
    char ifName[IFNAMSIZ];
    // for misc
    int ret;
    int recvsize = 1500; // 1500 MTU (raw socket)
    int bytes2send = 0;
    struct stat st;
    unsigned char my_dest_mac[6];

    if (argc < 3)
    {
        printf("Usage: %s <ip> <port> <[optional] recvsize (bytes)> <[optional] interface> <[optional] filepath>\n", argv[0]);
        exit(0);
    }

    // set recvsize (if larger than 1460 will do packetization (fragmentation))
    if (argc > 3)
        recvsize = atoi(argv[3]);

    //  set interface
    if (argc > 4) {
        strcpy(ifName, argv[4]);
    } else {
        strcpy(ifName, DEFAULT_IF);
    }

    // bind socket and send a trigger (UDP)
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = inet_addr(argv[1]);
    servaddr.sin_port = htons(atoi(argv[2]));
    ret = sendto(sockfd, "!?=\n", 4, 0, (struct sockaddr *)&servaddr, sizeof(servaddr));
    if (ret <= 0)
    {
        printf("! Trigger the initialization");
    }

    if ((sockfd = socket(PF_PACKET, SOCK_RAW, htons(ETH_P_ALL))) == -1)
    {
        fprintf(stderr, "! raw socket error.\n");
        exit(1);
    }

    // Get the index of the interface to receive from
    memset(&ifopts, 0, sizeof(struct ifreq));
    strncpy(ifopts.ifr_name, ifName, IFNAMSIZ - 1);
    if (ioctl(sockfd, SIOCGIFFLAGS, &ifopts) < 0)
    {
        fprintf(stderr, "! SIOCGIFFLAGS error. Check permission.\n");
        exit(EXIT_FAILURE);
    }
    ifopts.ifr_flags |= IFF_PROMISC;
    if (ioctl(sockfd, SIOCSIFFLAGS, &ifopts) < 0)
    {
        fprintf(stderr, "! SIOCSIFFLAGS error. Check permission.\n");
        exit(EXIT_FAILURE);
    }

    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &sockopt, sizeof sockopt) == -1) {
        fprintf(stderr, "! setsockopt error. \n");
        close(sockfd);
        exit(EXIT_FAILURE);
    }
    
    if (setsockopt(sockfd, SOL_SOCKET, SO_BINDTODEVICE, ifName, IFNAMSIZ - 1) == -1)  {
        fprintf(stderr, "! SO_BINDTODEVICE error. \n");
        close(sockfd);
        exit(EXIT_FAILURE);
    }

    // if instrument to write to a file
    if (argc > 5)
    {
        fd = open(argv[5], O_WRONLY | O_CREAT | O_TRUNC);
        if (fd == -1) {
            fprintf(stderr, "! Unable to open file %s.\n", argv[5]);
            close(sockfd);
            exit(1);
        }
    }
    
    // start timing
    gettimeofday(&t_start, NULL);

    // start to receive
    for (;;)
    {
    	// TODO: need a method to distinguish between other packets and rawsocket packets
        // printf("before: total_bytes_recv %d\n", total_bytes_recv);
        ret = recvfrom(sockfd, recvbuf, recvsize, 0, NULL, NULL);

        if (ret <= 0)
        {
            if (errno == 0)
                break;
            fprintf(stderr, "! Fail to recv: ret:%d, err:%d; quiting..\n", ret, errno);
            exit(1);
        }

        // a "code" to indicate rawsocket is done
        // printf("%d %d %d", (recvbuf[0] == '='), (recvbuf[1] == '?'), (recvbuf[0] == '!'));
        if ((recvbuf[0] == '=') && (recvbuf[1] == '?') && (recvbuf[2] == '!'))
            break;

        // write to file if specified filename
        if (argc > 5)
            write(fd, recvbuf, ret);

        // count how many bytes received
        total_bytes_recv += ret;
        printf("after: total_bytes_recv %d\n", total_bytes_recv);
    }

    // end timing
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf(
        "sent(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
        total_bytes_recv, elapsedTime, total_bytes_recv * 8 / elapsedTime);
    
    close(sockfd);
    if (argc > 5)
        close(fd);
    
    return 0;
}
