import socket

def main():
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    try:
        #server_address = ('localhost', 8080)
        server_address = ('localhost', 8081)
        
        message = "Привет, UDP сервер! Это тестовое сообщение."
        client_socket.sendto(message.encode('utf-8'), server_address)
        print(f"Отправлено сообщение: {message}")
        
        data, server = client_socket.recvfrom(1024)
        print(f"Получен ответ от сервера {server}: {data.decode('utf-8')}")
        
    except Exception as e:
        print(f"Ошибка: {e}")
    finally:
        client_socket.close()
        print("Сокет закрыт")

if __name__ == "__main__":
    main()