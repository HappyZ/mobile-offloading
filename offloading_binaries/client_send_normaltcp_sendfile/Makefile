CC = arm-linux-androideabi-gcc
# CC = gcc
CFLAGS = -pie -fPIE
TARGET = client_send_normaltcp_sendfile

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -Wall $(CFLAGS) -o $@

clean:
	-rm -f *.o
	-rm -f $(TARGET)
