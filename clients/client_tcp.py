import socket

def main():
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    
    try:
        server_address = ('localhost', 8080)
        print(f"Подключаюсь к серверу {server_address[0]}:{server_address[1]}")
        client_socket.connect(server_address)
        
        message = "Привет, сервер! Это тестовое сообщение от клиента."
        client_socket.sendall(message.encode('utf-8'))
        print(f"Отправлено сообщение: {message}")
        
        data = client_socket.recv(1024)
        print(f"Получен ответ от сервера: {data.decode('utf-8')}")
        
    except Exception as e:
        print(f"Ошибка: {e}")
    finally:
        client_socket.close()
        print("Соединение закрыто")

if __name__ == "__main__":
    main()