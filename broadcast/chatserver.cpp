#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#define BUF_SIZE 1024
#define MAX_CLNT 256
void errorhandling(char *message);
void send_msg(char *msg, int len);
void* handle_clnt(void* arg);
int clnt_cnt = 0;
int clnt_socks[MAX_CLNT];
pthread_mutex_t mutex_;

int main(int argc, char *argv[]){
    int serv_sock, clnt_sock;
    struct sockaddr_in serv_adr, clnt_adr;
    socklen_t clnt_adr_sz;
    pthread_t t_id;
    if(argc != 2){
        printf("Usage: %s <port> \n", argv[0]);
        exit(1);
    }
    pthread_mutex_init(&mutex_, NULL);
    serv_sock=socket(AF_INET, SOCK_STREAM, 0);

    memset(&serv_adr, 0, sizeof(serv_adr));
    serv_adr.sin_family = AF_INET;
    serv_adr.sin_addr.s_addr = htonl(INADDR_ANY);
    serv_adr.sin_port = htons(atoi(argv[1]));
    if(bind(serv_sock, (struct sockaddr*)&serv_adr, sizeof(serv_adr)) == -1){
        errorhandling("bind error.");
    }
    if(listen(serv_sock, 5) == -1){
        errorhandling("listen error.");
    }
    while(1){
        clnt_adr_sz = sizeof(clnt_adr);
        clnt_sock = accept(serv_sock, (struct sockaddr*)&clnt_adr, &clnt_adr_sz);

        pthread_mutex_lock(&mutex_);
        clnt_socks[clnt_cnt++] = clnt_sock;
        pthread_mutex_unlock(&mutex_);

        pthread_create(&t_id, NULL, handle_clnt, (void*)&clnt_sock);
        pthread_detach(t_id);
        printf("Connected Client ip: %s\n", inet_ntoa(clnt_adr.sin_addr));
    }
    close(serv_sock);
    return 0;
}


void* handle_clnt(void* arg){
    int clnt_sock = *((int*)arg);
    int str_len = 0;
    char msg[BUF_SIZE];
    //while函数表示相应的连接一直处于建立状态，一旦客户端发送EOF过来，则要开始该客户端的连接
    while((str_len = read(clnt_sock, msg, sizeof(msg))) != 0){
        send_msg(msg, strlen(msg));
    }
    pthread_mutex_lock(&mutex_);
    //断开相应的客户端连接
    for(int i = 0; i < clnt_cnt; i++){
        if(clnt_sock == clnt_socks[i]){
            while( i++ < clnt_sock -1){
                clnt_socks[i] = clnt_socks[i+1];
            }
            break;
        }
    }
    clnt_sock--;
    pthread_mutex_unlock(&mutex_);
    close(clnt_sock);
    printf("called..........\n");
    return NULL;
}

void send_msg(char *msg, int len){
    pthread_mutex_lock(&mutex_);
    for (int i = 0; i < clnt_cnt; ++i) {
        write(clnt_socks[i], msg, len);
    }
    pthread_mutex_unlock(&mutex_);
}

void errorhandling(char* message){
    fputs(message, stderr);
    fputc('\n', stderr);
    exit(1);
}
