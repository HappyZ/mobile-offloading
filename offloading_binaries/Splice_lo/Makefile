CC = arm-linux-androideabi-gcc
CFLAGS = -pie -fPIE
TARGET = splice_lo

OBJS = $(patsubst %.c, %.o, $(wildcard *.c))
HEADERS = $(wildcard *.h)

all: $(TARGET)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

.PRECIOUS: $(TARGET) $(OBJS)

$(TARGET): $(OBJS)
	$(CC) $(OBJS) -Wall $(CFLAGS) -o $@
	cp $(TARGET) /media/Lucifer/yanzi/projects/offloading/RDMAMobileDemo/app/src/main/assets/
clean:
	-rm -f *.o
	-rm -f $(TARGET)
