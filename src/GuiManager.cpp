#define _USE_MATH_DEFINES
#include "GuiManager.h"
#include "Config.h"
#include "ServerCore.h"
#include "imgui.h"
#include "implot.h"
#include <cmath>

GuiManager::GuiManager(ServerCore& server, MapManager& mapManager)
    : m_server(server), m_mapManager(mapManager) {}

void GuiManager::render() {
    renderMenuBar();
    renderCurrentDataWindow();
    renderSignalPlotsWindow();
    renderStatisticsWindow();
    renderMapControlsWindow();
    renderMapWindow();
}

void GuiManager::renderMenuBar() {
    if (ImGui::BeginMainMenuBar()) {
        if (ImGui::BeginMenu("File")) {
            if (ImGui::MenuItem("Exit")) exit(0);
            ImGui::EndMenu();
        }
        ImGui::EndMainMenuBar();
    }
}

void GuiManager::renderCurrentDataWindow() {
    ImGui::SetNextWindowPos(ImVec2(50,50), ImGuiCond_FirstUseEver);
    ImGui::SetNextWindowSize(ImVec2(400,500), ImGuiCond_FirstUseEver);
    if (ImGui::Begin("Current Data")) {
        auto m = m_server.getLastMeasurement();
        ImGui::Text("Device: %s", m.deviceId.c_str());
        ImGui::Text("Lat: %.6f  Lon: %.6f", m.location.latitude, m.location.longitude);
        ImGui::Text("Altitude: %.1f m", m.location.altitude);
        ImGui::Text("Accuracy: %.1f m", m.location.accuracy);
        ImGui::Separator();
        if (!m.lteCells.empty()) {
            auto& lte = m.registeredLteCell;
            ImGui::Text("LTE PCI: %d  RSRP: %d dBm", lte.pci, lte.rsrp);
            ImGui::Text("RSRQ: %d dB  RSSNR: %d dB", lte.rsrq, lte.rssnr);
        }
        ImGui::End();
    }
}

void GuiManager::renderSignalPlotsWindow() {
    ImGui::SetNextWindowPos(ImVec2(50,560), ImGuiCond_FirstUseEver);
    ImGui::SetNextWindowSize(ImVec2(400,300), ImGuiCond_FirstUseEver);
    if (ImGui::Begin("Signal Plots")) {
        static int signalType = 0;
        const char* signalTypeNames[] = { "RSRP", "RSRQ" };
        ImGui::Combo("Signal Type", &signalType, signalTypeNames, IM_ARRAYSIZE(signalTypeNames));
        ImGui::Separator();

        static int selectedPci = -1;
        auto pciList = m_server.getAvailablePciList();
        if (ImGui::BeginCombo("PCI", selectedPci == -1 ? "All (history)" : std::to_string(selectedPci).c_str())) {
            if (ImGui::Selectable("All (history)", selectedPci == -1)) selectedPci = -1;
            for (int pci : pciList) {
                if (ImGui::Selectable(std::to_string(pci).c_str(), selectedPci == pci)) {
                    selectedPci = pci;
                }
            }
            ImGui::EndCombo();
        }

        if (selectedPci == -1) {
            const auto& rsrpVals = m_server.getCachedRsrpHistory();
            const auto& rsrqVals = m_server.getCachedRsrqHistory();
            if (signalType == 0) {
                if (rsrpVals.size() > 1) {
                    if (ImPlot::BeginPlot("RSRP History", ImVec2(-1,200))) {
                        ImPlot::PlotLine("RSRP (all)", rsrpVals.data(), rsrpVals.size());
                        ImPlot::EndPlot();
                    }
                } else {
                    ImGui::Text("Not enough RSRP data");
                }
            } else {
                if (rsrqVals.size() > 1) {
                    if (ImPlot::BeginPlot("RSRQ History", ImVec2(-1,200))) {
                        ImPlot::PlotLine("RSRQ (all)", rsrqVals.data(), rsrqVals.size());
                        ImPlot::EndPlot();
                    }
                } else {
                    ImGui::Text("Not enough RSRQ data");
                }
            }
        } else {
            const auto& historyByPci = m_server.getHistoryByPci();
            auto it = historyByPci.find(selectedPci);
            if (it != historyByPci.end()) {
                const auto& hist = it->second;
                if (signalType == 0) {
                    if (hist.rsrpValues.size() > 1) {
                        if (ImPlot::BeginPlot("RSRP History", ImVec2(-1,200))) {
                            ImPlot::PlotLine(("RSRP (PCI " + std::to_string(selectedPci) + ")").c_str(),
                                             hist.rsrpValues.data(), hist.rsrpValues.size());
                            ImPlot::EndPlot();
                        }
                    } else {
                        ImGui::Text("Not enough RSRP data for PCI %d", selectedPci);
                    }
                } else {
                    if (hist.rsrqValues.size() > 1) {
                        if (ImPlot::BeginPlot("RSRQ History", ImVec2(-1,200))) {
                            ImPlot::PlotLine(("RSRQ (PCI " + std::to_string(selectedPci) + ")").c_str(),
                                             hist.rsrqValues.data(), hist.rsrqValues.size());
                            ImPlot::EndPlot();
                        }
                    } else {
                        ImGui::Text("Not enough RSRQ data for PCI %d", selectedPci);
                    }
                }
            } else {
                ImGui::Text("No data for PCI %d", selectedPci);
            }
        }
        ImGui::End();
    }
}

