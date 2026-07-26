
import { request } from '../../utils/request';

Page({

    /**
     * 页面的初始数据
     */
    data: {
        deviceId: 0,
        device: {} as any,
        chartData: {
            dates: [],
            earnings: []
        }
    },

    /**
     * 生命周期函数--监听页面加载
     */
    onLoad(options: any) {
        if (options.id) {
            this.setData({ deviceId: options.id });
            this.fetchDeviceDetail(options.id);
            this.fetchChartData(options.id);
        }
    },

    // 返回上一页
    goBack() {
        wx.navigateBack();
    },

    // 获取设备详情
    fetchDeviceDetail(id: any) {
        request({
            url: `/api/device/detail/${id}`,
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const device = res.data;
                // 格式化时间
                if (device.bindTime) device.bindTime = device.bindTime.replace('T', ' ');
                this.setData({ device });
            } else {
                wx.showToast({ title: res.msg || '获取详情失败', icon: 'none' });
            }
        }).catch(err => {
            console.error('fetchDeviceDetail error:', err);
        });
    },

    // 获取图表数据
    fetchChartData(id: any) {
        request({
            url: `/api/device/chart-data/${id}`,
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                this.setData({
                    chartData: res.data
                });
                // 开始绘制
                this.drawChart(res.data);
            }
        }).catch(err => {
            console.error('fetchChartData error:', err);
        });
    },

    // 绘制趋势图 (折线+渐变区域)
    drawChart(data: any) {
        const dates = data.dates || [];
        const earnings = data.earnings || [];

        if (dates.length === 0) return;

        const query = wx.createSelectorQuery();
        query.select('#earningsChart')
            .fields({ node: true, size: true })
            .exec((res) => {
                if (!res[0]) return;

                const canvas = res[0].node;
                const ctx = canvas.getContext('2d');
                const dpr = wx.getSystemInfoSync().pixelRatio;

                canvas.width = res[0].width * dpr;
                canvas.height = res[0].height * dpr;
                ctx.scale(dpr, dpr);

                const width = res[0].width;
                const height = res[0].height;

                // 绘图配置
                const padding = { top: 35, right: 24, bottom: 28, left: 24 };
                const chartWidth = width - padding.left - padding.right;
                const chartHeight = height - padding.top - padding.bottom;

                // 清空画布
                ctx.clearRect(0, 0, width, height);

                // 计算Y轴比例
                let maxVal = Math.max(...earnings);
                maxVal = maxVal > 0 ? maxVal * 1.25 : 10; // 留出25%顶部空间用于显示数值

                // 计算坐标点
                const points = earnings.map((val: number, index: number) => {
                    // 如果只有1个点，居中显示；否则平分宽度
                    const x = dates.length === 1
                        ? width / 2
                        : padding.left + (index * (chartWidth / (dates.length - 1)));

                    const y = padding.top + chartHeight - (val / maxVal) * chartHeight;
                    return { x, y, val };
                });

                ctx.strokeStyle = 'rgba(47, 125, 243, 0.08)';
                ctx.lineWidth = 1;
                for (let i = 0; i < 4; i++) {
                    const gridY = padding.top + (chartHeight / 3) * i;
                    ctx.beginPath();
                    ctx.moveTo(padding.left, gridY);
                    ctx.lineTo(width - padding.right, gridY);
                    ctx.stroke();
                }

                // 1. 绘制渐变区域背景
                const gradient = ctx.createLinearGradient(0, padding.top, 0, height - padding.bottom);
                gradient.addColorStop(0, 'rgba(47, 125, 243, 0.18)');
                gradient.addColorStop(0.58, 'rgba(24, 180, 97, 0.07)');
                gradient.addColorStop(1, 'rgba(47, 125, 243, 0.0)');

                ctx.beginPath();
                if (points.length > 0) {
                    ctx.moveTo(points[0].x, height - padding.bottom); // 起点底部
                    points.forEach((p: any) => ctx.lineTo(p.x, p.y));        // 连接各点
                    ctx.lineTo(points[points.length - 1].x, height - padding.bottom); // 终点底部
                }
                ctx.closePath();
                ctx.fillStyle = gradient;
                ctx.fill();

                // 2. 绘制折线
                ctx.beginPath();
                if (points.length > 0) {
                    ctx.moveTo(points[0].x, points[0].y);
                    points.forEach((p: any) => ctx.lineTo(p.x, p.y));
                }
                const lineGradient = ctx.createLinearGradient(padding.left, 0, width - padding.right, 0);
                lineGradient.addColorStop(0, '#2f7df3');
                lineGradient.addColorStop(1, '#18b461');
                ctx.strokeStyle = lineGradient;
                ctx.lineWidth = 2.5;
                ctx.lineCap = 'round';
                ctx.lineJoin = 'round';
                ctx.stroke();

                // 3. 绘制数据点和标签
                points.forEach((p: any, i: number) => {
                    // 绘制白底描边圆点
                    ctx.beginPath();
                    ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
                    ctx.fillStyle = '#fff';
                    ctx.fill();
                    ctx.strokeStyle = i === points.length - 1 ? '#f97316' : '#2f7df3';
                    ctx.lineWidth = 2;
                    ctx.stroke();

                    // 绘制数值 (仅当数值大于0时显示)
                    if (p.val > 0) {
                        ctx.fillStyle = i === points.length - 1 ? '#f97316' : '#334155';
                        ctx.font = 'bold 11px -apple-system, sans-serif';
                        ctx.textAlign = 'center';
                        ctx.fillText(p.val.toFixed(2), p.x, p.y - 12);
                    }

                    // 绘制X轴日期 (只显示日，如 06)
                    ctx.fillStyle = '#94a3b8';
                    ctx.font = '11px sans-serif';
                    ctx.textAlign = 'center';
                    const dateStr = dates[i].split('-')[2]; // 假设格式为 YYYY-MM-DD
                    ctx.fillText(dateStr, p.x, height - 5);
                });
            });
    },

    // 解绑设备
    unbindDevice() {
        const id = this.data.deviceId;
        wx.showModal({
            title: '确认解绑',
            content: '解绑后设备将从您的账户移除，确定要解绑吗？',
            success: (res) => {
                if (res.confirm) {
                    request({
                        url: '/api/device/unbind',
                        method: 'POST',
                        data: { id }
                    }).then(resp => {
                        if (resp.code === 200) {
                            wx.showToast({ title: '解绑成功', icon: 'success' });
                            setTimeout(() => {
                                wx.navigateBack();
                            }, 1500);
                        } else {
                            wx.showToast({ title: resp.msg || '解绑失败', icon: 'none' });
                        }
                    }).catch(err => {
                        console.error('unbindDevice error:', err);
                    });
                }
            }
        });
    }
})
