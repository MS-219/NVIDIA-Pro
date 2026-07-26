import { request } from '../../utils/request';
export { };

interface DeviceItem {
    id: number;
    sn: string;
    name: string;
    type: number;
    status: number;
    statusText: string;
    lastHeartbeat: string;
    createTime: string;
}

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        loading: true,
        memberId: 0,
        memberNickname: '',
        memberAvatar: '',
        memberLevel: 0,
        devices: [] as DeviceItem[],
        totalCount: 0,
        onlineCount: 0,
        offlineCount: 0,
        // 筛选状态: all, online, offline
        filterStatus: 'all'
    },

    onLoad(options: any) {
        const menuButton = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButton.top,
            navBarHeight: menuButton.height,
            memberId: options.memberId ? parseInt(options.memberId) : 0,
            filterStatus: options.status || 'all'
        });

        if (this.data.memberId) {
            this.loadMemberDevices();
        }
    },

    onPullDownRefresh() {
        this.loadMemberDevices().finally(() => {
            wx.stopPullDownRefresh();
        });
    },

    goBack() {
        wx.navigateBack();
    },

    loadMemberDevices() {
        const userId = wx.getStorageSync('userId');
        if (!userId || !this.data.memberId) {
            this.setData({ loading: false });
            return Promise.resolve();
        }

        this.setData({ loading: true });

        const params: any = {
            userId,
            memberId: this.data.memberId
        };

        // 添加筛选参数
        if (this.data.filterStatus !== 'all') {
            params.status = this.data.filterStatus;
        }

        return request({
            url: '/api/invite/member-devices',
            method: 'GET',
            data: params
        }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    memberNickname: data.memberNickname || '',
                    memberAvatar: data.memberAvatar || '',
                    memberLevel: data.memberLevel || 0,
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
            this.setData({ filterStatus: status });
            this.loadMemberDevices();
        }
    },

    // 点击设备，跳转到伙伴设备详情页查看收益
    onDeviceTap(e: any) {
        const deviceId = e.currentTarget.dataset.id;
        wx.navigateTo({
            url: `/pages/partner-device-detail/partner-device-detail?deviceId=${deviceId}`
        });
    }
});