void GuiManager::renderStatisticsWindow() {
    ImGui::SetNextWindowPos(ImVec2(50,870), ImGuiCond_FirstUseEver);
    ImGui::SetNextWindowSize(ImVec2(400,200), ImGuiCond_FirstUseEver);
    if (ImGui::Begin("Statistics")) {
        auto history = m_server.getCachedRsrpHistory();
        ImGui::Text("Total measurements: %zu", history.size());
        ImGui::End();
    }
}

void GuiManager::renderMapControlsWindow() {
    ImGui::SetNextWindowPos(ImVec2(1200,50), ImGuiCond_FirstUseEver);
    ImGui::SetNextWindowSize(ImVec2(250,300), ImGuiCond_FirstUseEver);
    if (ImGui::Begin("Map Controls")) {
        ImGui::Text("Zoom: %d", mapZoom);
        ImGui::SliderInt("Zoom", &mapZoom, 1, 18);
        ImGui::Checkbox("Show Heatmap", &showHeatmap);
        const char* criteria[] = {"RSRP","RSRQ","RSSI","Altitude"};
        ImGui::Combo("Criterion", &heatmapCriterion, criteria, 4);
        ImGui::SliderFloat("Heatmap radius (m)", &idwRadius, 10.0f, 40.0f);
        ImGui::InputInt("EARFCN filter (0=all)", &selectedEarfcn);
        ImGui::End();
    }
}

