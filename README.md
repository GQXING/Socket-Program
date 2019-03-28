## 介绍

​	网络上的两个程序通过一个双向的通信连接实现数据的交换，这个连接的一端称为一个socket。

## 过程介绍

​	服务器端和客户端通信过程如下所示：



![socket通信过程](http://images.cnblogs.com/cnblogs_com/helloworldcode/1414395/o_05232335-fb19fc7527e944d4845ef40831da4ec2.png)

### 服务端

​	服务端的过程主要在该图的左侧部分，下面对上图的每一步进行详细的介绍。

#### 1. 套接字对象的创建

```C++
	/*
     * _domain 套接字使用的协议族信息
     * _type 套接字的传输类型
     * __protocol 通信协议
     * */
     int socket (int __domain, int __type, int __protocol) __THROW;
```

socket起源于UNIX，在Unix一切皆文件哲学的思想下，socket是一种"打开—读/写—关闭"模式的实现，可以将该函数类比常用的`open()`函数，服务器和客户端各自维护一个"文件"，在建立连接打开后，可以向自己文件写入内容供对方读取或者读取对方内容，通讯结束时关闭文件。

**参数介绍**

第一个参数：关于协议族信息可选字段如下，只列出一般常见的字段。

|  地址族  |                    含义                    |
| :------: | :----------------------------------------: |
| AF_INET  |         IPv4网络协议中采用的地址族         |
| AF_INET6 |         IPv6网络协议中采用的地址族         |
| AF_LOCAL | 本地通信中采用的UNIX协议的地址族（用的少） |

第二个参数：套接字类型。常用的有SOCKET_RAW，SOCK_STREAM和SOCK_DGRAM。

| 套接字类型  |                             含义                             |
| :---------: | :----------------------------------------------------------: |
| SOCKET_RAW  | 原始套接字(SOCKET_RAW)允许对较低层次的协议直接访问，比如IP、 ICMP协议。 |
| SOCK_STREAM |        SOCK_STREAM是数据流，一般为TCP/IP协议的编程。         |
| SOCK_DGRAM  |        SOCK_DGRAM是数据报，一般为UDP协议的网络编程；         |

第三个参数：最终采用的协议。常见的协议有IPPROTO_TCP、IPPTOTO_UDP。如果第二个参数选择了SOCK_STREAM，那么采用的协议就只能是IPPROTO_TCP；如果第二个参数选择的是SOCK_DGRAM，则采用的协议就只能是IPPTOTO_UDP。

#### 2. 向套接字分配网络地址——bind()

```C++
/* 
* __fd:socket描述字，也就是socket引用
* myaddr:要绑定给sockfd的协议地址
* __len:地址的长度
*/
int bind (int __fd, const struct sockaddr* myaddr, socklen_t __len)  __THROW;
```

第一个参数：socket文件描述符`__fd`即套接字创建时返回的对象，

第二个参数：`myaddr`则是填充了一些网络地址信息，包含通信所需要的相关信息，其结构体具体如下：

```C++
struct sockaddr
  {
    sa_family_t sin_family;	/* Common data: address family and length.  */
    char sa_data[14];		/* Address data.  */
  };
```

在具体传参的时候，会用该结构体的变体`sockaddr_in`形式去初始化相关字段，该结构体具体形式如下，结构体`sockaddr`中的`sa_data`就保存着地址信息需要的IP地址和端口号，对应着结构体`sockaddr_in`的`sin_port`和`sin_addr`字段。

 ```C++
struct sockaddr_in{
    sa_family_t sin_family;		//前面介绍的地址族
    uint16_t sin_port;			//16位的TCP/UDP端口号
    struct in_addr sin_addr;	//32位的IP地址
    char sin_zero[8];			//不使用
}
 ```

`in_addr`  结构定义如下：

```C++
/* Internet address.  */
typedef uint32_t in_addr_t;
struct in_addr
{
	in_addr_t s_addr;
};
```

而`sin_zero` 无特殊的含义，只是为了与下面介绍的sockaddr结构体一致而插入的成员。因为在给套接字分配网络地址的时候会调用`bind`函数，其中的参数会把`sockaddr_in`转化为`sockaddr`的形式，如下：

```C++
struct sockaddr_in serv_addr;
...
bind(serv_socket, (struct sockaddr*)&serv_addr, sizeof(serv_addr)；
```

需要注意的是`s_addr`是一种`uint32_t`类型的数据，而且在网络传输时，统一都是以大端序的网络字节序方式传输数据，而我们通常习惯的IP地址格式是点分十进制，例如：“219.228.148.169”，这个时候就会调用以下函数进行转化，将IP地址转化为32位的整数形数据，同时进行网络字节转换：

```C++
in_addr_t inet_addr (const char *__cp) __THROW;
//或者
int inet_aton (const char *__cp, struct in_addr *__inp) __THROW;	//windows无此函数
```

如果单纯要进行网络字节序地址的转换，可以采用如下函数：

```C++
/*Functions to convert between host and network byte order.

   Please note that these functions normally take `unsigned long int' or
   `unsigned short int' values as arguments and also return them.  But
   this was a short-sighted decision since on different systems the types
   may have different representations but the values are always the same.  */

// h代表主机字节序
// n代表网络字节序
// s代表short(4字节)
// l代表long(8字节)
extern uint32_t ntohl (uint32_t __netlong) __THROW __attribute__ ((__const__));
extern uint16_t ntohs (uint16_t __netshort)
     __THROW __attribute__ ((__const__));
extern uint32_t htonl (uint32_t __hostlong)
     __THROW __attribute__ ((__const__));
extern uint16_t htons (uint16_t __hostshort)
```

#### 3. 进入等待连接请求状态

给套接字分配了所需的信息后，就可以调用`listen()`函数对来自客户端的连接请求进行监听（客户端此时要调用`connect()`函数进行连接）

```C++
/* Prepare to accept connections on socket FD.
   N connection requests will be queued before further requests are refused.
   Returns 0 on success, -1 for errors.  */
extern int listen (int __fd, int __n) __THROW;
```

第一个参数：socket文件描述符`__fd`，分配所需的信息后的套接字。

第二个参数：连接请求的队列长度，如果为6，表示队列中最多同时有6个连接请求。

这个函数的fd(socket套接字对象)就相当于一个门卫，对连接请求做处理，决定是否把连接请求放入到server端维护的一个队列中去。

#### 4. 受理客户端的连接请求

`listen()`中的sock(__fd : socket对象)发挥了服务器端接受请求的门卫作用，此时为了按序受理请求，给客户端做相应的回馈，连接到发起请求的客户端，此时就需要再次创建另一个套接字，该套接字可以用以下函数创建：

```C++
/* Await a connection on socket FD.
   When a connection arrives, open a new socket to communicate with it,
   set *ADDR (which is *ADDR_LEN bytes long) to the address of the connecting
   peer and *ADDR_LEN to the address's actual length, and return the
   new socket's descriptor, or -1 for errors.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
extern int accept (int __fd, struct sockaddr *addr, socklen_t *addr_len);
```

函数成功执行时返回socket文件描述符，失败时返回-1。

第一个参数：socket文件描述符`__fd`，要注意的是这个套接字文件描述符与前面几步的套接字文件描述符不同。

第二个参数：保存发起连接的客户端的地址信息。

第三个参数： 保存该结构体的长度。

#### 5. send/write发送信息

linux下的发送函数为：

````C++
/* Write N bytes of BUF to FD.  Return the number written, or -1.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
 ssize_t write (int __fd, const void *__buf, size_t __n) ;
````

而在windows下的发送函数为：

````C++
ssize_t send (int sockfd, const void *buf, size_t nbytes, int flag) ;
````

第四个参数是传输数据时可指定的信息，一般设置为0。

#### 6. recv/read接受信息

linux下的接收函数为

```C++
/* Read NBYTES into BUF from FD.  Return the
   number read, -1 for errors or 0 for EOF.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
ssize_t read (int __fd, void *__buf, size_t __nbytes);
```

而在windows下的接收函数为

```C++
ssize_t recv(int sockfd, void *buf, size_t nbytes, int flag) ;
```

#### 7 .关闭连接

```C++

/* Close the file descriptor FD.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
int close (int __fd);
```

退出连接，此时要注意的是：**调用`close()`函数即表示向对方发送了`EOF`结束标志信息**。

### 客户端

​	服务端的socket套接字在绑定自身的IP即 及端口号后这些信息后，就开始监听端口等待客户端的连接请求，此时客户端在创建套接字后就可以按照如下步骤与server端通信，创建套接字的过程不再重复了。

#### 1.  请求连接

```C++
/* Open a connection on socket FD to peer at ADDR (which LEN bytes long).
   For connectionless socket types, just set the default address to send to
   and the only address from which to accept transmissions.
   Return 0 on success, -1 for errors.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
int connect (int socket, struct sockaddr* servaddr, socklen_t addrlen);
```

几个参数的意义和前面的accept函数意义一样。要注意的是服务器端收到连接请求的时候并不是马上调用accept()函数，而是把它放入到请求信息的等待队列中。



## 程序案例

案例的过程，在网上看到了关于read和write的发送与接受过程的图，便于理解：

![](http://images.cnblogs.com/cnblogs_com/helloworldcode/1414395/o_TCP-socket.jpg)

#### 

注意以上代码都是在ubuntu下运行的，在windows的代码与此有所不同。比如要引入一个`<winsock2.h>`的头文件，调用`WSAStartup(...)`函数进行Winsock的初始化，而且它们的接受与发送函数也有所不同。

## 参考文献

[简单理解Socket](https://www.cnblogs.com/dolphinX/p/3460545.html)

[套接字](https://baike.baidu.com/item/%E5%A5%97%E6%8E%A5%E5%AD%97)

《TCP/IP网络编程》尹圣雨