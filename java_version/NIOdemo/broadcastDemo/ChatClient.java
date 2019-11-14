package com.nio;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;


public class ChatClient implements Runnable {
    private Selector selector;
    private SocketChannel sc;
    private Thread thread = new Thread(this);
    private final Charset charset = Charset.forName("UTF-8");

    //用于读取消息的buffer
    private ByteBuffer buffer = ByteBuffer.allocate(10240);
    private volatile boolean alive = true;

    //存放待发送的消息列表
    Queue<String> queue = new ConcurrentLinkedQueue<String>();

    public void start() throws IOException {
        selector = Selector.open();
        sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(new InetSocketAddress("127.0.0.1", 6666));
        if (sc.finishConnect()) {
            //连接成功后同时注册可读可写事件
            sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            thread.start();
        }
    }

    @Override
    public void run() {
        try {
            while (alive && !Thread.interrupted()) {
                if (selector.select(1000) == 0) {
                    continue;
                }
                Set<SelectionKey> set = selector.selectedKeys();
                Iterator<SelectionKey> it = set.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    SocketChannel sc = null;
                    String s = null;
                    int r = 0;
                    if (key.isValid() && key.isReadable()) {
                        sc = (SocketChannel) key.channel();
                        StringBuilder sb = new StringBuilder();
                        buffer.clear();
                        while ((r = sc.read(buffer)) > 0) {
                            buffer.flip();
                            sb.append(charset.decode(buffer));
                            buffer.clear();
                            s = sb.toString();
                            //回车键表示一条消息的结束
                            if (s.endsWith("\n")) {
                                break;
                            }
                        }
                        //收到的消息，可能是多条，即粘包现象,现在切分
                        String[] sa = s.split("\n");
                        for (String a : sa) {
                            System.out.println(a);
                        }
                    }
                    //如果有消息要发，则往通道写数据
                    if (key.isValid() && key.isWritable()) {
                        s = queue.poll();   // 移除并返问队列头部的元素
                        sc = (SocketChannel) key.channel();
                        if (s != null) {
                            ByteBuffer buf = ByteBuffer.wrap(s.getBytes("UTF-8"));
                            buf.limit(s.length());
                            while (buf.hasRemaining() && (r = sc.write(buf)) > 0) {
                                // do nothing
                            }
                        }
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                sc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void close() throws InterruptedException, IOException {
        alive = false;
        thread.join();  //当前主线程等待thread线程完成
        if (selector != null) {
            selector.close();
        }
        if (sc != null) {
            sc.close();
        }
    }


    public void send(String s) {
        queue.add(s);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader br = null;
        ChatClient client = new ChatClient();
        client.start();
        br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter bye to exit.");
        String cmd = null;
        while ((cmd = br.readLine()) != null) {
            client.send(cmd);
            if (cmd.equalsIgnoreCase("bye")) {
                client.close();
                break;
            }
        }
        br.close();
    }
}
