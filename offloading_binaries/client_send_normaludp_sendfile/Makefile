#CC = /media/Lucifer/android/lib/android-18-toolchain/bin/arm-linux-androideabi-gcc
CC1 = arm-linux-androideabi-gcc
CC2 = gcc
CFLAGS = -pie -fPIE
TARGET1 = client_send_normaludp_sendfile
TARGET2 = client_send_normaludp_sendfile_pc

all: $(TARGET1) $(TARGET2)

$(TARGET1): main.c
	$(CC1) main.c -Wall $(CFLAGS) -o $@

$(TARGET2): main.c
	$(CC2) main.c -Wall $(CFLAGS) -o $@

clean:
	-rm -f $(TARGET1)
	-rm -f $(TARGET2)
