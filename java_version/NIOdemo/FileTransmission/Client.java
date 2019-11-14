package com.nio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

public class Client {
    public static void main(String[] args) throws IOException {
        SocketChannel sc = null;
        FileChannel fc = null;
        sc = SocketChannel.open();
        sc.configureBlocking(true); //客户端使用阻塞模式
        sc.connect(new InetSocketAddress("127.0.0.1", 6666));
        if(!sc.finishConnect()){
            System.out.println("error: can not connect to the server.");
        }
        //从文件中读取内容
        fc = new FileInputStream(new File("F:\\apache-maven-3.6.2-bin.tar.gz")).getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(1024*1024*10);
        int r = 0;
        while ((r = fc.read(buffer)) > 0){
            System.out.println("read " + r + " bytes from file.");
            buffer.flip();
            while (buffer.hasRemaining() && (r = sc.write(buffer)) > 0){
                System.out.println("send " + r + "bytes to server.");
            }
            buffer.clear();
        }

        //读取服务器返回的信息
        while ((r = sc.read(buffer)) > 0){
            System.out.println("receive " + r + "bytes from server.");
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        System.out.println(new String(bytes, 0, bytes.length));
        fc.close();
        sc.close();
    }
}
