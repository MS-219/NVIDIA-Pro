import { request } from '../../utils/request';
export { };

interface PartnerMember {
    id: number;
    nickname: string;
    avatarUrl: string;
    createTime: string;
    level: number;
    deviceCount: number;
    onlineCount: number;
    offlineCount: number;
}

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        loading: true,
        members: [] as PartnerMember[],
        totalMembers: 0,
        totalDevices: 0,
        totalOnline: 0,
        totalOffline: 0
    },

    onLoad() {
        const menuButton = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButton.top,
            navBarHeight: menuButton.height
        });
        this.loadPartnerDevices();
    },

    onShow() {
        // 每次显示页面时刷新数据
        this.loadPartnerDevices();
    },

    onPullDownRefresh() {
        this.loadPartnerDevices().finally(() => {
            wx.stopPullDownRefresh();
        });
    },

    goBack() {
        wx.navigateBack();
    },

    loadPartnerDevices() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({ loading: false });
            return Promise.resolve();
        }

        this.setData({ loading: true });

        return request({
            url: '/api/invite/partner-devices',
            method: 'GET',
            data: { userId }
        }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    members: (data.members || []).map((m: any) => ({
                        id: m.id,
                        nickname: m.nickname || '用户' + m.id,
                        avatarUrl: m.avatarUrl || '',
                        createTime: this.formatTime(m.createTime),
                        level: m.level || 0,
                        deviceCount: m.deviceCount || 0,
                        onlineCount: m.onlineCount || 0,
                        offlineCount: m.offlineCount || 0
                    })),
                    totalMembers: data.totalMembers || 0,
                    totalDevices: data.totalDevices || 0,
                    totalOnline: data.totalOnline || 0,
                    totalOffline: data.totalOffline || 0
                });
            }
        }).catch(err => {
            console.error('获取伙伴设备失败:', err);
        }).finally(() => {
            this.setData({ loading: false });
        });
    },

    formatTime(timeStr: string) {
        if (!timeStr) return '';
        return timeStr.replace('T', ' ').substring(0, 10);
    },

    // 点击用户，跳转到设备详情页
    onMemberTap(e: any) {
        const member = e.currentTarget.dataset.member;
        wx.navigateTo({
            url: `/pages/member-devices/member-devices?memberId=${member.id}`
        });
    },

    // 点击统计区域，跳转到设备列表页面（按状态筛选）
    onStatTap(e: any) {
        const status = e.currentTarget.dataset.status;
        wx.navigateTo({
            url: `/pages/all-partner-devices/all-partner-devices?status=${status}`
        });
    }
});
