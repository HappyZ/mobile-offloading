/*
 * Initial commit by Yibo @ Jul. 28, 2015
 * Last update by Yanzi @ Sept. 26, 2016
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

// #define ETH_P_IP        0x0800      /* Internet Protocol packet */
// #define ETH_ALEN        6       /* from <net/ethernet.h> */
// #define ETH_P_ALL       0x0003

// #define MY_DEST_MAC0    0xba
// #define MY_DEST_MAC1    0xf6
// #define MY_DEST_MAC2    0xb1
// #define MY_DEST_MAC3    0x71
// #define MY_DEST_MAC4    0x09
// #define MY_DEST_MAC5    0x64
 
// #define DEFAULT_IF      "wlan0"
#define BUF_SIZ         4096

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
    uint slotLength = 10000; // in microseconds, for bandwidth control
    uint quota = 1000000000; // default bytes per slot, default 1GB/slot
    uint sentInSlot = 0, slot = 1;
    uint total_bytes_sent = 0;
    // for timing
    double elapsedTime;
    struct timeval t_start, t_end, t_now;
    // for socket
    int fd; // file descriptor of file to send
    int sockfd; // socket 
    // int sendsize = 1488;
    // char ifName[IFNAMSIZ];
    char sendbuf[BUF_SIZ];
    struct sockaddr_in servaddr;
    struct ether_header *eh = (struct ether_header *) sendbuf;
    struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
    // struct sockaddr_ll socket_address;
    // for misc
    int ret;
    int bytes2send = 0;
    struct stat st;

    if (argc < 2)
    {
        printf("Usage: %s <bytes2send/file2send> <[optional] bandwidth (bps)>\n", argv[0]);
        exit(0);
    }

    // set bandwidth
    if (argc > 2)
        quota = atoi(argv[2]) / 8 / (1000000 / slotLength);

    // get file size (bytes2send)
    if (isNumber(argv[1]))
    {
        // set bytes to send
        bytes2send = atoi(argv[1]);
        // open file descriptor
        fd = open("/data/local/tmp/bigfile", O_RDONLY);
        if (fd == -1)
        {
            fprintf(stderr, "! Unable to open /data/local/tmp/bigfile.\n");
            exit(1);
        }
    }
    else
    {
        // open file descriptor
        fd = open(argv[1], O_RDONLY);
        if (fd == -1)
        {
            fprintf(stderr, "! Unable to open file %s.\n", argv[1]);
            exit(1);
        }
        fstat(fd, &st);
        bytes2send = st.st_size;
        printf("bytes2send:%d\n", bytes2send);
    }

    // start timing
    gettimeofday(&t_start, NULL);

    // start to send
    while (total_bytes_sent < bytes2send)
    {
        if ((bytes2send - total_bytes_sent) < quota)
        {
            quota = bytes2send - total_bytes_sent;
        }
        // send in slots
        while (sentInSlot < quota)
        {
            // printf(
            //     "before: total_bytes_sent %d, sentInSlot %d, quota - sentInSlot %d\n",
            //     total_bytes_sent, sentInSlot, quota - sentInSlot);
            read(fd, sendbuf, (quota - sentInSlot < BUF_SIZ) ? (quota - sentInSlot) : BUF_SIZ);

            ret = (quota - sentInSlot < BUF_SIZ) ? (quota - sentInSlot) : BUF_SIZ;
            total_bytes_sent += ret;
            sentInSlot += ret;
            // printf(
            //     "after: total_bytes_sent %d, sentInSlot %d, quota - sentInSlot %d\n",
            //     total_bytes_sent, sentInSlot, quota - sentInSlot);
        }
        // control bandwidth
        if (total_bytes_sent < bytes2send)
        {
            gettimeofday(&t_now, NULL);
            elapsedTime = (t_now.tv_sec - t_start.tv_sec) * 1000000.0 + (t_now.tv_usec - t_start.tv_usec);
            if (elapsedTime < slotLength * slot)
            {
                // printf(
                //     "sent %d, quota %d, bytes2send %d, usleep %lfus\n",
                //     total_bytes_sent, quota, bytes2send, slotLength * slot - elapsedTime);
                usleep((int)(slotLength * slot - elapsedTime));
            }
        }
        sentInSlot = 0;
        ++slot;
    }

    // end timing
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf("duration(s):%lf\nthroughput(bps):%lf\n", elapsedTime, total_bytes_sent * 8 / elapsedTime);
    
    close(fd);
    
    return 0;
}
