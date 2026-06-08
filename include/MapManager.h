#ifndef MAP_MANAGER_H
#define MAP_MANAGER_H

#include <queue>
#include <map>
#include <string>
#include <vector>
#include <atomic>
#include <thread>
#include <cstdint>
#include <mutex>
#include <set>
#include <GL/glew.h>
#include "DataModels.h"
#include "Config.h"

struct Tile {
    int z, x, y;
    int width = 256, height = 256;
    std::vector<uint8_t> rgbaData;
    GLuint texture = 0;
    bool loaded = false;
};

struct Color {
    uint8_t r, g, b;
};

class MapManager {
public:
    MapManager();
    ~MapManager();
    void initGL();

    void requestTile(int z, int x, int y);
    Tile* getTile(int z, int x, int y);

    void renderMap(int winW, int winH, double centerLat, double centerLon, int zoom,
                   const std::vector<MapPoint>& currentPoints,
                   const std::vector<MapPoint>& aggregatedPoints,
                   bool showHeatmap, float heatmapRadiusMeters,
                   int criterion, int earfcnFilter);

    void clearCache();

    void requestHeatmapUpdate(double centerLat, double centerLon, int zoom, 
                              int winW, int winH, float radiusMeters, int criterion,
                              const std::vector<MapPoint>& allPoints);

    void stopHeatmapGeneration() { m_stopHeatmapRequested = true; }

    void getColorForRsrp(float rsrp, uint8_t& r, uint8_t& g, uint8_t& b);
    void getColorForRsrq(float rsrq, uint8_t& r, uint8_t& g, uint8_t& b);
    void getColorForRssi(float rssi, uint8_t& r, uint8_t& g, uint8_t& b);
    void getColorForAltitude(float alt, uint8_t& r, uint8_t& g, uint8_t& b);
    void getColorForValueDynamic(float value, float minVal, float maxVal, uint8_t& r, uint8_t& g, uint8_t& b);

private:
    std::string getTilePath(int z, int x, int y);
    bool loadTileFromFile(Tile& tile);
    bool saveTileToFile(const Tile& tile);
    bool downloadTile(Tile& tile);
    static size_t curlWriteCallback(void* data, size_t size, size_t nmemb, void* userp);
    void workerThreadFunc();

    double lonToTileX(double lon, int z);
    double latToTileY(double lat, int z);
    double tileXToLon(double tx, int z);
    double tileYToLat(double ty, int z);

    // Тепловая карта (синхронная)
    void valueToGradientColor(float value, float minVal, float maxVal, 
                               uint8_t& r, uint8_t& g, uint8_t& b);
    // Получение диапазона для выбранного критерия
    void getRangeForCriterion(int criterion, float& minVal, float& maxVal);

    std::atomic<bool> m_workerRunning{true};
    std::thread m_workerThread;
    struct TileRequest { int z, x, y; };
    std::queue<TileRequest> m_requestQueue;
    std::mutex m_queueMutex;
    std::mutex m_cacheMutex;
    std::map<std::string, Tile> m_tileCache;

    GLuint m_heatmapTexture = 0;

    double haversineDistance(double lat1, double lon1, double lat2, double lon2) const;

    void heatmapWorkerFunc();
    std::thread m_heatmapWorker;
    std::atomic<bool> m_heatmapPending{false};
    std::atomic<bool> m_heatmapReady{false};
    std::vector<uint8_t> m_lastHeatmapPixels;
    int m_lastHeatmapW = 0, m_lastHeatmapH = 0;
    // параметры для сравнения
    double m_lastCenterLat = 0, m_lastCenterLon = 0;
    int m_lastZoom = 0, m_lastWinW = 0, m_lastWinH = 0;
    float m_lastRadiusMeters = 0;
    int m_lastCriterion = 0;
    size_t m_lastPointsHash = 0;
    std::mutex m_heatmapMutex;

    std::vector<MapPoint> m_pendingPoints;

    std::set<std::string> m_requestedTiles;
    std::mutex m_requestedMutex;

    std::vector<uint8_t> computeHeatmapPixels(const std::vector<MapPoint>& points,
                                          double centerLat, double centerLon, int zoom,
                                          int winW, int winH,
                                          float radiusMeters, int criterion);

    // Предопределённые цвета для RSRP (по методике из чужого кода)
static const Color COLOR_EXCELLENT;
static const Color COLOR_GOOD;
static const Color COLOR_FAIR;
static const Color COLOR_POOR;
static const Color COLOR_NOSIGNAL; 
    
    std::atomic<bool> m_stopHeatmapRequested{false};

    float m_cachedMinVal = 0.0f;
    float m_cachedMaxVal = 0.0f;
    int m_cachedCriterion = -1;   // -1 означает, что кэш недействителен
};

#endif