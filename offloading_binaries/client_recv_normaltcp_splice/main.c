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
    uint bytes, bytes_recv, bytes_in_pipe;
    // for timing
    double elapsedTime;
    struct timeval t_start, t_end, t_now;
    // for socket
    int fd; // file descriptor of file to send
    int sockfd; // socket
    char recvbuf[BUF_SIZ];
    struct sockaddr_in servaddr;
    // for misc
    int ret;
    int recvsize = 4096;
    // create two pipes
    int filedes[2];
    ret = pipe(filedes);

    if (argc < 3)
    {
        printf("Usage: %s <ip> <port> <[optional] recvsize (bytes)> <[optional] filepath>\n", argv[0]);
        exit(0);
    }

    // set sendsize (if larger than 1460 will do packetization (fragmentation))
    if (argc > 3)
        recvsize = atoi(argv[3]);

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

    // if instrument to write to a file
    if (argc > 4)
    {
        fd = open(argv[4], O_WRONLY | O_CREAT | O_TRUNC);
        if (fd == -1) {
            fprintf(stderr, "! Unable to open file %s.\n", argv[4]);
            close(sockfd);
            exit(1);
        }
    }
    else
    {
        fd = open("/dev/null", O_WRONLY);
        if (fd == -1) {
            fprintf(stderr, "! Unable to open file %s.\n", argv[2]);
            close(sockfd);
            exit(1);
        }
    }

    // start timing
    gettimeofday(&t_start, NULL);

    // start to receive
    for (;;)
    {
        // printf("before: total_bytes_recv %d\n", total_bytes_recv);

        // Splice the data from in_fd into the pipe
        if ((bytes_recv = splice(sockfd, NULL, filedes[1], NULL,
                recvsize, SPLICE_F_MORE | SPLICE_F_MOVE)) <= 0) {
            if (errno == EINTR || errno == EAGAIN) {
                // Interrupted system call/try again
                // Just skip to the top of the loop and try again
                continue;
            }
            fprintf(stderr, "! splice error, errno: %d.\n", errno);
            exit(1);
        }

        // Splice the data from the pipe into out_fd
        bytes_in_pipe = bytes_recv;
        while (bytes_in_pipe > 0) {
            if ((bytes = splice(filedes[0], NULL, fd, NULL, bytes_in_pipe,
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

        total_bytes_recv += bytes_recv;
        // printf("after: total_bytes_recv %d\n", total_bytes_recv);
    }

    // end timing
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf(
        "recv(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
        total_bytes_recv, elapsedTime, total_bytes_recv * 8 / elapsedTime);
    
    close(sockfd);
    close(fd);
    
    return 0;
}
