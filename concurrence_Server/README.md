# Basic Knowledge

## 1. 关于fork

```C++
#include <iostream>
#include <unistd.h>
using namespace std;
int val = 10;
int main(int argc, char *argv[])
{
    pid_t pid;
    int lval = 20;

    pid = fork();

    if(pid == 0){
        val += 2;
        lval += 5;
    }else{
        val -= 2;
        lval += 5;
    }

    if(pid == 0){
        cout << "val:" << val << ", lval = " << lval << endl;
    }else{
        cout << "val:" << val << ", lval = " << lval << endl;
    }
    return 0;
}
```

对于父进程而言，fork()函数返回子进程的ID（子进程的PID）；而对于子进程而言，fork函数返回0。

**僵尸进程**

父进程创建子进程后，子进程运行终止时刻（例如，调用`exit()`函数，或者运行到`main`中的`return`语句时，都会将返回的值传递给 操作系统），此时如果父进程还在运行，子进程并不会立即被销毁，直到这些值传到了产生该子进程的父进程。也就是说，如果父进程没有主动要求获得子进程的结束状态值，操作系统就会一直保存该进程的相关信息，并让子进程长时间处于僵尸状态，例如下面程序：

```C++
int main(){
    pid_t pid = fork();
    if(pid == 0){
        cout << "I am a Child Process." <<endl;
    }else{
        cout << "I am a Father Process and Child Process is " << pid << endl;
        sleep(30);  //让父进程休眠30秒，此时便于观察子进程的状态
    }
    if(pid == 0){
        cout << " Child Process exits " << endl;
    }else{
        cout << "Father Process exits " << endl;
    }
    return 0;
}
```

此时，运行该程序，查看后台进程可知：

```shell
gqx@gqx-Lenovo-Product:~$ ps -au
USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
root       923  0.6  0.9 480840 159824 tty7    Ssl+ 4月09  36:07 /usr/lib/xorg/
root      1351  0.0  0.0  17676  1768 tty1     Ss+  4月09   0:00 /sbin/agetty -
...
gqx      24856  0.0  0.0      0     0 pts/11   Z+   11:03   0:00 [tes16] <defunct>
gqx      24859  0.0  0.0  39104  3300 pts/3    R+   11:03   0:00 ps -au
```

**僵尸进程的消除**

方法一：调用`wait()`函数：

```C++
/* Wait for a child to die.  When one does, put its status in *STAT_LOC
   and return its process ID.  For errors, return (pid_t) -1.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
extern __pid_t wait (__WAIT_STATUS __stat_loc);
```

成功返回终止的进程ID，失败返回-1；子进程的最终返回值将指向该函数参数所指向的内存空间，但函数所指向的内存单元总还含有其他的信息，需要使用宏进行分离。

```C++
# define WIFEXITED(status)	__WIFEXITED (__WAIT_INT (status))  //子进程正常终止返回"true"
# define WEXITSTATUS(status)	__WEXITSTATUS (__WAIT_INT (status)) //返回子进程的返回值
```

要注意的是：**如果没有已终止的子进程，那么程序将被阻塞，直到有子进程终止。**

方法二：调用`waitpid()`函数

```C++
/* Wait for a child matching PID to die.
   If PID is greater than 0, match any process whose process ID is PID.
   If PID is (pid_t) -1, match any process.
   If PID is (pid_t) 0, match any process with the
   same process group as the current process.
   If PID is less than -1, match any process whose
   process group is the absolute value of PID.
   If the WNOHANG bit is set in OPTIONS, and that child
   is not already dead, return (pid_t) 0.  If successful,
   return PID and store the dead child's status in STAT_LOC.
   Return (pid_t) -1 for errors.  If the WUNTRACED bit is
   set in OPTIONS, return status for stopped children; otherwise don't.

   This function is a cancellation point and therefore not marked with
   __THROW.  */
extern __pid_t waitpid (__pid_t __pid, int *__stat_loc, int __options);
```

第一个参数：如果`__pid`的值是-1，则与`wait()`函数相同，可以等待任意的子程序终止；如果是0，则等待进程组识别码与目前进程相同的任何子进程；如果pid>0，则等待任何子进程识别码为 pid 的子进程。

第二个参数：与前一个函数`wait()`的参数意义相同。

第三个参数：常用WNOHANG——若pid指定的子进程没有结束，则waitpid()函数返回0，不予以等待。若结束，则返回该子进程的ID。

示例程序如下：

