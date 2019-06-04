#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <pthread.h>

#define BUF_SIZE 1024
#define SMALL_BUF 100


void* request_handler(void  *arg);
void send_data(FILE* fp, char* ct, char* file_name);
char* content_type(char *file);
void* send_error(FILE* fp);
void error_handling(char *msg);


int main(int argc, char *argv[])
{
    if(argc != 2){
        printf("Usage : %s <port> \n", argv[0]);
        exit(1);
    }
    int serv_sock, clnt_sock;
    serv_sock = socket(PF_INET, SOCK_STREAM, 0);
    struct sockaddr_in serv_adr, clnt_adr;
    memset(&serv_adr, 0, sizeof(serv_sock));
    serv_adr.sin_addr.s_addr =htonl(INADDR_ANY);
    serv_adr.sin_family = AF_INET;
    serv_adr.sin_port = htons(atoi(argv[1]));
    if(bind(serv_sock, (struct sockaddr*)&serv_adr, sizeof(serv_adr)) == -1){
        error_handling("bind() error");
    }

    if(listen(serv_sock, 5) == -1){
        error_handling("listen() error");
    }

    socklen_t clnt_len;
    pthread_t t_id;
    while(1){
        clnt_len = sizeof(clnt_adr);
        clnt_sock = accept(serv_sock, (struct sockaddr*)&clnt_adr, &clnt_len);
        printf("Connected request: %s %d\n", inet_ntoa(clnt_adr.sin_addr), ntohs(clnt_adr.sin_port));
        pthread_create(&t_id, NULL, request_handler, &clnt_sock);
        pthread_detach(t_id);
    }
    close(serv_sock);
    return 0;
}


void* request_handler(void  *arg){
    int clnt_sock = *((int*)arg);
    char req_line[SMALL_BUF];
    FILE* clnt_read;
    FILE* clnt_write;

    char method[10];
    char ct[15];
    char file_name[30];
    clnt_read = fdopen(clnt_sock, "r");
    clnt_write = fdopen(dup(clnt_sock),"w");


    fgets(req_line, SMALL_BUF, clnt_read);
    if(strstr(req_line, "HTTP/") == NULL){
        send_error(clnt_write);
        fclose(clnt_read);
        fclose(clnt_write);
        return NULL;
    }
    //strtok参数req_line指向欲分割的字符串，参数\则为分割字符串中包含的所有字符。
    strcpy(method, strtok(req_line, "/"));
    //s为空值NULL，则函数保存的指针SAVE_PTR在下一次调用中将作为起始位置。
    strcpy(file_name, strtok(NULL, " "));
    strcpy(ct, content_type(file_name));
    if(strcmp(method, "GET ") != 0){
        send_error(clnt_write);
        fclose(clnt_read);
        fclose(clnt_write);
        return NULL;
    }
    fclose(clnt_read);
    send_data(clnt_write, ct, file_name);
}

void error_handling(char *msg){
    fputs(msg, stderr);
    fputc('\n', stderr);
    exit(1);
}

void send_data(FILE* fp, char * ct, char* file_name){
    //状态行
    char protocol[] ="HTTP/1.0 200 OK\r\n";
    //消息头
    char server[] = "Server:Linux Web Server \r\n";   //服务器名称
    char cnt_len[] = "Content-length:2048 \r\n";
    char cnt_type[SMALL_BUF];
    char buf[BUF_SIZE];
    FILE* send_file;
    sprintf(cnt_type, "Content-type:%s;charset=utf-8\r\n\r\n", ct);
    printf("\nfile_name...%s...\n",file_name);
    send_file = fopen(file_name, "r");
    if(send_file == NULL){
        send_error(fp);
        return;
    }

    /*传输头信息*/
    fputs(protocol, fp);
    fputs(server,fp);
    fputs(cnt_len, fp);
    fputs(cnt_type, fp);

    /*传输请求数据*/
    while(fgets(buf, BUF_SIZE, send_file) != NULL) {
        fputs(buf, fp);
        printf("........%s.........\n", buf);
        fflush(fp);
        printf("00000\n");
    }
    printf("1111\n");
    fflush(fp);
    fclose(fp);
}

void* send_error(FILE* fp){
    char protocol[] = "HTTP/1.0 400  Bad Request\r\n";
    char server[] = "Server:Linux Web Server \r\n";   //服务器名称
    char cnt_len[] = "Content-length:2048 \r\n";
    char cnt_type[] = "Content-type:text/html;charset=utf-8\r\n\r\n";
    char buf[] = "<html><head><title>Network</title></head><body><h1>发生错误，查看请求文件名和请求方式！</h1></body></html>";
    fputs(protocol, fp);
    fputs(server, fp);
    fputs(cnt_len, fp);
    fputs(cnt_type, fp);
    fputs(buf, fp);
    fflush(fp);
}

char* content_type(char *file){
    char externsion[SMALL_BUF];
    char file_name[SMALL_BUF];
    strcpy(file_name, file);
    strtok(file_name, ".");
    strcpy(externsion, strtok(NULL, "."));

    if(!strcmp(externsion, "html")|| !strcmp(externsion, "htm")){
        return "text/html";     //将文件的content-type设置为text/html的形式，浏览器在获取到这种文件时会自动调用html的解析器对文件进行相应的处理。
    }else{
        return "text/plain";    //文件设置为纯文本的形式，浏览器在获取到这种文件时并不会对其进行处理。
    }
}
