### 



## 编译指令

```shell
#服务端
gqx@gqx-Lenovo-Product:~/workplace/SocketServer$ g++ -o server main.cpp
gqx@gqx-Lenovo-Product:~/workplace/SocketServer$ ./server 9999

#客户端
gqx@gqx-Lenovo-Product:~/workplace/SocketClient$ g++ -std=c++11 -o client main.cpp
gqx@gqx-Lenovo-Product:~/workplace/SocketClient$ ./client 127.0.0.1 9999
```



该部分代码只是一个简单的回声测试，demo有点简陋。

