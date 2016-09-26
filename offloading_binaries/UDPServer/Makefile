CC = gcc
CFLAGS = -pie -fPIE
TARGET = UDPServer

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -Wall $(CFLAGS) -o $@
	cp UDPServer ~/mobileRDMABeach/
clean:
	-rm -f *.o
	-rm -f $(TARGET)
