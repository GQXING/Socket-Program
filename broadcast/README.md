# 多客户端的信息交换

效果如下：

```
gcc -o server chatserver.cpp -lpthread
Connected Client ip: 127.0.0.1
Connected Client ip: 127.0.0.1
Connected Client ip: 127.0.0.1

gcc -o client client.cpp -lpthread

./client 127.0.0.1 9999 one
[three] nihao
[two] test
hhhh
[one] hhhh

./client 127.0.0.1 9999 two
[three] nihao
test
[two] test
[one] hhhh
....
```

##  Question & Summary

### printf

printf是一个行缓冲函数，先写到缓冲区，满足条件后，才将缓冲区刷到对应文件中，刷缓冲区的条件如下：

1. 缓冲区填满

2. 写入的字符中有‘\n’ '\r'

3. 调用fflush手动刷新缓冲区

4. 调用scanf要从缓冲区中读取数据时，也会将缓冲区内的数据刷新。

做如下实验：

```C++
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include<unistd.h>
using namespace std;

int main(int argc, char *argv[])
{
    printf("test");
    sleep(10);
    return 0;
}
```

10秒之后才打印test，中途Ctrl+C则不会打印test。如果打印语句换成以下形式：

```C++
 printf("test\n");
```

程序运行时刻打印test。

### 关于pthread_join和pthread_detach

- 调用pthread_join的线程会阻塞，直到指定的线程返回，调用了pthread_exit，或者被取消。调用pthread_join(pthread_id)后，如果该线程没有运行结束，调用者会被阻塞，在有些情况下我们并不希望如此，比如在Web服务器中当主线程为每个新来的链接创建一个子线程进行处理的时候，主线程并不希望因为调用pthread_join而阻塞
- 调用 pthread_detach(thread_id)（非阻塞，可立即返回） 这将该子线程的状态设置为detached,则该线程运行结束后会自动释放所有资源。 

### 线程同步

Linux下c语言开发时线程同步的方大致有两种——“互斥量”和“信号量”。程序中使用的是互斥量。

信号量则是生产者和消费者问题。下面案例：

```C++
#include<stdio.h>
#include<pthread.h>
#include<semaphore.h>

void *read(void *arg);
void *accumlate(void *arg);

static sem_t sem_one;
static sem_t sem_two;

static int num;

int main(){
    pthread_t id_t1, id_t2;
    sem_init(&sem_one, 0, 0);
    sem_init(&sem_two, 0, 1);

    pthread_create(&id_t1, NULL, read, NULL);
    pthread_create(&id_t2, NULL, accumlate, NULL);

    void* value;
    pthread_join(id_t1, NULL);
    pthread_join(id_t2, &value);

    printf("sum is %d\n", (int*)value);
    sem_destroy(&sem_one);
    sem_destroy(&sem_two);
    return 0;
}

void *read(void *arg){
    int i;
    for(i = 0; i < 5; i++){
        sem_wait(&sem_two);
        fputs("Input num: ", stdout);
        scanf("%d", &num);
        sem_post(&sem_one);
    }
    return NULL;
}

void *accumlate(void *arg){
    int sum = 0 ,i;
    for(i = 0; i < 5; i++){
        sem_wait(&sem_one);
        sum += num;
        sem_post(&sem_two);
    }
    return (void*)sum;
}
```

