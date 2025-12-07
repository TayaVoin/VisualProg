#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <sys/time.h>

//#define PORT 8080
#define PORT 8081
#define BUFFER_SIZE 1024
#define SERVER_IP "127.0.0.1"

int main() {
    int sock;
    struct sockaddr_in serv_addr;
    socklen_t addr_len;  // Исправлено: socklen_t вместо int
    char *hello = "Hello from UDP client";
    char buffer[BUFFER_SIZE] = {0};

    if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        printf("Socket creation error\n");
        return -1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);
    inet_pton(AF_INET, SERVER_IP, &serv_addr.sin_addr);

    addr_len = sizeof(serv_addr);  // Правильная инициализация

    printf("UDP client started. Sending to %s:%d\n", SERVER_IP, PORT);

    int bytes_sent = sendto(sock, hello, strlen(hello), 0, 
                           (struct sockaddr*)&serv_addr, addr_len);
    if (bytes_sent < 0) {
        printf("Send failed\n");
        close(sock);
        return -1;
    }

    printf("Message sent: %s\n", hello);

    // Таймаут 5 секунд
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0) {
        printf("Set timeout failed\n");
    }

    int bytes_received = recvfrom(sock, buffer, BUFFER_SIZE - 1, 0, 
                                 (struct sockaddr*)&serv_addr, &addr_len);
    if (bytes_received < 0) {
        printf("Receive timeout: no response from server\n");
    } else {
        buffer[bytes_received] = '\0';
        printf("Server response: %s\n", buffer);
    }

    close(sock);
    return 0;
}
