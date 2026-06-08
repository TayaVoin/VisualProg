#include "ServerCore.h"
#include "Config.h"
#include <zmq.hpp>
#include <nlohmann/json.hpp>
#include <fstream>
#include <iostream>
#include <chrono>
#include <ctime>

using json = nlohmann::json;

ServerCore::ServerCore() {}
ServerCore::~ServerCore() {
    stop();
    flushJsonBuffer(); // добавить
}

bool ServerCore::start() {
    if (m_running) return false;
    if (!initDatabase()) {
        std::cerr << "Database init failed, continuing without DB" << std::endl;
    } else {
        // Загрузить историю для графиков
        loadHistoryFromDatabase();
    }
    // Создаём ZMQ сокет (REP)
    m_zmqContext = new zmq::context_t(1);
    zmq::socket_t* sock = new zmq::socket_t(*static_cast<zmq::context_t*>(m_zmqContext), ZMQ_REP);
    std::string addr = "tcp://" + ZMQ_HOST + ":" + std::to_string(ZMQ_PORT);
    sock->bind(addr);
    m_zmqSocket = sock; // сохраняем как void*, в потоке приведём обратно
    m_running = true;
    m_thread = std::make_unique<std::thread>(&ServerCore::serverLoop, this);
    return true;
}

void ServerCore::stop() {
    m_running = false;
    if (m_thread && m_thread->joinable()) m_thread->join();
    if (m_zmqSocket) {
        delete static_cast<zmq::socket_t*>(m_zmqSocket);
        m_zmqSocket = nullptr;
    }
    if (m_zmqContext) {
        delete static_cast<zmq::context_t*>(m_zmqContext);
        m_zmqContext = nullptr;
    }
    if (m_dbConn) {
        PQfinish(m_dbConn);
        m_dbConn = nullptr;
    }
}

void ServerCore::serverLoop() {
    zmq::socket_t* sock = static_cast<zmq::socket_t*>(m_zmqSocket);
    if (!sock) return;
    
    // Устанавливаем таймаут на приём (1000 мс), чтобы поток мог проверять m_running
    sock->set(zmq::sockopt::rcvtimeo, 1000);
    
    while (m_running) {
        zmq::message_t request;
        try {
            auto res = sock->recv(request, zmq::recv_flags::none);
            if (!res) {
                // Таймаут или ошибка – просто проверим m_running и продолжим
                continue;
            }
            std::cout << "DEBUG: Received " << request.size() << " bytes" << std::endl;
            std::string jsonStr(static_cast<char*>(request.data()), request.size());
            // Парсим JSON в Measurement
            Measurement m = parseJson(jsonStr);
            m.receivedTime = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count();

            // Сохраняем в память
            {
                std::lock_guard<std::mutex> lock(m_mutex);
                m_history.push_back(m);
                if (m_history.size() > MAX_HISTORY_SIZE)
                    m_history.erase(m_history.begin());
                m_lastMeasurement = m;
                m_hasNewData = true;
            }

            // Обновить кэш для графиков (только если изменилась история)
            {
                std::lock_guard<std::mutex> lock(m_plotMutex);
                m_cachedRsrpHistory.clear();
                m_cachedRsrqHistory.clear();
                m_cachedRsrpHistory.reserve(m_history.size());
                m_cachedRsrqHistory.reserve(m_history.size());
                for (const auto& meas : m_history) {
                    if (meas.hasRegisteredLte) {
                        m_cachedRsrpHistory.push_back((float)meas.registeredLteCell.rsrp);
                        m_cachedRsrqHistory.push_back((float)meas.registeredLteCell.rsrq);
                    } else if (!meas.lteCells.empty()) {
                        // fallback на первую соту, если зарегистрированной нет
                        m_cachedRsrpHistory.push_back((float)meas.lteCells[0].rsrp);
                        m_cachedRsrqHistory.push_back((float)meas.lteCells[0].rsrq);
                    } else {
                        // нет данных – заполняем -140 и -20 как значения по умолчанию
                        m_cachedRsrpHistory.push_back(-140.0f);
                        m_cachedRsrqHistory.push_back(-20.0f);
                    }
                }
            }

            // В БД
            if (m_dbConn) saveToDatabase(m);
            // В JSON файл (бэкап)
            saveToJsonFile(m);
            // Добавить в историю по PCI
            addMeasurementToHistory(m);

            // Отправляем ответ
            std::string reply = "OK";
            sock->send(zmq::buffer(reply), zmq::send_flags::none);
        } catch (const zmq::error_t& e) {
            // Игнорируем таймаут (EAGAIN), иначе выводим ошибку
            if (e.num() != EAGAIN) {
                std::cerr << "ZMQ error: " << e.what() << std::endl;
            }
        } catch (const std::exception& e) {
            std::cerr << "Server loop error: " << e.what() << std::endl;
        }
    }
}