```C++
#include <iostream>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
using namespace std;

void read_childproc(int sig){
    int status;
    pid_t id = waitpid(-1, &status, WNOHANG);
    if(WIFEXITED(status)){
        printf("Remove proc id: %d \n", id);
        printf("Child send: %d \n", WEXITSTATUS(status));
    }
}

int main(){
    pid_t pid;
    struct sigaction act;
    act.sa_handler = read_childproc;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;
    sigaction(SIGCHLD, &act, 0);

    pid = fork();

    if(pid == 0){
        puts("Hi, I am a child process!");
        sleep(6);
        return 12;
    }else{
        printf("Child proc id: %d \n", pid);
        pid = fork();
        if(pid == 0){
            puts("Hi, I am a child process!");
            sleep(13);
            exit(24);
        }else{
            int i;
            printf("Child proc id: %d \n", pid);
            for(i  = 0; i < 4; i++){
                puts("wait...");
                sleep(5);
            }
        }
    }
    return 0;
}
```

## 2. 信号处理 

#### 理解概念

​	可以用来处理进程间的异步事件——即进程间可以通过系统调用来发送信号，只是告知某进程发生了什么事，使得被告知的进程去做对应的事件（信号处理），要注意的是，发送信号的过程并不会传送任何数据。通过`kill -l`可以看到信号的名字和序号。

可以通过这个案例来说明：

​	在终端运行`top`来查看系统运行的一些相关信息，可以看到终端的数据一直是变化的，同事通过`ps -aux|grep top`来查看现在系统是否正在运行该指令，可以得到运行该指令的进程号，然后用`kill -9 进程号`将该进程杀掉，我们此时通过`ps -aux|grep top`来发现此时`top`的运行相关信息已经没有了。这个过程就是一个进程给另一个进程发送了` SIGKILL`的信号。（注意：`kill`，就是送出一个特定的信号给某个进程，而该进程根据信号做出相应的动作（`sigqueue`也是），`-9`可以通过`kill -l` 看出是`SIGKILL`）

```shell
gqx@gqx-Lenovo-Product:~$ kill -l
 1) SIGHUP	 2) SIGINT	 3) SIGQUIT	 4) SIGILL	 5) SIGTRAP
 6) SIGABRT	 7) SIGBUS	 8) SIGFPE	 9) SIGKILL	10) SIGUSR1
 ......
```

#### 信号处理方式

一般信号的处理可以分为三种：

* 忽略信号  

  ​	大多数信号可以使用这个方式来处理，但是有两种信号不能被忽略（分别是 `SIGKILL`和`SIGSTOP`）。因为他们向内核和超级用户提供了进程终止和停止的可靠方法，如果忽略了，那么这个进程就变成了没人能管理的的进程，显然是内核设计者不希望看到的场景

  * 捕捉信号

    ​	需要告诉内核，用户希望如何处理某一种信号，说白了就是写一个信号处理函数，然后将这个函数告诉内核。当该信号产生时，由内核来调用用户自定义的函数，以此来实现某种信号的处理。

  * 系统的默认动作

    ​	对于每个信号来说，系统都对应由默认的处理动作，当发生了该信号，系统会自动执行。不过，对系统来说，大部分的处理方式都比较粗暴，就是直接杀死该进程。

