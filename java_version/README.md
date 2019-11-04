# NIO

NIO(New IO)，是Java SE 1.4版，为了提升网络传输性能而设计的新版本的IO，注意，这里的优化主要针对的是网络通信方面的socket的优化。如下程序可以测试针对本地文件IO，两者的异同。

```java
public class FileTransformCompare {
    private long transferFile(File source, File dest) throws IOException {
        long startTime = System.currentTimeMillis();

        if(!dest.exists())
            dest.createNewFile();

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(source));
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dest));

        //将数据从源读到目的文件
        byte[] bytes = new byte[1024];
        int len = 0;
        while ((len = bis.read(bytes))>0){
            bos.write(bytes, 0, len);
        }
        long endTime = System.currentTimeMillis();

        return endTime - startTime;
    }

    private long transferFileFileWithNio(File source, File dest) throws IOException {
        long startTime = System.currentTimeMillis();

        if(!dest.exists())
            dest.createNewFile();

        RandomAccessFile sourceRAF = new RandomAccessFile(source, "rw");
        RandomAccessFile destRAF = new RandomAccessFile(dest, "rw");

        FileChannel readChannel = sourceRAF.getChannel();
        FileChannel writeChannel = destRAF.getChannel();

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024*1024); //1M缓冲区
        while (readChannel.read(byteBuffer) > 0){
            byteBuffer.flip();
            writeChannel.write(byteBuffer);
            byteBuffer.clear();
        }

        writeChannel.close();
        readChannel.close();
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    public static void main(String[] args) throws IOException {
        FileTransformCompare ftc = new FileTransformCompare();
//        File source = new File("F:\\apache-maven-3.6.2-bin.tar.gz");
//        File dest1 = new File("G:\\迅雷下载\\apache1.tar.gz");
//        File dest2 = new File("G:\\迅雷下载\\apache2.tar.gz");
        File source = new File("G:\\迅雷下载\\影视\\战争之王.BD1280超清国英双语中英双字.mp4");
        File dest1 = new File("G:\\迅雷下载\\test1.mp4");
        File dest2 = new File("G:\\迅雷下载\\test2.mp4");
        long time = ftc.transferFile(source, dest1);
        System.out.println("普通字节流时间: " + time);
        long timeNio = ftc.transferFileFileWithNio(source, dest2);
        System.out.println("NIO时间: " + timeNio);
    }
}

/*
	当文件的大小较小的时候，ＮＩＯ会比传统IO好一点，但是文件较大的时候，则ＮＩＯ不如传统ＩＯ
	下面结果是复制一部２．６Ｇ的电影的结果：
	　普通字节流时间: 79745
　　　	NIO时间: 80160
*/
```

也就是说，通常谈到NIO的时候，只会针对网络编程来说。

## 1. 问题的引出——同步阻塞通信(BIO)

首先来看一个传统简单的网络通信案例，该案例是基于同步阻塞的I/O，服务端代码如下

```java
public class Server extends Thread{
    private ServerSocket serverSocket;
    public Server(int port) throws IOException
    {
        serverSocket = new ServerSocket(port, 1000);    //端口号，以及运行连接可以保存的最长队列
        serverSocket.setSoTimeout(1000000);
    }
    public void run()
    {
        while(true)
        {
            try
            {
                System.out.println("等待远程连接，端口号为：" + serverSocket.getLocalPort() + "...");
                Socket server = serverSocket.accept();
                System.out.println("远程主机地址：" + server.getRemoteSocketAddress());
                DataInputStream in = new DataInputStream(server.getInputStream());
                Thread.sleep(2000);
                System.out.println(in.readUTF());
                DataOutputStream out = new DataOutputStream(server.getOutputStream());
                out.writeUTF("0101, 主机收到：" + server.getLocalSocketAddress() + "\nGoodbye!");
                server.close();
            }catch(SocketTimeoutException s)
            {
                System.out.println("Socket timed out!");
                break;
            }catch(IOException e)
            {
                e.printStackTrace();
                break;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public static void main(String [] args) throws IOException {
            Thread t = new Server(6666);
            t.run();
    }
}
```

客户端代码如下：

```java
public class Client implements Runnable{
    private int id;
    public Client(int id){
        this.id = id;
    }
    public static void main(String[] args) throws InterruptedException, IOException {
        ExecutorService es = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 100; i++) {
            es.execute(new Client(i+1));
        }
        es.shutdown();
    }

    @Override
    public void run() {
        Socket client = null;
        try {
            client = new Socket("127.0.0.1", 6666);
            OutputStream outToServer = client.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            out.writeUTF("Hello, I am the " + id + "-client and I come from " + client.getLocalSocketAddress());
            InputStream inFromServer = client.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
           System.out.println("client-" + id + " : response : " + in.readUTF());
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

可以看到当假设100个客户端同时连接服务器的时候，单线程下服务端对接收的请求只会一个一个去处理，导致很多客户端请求被阻塞，处于等待情况，这个时候，通常的服务端优化的解决办法是开启利用线程池开启多个线程去处理。如下：

```java
public class BlockServer implements Runnable{

    private Socket server;
    public  BlockServer(Socket server){
        this.server = server;
    }

