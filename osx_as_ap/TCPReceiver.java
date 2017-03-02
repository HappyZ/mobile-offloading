import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;


public class TCPReceiver  {
  ServerSocketChannel listener = null;
  protected void mySetup()
  {
    InetSocketAddress listenAddr =  new InetSocketAddress(4444);

    try {
      listener = ServerSocketChannel.open();
      ServerSocket ss = listener.socket();
      ss.setReuseAddress(true);
      ss.bind(listenAddr);
      System.out.println("Listening on port : "+ listenAddr.toString());
    } catch (IOException e) {
      System.out.println("Failed to bind, is port : "+ listenAddr.toString()
          + " already in use ? Error Msg : "+e.getMessage());
      e.printStackTrace();
    }

  }

  public static void main(String[] args)
  {
    TCPReceiver dns = new TCPReceiver();
    dns.mySetup();
    dns.readData();
  }

  private void readData()  {
      ByteBuffer dst = ByteBuffer.allocate(4096);
      try {
          while(true) {
              SocketChannel conn = listener.accept();
              System.out.println("Accepted : "+conn);
              conn.configureBlocking(true);
              int nread = 0;
              int data_recv = 0;
              while (nread != -1)  {
                  try {
                      nread = conn.read(dst);
                      data_recv += nread;
                      // System.out.println("data: " +nread);
                  } catch (IOException e) {
                      e.printStackTrace();
                      nread = -1;
                  }
                  dst.rewind();
              }
              System.out.println("Received data: "+data_recv);
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
  }
}