cd client_send_normaltcp_sendfile && adb push client_send_normaltcp_sendfile /data/local/tmp/
cd ../
cd client_send_normaltcp_splice && adb push client_send_normaltcp_splice /data/local/tmp/
cd ../
cd client_send_bypassl3 && adb push client_send_bypassl3 /data/local/tmp/
cd ../
cd client_send_normaludp && adb push client_send_normaludp /data/local/tmp/
cd ../
cd client_send_normaltcp && adb push client_send_normaltcp /data/local/tmp/
cd ../
cd client_readfile_only && adb push client_readfile_only /data/local/tmp/
cd ../
cd client_recv_normaltcp_splice && adb push client_recv_normaltcp_splice /data/local/tmp/
cd ../
cd client_recv_normaltcp && adb push client_recv_normaltcp /data/local/tmp/
cd ../
cd client_recv_normaludp && adb push client_recv_normaludp /data/local/tmp/
cd ../
cd server_recv_normaltcp && adb push server_m_recv_normaltcp /data/local/tmp/
cd ../
cd server_recv_normaludp && adb push server_m_recv_normaludp /data/local/tmp/
cd ../
