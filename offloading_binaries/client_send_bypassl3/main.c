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
    int i, j, fd; // file descriptor of file to send
    int sockfd; // socket
    int tx_len = 0;
    char sendbuf[BUF_SIZ];
    struct ifreq if_idx;
    struct ifreq if_mac;
    struct sockaddr_in servaddr;
    struct ether_header *eh = (struct ether_header *) sendbuf;
    struct iphdr *iph = (struct iphdr *) (sendbuf + sizeof(struct ether_header));
    struct sockaddr_ll socket_address;
    char ifName[IFNAMSIZ];
    // for misc
    int ret;
    int sendsize = 1500; // 1500 MTU (raw socket)
    int bytes2send = 0;
    struct stat st;
    unsigned char my_dest_mac[6];

    if (argc < 3)
    {
        printf("Usage: %s <bytes2send/file2send> <dest MAC address> <[optional] bandwidth (bps)> <[optional] sendsize (bytes)> <[optional] interface>\n", argv[0]);
        exit(0);
    }

    // set bandwidth
    if (argc > 3)
        quota = atoi(argv[3]) / 8 / (1000000 / slotLength);

    // set sendsize (if larger than 1460 will do packetization (fragmentation))
    if (argc > 4)
        sendsize = atoi(argv[4]);

    //  set interface
    if (argc > 5) {
        strcpy(ifName, argv[5]);
    } else {
        strcpy(ifName, DEFAULT_IF);
    }

    // adjust slotLength to address packet size issue in the end
    if ((quota % sendsize) > 0)
    {
        // printf("quota:%d,sendsize:%d,slotLength:%d\n", quota, sendsize, slotLength);
        slotLength = (uint)((double)(quota / sendsize + 1) * sendsize / quota * slotLength);
        quota = (quota / sendsize + 1) * sendsize;
        // printf("quota:%d,sendsize:%d,slotLength:%d\n", quota, sendsize, slotLength);
    }

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

    if ((sockfd = socket(AF_PACKET, SOCK_RAW, IPPROTO_RAW)) == -1)
    {
        fprintf(stderr, "! raw socket error.\n");
        exit(1);
    }

    // Get the index of the interface to send on
    memset(&if_idx, 0, sizeof(struct ifreq));
    strncpy(if_idx.ifr_name, ifName, IFNAMSIZ - 1);
    if (ioctl(sockfd, SIOCGIFINDEX, &if_idx) < 0)
    {
        fprintf(stderr, "! SIOCGIFINDEX error. Check permission.\n");
        exit(1);
    }

    // Get the MAC address of the interface to send on
    memset(&if_mac, 0, sizeof(struct ifreq));
    strncpy(if_mac.ifr_name, ifName, IFNAMSIZ - 1);
    if (ioctl(sockfd, SIOCGIFHWADDR, &if_mac) < 0)
    {
        fprintf(stderr, "! SIOCGIFHWADDR error. Check permission.\n");
        exit(1);
    }

    // Set mem
    memset(sendbuf, 0, BUF_SIZ);

    // parse input MAC address
    sscanf(
        argv[2], "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
        &my_dest_mac[0], &my_dest_mac[1], &my_dest_mac[2],
        &my_dest_mac[3], &my_dest_mac[4], &my_dest_mac[5]);

    printf("destMAC:%02x:%02x:%02x:%02x:%02x:%02x\n",
        my_dest_mac[0], my_dest_mac[1], my_dest_mac[2],
        my_dest_mac[3], my_dest_mac[4], my_dest_mac[5]);

    // Construct the Ethernet header 
    eh->ether_shost[0] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[0];
    eh->ether_shost[1] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[1];
    eh->ether_shost[2] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[2];
    eh->ether_shost[3] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[3];
    eh->ether_shost[4] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[4];
    eh->ether_shost[5] = ((uint8_t *)&if_mac.ifr_hwaddr.sa_data)[5];
    eh->ether_dhost[0] = my_dest_mac[0];
    eh->ether_dhost[1] = my_dest_mac[1];
    eh->ether_dhost[2] = my_dest_mac[2];
    eh->ether_dhost[3] = my_dest_mac[3];
    eh->ether_dhost[4] = my_dest_mac[4];
    eh->ether_dhost[5] = my_dest_mac[5];
    eh->ether_type = htons(ETH_P_IP);
    tx_len += sizeof(struct ether_header);

    // Index of the network device
    socket_address.sll_ifindex = if_idx.ifr_ifindex;

    // Address length
    socket_address.sll_halen = ETH_ALEN;

    // Destination MAC
    socket_address.sll_addr[0] = MY_DEST_MAC0;
    socket_address.sll_addr[1] = MY_DEST_MAC1;
    socket_address.sll_addr[2] = MY_DEST_MAC2;
    socket_address.sll_addr[3] = MY_DEST_MAC3;
    socket_address.sll_addr[4] = MY_DEST_MAC4;
    socket_address.sll_addr[5] = MY_DEST_MAC5;

    // start timing
    gettimeofday(&t_start, NULL);
    read(fd, sendbuf + tx_len, sendsize - tx_len);

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
            read(
                fd, sendbuf + tx_len,
                (
                    ((quota - sentInSlot) < (sendsize - tx_len)) ?
                    (quota - sentInSlot) : (sendsize - tx_len))
                );
            ret = sendto(
                sockfd, sendbuf,
                (quota - sentInSlot < sendsize) ? (quota - sentInSlot) : sendsize,
                MSG_DONTWAIT, (struct sockaddr*)&socket_address, sizeof(struct sockaddr_ll));

            if (ret <= 0)
            {
                fprintf(stderr, "! Fail to send: ret:%d, err:%d; wait for 100us..\n", ret, errno);
                usleep(100);
                continue;
            }
            total_bytes_sent += ret;
            sentInSlot += ret;
            // printf(
            //     "after: total_bytes_sent %d, sentInSlot %d, quota - sentInSlot %d\n",
            //     total_bytes_sent, sentInSlot, quota - sentInSlot);
        }
        // control bandwidth
        gettimeofday(&t_now, NULL);
        elapsedTime = (t_now.tv_sec - t_start.tv_sec) * 1000000.0 + (t_now.tv_usec - t_start.tv_usec);
        if (elapsedTime < (slotLength * slot))
        {
            // printf(
            //     "sent %d, quota %d, bytes2send %d, usleep %lfus\n",
            //     total_bytes_sent, quota, bytes2send, slotLength * slot - elapsedTime);
            usleep((int)(slotLength * slot - elapsedTime));
        }
        sentInSlot = 0;
        ++slot;
    }

    // end timing
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf(
        "sent(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
        total_bytes_sent, elapsedTime, total_bytes_sent * 8 / elapsedTime);
    
    close(sockfd);
    close(fd);
    
    return 0;
}
