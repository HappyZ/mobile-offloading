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
#include <sys/uio.h>
#include <netinet/tcp.h>

// #define DEFAULT_IF  "lo"
// #define BUF_SIZ     8192

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
    int bytes, bytes_sent, bytes_in_pipe;
    // for timing
    double elapsedTime;
    struct timeval t_start, t_end, t_now;
    // for socket
    int fd; // file descriptor of file to send
    int sockfd; // socket
    struct sockaddr_in servaddr;
    // struct sockaddr_ll socket_address;
    // for misc
    int ret;
    int sendsize = 1460; // 1500 MTU - 20 IPv4 - 20 TCP
    int bytes2send = 0;
    struct stat st;
    // create two pipes
    int filedes[2];
    ret = pipe(filedes);

    if (argc < 4)
    {
        printf("Usage: %s <bytes2send/file2send> <ip> <port> <[optional] bandwidth (bps)> <[optional] sendsize (bytes)>\n", argv[0]);
        exit(0);
    }

    // set bandwidth
    if (argc > 4)
        quota = atoi(argv[4]) / 8 / (1000000 / slotLength);

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
    
    // bind socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = inet_addr(argv[2]);
    servaddr.sin_port = htons(atoi(argv[3]));

    // connect socket
    if (connect(sockfd, (struct sockaddr *)&servaddr, sizeof(servaddr)) < 0)
    {
        fprintf(stderr, "! Unable to connect the server.\n");
        exit(1);
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

            // Splice the data from in_fd into the pipe
            if ((bytes_sent = splice(fd, NULL, filedes[1], NULL,
                    quota - sentInSlot, 
                    SPLICE_F_MORE | SPLICE_F_MOVE)) <= 0) {
                if (errno == EINTR || errno == EAGAIN) {
                    // Interrupted system call/try again
                    // Just skip to the top of the loop and try again
                    continue;
                }
                fprintf(stderr, "! splice error, errno: %d.\n", errno);
                exit(1);
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
                    fprintf(stderr, "! splice error, errno: %d.\n", errno);
                    exit(1);
                }
                bytes_in_pipe -= bytes;
            }

            total_bytes_sent += bytes_sent;
            sentInSlot += bytes_sent;
            // printf(
            //     "after: total_bytes_sent %d, sentInSlot %d, quota - sentInSlot %d\n",
            //     total_bytes_sent, sentInSlot, quota - sentInSlot);
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
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf(
        "sent(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
        total_bytes_sent, elapsedTime, total_bytes_sent * 8 / elapsedTime);
    
    close(sockfd);
    close(fd);
    
    return 0;
}

