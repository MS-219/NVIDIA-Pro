import { request } from '../../utils/request';
export { };

interface DeviceWithOwner {
    id: number;
    sn: string;
    name: string;
    type: number;
    status: number;
    statusText: string;
    lastHeartbeat: string;
    createTime: string;
    ownerId: number;
    ownerNickname: string;
    ownerAvatar: string;
    ownerLevel: number;
}

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        loading: true,
        devices: [] as DeviceWithOwner[],
        totalCount: 0,
        onlineCount: 0,
        offlineCount: 0,
        // 筛选状态: all, online, offline
        filterStatus: 'all',
        pageTitle: '全部设备'
    },

    onLoad(options: any) {
        const menuButton = wx.getMenuButtonBoundingClientRect();
        const status = options.status || 'all';
        let pageTitle = '全部设备';
        if (status === 'online') pageTitle = '在线设备';
        else if (status === 'offline') pageTitle = '离线设备';

        this.setData({
            navBarTop: menuButton.top,
            navBarHeight: menuButton.height,
            filterStatus: status,
            pageTitle: pageTitle
        });

        this.loadDevices();
    },

    onPullDownRefresh() {
        this.loadDevices().finally(() => {
            wx.stopPullDownRefresh();
        });
    },

    goBack() {
        wx.navigateBack();
    },

    loadDevices() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({ loading: false });
            return Promise.resolve();
        }

        this.setData({ loading: true });

        return request({
            url: '/api/invite/all-partner-devices',
            method: 'GET',
            data: {
                userId,
                status: this.data.filterStatus
            }
        }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    devices: (data.devices || []).map((d: any) => ({
                        ...d,
                        lastHeartbeat: this.formatTime(d.lastHeartbeat),
                        createTime: this.formatTime(d.createTime)
                    })),
                    totalCount: data.totalCount || 0,
                    onlineCount: data.onlineCount || 0,
                    offlineCount: data.offlineCount || 0
                });
            }
        }).catch(err => {
            console.error('获取设备列表失败:', err);
            wx.showToast({ title: '加载失败', icon: 'none' });
        }).finally(() => {
            this.setData({ loading: false });
        });
    },

    formatTime(timeStr: string) {
        if (!timeStr) return '-';
        return timeStr.replace('T', ' ').substring(0, 16);
    },

    // 切换筛选状态
    onFilterChange(e: any) {
        const status = e.currentTarget.dataset.status;
        if (status !== this.data.filterStatus) {
            let pageTitle = '全部设备';
            if (status === 'online') pageTitle = '在线设备';
            else if (status === 'offline') pageTitle = '离线设备';

            this.setData({ filterStatus: status, pageTitle: pageTitle });
            this.loadDevices();
        }
    },

    // 点击设备查看详情
    onDeviceTap(e: any) {
        const device = e.currentTarget.dataset.device;
        wx.navigateTo({
            url: `/pages/partner-device-detail/partner-device-detail?deviceId=${device.id}`
        });
    }
});
