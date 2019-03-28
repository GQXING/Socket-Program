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

int main(int argc, char *argv[])
{
    int sock;
    struct sockaddr_in serv_addr;
    char message[BUF_SIZE];
    int str_len = 0 ,idx = 0, read_len = 0;

    if(argc != 3){
        cout << "Usage : " << argv[0] << "<IP> <port> "<<endl;
        exit(0);
    }

    sock = socket(PF_INET, SOCK_STREAM, 0);
    if(sock == -1){
        errorhandling("socket() error;");
    }

    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = inet_addr(argv[1]);
    serv_addr.sin_port = htons(atoi(argv[2]));

    if(connect(sock, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) == -1){
        errorhandling("connect error!");
    }else{
        cout << "connected....." << endl;
    }

    while(1){
        cout << "Input Q/q to exit." << endl;
        fgets(message, BUF_SIZE, stdin);
        if(!strcmp(message, "q\n") || !strcmp(message, "Q\n"))
            break;
        write(sock, message, strlen(message));
        str_len = read(sock, message, BUF_SIZE - 1);
        message[str_len] = '\0';
        printf("Message from server : %s", message);
    }

    close(sock);

    /*
    while(read_len =read(sock, &message[idx++], 1)){
        if(read_len == -1){
            errorhandling("read error");
        }
        str_len += read_len;
    }

    cout << "message from server: " << message <<endl;
    cout << "read function call " << str_len << "times" <<endl;
    */
    close(sock);
    return 0;
}

void errorhandling(char *message){
    fputs(message, stderr);
    fputc('\n', stderr);
    exit(1);
}