Measurement ServerCore::parseJson(const std::string& jsonStr) {
    Measurement m;
    try {
        json j = json::parse(jsonStr);
        if (j.contains("location")) {
            auto& loc = j["location"];
            m.location.latitude = loc.value("latitude", 0.0);
            m.location.longitude = loc.value("longitude", 0.0);
            m.location.altitude = loc.value("altitude", 0.0);
            m.location.timestamp = loc.value("timestamp", 0LL);
            m.location.speed = loc.value("speed", 0.0f);
            m.location.accuracy = loc.value("accuracy", 0.0f);
        }
        if (j.contains("lteCells") && j["lteCells"].is_array()) {
            bool foundRegistered = false;
            for (auto& cell : j["lteCells"]) {
                LteCellDto lte;
                lte.registered = cell.value("registered", false);
                lte.band = cell.value("band", 0);
                lte.earfcn = cell.value("earfcn", 0);
                lte.mcc = cell.value("mcc", 0);
                lte.mnc = cell.value("mnc", 0);
                lte.pci = cell.value("pci", 0);
                lte.tac = cell.value("tac", 0);
                lte.rsrp = cell.value("rsrp", -140);
                lte.rsrq = cell.value("rsrq", -20);
                lte.rssnr = cell.value("rssnr", 0);
                lte.cqi = cell.value("cqi", 0);
                lte.timingAdvance = cell.value("timingAdvance", 0);
                lte.rssi = cell.value("rssi", -120);
                m.lteCells.push_back(lte);
                if (lte.registered) {
                    m.registeredLteCell = lte;
                    m.hasRegisteredLte = true;
                    foundRegistered = true;
                }
            }
            if (!foundRegistered && !m.lteCells.empty()) {
                m.registeredLteCell = m.lteCells[0];
                m.hasRegisteredLte = true;
            }
        }
        // gsmCells
        if (j.contains("gsmCells") && j["gsmCells"].is_array()) {
            for (auto& cell : j["gsmCells"]) {
                GsmCellDto gsm;
                gsm.registered = cell.value("registered", false);
                gsm.cid = cell.value("cid", 0);
                gsm.lac = cell.value("lac", 0);
                gsm.dbm = cell.value("dbm", -120);
                gsm.mcc = cell.value("mcc", 0);
                gsm.mnc = cell.value("mnc", 0);
                gsm.bsic = cell.value("bsic", 0);
                gsm.arfcn = cell.value("arfcn", 0);
                gsm.rssi = cell.value("rssi", -120);
                gsm.timingAdvance = cell.value("timingAdvance", 0);
                m.gsmCells.push_back(gsm);
                // если нужно – можно также выделить зарегистрированную GSM соту (добавить поле в Measurement)
            }
        }
        // nrCells (5G)
        if (j.contains("nrCells") && j["nrCells"].is_array()) {
            for (auto& cell : j["nrCells"]) {
                NrCellDto nr;
                nr.registered = cell.value("registered", false);
                nr.nci = cell.value("nci", 0LL);
                nr.nrarfcn = cell.value("nrarfcn", 0);
                nr.tac = cell.value("tac", 0);
                nr.mcc = cell.value("mcc", 0);
                nr.mnc = cell.value("mnc", 0);
                nr.pci = cell.value("pci", 0);
                nr.arfcn = cell.value("arfcn", 0);
                nr.rsrp = cell.value("rsrp", -140.0f);
                nr.rsrq = cell.value("rsrq", -20.0f);
                nr.ssRsrp = cell.value("ssRsrp", -140);
                nr.ssRsrq = cell.value("ssRsrq", -20);
                nr.ssSinr = cell.value("ssSinr", 0);
                m.nrCells.push_back(nr);
            }
        }
        // traffic
        if (j.contains("traffic")) {
            auto& t = j["traffic"];
            m.traffic.totalRxBytes = t.value("totalRxBytes", 0LL);
            m.traffic.totalTxBytes = t.value("totalTxBytes", 0LL);
            // topApps – при необходимости (массив)
            if (t.contains("topApps") && t["topApps"].is_array()) {
                for (auto& app : t["topApps"]) {
                    std::string name = app.value("name", "");
                    int64_t bytes = app.value("bytes", 0LL);
                    m.traffic.topApps.emplace_back(name, bytes);
                }
            }
        }
        m.deviceId = j.value("deviceId", "unknown");
    } catch (...) {}
    return m;
}

