/*
 * Initial commit by Yibo @ Jul. 28, 2015
 * Last update by Yanzi @ Sept. 27, 2016
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

#define BUF_SIZ         65536

int main(int argc, char *argv[])
{
    // defaults
    uint total_bytes_recv = 0;
    // for timing
    double elapsedTime;
    struct timeval t_start, t_end;
    // for socket
    int fd = 0; // file descriptor of file to write (receive)
    int sockfd; // socket
    char recvbuf[BUF_SIZ];
    struct sockaddr_in servaddr, cliaddr;
    socklen_t clilen;
    // for misc
    int ret;
    int recvsize = 4096;

    if (argc < 3)
    {
        printf("Usage: %s <ip> <port> <[optional] recvsize (bytes)> <[optional] filepath>\n", argv[0]);
        exit(0);
    }

    // set sendsize (if larger than 1460 will do packetization (fragmentation))
    if (argc > 3)
        recvsize = atoi(argv[3]);

    // bind socket
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = inet_addr(argv[2]);
    servaddr.sin_port = htons(atoi(argv[3]));

    // if instrument to write to a file
    if (argc > 4)
    {
        fd = open(argv[4], O_WRONLY | O_CREAT | O_TRUNC);
        if (fd == -1) {
            fprintf(stderr, "! Unable to open file %s.\n", argv[2]);
            close(sockfd);
            exit(1);
        }
    }

    ret = sendto(sockfd, "!?=\n", 4, 0, (struct sockaddr *)&servaddr, sizeof(servaddr));
    if (ret <= 0)
    {
        fprintf(stderr, "! Unable to initialize data transfer. errno:%d.\n", errno);
        close(sockfd);
        exit(1);
    }

    // start timing
    gettimeofday(&t_start, NULL);

    // start to receive
    for (;;)
    {
        // printf("before: total_bytes_recv %d\n", total_bytes_recv);
        ret = recvfrom(sockfd, recvbuf, recvsize, 0, NULL, NULL);

        if (ret <= 0)
        {
            if (errno == 0)
                break;
            fprintf(stderr, "! Fail to recv: ret:%d, err:%d; quiting..\n", ret, errno);
            exit(1);
        }

        // a "code" to indicate UDP is done
        // printf("%d %d %d", (recvbuf[0] == '='), (recvbuf[1] == '?'), (recvbuf[0] == '!'));
        if ((recvbuf[0] == '=') && (recvbuf[1] == '?') && (recvbuf[2] == '!'))
            break;

        if (argc > 4)
            write(fd, recvbuf, ret);

        // count how many bytes received
        total_bytes_recv += ret;
        // printf("after: total_bytes_recv %d\n", total_bytes_recv);
    }

    // end timing
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf(
        "recv(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
        total_bytes_recv, elapsedTime, total_bytes_recv * 8 / elapsedTime);
    
    close(sockfd);
    if (argc > 4)
        close(fd);
    
    return 0;
}
