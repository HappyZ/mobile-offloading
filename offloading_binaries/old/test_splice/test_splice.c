/*
 * Copyright (C) 2009 Max Kellermann <max@duempel.org>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the
 * distribution.
 */

/*
 * This tiny program prints a matrix: which file descriptor
 * combinations are supported by splice()?
 */

#define _GNU_SOURCE
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>

static struct {
    const char *const name;
    int in, out;
} fds[] = {
    { .name = "pipe", },
    { .name = "reg", },
    { .name = "chr", },
    { .name = "unix", },
    { .name = "tcp", },
    { .name = "udp", },
};

enum {
    NUM_FDS = sizeof(fds) / sizeof(fds[0]),
};

int main(int argc, char **argv)
{
    int f[2], ret;
    unsigned x, y;
    char template1[] = "/tmp/test_splice.XXXXXX";
    char template2[] = "/tmp/test_splice.XXXXXX";

    (void)argc;
    (void)argv;

    /* open two file descriptors of each kind */

    fds[0].in = pipe(f) >= 0 ? f[0] : -1;
    fds[0].out = pipe(f) >= 0 ? f[1] : -1;
    fds[1].in = mkstemp(template1);
    fds[1].out = mkstemp(template2);
    fds[2].in = open("/dev/zero", O_RDONLY);
    fds[2].out = open("/dev/null", O_WRONLY);
    fds[3].in = socketpair(AF_UNIX, SOCK_STREAM, 0, f) >= 0 ? f[0] : -1;
    fds[3].out = socketpair(AF_UNIX, SOCK_STREAM, 0, f) >= 0 ? f[0] : -1;
    fds[4].in  = socket(AF_INET, SOCK_STREAM, 0);
    fds[4].out = socket(AF_INET, SOCK_STREAM, 0);
    fds[5].in  = socket(AF_INET, SOCK_DGRAM, 0);
    fds[5].out = socket(AF_INET, SOCK_DGRAM, 0);

    /* print table header */

    printf("in\\out");
    for (x = 0; x < NUM_FDS; ++x)
        printf("\t%s", fds[x].name);
    putchar('\n');

    for (y = 0; y < NUM_FDS; ++y) {
        fputs(fds[y].name, stdout);

        for (x = 0; x < NUM_FDS; ++x) {
            putchar('\t');

            if (fds[x].out < 0 || fds[y].in < 0) {
                fputs("n/a", stdout);
                continue;
            }

            ret = splice(fds[y].in, NULL, fds[x].out, NULL, 1,
                         SPLICE_F_NONBLOCK);
            if (ret >= 0 || errno == EAGAIN || errno == EWOULDBLOCK
		|| errno == ENOTCONN)
                /* EAGAIN or EWOULDBLOCK means that the kernel has
                   accepted this combination, but can't move pages
                   right now */
                fputs("yes", stdout);
            else if (errno == EINVAL)
                /* the kernel doesn't support this combination */
                fputs("no", stdout);
            else if (errno == ENOSYS)
                /* splice() isn't supported at all */
                fputs("ENOSYS", stdout);
            else
                /* an unexpected error code */
                fputs("err", stdout);
        }

        putchar('\n');
    }

    unlink(template1);
    unlink(template2);
}
