import { request } from '../../utils/request';

export { };

type PageState = 'loading' | 'error' | 'ready';
type ChartState = 'loading' | 'error' | 'empty' | 'ready';
type OfflineState = 'loading' | 'error' | 'empty' | 'ready';

interface EarningsChartData {
    dates: string[];
    earnings: number[];
}

interface MetricValue {
    value: string;
    progress: number;
    hasValue: boolean;
}

interface OfflineRecord {
    id: number;
    offlineTime: string;
    lastHeartbeatTime: string;
    reason: string;
    statusText: string;
}

interface ChartGeometry {
    left: number;
    width: number;
    count: number;
}

let chartGeometry: ChartGeometry | null = null;
let telemetryTimer: number | null = null;
let telemetryTick = 0;

const DEMO_UTILIZATION_MIN = 60;
const DEMO_UTILIZATION_MAX = 85;

function hasValue(value: unknown): boolean {
    return value !== null && value !== undefined && String(value).trim() !== '';
}

function displayText(value: unknown): string {
    return hasValue(value) ? String(value).trim() : '--';
}

function formatDateTime(value: unknown): string {
    if (!hasValue(value)) return '--';
    return String(value).replace('T', ' ').substring(0, 19);
}

function demoMetric(seed: number, tick: number): MetricValue {
    const phase = tick * 0.85 + seed * 1.43;
    const wave = 72.5 + 12.5 * Math.sin(phase);
    const parsed = Math.max(
        DEMO_UTILIZATION_MIN,
        Math.min(DEMO_UTILIZATION_MAX, Math.round(wave))
    );
    return {
        value: String(parsed),
        progress: parsed,
        hasValue: true
    };
}

function buildTelemetry(deviceId: number, heartbeat: string, tick: number, status: unknown): any[] {
    if (status !== 1) {
        return [
            { code: 'CPU', label: 'CPU 占用', value: '--', unit: '%', progress: 0, hasProgress: false, tone: 'compute' },
            { code: 'MEM', label: '内存占用', value: '--', unit: '%', progress: 0, hasProgress: false, tone: 'memory' },
            { code: 'GPU', label: 'GPU 占用', value: '--', unit: '%', progress: 0, hasProgress: false, tone: 'gpu' },
            { code: 'SYNC', label: '最后心跳', value: heartbeat, unit: '', progress: 0, hasProgress: false, tone: 'sync', compact: true }
        ];
    }

    const deviceSeed = Math.abs(deviceId % 17) / 10;
    const cpu = demoMetric(1 + deviceSeed, tick);
    const memory = demoMetric(2.7 + deviceSeed, tick);
    const gpu = demoMetric(4.3 + deviceSeed, tick);

    return [
        { code: 'CPU', label: 'CPU 占用', value: cpu.value, unit: '%', progress: cpu.progress, hasProgress: true, tone: 'compute' },
        { code: 'MEM', label: '内存占用', value: memory.value, unit: '%', progress: memory.progress, hasProgress: true, tone: 'memory' },
        { code: 'GPU', label: 'GPU 占用', value: gpu.value, unit: '%', progress: gpu.progress, hasProgress: true, tone: 'gpu' },
        { code: 'SYNC', label: '最后心跳', value: heartbeat, unit: '', progress: 0, hasProgress: false, tone: 'sync', compact: true }
    ];
}

function normalizeOfflineRecord(raw: any): OfflineRecord {
    const onlineAt = raw?.onlineAt || raw?.onlineTime;
    return {
        id: Number(raw?.id) || 0,
        offlineTime: formatDateTime(raw?.offlineTime || raw?.offlineStart),
        lastHeartbeatTime: formatDateTime(raw?.lastHeartbeatTime),
        reason: displayText(raw?.reason) === '--' ? '心跳超时' : displayText(raw?.reason),
        statusText: hasValue(onlineAt) ? '已恢复' : '离线记录'
    };
}

function normalizeChartData(raw: any): EarningsChartData {
    const rawDates = Array.isArray(raw?.dates) ? raw.dates : [];
    const rawEarnings = Array.isArray(raw?.earnings) ? raw.earnings : [];
    const dates: string[] = [];
    const earnings: number[] = [];
    const length = Math.min(rawDates.length, rawEarnings.length);

    for (let index = 0; index < length; index += 1) {
        const date = displayText(rawDates[index]);
        const earning = Number(rawEarnings[index]);
        if (date !== '--' && Number.isFinite(earning)) {
            dates.push(date);
            earnings.push(earning);
        }
    }

    return { dates, earnings };
}