bool ServerCore::initDatabase() {
    std::string connInfo = "host=" + DB_HOST + " port=" + DB_PORT +
                           " dbname=" + DB_NAME + " user=" + DB_USER +
                           " password=" + DB_PASS;
    m_dbConn = PQconnectdb(connInfo.c_str());
    if (PQstatus(m_dbConn) != CONNECTION_OK) {
        std::cerr << "DB connection failed: " << PQerrorMessage(m_dbConn) << std::endl;
        return false;
    }
    return createTable();
}

bool ServerCore::createTable() {
    const char* sql = R"(
        CREATE TABLE IF NOT EXISTS measurements (
            id SERIAL PRIMARY KEY,
            timestamp BIGINT,
            latitude DOUBLE PRECISION,
            longitude DOUBLE PRECISION,
            altitude DOUBLE PRECISION,
            accuracy REAL,
            speed REAL,
            device_id TEXT,
            lte_pci INT,
            lte_rsrp INT,
            lte_rsrq INT,
            lte_rssnr INT,
            lte_earfcn INT,
            lte_tac INT,
            received_time BIGINT
        )
    )";
    PGresult* res = PQexec(m_dbConn, sql);
    bool ok = (PQresultStatus(res) == PGRES_COMMAND_OK);
    PQclear(res);
    if (!ok) std::cerr << "Failed to create table: " << PQerrorMessage(m_dbConn) << std::endl;
    return ok;
}

bool ServerCore::saveToDatabase(const Measurement& m) {
    if (!m_dbConn) return false;

    // Экранирование device_id
    char deviceIdEsc[256];
    int error = 0;
    PQescapeStringConn(m_dbConn, deviceIdEsc, m.deviceId.c_str(), m.deviceId.size(), &error);
    if (error) {
        std::cerr << "Escape error: " << error << std::endl;
        return false;
    }

    // Числовые поля
    char ts[32], lat[32], lon[32], alt[32], acc[32], spd[32], recv[32];
    snprintf(ts, sizeof(ts), "%lld", (long long)m.location.timestamp);
    snprintf(lat, sizeof(lat), "%f", m.location.latitude);
    snprintf(lon, sizeof(lon), "%f", m.location.longitude);
    snprintf(alt, sizeof(alt), "%f", m.location.altitude);
    snprintf(acc, sizeof(acc), "%f", m.location.accuracy);
    snprintf(spd, sizeof(spd), "%f", m.location.speed);
    snprintf(recv, sizeof(recv), "%lld", (long long)m.receivedTime);

    const LteCellDto* lte = &m.registeredLteCell;
    char pci_buf[16] = "0", rsrp_buf[16] = "0", rsrq_buf[16] = "0";
    char rssnr_buf[16] = "0", earfcn_buf[16] = "0", tac_buf[16] = "0";
    char rssi_buf[16] = "0";
    if (lte) {
        if (lte->pci != -1) snprintf(pci_buf, sizeof(pci_buf), "%d", lte->pci);
        if (lte->rsrp != -140) snprintf(rsrp_buf, sizeof(rsrp_buf), "%d", lte->rsrp);
        if (lte->rsrq != -20) snprintf(rsrq_buf, sizeof(rsrq_buf), "%d", lte->rsrq);
        if (lte->rssnr != 0) snprintf(rssnr_buf, sizeof(rssnr_buf), "%d", lte->rssnr);
        if (lte->earfcn != 0) snprintf(earfcn_buf, sizeof(earfcn_buf), "%d", lte->earfcn);
        if (lte->tac != 0) snprintf(tac_buf, sizeof(tac_buf), "%d", lte->tac);
        if (lte->rssi != -120) snprintf(rssi_buf, sizeof(rssi_buf), "%d", lte->rssi);
    }

    const char* paramValues[15] = {
        ts, lat, lon, alt, acc, spd, deviceIdEsc,
        pci_buf, rsrp_buf, rsrq_buf, rssnr_buf, earfcn_buf, tac_buf, recv, rssi_buf
    };
    const char* sql = "INSERT INTO measurements "
        "(timestamp, latitude, longitude, altitude, accuracy, speed, device_id, "
        "lte_pci, lte_rsrp, lte_rsrq, lte_rssnr, lte_earfcn, lte_tac, received_time, lte_rssi) "
        "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)";

    PGresult* res = PQexecParams(m_dbConn, sql, 15, NULL, paramValues, NULL, NULL, 0);
    bool ok = (PQresultStatus(res) == PGRES_COMMAND_OK);
    if (!ok) {
        std::cerr << "DB insert error: " << PQerrorMessage(m_dbConn) << std::endl;
    }
    PQclear(res);
    return ok;
}

