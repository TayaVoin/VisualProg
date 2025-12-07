#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>

#define TCP_PORT 8080
#define UDP_PORT 8081
#define BUFFER_SIZE 1024

void* tcp_server(void* arg) {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int opt = 1;
    socklen_t addrlen = sizeof(address);
    char buffer[BUFFER_SIZE] = {0};
    char *response = "Hello from TCP server";

    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("TCP socket failed");
        return NULL;
    }

    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(TCP_PORT);

    bind(server_fd, (struct sockaddr *)&address, sizeof(address));
    listen(server_fd, 3);
    printf("TCP server listening on port %d\n", TCP_PORT);

    while (1) {
        new_socket = accept(server_fd, (struct sockaddr *)&address, &addrlen);
        if (new_socket >= 0) {
            int valread = read(new_socket, buffer, BUFFER_SIZE);
            buffer[valread] = '\0';
            printf("TCP Client: %s\n", buffer);
            send(new_socket, response, strlen(response), 0);
            close(new_socket);
        }
    }
    close(server_fd);
    return NULL;
}

void* udp_server(void* arg) {
    int sockfd;
    struct sockaddr_in serv_addr, cli_addr;
    socklen_t clilen;
    char buffer[BUFFER_SIZE];

    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        perror("UDP socket failed");
        return NULL;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(UDP_PORT);

    bind(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
    printf("UDP server listening on port %d\n", UDP_PORT);

    while (1) {
        clilen = sizeof(cli_addr);
        int n = recvfrom(sockfd, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&cli_addr, &clilen);
        buffer[n] = '\0';
        printf("UDP Client: %s\n", buffer);
        sendto(sockfd, "Hello from UDP server", 21, 0, (struct sockaddr *)&cli_addr, clilen);
    }
    close(sockfd);
    return NULL;
}

int main() {
    pthread_t tcp_thread, udp_thread;

    printf("Starting combined TCP/UDP server...\n");

    pthread_create(&tcp_thread, NULL, tcp_server, NULL);
    pthread_create(&udp_thread, NULL, udp_server, NULL);

    pthread_join(tcp_thread, NULL);
    pthread_join(udp_thread, NULL);

    return 0;
}
