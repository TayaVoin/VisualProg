import socket
import threading
import time

def udp_server_thread():
    udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp_address = ('localhost', 8080)
    udp_socket.bind(udp_address)
    
    print(f"UDP сервер запущен на {udp_address[0]}:{udp_address[1]}")
    print("UDP сервер ожидает данные...")
    
    try:
        while True:
            data, client_address = udp_socket.recvfrom(1024)
            
            if data:
                print(f"UDP: Получены данные от {client_address}: {data.decode('utf-8')}")
                
                response = "Hello from UDP server"
                udp_socket.sendto(response.encode('utf-8'), client_address)
                print(f"UDP: Ответ отправлен клиенту {client_address}")
                
    except Exception as e:
        print(f"UDP сервер ошибка: {e}")
    finally:
        udp_socket.close()

def tcp_client_thread(client_socket, client_address):
    try:
        print(f"TCP: Обработка клиента {client_address}")
        
        data = client_socket.recv(1024)
        if data:
            print(f"TCP: Получены данные от {client_address}: {data.decode('utf-8')}")
            
            response = "Hello from TCP server"
            client_socket.sendall(response.encode('utf-8'))
            print(f"TCP: Ответ отправлен клиенту {client_address}")
            
    except Exception as e:
        print(f"TCP клиент ошибка: {e}")
    finally:
        client_socket.close()
        print(f"TCP: Соединение с {client_address} закрыто")

def tcp_server_thread():
    tcp_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    tcp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    tcp_address = ('localhost', 8080)
    tcp_socket.bind(tcp_address)
    tcp_socket.listen(5)
    
    print(f"TCP сервер запущен на {tcp_address[0]}:{tcp_address[1]}")
    print("TCP сервер ожидает подключения...")
    
    try:
        while True:
            client_socket, client_address = tcp_socket.accept()
            print(f"TCP: Клиент подключен с {client_address}")
            
            client_thread = threading.Thread(
                target=tcp_client_thread, 
                args=(client_socket, client_address)
            )
            client_thread.daemon = True
            client_thread.start()
            
    except Exception as e:
        print(f"TCP сервер ошибка: {e}")
    finally:
        tcp_socket.close()

def main():
    print("Запуск сервера (TCP и UDP на порту 8080)...")
    print("Сервер запущен. Нажмите Ctrl+C для остановки.\n")
    
    tcp_thread = threading.Thread(target=tcp_server_thread)
    tcp_thread.daemon = True
    
    udp_thread = threading.Thread(target=udp_server_thread)
    udp_thread.daemon = True
    
    try:
        tcp_thread.start()
        udp_thread.start()
        
        print("Оба сервера запущены в отдельных потоках")
        print("-" * 50)
        
        while True:
            time.sleep(1)
            
    except KeyboardInterrupt:
        print("\nОстановка сервера...")
    except Exception as e:
        print(f"Ошибка: {e}")
    finally:
        print("Сервер остановлен")

if __name__ == "__main__":
    main()