void ServerCore::saveToJsonFile(const Measurement& m) {
    json j = {
        {"timestamp", m.location.timestamp},
        {"lat", m.location.latitude},
        {"lon", m.location.longitude},
        {"rsrp", m.lteCells.empty() ? 0 : m.registeredLteCell.rsrp}
    };
    m_pendingJson.push_back(j.dump());
    if (m_pendingJson.size() >= JSON_BUFFER_SIZE) {
        flushJsonBuffer();
    }
}

std::vector<AggregatedPoint> ServerCore::loadAggregatedPoints() {
    std::vector<AggregatedPoint> result;
    if (!m_dbConn) return result;

    // Загружаем последние 50000 записей (для быстрого старта)
    // Используем индекс по timestamp
    const char* sql = "SELECT latitude, longitude, lte_rsrp, lte_rsrq, lte_rssi, altitude, lte_earfcn "
                      "FROM measurements WHERE latitude IS NOT NULL AND lte_rsrp IS NOT NULL "
                      "ORDER BY timestamp DESC LIMIT 50000";
    PGresult* res = PQexec(m_dbConn, sql);
    if (PQresultStatus(res) != PGRES_TUPLES_OK) {
        std::cerr << "loadAggregatedPoints query failed: " << PQerrorMessage(m_dbConn) << std::endl;
        PQclear(res);
        return result;
    }

    int rows = PQntuples(res);
    // Берём каждую AGGREGATION_STEP-ю запись (по умолчанию 10)
    for (int i = 0; i < rows; i += AGGREGATION_STEP) {
        AggregatedPoint p;
        // Если значение NULL, пропускаем точку
        if (PQgetisnull(res, i, 0) || PQgetisnull(res, i, 1)) continue;
        p.lat = atof(PQgetvalue(res, i, 0));
        p.lon = atof(PQgetvalue(res, i, 1));
        // Для rsrp: если NULL, задаём значение по умолчанию -140
        if (PQgetisnull(res, i, 2)) {
            p.rsrp = -140.0f;
        } else {
            p.rsrp = (float)atoi(PQgetvalue(res, i, 2));
        }
        // Для rsrq
        if (PQgetisnull(res, i, 3)) {
            p.rsrq = -140.0f;
        } else {
            p.rsrq = (float)atoi(PQgetvalue(res, i, 3));
        }
        // Для rssi
        if (PQgetisnull(res, i, 4)) {
            p.rssi = -140.0f;
        } else {
            p.rssi = (float)atoi(PQgetvalue(res, i, 4));
        }
        // Для altitude
        if (PQgetisnull(res, i, 5)) {
            p.altitude = -140.0f;
        } else {
            p.altitude = (float)atoi(PQgetvalue(res, i, 5));
        }
        // Для earfcn
        if (PQgetisnull(res, i, 6)) {
            p.earfcn = -140.0f;
        } else {
            p.earfcn = (float)atoi(PQgetvalue(res, i, 6));
        }
        p.count = 1;
        result.push_back(p);
        if (result.size() >= MAX_AGGREGATED_POINTS) break;
    }
    PQclear(res);
    return result;
}

