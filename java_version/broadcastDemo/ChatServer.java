package com.nio;


import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatServer implements Runnable {
    private static final Charset charset = Charset.forName("UTF-8");
    private Selector selector;
    private ServerSocketChannel ssc;
    private Thread thread = new Thread(this);

    //暂存来自客户端的消息
    private Queue<String> queue = new ConcurrentLinkedQueue<String>();
    private volatile boolean alive = true;

    public void start() throws IOException {
        selector = Selector.open();
        ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.socket().bind(new InetSocketAddress(6666));
        ssc.register(selector, SelectionKey.OP_ACCEPT); //注册连接请求事件
        thread.start();
    }

    @Override
    public void run() {
        System.out.println("test");
        while (alive && !Thread.interrupted()) {
            try {
                if (selector.select(1000) == 0) {
                    continue;
                }
                ByteBuffer outBuf = null;
                String outMsg = queue.poll();
                /*
                 此处需导入jar包
                 <dependency>
                    <groupId>org.apache.commons</groupId>
                    <artifactId>commons-lang3</artifactId>
                    <version>3.4</version>
                </dependency>
                 */
                if (!StringUtils.isBlank(outMsg)) {
                    outBuf = ByteBuffer.wrap(outMsg.getBytes("UTF-8"));
                    outBuf.limit(outMsg.length());
                }
                Set<SelectionKey> set = selector.selectedKeys();
                Iterator<SelectionKey> it = set.iterator();
                while (it.hasNext()){
                    SelectionKey key = it.next();
                    it.remove();
                    if(key.isValid() && key.isAcceptable()){
                        this.onAcceptable(key);
                    }
                    if (key.isValid() && key.isReadable()){
                        this.onReadable(key);
                    }
                    if(key.isValid() && key.isWritable() && outBuf != null){
                        SocketChannel sc = (SocketChannel) key.channel();
                        this.write(sc, outBuf);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void onAcceptable(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = null;
        try {
            sc = ssc.accept();
            if(sc != null){
                sc.configureBlocking(false);
                sc.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE, ByteBuffer.allocate(1024 * 10));
            }
        } catch (IOException e) {
            e.printStackTrace();
            sc.close();
        }
    }

    private void onReadable(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        int r = 0;
        StringBuilder sb = new StringBuilder();
        String rs = null;
        String ip = null;
        buffer.clear();
        try {
            ip = sc.getRemoteAddress().toString();
            while ((r = sc.read(buffer)) > 0){
                buffer.flip();
                sb.append(charset.decode(buffer));
                rs = sb.toString();
                System.out.println(rs);
                if(rs.endsWith("\n")){
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            sc.close();
            return;
        }
        if(!StringUtils.isBlank(rs)){
            //防止粘包，手动切分
            String strs[] = rs.split("\n");
            for (String str : strs){
                if(!StringUtils.isBlank(str)){
                    queue.add(ip + " say : " + str);
                    if(str.equalsIgnoreCase("bye")){
                        sc.close();
                    }
                }
            }
        }
    }

    private void write(SocketChannel sc, ByteBuffer outBuf) throws IOException {
        outBuf.position(0);
        int r = 0;
        try {
            while (outBuf.hasRemaining() && (r = sc.write(outBuf)) > 0 ){

            }
        }catch (IOException e){
            e.printStackTrace();
            sc.close();
            return;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader br = null;
        ChatServer server = new ChatServer();
        server.start();
        String cmd  = null;
        br = new BufferedReader(new InputStreamReader(System.in));
        while ((cmd = br.readLine()) != null){
            if(cmd.equalsIgnoreCase("exit")){
                break;
            }
        }
        server.close();
    }


    public void close() throws InterruptedException, IOException {
        alive = false;
        thread.join();  //当前主线程等待thread线程完成
        if(selector != null){
            selector.close();
        }
        if(ssc != null){
            ssc.close();
        }
    }

}
