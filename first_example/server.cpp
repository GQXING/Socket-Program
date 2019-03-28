#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
using namespace std;

#define BUF_SIZE 1024

void errorhandling(char *message);

int main(int argc, char *argv[]){

    int serv_socket;
    int clnt_socket;
    int id = 1, strlen;
    struct sockaddr_in serv_addr;
    struct sockaddr_in clnt_addr;
    socklen_t clnt_addr_size;

    char message[BUF_SIZE];

    if(argc != 2){
        cout << "Usage : " << argv[0] << " <port> "<<endl;
        exit(0);
    }


    serv_socket = socket(PF_INET, SOCK_STREAM, 0);
    if(serv_socket == -1){
        errorhandling("socket() error!");
    }

    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);  //自动获取计算机的IP地址
    serv_addr.sin_port = htons(atoi(argv[1]));  //atoi (表示ascii to integer)是把字符串转换成整型数的一个函数

    if(bind(serv_socket, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) == -1){
        errorhandling("bind() error");
    }
    if(listen(serv_socket, 5) == -1){
        errorhandling("listen() error");
    }
    clnt_addr_size = sizeof(clnt_addr);

    //处理五次连接请求
    for (int i = 0; i < 5; ++i) {
        clnt_socket = accept(serv_socket, (sockaddr*)&clnt_addr, &clnt_addr_size);
        if(clnt_socket == -1)
            errorhandling("accept error");
        else{
            cout << "Conneted client " << id << endl;

        }

        while((strlen=read(clnt_socket, message, BUF_SIZE)) != 0){
            printf("Message from client : %s", message);
            write(clnt_socket, message, strlen);
            memset(message, 0, sizeof(message));
        }
        id++;
        close(clnt_socket);
    }
    /*
    clnt_socket = accept(serv_socket, (struct sockaddr*)&clnt_addr, &clnt_addr_size);
    if(clnt_socket == -1){
        errorhandling("accept error");
    }

    write(clnt_socket, message, sizeof(message));
    */
    close(serv_socket);
    return 0;
}

void errorhandling(char *message){
    fputs(message, stderr);
    fputc('\n', stderr);
    exit(1);
}
