# client sender
cd client_send_normaltcp_sendfile && make
cd ../
cd client_send_normaltcp_splice && make
cd ../
cd client_send_bypassl3 && make
cd ../
cd client_send_normaludp && make
cd ../
cd client_send_normaltcp && make
cd ../
# client read file only
cd client_readfile_only && make
cd ../
# client receiver
cd client_recv_normaltcp_splice && make
cd ../
cd client_recv_bypassl3 && make
cd ../
cd client_recv_normaltcp && make
cd ../
cd client_recv_normaludp && make
cd ../
# server sender
cd server_send_normaltcp && make
cd ../
cd server_send_normaludp && make
cd ../
# server receiver
cd server_recv_normaltcp && make
cd ../
cd server_recv_normaludp && make
cd ../
