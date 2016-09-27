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
#include <unistd.h>
#include <signal.h>
#include <ctype.h>

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
    uint total_bytes_recv = 0;
    // for timing
    double elapsedTime;
    struct timeval t_start, t_end;
    // for socket
    int fd = 0; // file descriptor of file to write (receive)
    int port;
    int recvsize = 4096;
    int sockfd, listenfd; // fd
    char recvbuf[BUF_SIZ];
    struct sockaddr_in servaddr, cliaddr;
    socklen_t clilen;
    // for misc
    int ret;
    unsigned char listenOnce = 0;

    signal(SIGPIPE, SIG_IGN);
    
    if (argc < 2)
    {
        printf("Usage: %s <port> <[optional] listenOnce ([0]/1)> <[optional] filepath>\n", argv[0]);
        exit(0);
    }

    port  = atoi(argv[1]);

    if (argc > 2)
        listenOnce = 1;

    // listen to socket
    listenfd = socket(AF_INET, SOCK_STREAM, 0);
    // bind socket and listen
    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(port);
    bind(listenfd, (struct sockaddr *)&servaddr, sizeof(servaddr));
    listen(listenfd, 1);

    // infinity loop to listen
    for (;;)
    {
        clilen = sizeof(cliaddr);

        // wait for one client and accept it once found
        sockfd = accept(listenfd, (struct sockaddr *)&cliaddr, &clilen);
        printf("Accepted client at %s\n", inet_ntoa(cliaddr.sin_addr));

        // clear total_bytes_recv to 0
        total_bytes_recv = 0;

        // if instrument to write to a file
        if (argc > 3)
        {
            fd = open(argv[3], O_WRONLY | O_CREAT | O_TRUNC);
            if (fd == -1) {
                fprintf(stderr, "! Unable to open file %s.\n", argv[2]);
                close(sockfd);
                if (listenOnce)
                    break;
                continue; // skip and listen to a new one
            }
        }

        // start timing
        gettimeofday(&t_start, NULL);

        // start to receive
        for (;;)
        {
            ret = recv(sockfd, recvbuf, recvsize, 0);
            if (ret <= 0)
            {
                if (errno == 0)
                {
                    if (argc > 3)
                        close(fd);
                    break;
                }
                fprintf(stderr, "! Fail to recv: ret:%d, err:%d; quiting..\n", ret, errno);
                exit(1);
            }
            if (argc > 3)
                write(fd, recvbuf, ret);
            total_bytes_recv += ret;
            // printf("total_bytes_recv %d\n", total_bytes_recv);
        }

        // end timing
        gettimeofday(&t_end, NULL);
        elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
        printf(
            "recv(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
            total_bytes_recv, elapsedTime, total_bytes_recv * 8 / elapsedTime);

        close(sockfd);

        if (listenOnce)
            break;
    }

    return 0;
}
