package com.nio;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;


public class Server implements Runnable {
    //多路复用器，用于同时处理多个通道的多个事件
    private Selector selector = null;
    //服务端的socket通道
    private ServerSocketChannel ssc = null;
    //工作线程对象
    private Thread thread = new Thread(this);
    //工作线程的退出标志
    private volatile boolean live = true;

    public void start() throws IOException {
        //创建多路复用器
        selector = Selector.open();
        //创建serversocket通道
        ssc = ServerSocketChannel.open();
        //绑定端口，监听请求
        ssc.socket().bind(new InetSocketAddress(6666));
        //使用非阻塞模式
        ssc.configureBlocking(false);
        //注册连接请求事件
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        //开启线程，循环等待
        thread.start();
    }

    @Override
    public void run() {
        //不断处理socket通道事件，知道live为false
        while (live && !Thread.interrupted()){
            try {
                //每隔一秒检查所有注册的通道是否发生可读或可写事件
                if(selector.select(1000) == 0){
                    continue;   //没有，则继续等待
                }
                //有事件产生，取出这些事件，并遍历它们
                Set<SelectionKey> set = selector.selectedKeys();
                Iterator<SelectionKey> it = set.iterator();
                while (it.hasNext()){
                    SelectionKey key = it.next();
                    //事件已处理，则移除
                    it.remove();
                    //判断该请求是否是有效的连接请求
                    if(key.isValid() && key.isAcceptable()){
                        this.onAcceptable(key); //处理请求
                    }

                    //通道是否可读
                    if(key.isValid() && key.isReadable()){
                        this.onReadable(key);
                    }
                    //通道是否可写
                    if(key.isValid() && key.isWritable()){
                        this.onWritable(key);
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void onAcceptable(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
        SocketChannel sc = null;
        try {
            sc = ssc.accept();
            if(sc != null){
                sc.configureBlocking(false);
                //在新建立的socket通道上注册可读事件，同时为该连接建立一个缓冲区，作为后面操作使用
                sc.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(10 * 1024));
            }
        } catch (IOException e) {
            e.printStackTrace();
            sc.close();
        }
    }

    private void onReadable(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        //将此前的缓冲区取出
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        FileChannel fc = null;
        InetSocketAddress isa = (InetSocketAddress) sc.getRemoteAddress();
        fc = new FileOutputStream(new File("D:\\" + isa.getPort() + ".tar.gz")).getChannel();
        int r = 0;
        while ((r = sc.read(buffer)) > 0){
            System.out.println(r + "received...");
            buffer.flip();
            r = fc.write(buffer);
            buffer.clear();
        }
        //数据读取完之后注册可写事件，用于向客户端作出回应
        sc.register(selector, SelectionKey.OP_WRITE, "OK");
        sc.close();
        fc.close();
    }

    private void onWritable(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        String s = (String) key.attachment();
        byte[] bytes = s.getBytes("UTF-8");
        //将byte字节数组放入ByteBuffer中
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.limit(bytes.length); //设置可读的最终位置(pos--limit)
        int r = 0;
        while ((r = sc.write(buffer)) > 0){
            System.out.println("sent " + r + "bytes to client.");
        }
        sc.close();
    }

    public void close() throws InterruptedException, IOException {
        live = false;
        thread.join();  //当前主线程等待thread线程完成
        if(selector != null){
            selector.close();
        }
        if(ssc != null){
            ssc.close();
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader br = null;
        Server server = new Server();
        try {
            server.start();
            System.out.println("Enter exit to exit.");
            String cmd  = null;
            br = new BufferedReader(new InputStreamReader(System.in));
            while ((cmd = br.readLine()) != null){
                if("exit".equalsIgnoreCase(cmd)){
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.close();
    }
}