bool ServerCore::loadHistoryFromDatabase() {
    if (!m_dbConn) return false;
    
    // Загружаем достаточно записей, чтобы после прореживания получить MAX_HISTORY_SIZE точек
    int limit = MAX_HISTORY_SIZE * AGGREGATION_STEP;
    std::string sql = std::string("SELECT timestamp, received_time, latitude, longitude, altitude, accuracy, speed, device_id, "
                      "lte_pci, lte_rsrp, lte_rsrq, lte_rssnr, lte_earfcn, lte_tac, lte_rssi "
                      "FROM measurements "
                      "WHERE lte_rsrp IS NOT NULL "
                      "ORDER BY timestamp ASC LIMIT ") + std::to_string(limit);
    
    PGresult* res = PQexec(m_dbConn, sql.c_str());
    if (PQresultStatus(res) != PGRES_TUPLES_OK) {
        std::cerr << "loadHistoryFromDatabase query failed: " << PQerrorMessage(m_dbConn) << std::endl;
        PQclear(res);
        return false;
    }
    
    int rows = PQntuples(res);
    if (rows == 0) {
        PQclear(res);
        std::cout << "No historical data in database." << std::endl;
        return true;  // нет данных, но не ошибка
    }
    
    // Временные контейнеры для агрегированных данных
    std::vector<Measurement> tempHistory;
    std::vector<long long> tempTimestamps;
    std::unordered_map<int, std::vector<float>> tempRsrpByPci;
    std::unordered_map<int, std::vector<float>> tempRsrqByPci;
    
    for (int i = 0; i < rows; i += AGGREGATION_STEP) {
        Measurement m;
        // Заполняем поля
        m.location.timestamp = atoll(PQgetvalue(res, i, 0));
        m.receivedTime = atoll(PQgetvalue(res, i, 1));
        m.location.latitude = atof(PQgetvalue(res, i, 2));
        m.location.longitude = atof(PQgetvalue(res, i, 3));
        m.location.altitude = atof(PQgetvalue(res, i, 4));
        m.location.accuracy = (float)atof(PQgetvalue(res, i, 5));
        m.location.speed = (float)atof(PQgetvalue(res, i, 6));
        if (!PQgetisnull(res, i, 7)) {
            m.deviceId = PQgetvalue(res, i, 7);
        }
        
        // LTE соты (зарегистрированная)
        LteCellDto lte;
        lte.pci = PQgetisnull(res, i, 8) ? 0 : atoi(PQgetvalue(res, i, 8));
        lte.rsrp = PQgetisnull(res, i, 9) ? -140 : atoi(PQgetvalue(res, i, 9));
        lte.rsrq = PQgetisnull(res, i, 10) ? -20 : atoi(PQgetvalue(res, i, 10));
        lte.rssnr = PQgetisnull(res, i, 11) ? 0 : atoi(PQgetvalue(res, i, 11));
        lte.earfcn = PQgetisnull(res, i, 12) ? 0 : atoi(PQgetvalue(res, i, 12));
        lte.tac = PQgetisnull(res, i, 13) ? 0 : atoi(PQgetvalue(res, i, 13));
        lte.rssi = PQgetisnull(res, i, 14) ? -120 : atoi(PQgetvalue(res, i, 14));
        lte.registered = true;
        m.lteCells.push_back(lte);
        m.registeredLteCell = lte;
        m.hasRegisteredLte = true;
        
        // Добавляем в историю
        tempHistory.push_back(m);
        
        // Для истории по PCI
        tempTimestamps.push_back(m.receivedTime);
        tempRsrpByPci[lte.pci].push_back((float)lte.rsrp);
        tempRsrqByPci[lte.pci].push_back((float)lte.rsrq);
    }
    PQclear(res);
    
    // Переворачиваем порядок, чтобы от старых к новым (для графика)
    std::reverse(tempHistory.begin(), tempHistory.end());
    std::reverse(tempTimestamps.begin(), tempTimestamps.end());
    for (auto& [pci, vec] : tempRsrpByPci) {
        std::reverse(vec.begin(), vec.end());
        std::reverse(tempRsrqByPci[pci].begin(), tempRsrqByPci[pci].end());
    }

    // Сохраняем в основные контейнеры
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_history = std::move(tempHistory);
    }

    // Обновляем кэш графиков
    {
        std::lock_guard<std::mutex> lock(m_plotMutex);
        m_cachedRsrpHistory.clear();
        m_cachedRsrqHistory.clear();
        m_cachedRsrpHistory.reserve(m_history.size());
        m_cachedRsrqHistory.reserve(m_history.size());
        for (const auto& meas : m_history) {
            if (meas.hasRegisteredLte) {
                m_cachedRsrpHistory.push_back((float)meas.registeredLteCell.rsrp);
                m_cachedRsrqHistory.push_back((float)meas.registeredLteCell.rsrq);
            } else {
                m_cachedRsrpHistory.push_back(-140.0f);
                m_cachedRsrqHistory.push_back(-20.0f);
            }
        }
    }
    
    // Обновляем историю по PCI
    {
        std::lock_guard<std::mutex> lock(m_historyMutex);
        m_globalTimestamps = std::move(tempTimestamps);
        m_historyByPci.clear();
        for (const auto& [pci, rsrpVec] : tempRsrpByPci) {
            PciHistory hist;
            hist.rsrpValues = rsrpVec;
            hist.rsrqValues = tempRsrqByPci[pci];
            // timestamps общие, не храним в каждой структуре
            m_historyByPci[pci] = std::move(hist);
        }
    }
    
    std::cout << "Loaded " << m_history.size() << " aggregated historical measurements (step=" 
              << AGGREGATION_STEP << ") from database for graphs." << std::endl;
    return true;
}

