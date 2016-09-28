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
#include <unistd.h>
#include <fcntl.h>
#include <ctype.h>
#include <signal.h>

#define BUF_SIZ         65536

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
    int listenfd; // socket
    char sendbuf[BUF_SIZ];
    struct sockaddr_in servaddr, cliaddr;
    socklen_t clilen;
    // for misc
    int ret;
    int sendsize = 1460; // 1500 MTU - 20 IPv4 - 20 TCP
    uint bytes2send = 0;
    struct stat st;
    unsigned char listenOnce = 0;

    signal(SIGPIPE, SIG_IGN);

    if (argc < 3)
    {
        printf("Usage: %s <port> <bytes2send/file2send> <[optional] bandwidth (bps)> <[optional] listenOnce ([0]/1)> <[optional] sendsize (bytes)>\n", argv[0]);
        exit(0);
    }

    // set bandwidth
    if (argc > 3)
        quota = atoi(argv[3]) / 8 / (1000000 / slotLength);

    // only one session
    if (argc > 4)
        listenOnce = atoi(argv[4]);

    // set sendsize (if larger than 1460 will do packetization (fragmentation))
    if (argc > 5)
        sendsize = atoi(argv[5]);

    // adjust slotLength to address packet size issue in the end
    if ((quota % sendsize) > 0)
    {
        // printf("quota:%d,sendsize:%d,slotLength:%d\n", quota, sendsize, slotLength);
        slotLength = (uint)((double)(quota / sendsize + 1) * sendsize / quota * slotLength);
        quota = (quota / sendsize + 1) * sendsize;
        // printf("quota:%d,sendsize:%d,slotLength:%d\n", quota, sendsize, slotLength);
    }

    // listen to socket
    listenfd = socket(AF_INET, SOCK_DGRAM, 0);
    // bind socket and listen
    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(atoi(argv[1]));
    bind(listenfd, (struct sockaddr *)&servaddr, sizeof(servaddr));

    for (;;)
    {
        clilen = sizeof(cliaddr);

        // wait for one client and accept it once found
        ret = recvfrom(listenfd, sendbuf, 4096, 0, (struct sockaddr *)&cliaddr, &clilen);
        if (ret <= 0)
        {
            fprintf(stderr, "! Fail to recv: ret:%d, err:%d; quiting..\n", ret, errno);
            exit(1);
        }

        // check validity of udp transmission
        if ((sendbuf[0] == '!') && (sendbuf[1] == '?') && (sendbuf[2] == '='))
        {
            printf("Accepted client at %s\n", inet_ntoa(cliaddr.sin_addr));

            // get file size (bytes2send)
            if (isNumber(argv[2]))
            {
                // set bytes to send
                bytes2send = atoi(argv[2]);
                // open file descriptor
                fd = open("/dev/zero", O_RDONLY);
                if (fd == -1)
                {
                    fprintf(stderr, "! Unable to open /data/local/tmp/bigfile.\n");
                    exit(1);
                }
            }
            else
            {
                // open file descriptor
                fd = open(argv[2], O_RDONLY);
                if (fd == -1)
                {
                    fprintf(stderr, "! Unable to open file %s.\n", argv[2]);
                    exit(1);
                }
                fstat(fd, &st);
                bytes2send = st.st_size;
                printf("bytes2send:%d\n", bytes2send);
            }

            // clear total_bytes_sent to 0
            total_bytes_sent = 0;
            sentInSlot = 0;
            slot = 1;

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
                    read(fd, sendbuf, (quota - sentInSlot < sendsize) ? (quota - sentInSlot) : sendsize);
                    ret = sendto(
                        listenfd, sendbuf,
                        (quota - sentInSlot < sendsize) ? (quota - sentInSlot) : sendsize,
                        0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));

                    if (ret <= 0)
                    {
                        fprintf(stderr, "! Fail to send: ret:%d, err:%d; wait for 100us..\n", ret, errno);
                        total_bytes_sent = bytes2send + 1;
                        break;
                    }
                    total_bytes_sent += ret;
                    sentInSlot += ret;
                    // printf(
                    //     "after: total_bytes_sent %d, sentInSlot %d, quota - sentInSlot %d\n",
                    //     total_bytes_sent, sentInSlot, quota - sentInSlot);
                }

                // workaround to break the while loop when error happens
                if (total_bytes_sent > bytes2send)
                {
                    break;
                }

                // control bandwidth
                gettimeofday(&t_now, NULL);
                elapsedTime = (t_now.tv_sec - t_start.tv_sec) * 1000000.0 + (t_now.tv_usec - t_start.tv_usec);
                if (elapsedTime < slotLength * slot)
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
            if (total_bytes_sent == bytes2send)
            {
                gettimeofday(&t_end, NULL);
                elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
                printf(
                    "sent(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
                    total_bytes_sent, elapsedTime, total_bytes_sent * 8 / elapsedTime);
                
                ret = sendto(listenfd, "=?!\n", 4, 0, (struct sockaddr *)&cliaddr, sizeof(cliaddr));
                if (ret <= 0)
                {
                    fprintf(stderr, "! Unable to initialize data transfer. errno:%d.\n", errno);
                    close(fd);
                    close(listenfd);
                    exit(1);
                }
            }
            else
            {
                fprintf(stderr, "! error: total_bytes_sent > bytes2send\n");
            }
            
            close(fd);

            if (listenOnce)
                break;
        }
    }
   
    close(listenfd);
    
    return 0;
}