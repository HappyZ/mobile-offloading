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
#include <unistd.h>
#include <signal.h>

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
    int port;
    int recvsize = 4096;
    int listenfd; // fd
    char recvbuf[BUF_SIZ];
    struct sockaddr_in servaddr, cliaddr;
    socklen_t clilen;
    // for misc
    int ret;

    signal(SIGPIPE, SIG_IGN);
    
    if (argc < 2)
    {
        printf("Usage: %s <port> <[optional] filepath>\n", argv[0]);
        exit(0);
    }

    port  = atoi(argv[1]);

    // listen to socket
    listenfd = socket(AF_INET, SOCK_DGRAM, 0);
    // bind socket and listen
    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(port);
    bind(listenfd, (struct sockaddr *)&servaddr, sizeof(servaddr));

    // infinity loop to listen
    printf("Waiting on port %d...\n", port);
    // clear total_bytes_recv to 0
    total_bytes_recv = 0;

    // if instrument to write to a file
    if (argc > 2)
    {
        fd = open(argv[2], O_WRONLY | O_CREAT | O_TRUNC);
        if (fd == -1) {
            fprintf(stderr, "! Unable to open file %s.\n", argv[2]);
            close(listenfd);
            exit(1);
        }
    }

    // start timing
    gettimeofday(&t_start, NULL);

    // start receiving
    for (;;)
    {
        clilen = sizeof(cliaddr);

        // wait for one client and accept it once found
        ret = recvfrom(listenfd, recvbuf, recvsize, 0, (struct sockaddr *)&cliaddr, &clilen);
        printf("Accepted client at %s with %d len msg\n", inet_ntoa(cliaddr.sin_addr), ret);

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

        if (argc > 2)
            write(fd, recvbuf, ret);

        total_bytes_recv += ret;
        // printf("total_bytes_recv %d\n", total_bytes_recv);

    }
    
    if (argc > 2)
        close(fd);

    // end timing
    gettimeofday(&t_end, NULL);
    elapsedTime = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_usec - t_start.tv_usec) / 1000000.0;
    printf(
        "recv(bytes):%d\nduration(s):%lf\nthroughput(bps):%lf\n",
        total_bytes_recv, elapsedTime, total_bytes_recv * 8 / elapsedTime);

    close(listenfd);

    return 0;
}
