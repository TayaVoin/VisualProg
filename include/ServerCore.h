#ifndef SERVER_CORE_H
#define SERVER_CORE_H

#include <memory>
#include <atomic>
#include <thread>
#include <mutex>
#include <vector>
#include <unordered_map>
#include <libpq-fe.h>
#include "DataModels.h"

class ServerCore {
public:
    ServerCore();
    ~ServerCore();

    // Запуск ZMQ сервера (REP) и инициализация БД
    bool start();
    void stop();

    // Получить последнее измерение и историю (для GUI)
    Measurement getLastMeasurement() const;
    bool hasNewData();
    const std::vector<float>& getCachedRsrpHistory() const;

    // Загрузить агрегированные точки из БД (каждые AGGREGATION_STEP записей)
    std::vector<AggregatedPoint> loadAggregatedPoints();

    void addMeasurementToHistory(const Measurement& m);
    const std::unordered_map<int, PciHistory>& getHistoryByPci() const;
    std::vector<int> getAvailablePciList() const;

    const std::vector<float>& getCachedRsrqHistory() const;

private:
    void serverLoop();
    bool initDatabase();
    bool createTable();
    bool saveToDatabase(const Measurement& m);
    void saveToJsonFile(const Measurement& m);
    Measurement parseJson(const std::string& jsonStr);

    // ZMQ
    std::atomic<bool> m_running{false};
    std::unique_ptr<std::thread> m_thread;
    void* m_zmqSocket = nullptr;   // void* чтобы не тянуть zmq.hpp в заголовок
    void* m_zmqContext = nullptr;

    // Данные
    mutable std::mutex m_mutex;
    Measurement m_lastMeasurement;
    std::vector<Measurement> m_history;
    std::atomic<bool> m_hasNewData{false};

    // PostgreSQL
    PGconn* m_dbConn = nullptr;

    mutable std::mutex m_plotMutex;
    std::vector<float> m_cachedRsrpHistory;
    std::vector<float> m_cachedRsrqHistory;

    std::vector<std::string> m_pendingJson;   // буфер непросохранённых JSON
    static const size_t JSON_BUFFER_SIZE = 10; // сбрасывать каждые 10 записей
    void flushJsonBuffer();                   // метод для сброса буфера в файл

    std::unordered_map<int, PciHistory> m_historyByPci;
    std::vector<long long> m_globalTimestamps; // общая временная шкала
    mutable std::mutex m_historyMutex;

    bool loadHistoryFromDatabase();   // загрузить историю из БД в m_history и кэши
};

#endif