处理方式转载自**[Linux 信号（signal）](https://www.jianshu.com/p/f445bfeea40a)**

#### 信号处理的注册函数

**1. signal函数**

```C++
/* Type of a signal handler.  */
typedef void (*__sighandler_t) (int);
/* Set the handler for the signal SIG to HANDLER, returning the old
   handler, or SIG_ERR on error.
   By default `signal' has the BSD semantic.  */
extern __sighandler_t signal (int __sig, __sighandler_t __handler);
```

第一个参数表示信号量类型（对应的`kill -l`里的数据），第二个参数则表示该进程被告知该信号后的处理函数。参考案例如下：

```C++
#include <iostream>
#include <list>
#include <unistd.h>
#include <sys/wait.h>
using namespace std;
void timeout(int sig){
    if(sig == SIGALRM){
        puts("Time out!");
    }
    alarm(2);
}

void keycontrol(int sig){
    if(sig == SIGINT){
        puts("CTRL + C pressed");
    }
}

int main(){
    int  i;
    signal(SIGALRM, timeout);   //到达通过了alarm函数设置的时间，调用函数timeout
    signal(SIGINT, keycontrol); //键盘键入Ctrl+后，调用keycontrol函数
    alarm(2);

    for(i = 0; i < 6; i++){
        puts("wait...");
        sleep(100);
    }
    return 0;
}
```

​	这段代码要注意的是，在`signal`中注册信号函数后，调用信号函数的则是**操作系统**，但进程处于睡眠状态的时间为100s，而alarm函数等待的时间是2秒，即2秒后会产生`SIGALRM`信号，此时将唤醒处于休眠状态的进程，而且进程一旦被唤醒，则不会再进入休眠状态，所以上述程序运行时间比较短。

**2. sigaction**

该函数已经完全取代了上述`signal`函数。

```C++
struct sigaction {
   void       (*sa_handler)(int); //信号处理程序，不接受额外数据，SIG_IGN 为忽略，SIG_DFL 为默认动作
   void       (*sa_sigaction)(int, siginfo_t *, void *); //信号处理程序，能够接受额外数据和sigqueue配合使用
   sigset_t   sa_mask;//阻塞关键字的信号集，可以再调用捕捉函数之前，把信号添加到信号阻塞字，信号捕捉函数返回之前恢复为原先的值。
   int        sa_flags;//影响信号的行为SA_SIGINFO表示能够接受数据
 };
/* Get and/or set the action for signal SIG.  */
extern int sigaction (int __sig, const struct sigaction *__restrict __act,
		      struct sigaction *__restrict __oact) __THROW;
```

第一个参数表示信号量类型；第二个参数信号处理函数；第三个参数：通过此参数获取之前注册的信号处理函数指针，若不需要，则传递0；

程序示例如下，改程序用来消除由父进程产生的两个子进程会导致僵尸进程的产生的情况，当子进程的生命周期结束后，回收子进程的内存信息，而不用等到父进程结束才去回收销毁子进程：

```C++
#include <iostream>
#include <list>
#include <unistd.h>
#include <sys/wait.h>
using namespace std;
void read_childproc(int sig){
    int status;
    pid_t id = waitpid(-1, &status, WNOHANG);   //消灭子进程结束后产生的僵尸进程
    if(WIFEXITED(status)){
        printf("Remove proc id: %d \n", id);
        printf("Child send: %d \n", WEXITSTATUS(status));
    }
}

int main(){
    pid_t pid;
    struct sigaction act;
    act.sa_handler = read_childproc;
    sigemptyset(&act.sa_mask);  //将sa_mask所有位初始化为0（初始化sa_mask中传入的信号集，清空其中所有信号）
    act.sa_flags = 0;
    sigaction(SIGCHLD, &act, 0);    //SIGCHLD 子进程结束信号
    pid = fork();
    if(pid == 0){
        puts("Hi, I am a child process!");
        sleep(6);
        return 12;
    }else{
        printf("Child proc id: %d \n", pid);
        pid = fork();
        if(pid == 0){
            puts("Hi, I am a child process!");
            sleep(13);
            exit(24);
        }else{
            int i;
            printf("Child proc id: %d \n", pid);
            for(i  = 0; i < 4; i++){
                puts("wait...");
                sleep(5);
            }
        }
    }
    return 0;
}
```



## 3. 进程间通信

进程间通信（IPC，InterProcess Communication）是指在不同进程之间传播或交换信息。进程间通信的方式有如下几种：

### 1.管道通信

**特点：**

1. 管道只允许具有血缘关系的进程间通信，如父子进程间的通信。

2. 它是半双工的（即数据只能在一个方向上流动），具有固定的读端和写端。
3. 管道并非是进程所有的资源，而是和套接字一样，归操作系统所有。可以将它看成文件系统，但该文件系统只存在于内存当中。

**原型**

```C++
#include <unistd.h>
/* Create a one-way communication channel (pipe).
   If successful, two file descriptors are stored in PIPEDES;
   bytes written on PIPEDES[1] can be read from PIPEDES[0].
   Returns 0 if successful, -1 if not.  */
extern int pipe (int fd[2]) __THROW __wur;
```

参数的说明：

​	字符数组fd是管道传输或者接收时用到的文件描述符，其中`fd[0]`是接收的时候使用的文件描述符，即管道出口；而`fd[1]`是传输的时候用到的文件描述符，即管道入口。

​	为了使数据可以双向传递，可以使用两个管道，一个管道负责进程1的写和进程2的读，另一个管道负责一个进程1的读和进程2的写。测试程序如下：

```C++
#include <iostream>
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>
#define BUF_SIZE 30

int main(){
    int fds1[2], fds2[2];
    /*
     *注意：
     * 此处不能写char* str1 = "Who are you?";
     * 得到的sizeof(str1)等于8，该值实际上是指针的大小，并不是字符串数组的大小
     * 这个时候要用strlen()函数（strlen的唯一标准是找‘\0’）
     * 系统函数返回值是char *(#include <string.h>)类型的往往会在末尾加上'\0'。
     * 要注意的是未初始化的情况下，用strlen是不可行的
     **/
    char str1[] = "Who are you?";
    char str2[] = "Thank you for your message.";
    char buf[BUF_SIZE];
    pipe(fds1);     //创建两个管道
    pipe(fds2);

    pid_t pid = fork();
    if(pid == 0){
        write(fds1[1], str1, sizeof(str1));
        read(fds2[0], buf, BUF_SIZE);
        printf("Child process copy the message: %s\n", buf);
    }else {
        read(fds1[0], buf, BUF_SIZE);
        printf("Parent Process copy the message: %s\n", buf);
        write(fds2[1], str2, sizeof(str2));
    }
    return 0;
}
```



**2.FIFO**

​	FIFO即命名管道，**在磁盘上有对应的节点，但没有数据块**—换言之，只是拥有一个名字和相应的访问权限，通过`mknode()`系统调用或者`mkfifo()`函数来建立的。一旦建立，任何进程都可以通过文件名将其打开和进行读写，而不局限于父子进程，当然前提是进程对FIFO有适当的访问权。当不再被进程使用时，FIFO在内存中释放，但磁盘节点仍然存在。

```C++
/* Create a new FIFO named PATH, with permission bits MODE.  */
extern int mkfifo (const char *__path, __mode_t __mode)
     __THROW __nonnull ((1));
```

其中的 mode 参数与`open`函数中的 mode 相同。一旦创建了一个 FIFO，就可以用一般的文件I/O函数操作它。

当 open 一个FIFO时，是否设置非阻塞标志（`O_NONBLOCK`）的区别：

* 若没有指定`O_NONBLOCK`（默认），以只读方式打开的FIFO要阻塞到其他的某个程序以写打开这个FIFO。同样以只写方式打开的FIFO要阻塞到其他某个进程以读方式打开该FIFO。
* 若指定了`O_NONBLOCK`，则以只读方式打开会立刻返回而不阻塞（不是出错返回）。而以只写方式打开，若之前没有进程以读方式打开这个FIFO则立刻出错返回。

示例代码：一个进程发送消息给另一个进程

writefifo.cpp

```C++
#include <iostream>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <fcntl.h>  // O_WRONLY
#include <time.h>   //time
#include <unistd.h>
using namespace std;

int main(){
    int fd;
    int n;
    char buf[1024];
    time_t tp;

    if( mkfifo("fifo1",  0666) < 0 && errno != EEXIST) //创建FIFO管道
    {
        perror("Create FIFO Faileed");
    }

    printf("I am %d process.\n", getpt());  //说明进程的ID

    if((fd = open("fifo1",O_WRONLY )) < 0){   //以只写方式打开FIFO
        perror("Open FIFO failed");
        exit(1);
    }
    for (int i = 0; i < 10; ++i) {
        time(&tp);  //获取当前系统时间
        n = sprintf(buf, "Process %d's time is %s",getpid(), ctime(&tp));
        printf("send message: %s", buf);
        if(write(fd, buf, n+1) < 0)
        {
            perror("write FIFO Failed");
            close(fd);
            exit(1);
        }
        sleep(1);
    }
    close(fd);
    return 0;
}

```

readfifo.cpp

```C++
#include <stdio.h>
#include <iostream>
#include <stdlib.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>  // O_WRONLY
#include <time.h>   //time
#include <unistd.h>
using namespace std;

int main(){
    int fd;
    int len;
    char buf[1024];
    if((fd = open("fifo1",  O_RDONLY)) < 0){   //以只读方式打开FIFO
        perror("Open FIFO failed");
        exit(1);
    }
    while ((len = read(fd, buf ,1024)) > 0)   //读取FIFO管道
    {
       printf("Read message: %s", buf);
    }

    close(fd);
    return 0;
}

```

如果在`writefifo.cpp`中修改如下，设置非阻塞标志：

```C++
if((fd = open("fifo1",O_WRONLY | O_NONBLOCK)) < 0){   //以只写方式打开FIFO
```

如果先运行writefifo，在运行readfifo，则会出错。

**3. 消息队列**

​	消息队列，就是一个消息的链表，是一系列保存在**内核**的列表。用户进程可以向消息队列添加消息，也可以向消息队列读取消息。

特点：

* 队列独立于发送与接收进程。进程终止时，消息队列及其内容并不删除。
* 消息队列可以实现消息的随机查询,消息不一定要以先进先出的次序读取,也可以按消息的类型读取。

**4.共享内存**

​	共享内存（Shared Memory），指两个或多个进程共享一个给定的存储区。

**5. 信号量**

​	信号量是一个计数器，可以用来控制多个进程对共享资源的访问。它常作为一种锁机制，防止某进程正在访问共享资源时，其他进程也访问该资源。因此，主要作为进程间以及同一进程内不同线程之间的同步手段。

**6.套接字**

​	套解口也是一种进程间通信机制，与其他通信机制不同的是，它可用于不同及其间的进程通信。



## 参考资料

[进程间通信的方式——信号、管道、消息队列、共享内存](https://www.cnblogs.com/LUO77/p/5816326.html)

[进程间的五种通信方式介绍](https://www.cnblogs.com/zgq0/p/8780893.html)

[linux signal 用法和注意事项](https://www.cnblogs.com/lidabo/p/4581026.html)

[Linux 信号（signal）](https://www.jianshu.com/p/f445bfeea40a)