    @Override
    public void run() {
        DataInputStream in = null;
        DataOutputStream out = null;
        try {
            in = new DataInputStream(server.getInputStream());
            System.out.println(server.getInetAddress() + ":" + in.readUTF());
            out = new DataOutputStream(server.getOutputStream());
            Thread.sleep(2000);
            out.writeUTF("server receive your message." );
            in.close();
            out.close();
            server.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        ExecutorService es = Executors.newFixedThreadPool(100);
        ServerSocket serverSocket = new ServerSocket(6666, 1000);
        System.out.println("等待远程连接，端口号为：" + serverSocket.getLocalPort() + "...");
        while (!Thread.currentThread().isInterrupted()){
            Socket socket = serverSocket.accept();
            es.execute(new BlockServer(socket));
        }
        es.shutdown();
    }
}
```

两种结果的输出可以看出基于多线程的网络通信效率远远高于单线程。不过多线程通信有一个很大的缺陷——严重依赖线程，通常在Linux环境下并没有线程的概念，此时，线程的本质就是进程了，此时线程的创建销毁，以及线程（上下文）的切换将导致很大的开销，因此，基于这些原因，导致了线程资源不能随便的使用，当我们面对大量的客户端连接服务器的时候，并不能一味的去疯狂创建线程。此时，NIO就可以帮助我们解决此类问题。

## 2. 非阻塞通信—多路复用的NIO

  BIO模型中，因为在进行IO操作的时候，程序无法知道数据到底准备好没有，能否可读，只能一直干等着，而且即便我们可以猜到什么时候数据准备好了，但我们也没有办法通过socket.read()或者socket.write()函数去返回，而NIO却可以通过[I/O复用技术]( https://www.cnblogs.com/helloworldcode/p/10883130.html )把这些连接请求注册到多路复用器Selector中去，用一个线程去监听和处理多个SocketChannel上的事件。

### BufferByte和Channel

 在NIO中并不是以流的方式来处理数据的，而是以buffer缓冲区和Channel管道（全双工）配合使用来处理数据。 其中Channel分为四类：

* FileChannel: 用于文件IO，支持阻塞模式。

* DatagramChannel:  用于UDP通信。

* SocketChannel: 用于TCP的客户端通信。

* ServerSocketChannel: 用于TCP的服务端通信。

其中Channel的用法在开始的文件传输示例代码中有展示，而ByteBuffer的用法如下所示：

```java
public class Test1 {
    public static void main(String[] args) {
        // 创建一个缓冲区
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        // 看一下初始时4个核心变量的值  
        //limit 缓冲区里的数据的总数
        System.out.println("初始时-->limit--->"+byteBuffer.limit());
        //position 下一个要被读或写的元素的位置
        System.out.println("初始时-->position--->"+byteBuffer.position());
        //capacity 缓冲区能够容纳的数据元素的最大数量。
        System.out.println("初始时-->capacity--->"+byteBuffer.capacity());
        //mark 一个备忘位置。用于记录上一次读写的位置。
        System.out.println("初始时-->mark--->" + byteBuffer.mark());

        System.out.println("--------------------------------------");

        // 添加一些数据到缓冲区中
        String s = "testing.....";
        byteBuffer.put(s.getBytes());

        // 看一下初始时4个核心变量的值
        System.out.println("put完之后-->limit--->"+byteBuffer.limit());
        System.out.println("put完之后-->position--->"+byteBuffer.position());
        System.out.println("put完之后-->capacity--->"+byteBuffer.capacity());
        System.out.println("put完之后-->mark--->" + byteBuffer.mark());
		
        //读数据前要调用，可以指示读数据的操作从position读到limit之间的数据
        byteBuffer.flip();

        System.out.println("--------------------------------------");
        System.out.println("flip完之后-->limit--->"+byteBuffer.limit());
        System.out.println("flip完之后-->position--->"+byteBuffer.position());
        System.out.println("flip完之后-->capacity--->"+byteBuffer.capacity());
        System.out.println("flip完之后-->mark--->" + byteBuffer.mark());

        // 创建一个limit()大小的字节数组(因为就只有limit这么多个数据可读)
        byte[] bytes = new byte[byteBuffer.limit()];

        // 将读取的数据装进我们的字节数组中
        byteBuffer.get(bytes);

        // 输出数据
        System.out.println(new String(bytes, 0, bytes.length));
    }
}
/*output
初始时-->limit--->1024
初始时-->position--->0
初始时-->capacity--->1024
初始时-->mark--->java.nio.HeapByteBuffer[pos=0 lim=1024 cap=1024]
--------------------------------------
put完之后-->limit--->1024
put完之后-->position--->12
put完之后-->capacity--->1024
put完之后-->mark--->java.nio.HeapByteBuffer[pos=12 lim=1024 cap=1024]
--------------------------------------
flip完之后-->limit--->12
flip完之后-->position--->0
flip完之后-->capacity--->1024
flip完之后-->mark--->java.nio.HeapByteBuffer[pos=0 lim=12 cap=1024]
testing.....
*/
```





## 参考文献

1. [如何学习Java的NIO？](https://www.zhihu.com/question/29005375)
2. [Java NIO浅析](https://tech.meituan.com/2016/11/04/nio.html)