void GuiManager::renderMapWindow() {
    ImGui::SetNextWindowPos(ImVec2(470,50), ImGuiCond_FirstUseEver);
    ImGui::SetNextWindowSize(ImVec2(HEATMAP_WIDTH, HEATMAP_HEIGHT), ImGuiCond_FirstUseEver);

    static ImVec2 dragStartMouse;
    static double dragStartLon, dragStartLat;
    static bool isDragging = false;

    if (ImGui::Begin("OSM Map")) {
        ImVec2 winPos = ImGui::GetCursorScreenPos();
        ImVec2 winSize = ImGui::GetContentRegionAvail();

        // ---- Обработка ввода (панорамирование и зум) ----
        if (ImGui::IsWindowHovered()) {
            // Панорамирование левой кнопкой
            if (ImGui::IsMouseDragging(ImGuiMouseButton_Left)) {
                if (!isDragging) {
                    isDragging = true;
                    dragStartMouse = ImGui::GetMousePos();
                    dragStartLon = mapCenterLon;
                    dragStartLat = mapCenterLat;
                } else {
                    ImVec2 delta = ImGui::GetMouseDragDelta(ImGuiMouseButton_Left);
                    double lonPerPixel = 360.0 / (1 << mapZoom) / winSize.x;
                    double latPerPixel = 180.0 / (1 << mapZoom) / winSize.y;
                    mapCenterLon = dragStartLon - delta.x * lonPerPixel;
                    mapCenterLat = dragStartLat + delta.y * latPerPixel;
                    m_mapManager.stopHeatmapGeneration();
                }
            } else {
                isDragging = false;
            }

            // Зум колёсиком
            float wheel = ImGui::GetIO().MouseWheel;
            if (wheel != 0) {
                int newZoom = mapZoom + (wheel > 0 ? 1 : -1);
                newZoom = std::max(1, std::min(18, newZoom));
                if (newZoom != mapZoom) {
                    ImVec2 mouse = ImGui::GetMousePos();
                    double mouseRelX = (mouse.x - winPos.x) / winSize.x;
                    double mouseRelY = (mouse.y - winPos.y) / winSize.y;
                    double lonMin = mapCenterLon - 180.0 / (1 << mapZoom);
                    double lonMax = mapCenterLon + 180.0 / (1 << mapZoom);
                    double latMin = mapCenterLat - 90.0 / (1 << mapZoom);
                    double latMax = mapCenterLat + 90.0 / (1 << mapZoom);
                    double lonClick = lonMin + mouseRelX * (lonMax - lonMin);
                    double latClick = latMax - mouseRelY * (latMax - latMin);
                    mapZoom = newZoom;
                    m_mapManager.stopHeatmapGeneration();   // прервать текущую генерацию
                    lonMin = mapCenterLon - 180.0 / (1 << mapZoom);
                    lonMax = mapCenterLon + 180.0 / (1 << mapZoom);
                    latMin = mapCenterLat - 90.0 / (1 << mapZoom);
                    latMax = mapCenterLat + 90.0 / (1 << mapZoom);
                    double tX = (lonClick - lonMin) / (lonMax - lonMin);
                    double tY = (latMax - latClick) / (latMax - latMin);
                    mapCenterLon = lonMin + tX * (lonMax - lonMin);
                    mapCenterLat = latMax - tY * (latMax - latMin);
                }
            }
        }

        // ---- Отрисовка карты ----
        if (winSize.x > 0 && winSize.y > 0) {
            // Собираем текущие точки
            std::vector<MapPoint> currentPoints;
            auto m = m_server.getLastMeasurement();
            m_mapManager.stopHeatmapGeneration();
            if (!m.lteCells.empty()) {
                MapPoint p;
                p.lat = m.location.latitude;
                p.lon = m.location.longitude;
                p.rsrp = (float)m.registeredLteCell.rsrp;
                p.rsrq = (float)m.registeredLteCell.rsrq;
                // Если в DTO нет rssi, можно использовать rsrp как временную заглушку
                p.rssi = (float)m.registeredLteCell.rsrp; // или задать 0
                p.altitude = (float)m.location.altitude;
                p.earfcn = m.registeredLteCell.earfcn;
                p.pci = m.registeredLteCell.pci;
                p.isCurrent = true;
                currentPoints.push_back(p);
            }

            // Загружаем агрегированные точки из БД (только один раз)
            if (!m_aggregatedLoaded) {
                auto aggVec = m_server.loadAggregatedPoints();
                for (auto& a : aggVec) {
                    MapPoint p;
                    p.lat = a.lat;
                    p.lon = a.lon;
                    p.rsrp = a.rsrp;
                    p.rsrq = a.rsrq;
                    p.rssi = a.rssi;
                    p.altitude = a.altitude;
                    p.earfcn = a.earfcn;
                    p.isCurrent = false;
                    m_cachedAggregatedPoints.push_back(p);
                }
                m_aggregatedLoaded = true;
            }
            // Используем кэш
            const auto& aggregatedPoints = m_cachedAggregatedPoints;

            // Вычисляем радиус в пикселях из радиуса в метрах
            double metersPerPixel = 156543.03392 * std::cos(mapCenterLat * M_PI / 180.0) / (1 << mapZoom);
            float radiusPixels = idwRadius / metersPerPixel;
            radiusPixels = std::min(radiusPixels, 100.0f); // ограничим, чтобы не вылетать

            // Вызываем отрисовку карты (она сама сгенерирует тепловую карту при необходимости)
            m_mapManager.renderMap((int)winSize.x, (int)winSize.y,
                                   mapCenterLat, mapCenterLon, mapZoom,
                                   currentPoints, aggregatedPoints,
                                   showHeatmap, idwRadius, heatmapCriterion, selectedEarfcn);
        }
        ImGui::End();
    }
}

void GuiManager::setAggregatedPoints(const std::vector<AggregatedPoint>& points) {
    m_cachedAggregatedPoints.clear();
    for (const auto& a : points) {
        MapPoint p;
        p.lat = a.lat;
        p.lon = a.lon;
        p.rsrp = a.rsrp;
        p.rsrq = a.rsrq;
        p.rssi = a.rssi;
        p.altitude = a.altitude;
        p.earfcn = a.earfcn;
        p.isCurrent = false;
        m_cachedAggregatedPoints.push_back(p);
    }
    m_aggregatedLoaded = true;
}