function shortDate(value: string): string {
    const parts = value.split('-');
    return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : value;
}

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        deviceId: 0,
        pageState: 'loading' as PageState,
        chartState: 'loading' as ChartState,
        offlineState: 'loading' as OfflineState,
        errorMessage: '',
        refreshing: false,
        unbinding: false,
        device: {} as any,
        telemetry: [] as any[],
        offlineRecords: [] as OfflineRecord[],
        softwareTags: [] as any[],
        latestEarning: '--',
        chartSummary: {
            total: '--',
            average: '--'
        },
        chartFocus: {
            date: '--',
            value: '--'
        },
        selectedChartIndex: 0,
        chartData: {
            dates: [],
            earnings: []
        } as EarningsChartData
    },

    onLoad(options: Record<string, string>) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        const deviceId = Number(options.id);
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: Math.max(menuButtonInfo.height, 44)
        });

        if (!Number.isFinite(deviceId) || deviceId <= 0) {
            this.setData({
                pageState: 'error',
                errorMessage: '设备编号无效'
            });
            return;
        }

        this.setData({ deviceId });
        this.loadPage();
        this.startTelemetryTicker();
    },

    onUnload() {
        chartGeometry = null;
        this.stopTelemetryTicker();
    },

    goBack() {
        wx.navigateBack();
    },

    loadPage() {
        this.setData({
            pageState: 'loading',
            chartState: 'loading',
            offlineState: 'loading',
            errorMessage: ''
        });
        this.fetchDeviceDetail(this.data.deviceId);
        this.fetchChartData(this.data.deviceId);
        this.fetchOfflineRecords(this.data.deviceId);
    },

    retryPage() {
        this.loadPage();
    },

    handleRefresh() {
        if (this.data.refreshing) return;
        this.setData({ refreshing: true });
        Promise.all([
            this.fetchDeviceDetail(this.data.deviceId),
            this.fetchChartData(this.data.deviceId),
            this.fetchOfflineRecords(this.data.deviceId)
        ]).finally(() => {
            this.setData({ refreshing: false });
        });
    },

    fetchDeviceDetail(id: number): Promise<void> {
        return request({
            url: `/api/device/detail/${id}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code !== 200 || !res.data) {
                this.setData({
                    pageState: 'error',
                    errorMessage: res.msg || '设备详情加载失败'
                });
                return;
            }

            const raw = res.data;
            const identityRaw = hasValue(raw.bindCode)
                ? String(raw.bindCode).trim()
                : (hasValue(raw.sn) ? String(raw.sn).trim() : '');
            const heartbeat = formatDateTime(raw.lastHeartbeatTime || raw.lastHeartbeat);
            const statusTone = raw.status === 1 ? 'online' : (raw.status === 0 ? 'offline' : 'unknown');
            const statusLabel = raw.status === 1 ? '在线运行' : (raw.status === 0 ? '设备离线' : '状态未知');

            const device = {
                ...raw,
                displayName: displayText(raw.name),
                identity: identityRaw || '--',
                identityRaw,
                businessIdText: displayText(raw.businessId),
                modelText: displayText(raw.deviceModel),
                locationText: displayText(raw.location),
                bindTimeText: formatDateTime(raw.bindTime),
                heartbeatText: heartbeat,
                statusTone,
                statusLabel,
                hashrateText: displayText(raw.hashrate)
            };

            const telemetry = buildTelemetry(this.data.deviceId, heartbeat, telemetryTick, raw.status);

            const softwareTags = [
                { label: 'L4T', value: displayText(raw.l4tVersion) },
                { label: 'CUDA', value: displayText(raw.cudaVersion) },
                { label: 'AGENT', value: displayText(raw.agentVersion) },
                { label: 'IMAGE', value: displayText(raw.imageVersion) },
                { label: 'ARCH', value: displayText(raw.architecture) }
            ];

            this.setData({
                device,
                telemetry,
                softwareTags,
                pageState: 'ready',
                errorMessage: ''
            }, () => {
                if (this.data.chartState === 'ready') {
                    this.drawChart(this.data.chartData);
                }
            });
        }).catch((error: unknown) => {
            console.error('fetchDeviceDetail error:', error);
            this.setData({
                pageState: 'error',
                errorMessage: '网络连接异常，请稍后重试'
            });
        });
    },

    fetchOfflineRecords(id: number): Promise<void> {
        this.setData({ offlineState: 'loading' });
        return request({
            url: `/api/device/offline-records/${id}`,
            method: 'GET',
            data: { page: 1, size: 20 }
        }).then((res: any) => {
            if (res.code !== 200) {
                this.setData({ offlineState: 'error' });
                return;
            }

            const pageData = res.data || {};
            const rawRecords = Array.isArray(pageData)
                ? pageData
                : (Array.isArray(pageData.records) ? pageData.records : []);
            const offlineRecords = rawRecords.map(normalizeOfflineRecord);
            this.setData({
                offlineRecords,
                offlineState: offlineRecords.length > 0 ? 'ready' : 'empty'
            });
        }).catch((error: unknown) => {
            console.error('fetchOfflineRecords error:', error);
            this.setData({ offlineState: 'error' });
        });
    },

    retryOfflineRecords() {
        this.fetchOfflineRecords(this.data.deviceId);
    },

    startTelemetryTicker() {
        this.stopTelemetryTicker();
        telemetryTick = 0;
        telemetryTimer = setInterval(() => {
            if (this.data.pageState !== 'ready') return;
            telemetryTick += 1;
            this.setData({
                telemetry: buildTelemetry(
                    this.data.deviceId,
                    this.data.device?.heartbeatText || '--',
                    telemetryTick,
                    this.data.device?.status
                )
            });
        }, 5000);
    },

    stopTelemetryTicker() {
        if (telemetryTimer !== null) {
            clearInterval(telemetryTimer);
            telemetryTimer = null;
        }
    },

    fetchChartData(id: number): Promise<void> {
        this.setData({ chartState: 'loading' });
        return request({
            url: `/api/device/chart-data/${id}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code !== 200) {
                this.setData({ chartState: 'error' });
                return;
            }

            const chartData = normalizeChartData(res.data);
            if (chartData.dates.length === 0) {
                this.setData({
                    chartData,
                    chartState: 'empty',
                    latestEarning: '--',
                    chartSummary: { total: '--', average: '--' },
                    chartFocus: { date: '--', value: '--' }
                });
                return;
            }

            const total = chartData.earnings.reduce((sum, value) => sum + value, 0);
            const selectedChartIndex = chartData.earnings.length - 1;
            const latest = chartData.earnings[selectedChartIndex];
            this.setData({
                chartData,
                chartState: 'ready',
                latestEarning: latest.toFixed(2),
                chartSummary: {
                    total: total.toFixed(2),
                    average: (total / chartData.earnings.length).toFixed(2)
                },
                selectedChartIndex,
                chartFocus: {
                    date: shortDate(chartData.dates[selectedChartIndex]),
                    value: latest.toFixed(2)
                }
            }, () => this.drawChart(chartData));
        }).catch((error: unknown) => {
            console.error('fetchChartData error:', error);
            this.setData({ chartState: 'error' });
        });
    },

    retryChart() {
        this.fetchChartData(this.data.deviceId);
    },

    onChartTouch(event: any) {
        if (!chartGeometry || this.data.chartState !== 'ready') return;
        const touch = event.touches?.[0];
        const touchX = Number(touch?.x ?? event.detail?.x);
        if (!Number.isFinite(touchX)) return;

        const ratio = chartGeometry.count === 1
            ? 0
            : Math.max(0, Math.min(1, (touchX - chartGeometry.left) / chartGeometry.width));
        const index = chartGeometry.count === 1
            ? 0
            : Math.round(ratio * (chartGeometry.count - 1));
        const value = this.data.chartData.earnings[index];
        const date = this.data.chartData.dates[index];

        if (!Number.isFinite(value) || !date || index === this.data.selectedChartIndex) return;
        this.setData({
            selectedChartIndex: index,
            chartFocus: {
                date: shortDate(date),
                value: value.toFixed(2)
            }
        }, () => this.drawChart(this.data.chartData));
    },

    drawChart(data: EarningsChartData) {
        if (data.dates.length === 0 || data.earnings.length === 0) return;

        const query = wx.createSelectorQuery();
        query.select('#earningsChart')
            .fields({ node: true, size: true })
            .exec((result: any[]) => {
                if (!result[0]?.node || !result[0].width || !result[0].height) return;

                const canvas = result[0].node;
                const context = canvas.getContext('2d');
                const dpr = wx.getSystemInfoSync().pixelRatio;
                const width = result[0].width;
                const height = result[0].height;
                const padding = { top: 20, right: 18, bottom: 30, left: 18 };
                const chartWidth = width - padding.left - padding.right;
                const chartHeight = height - padding.top - padding.bottom;

                canvas.width = width * dpr;
                canvas.height = height * dpr;
                context.scale(dpr, dpr);
                context.clearRect(0, 0, width, height);

                const maxValue = Math.max(...data.earnings, 0);
                const yMax = maxValue > 0 ? maxValue * 1.2 : 1;
                const points = data.earnings.map((value, index) => ({
                    x: data.earnings.length === 1
                        ? width / 2
                        : padding.left + index * (chartWidth / (data.earnings.length - 1)),
                    y: padding.top + chartHeight - (value / yMax) * chartHeight,
                    value
                }));

                chartGeometry = {
                    left: padding.left,
                    width: chartWidth,
                    count: points.length
                };

                context.strokeStyle = 'rgba(56, 64, 53, 0.10)';
                context.lineWidth = 1;
                for (let index = 0; index < 4; index += 1) {
                    const gridY = padding.top + (chartHeight / 3) * index;
                    context.beginPath();
                    context.moveTo(padding.left, gridY);
                    context.lineTo(width - padding.right, gridY);
                    context.stroke();
                }

                const areaGradient = context.createLinearGradient(0, padding.top, 0, height - padding.bottom);
                areaGradient.addColorStop(0, 'rgba(118, 185, 0, 0.22)');
                areaGradient.addColorStop(0.58, 'rgba(22, 132, 126, 0.07)');
                areaGradient.addColorStop(1, 'rgba(118, 185, 0, 0)');
                context.beginPath();
                context.moveTo(points[0].x, height - padding.bottom);
                points.forEach(point => context.lineTo(point.x, point.y));
                context.lineTo(points[points.length - 1].x, height - padding.bottom);
                context.closePath();
                context.fillStyle = areaGradient;
                context.fill();

                const lineGradient = context.createLinearGradient(padding.left, 0, width - padding.right, 0);
                lineGradient.addColorStop(0, '#76b900');
                lineGradient.addColorStop(1, '#16847e');
                context.beginPath();
                context.moveTo(points[0].x, points[0].y);
                points.slice(1).forEach(point => context.lineTo(point.x, point.y));
                context.strokeStyle = lineGradient;
                context.lineWidth = 2.5;
                context.lineCap = 'round';
                context.lineJoin = 'round';
                context.stroke();

                const selectedIndex = Math.min(this.data.selectedChartIndex, points.length - 1);
                const selectedPoint = points[selectedIndex];
                context.strokeStyle = 'rgba(22, 132, 126, 0.28)';
                context.lineWidth = 1;
                context.beginPath();
                context.moveTo(selectedPoint.x, padding.top);
                context.lineTo(selectedPoint.x, height - padding.bottom);
                context.stroke();

                points.forEach((point, index) => {
                    context.beginPath();
                    context.arc(point.x, point.y, index === selectedIndex ? 4.5 : 2.5, 0, Math.PI * 2);
                    context.fillStyle = index === selectedIndex ? '#111510' : '#ffffff';
                    context.fill();
                    context.strokeStyle = index === selectedIndex ? '#76b900' : '#16847e';
                    context.lineWidth = 2;
                    context.stroke();
                });

                const labelIndexes = new Set([0, Math.floor((data.dates.length - 1) / 2), data.dates.length - 1]);
                context.fillStyle = '#788175';
                context.font = '10px monospace';
                context.textAlign = 'center';
                labelIndexes.forEach(index => {
                    context.fillText(shortDate(data.dates[index]), points[index].x, height - 7);
                });
            });
    },

    copyDeviceIdentity() {
        const identity = this.data.device.identityRaw;
        if (!identity) return;
        wx.setClipboardData({
            data: identity,
            success: () => wx.showToast({ title: '设备编号已复制', icon: 'success' })
        });
    },

    unbindDevice() {
        if (this.data.unbinding) return;
        const id = this.data.deviceId;
        wx.showModal({
            title: '确认解绑',
            content: '解绑后设备将从您的账户移除，确定要解绑吗？',
            confirmText: '解除绑定',
            confirmColor: '#b3453e',
            success: (result) => {
                if (!result.confirm) return;
                this.setData({ unbinding: true });
                request({
                    url: '/api/device/unbind',
                    method: 'POST',
                    data: { id }
                }).then((response: any) => {
                    if (response.code === 200) {
                        wx.showToast({ title: '解绑成功', icon: 'success' });
                        setTimeout(() => wx.navigateBack(), 1500);
                    } else {
                        wx.showToast({ title: response.msg || '解绑失败', icon: 'none' });
                    }
                }).catch((error: unknown) => {
                    console.error('unbindDevice error:', error);
                    wx.showToast({ title: '网络连接异常', icon: 'none' });
                }).finally(() => {
                    this.setData({ unbinding: false });
                });
            }
        });
    }
});