Measurement ServerCore::getLastMeasurement() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_lastMeasurement;
}

bool ServerCore::hasNewData() {
    return m_hasNewData.exchange(false);
}

const std::vector<float>& ServerCore::getCachedRsrpHistory() const {
    std::lock_guard<std::mutex> lock(m_plotMutex);
    return m_cachedRsrpHistory;
}

const std::vector<float>& ServerCore::getCachedRsrqHistory() const {
    std::lock_guard<std::mutex> lock(m_plotMutex);
    return m_cachedRsrqHistory;
}

void ServerCore::flushJsonBuffer() {
    if (m_pendingJson.empty()) return;
    std::ofstream file(JSON_LOG_FILE, std::ios::app);
    if (file.is_open()) {
        for (const auto& jsonStr : m_pendingJson) {
            file << jsonStr << std::endl;
        }
        m_pendingJson.clear();
    }
}

void ServerCore::addMeasurementToHistory(const Measurement& m) {
    std::lock_guard<std::mutex> lock(m_historyMutex);
    long long now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    m_globalTimestamps.push_back(now);
    
    int currentPci = m.registeredLteCell.pci;
    float rsrp = m.registeredLteCell.rsrp;
    float rsrq = m.registeredLteCell.rsrq;
    
    for (auto& [pci, hist] : m_historyByPci) {
        if (pci == currentPci) {
            hist.rsrpValues.push_back(rsrp);
            hist.rsrqValues.push_back(rsrq);
        } else {
            // для RSRP
            if (hist.rsrpValues.empty()) {
                hist.rsrpValues.push_back(NAN);
            } else {
                hist.rsrpValues.push_back(hist.rsrpValues.back());
            }
            // для RSRQ
            if (hist.rsrqValues.empty()) {
                hist.rsrqValues.push_back(NAN);
            } else {
                hist.rsrqValues.push_back(hist.rsrqValues.back());
            }
        }
        // синхронизируем размер с глобальным массивом
        while (hist.rsrpValues.size() < m_globalTimestamps.size()) {
            hist.rsrpValues.push_back(NAN);
        }
    }
    // если PCI новый, создаём историю
    if (m_historyByPci.find(currentPci) == m_historyByPci.end()) {
        PciHistory newHist;
        size_t curSize = m_globalTimestamps.size();
        // Заполняем NAN для всех предыдущих шагов
        newHist.rsrpValues.assign(curSize - 1, NAN);
        newHist.rsrpValues.push_back(rsrp);
        newHist.rsrqValues.assign(curSize - 1, NAN);
        newHist.rsrqValues.push_back(rsrq);
        m_historyByPci[currentPci] = newHist;
    }
}

std::vector<int> ServerCore::getAvailablePciList() const {
    std::lock_guard<std::mutex> lock(m_historyMutex);
    std::vector<int> result;
    for (const auto& pair : m_historyByPci) {
        result.push_back(pair.first);
    }
    return result;
}

const std::unordered_map<int, PciHistory>& ServerCore::getHistoryByPci() const {
    return m_historyByPci;
}