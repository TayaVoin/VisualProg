import zmq
import json
import time

PORT = 50123  # новый порт

context = zmq.Context()
socket = context.socket(zmq.REP)
socket.bind(f"tcp://*:{PORT}")
socket.RCVTIMEO = 5000
print(f"Server started on tcp://*:{PORT}")

counter = 0

while True:
    try:
        msg = socket.recv()
    except zmq.Again:
        print("No message in 5 seconds")
        continue

    counter += 1
    text = msg.decode("utf-8")
    print(f"Received #{counter}: {text[:120]}...")

    data = {
        "counter": counter,
        "received_at": time.time(),
        "payload": json.loads(text)
    }
    with open("measurements.jsonl", "a", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False) + "\n")

    socket.send(b"Hello from Server!")