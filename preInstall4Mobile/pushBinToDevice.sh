echo "* Pushing offloading binaries"
adb push ../offloading_binaries/client_send_normaltcp_sendfile/client_send_normaltcp_sendfile /data/local/tmp/
adb push ../offloading_binaries/client_send_normaltcp_splice/client_send_normaltcp_splice /data/local/tmp/
adb push ../offloading_binaries/client_send_bypassl3/client_send_bypassl3 /data/local/tmp/
adb push ../offloading_binaries/client_send_normaludp/client_send_normaludp /data/local/tmp/
adb push ../offloading_binaries/client_send_normaltcp/client_send_normaltcp /data/local/tmp/
adb push ../offloading_binaries/client_readfile_only/client_readfile_only /data/local/tmp/
adb push ../offloading_binaries/client_recv_normaltcp_splice/client_recv_normaltcp_splice /data/local/tmp/
adb push ../offloading_binaries/client_recv_bypassl3/client_recv_bypassl3 /data/local/tmp/
adb push ../offloading_binaries/client_recv_normaltcp/client_recv_normaltcp /data/local/tmp/
adb push ../offloading_binaries/client_recv_normaludp/client_recv_normaludp /data/local/tmp/
adb push ../offloading_binaries/server_recv_normaltcp/server_m_recv_normaltcp /data/local/tmp/
adb push ../offloading_binaries/server_recv_normaludp/server_m_recv_normaludp /data/local/tmp/
echo "* Pushing tcpdump 4.8.1"
adb push tcpdump /data/local/tmp/
echo "* Change permission"
adb shell chmod 755 /data/local/tmp/*
echo "* Done."
