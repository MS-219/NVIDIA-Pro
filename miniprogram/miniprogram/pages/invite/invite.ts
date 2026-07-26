import { API_BASE } from '../../config';
export { };

interface InvitedUser {
    id: number;
    nickname: string;
    avatarUrl: string;
    registerTime: string;
    deviceCount: number;
    reward: string;
}

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        inviteCode: '',
        inviteUrl: '',
        inviteCount: 0,
        totalReward: '0.00',
        invitedUsers: [] as InvitedUser[],
        loading: false,
        // 我的邀请人
        hasInviter: false,
        inviterNickname: '',
        inviterAvatar: '',
        // 填写邀请码
        inputCode: '',
        showBindInput: false,
        // 等级相关
        userLevel: 0,
        totalDeviceCount: 0,
        onlineDeviceCount: 0,
        currentLevelConfig: null as any,
        nextLevelConfig: null as any,
        currentLevelName: '普通用户',
        nextLevelDesc: ''
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
        this.loadInviteInfo();
        this.loadMyInviter();
    },

    goBack() {
        wx.navigateBack();
    },

    loadInviteInfo() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        // 生成邀请码
        const inviteCode = 'JX' + String(userId).padStart(6, '0');
        const inviteUrl = `https://orin-api.example.invalid/invite?code=${inviteCode}`;

        this.setData({ inviteCode, inviteUrl, loading: true });

        // 获取邀请统计和邀请用户列表
        wx.request({
            url: `${API_BASE}/api/invite/stats`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    this.setData({
                        inviteCount: data.inviteCount || 0,
                        totalReward: data.totalReward || '0.00',
                        userLevel: data.level || 0,
                        totalDeviceCount: data.totalDeviceCount || 0,
                        onlineDeviceCount: data.teamOnlineDeviceCount || 0,
                        invitedUsers: (data.invitedUsers || []).map((u: any) => ({
                            id: u.id,
                            nickname: u.nickname || '用户' + String(u.id).slice(-4),
                            avatarUrl: u.avatarUrl || '',
                            registerTime: this.formatTime(u.registerTime || u.createTime),
                            deviceCount: u.deviceCount || 0,
                            reward: u.reward || '0.00'
                        }))
                    });

                    // 计算等级进度
                    if (data.levelConfigs) {
                        const level = data.level || 0;
                        const totalDevices = data.totalDeviceCount || 0;
                        const current = data.levelConfigs.find((lc: any) => lc.index === level);
                        const next = data.levelConfigs.find((lc: any) => lc.index === level + 1);

                        this.setData({
                            currentLevelConfig: current,
                            nextLevelConfig: next,
                            currentLevelName: current ? current.name : '普通用户'
                        });

                        if (next) {
                            const diff = next.threshold - totalDevices;
                            this.setData({
                                nextLevelDesc: `距离升级 ${next.name} 还差 ${diff > 0 ? diff : 0} 台设备`
                            });
                        } else {
                            this.setData({
                                nextLevelDesc: '您已达到最高等级'
                            });
                        }
                    }
                }
            },
            complete: () => {
                this.setData({ loading: false });
            }
        });
    },

    loadMyInviter() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        wx.request({
            url: `${API_BASE}/api/invite/inviter`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    this.setData({
                        hasInviter: data.hasInviter || false,
                        inviterNickname: data.inviterNickname || '',
                        inviterAvatar: data.inviterAvatar || ''
                    });
                }
            }
        });
    },

    formatTime(timeStr: string) {
        if (!timeStr) return '';
        return timeStr.replace('T', ' ').substring(0, 10);
    },

    copyInviteCode() {
        wx.setClipboardData({
            data: this.data.inviteCode,
            success: () => {
                wx.showToast({ title: '邀请码已复制', icon: 'success' });
            }
        });
    },

    // 显示填写邀请码输入框
    showBindInput() {
        this.setData({ showBindInput: true });
    },

    // 输入邀请码
    onInputCode(e: any) {
        this.setData({ inputCode: e.detail.value.toUpperCase() });
    },

    // 提交绑定邀请码
    submitBindCode() {
        const { inputCode } = this.data;
        const userId = wx.getStorageSync('userId');

        if (!inputCode || inputCode.length < 3) {
            wx.showToast({ title: '请输入正确的邀请码', icon: 'none' });
            return;
        }

        wx.showLoading({ title: '绑定中...', mask: true });
        wx.request({
            url: `${API_BASE}/api/invite/bind`,
            method: 'POST',
            data: { userId, inviteCode: inputCode },
            success: (res: any) => {
                wx.hideLoading();
                if (res.data.code === 200) {
                    wx.showToast({ title: '绑定成功', icon: 'success' });
                    this.setData({ showBindInput: false, inputCode: '' });
                    this.loadMyInviter();
                } else {
                    wx.showToast({ title: res.data.msg || '绑定失败', icon: 'none' });
                }
            },
            fail: () => {
                wx.hideLoading();
                wx.showToast({ title: '网络错误', icon: 'none' });
            }
        });
    },

    cancelBind() {
        this.setData({ showBindInput: false, inputCode: '' });
    },

    onShareAppMessage() {
        return {
            title: '聚芯 Orin - 邀请你一起赚收益',
            path: `/pages/index/index?inviteCode=${this.data.inviteCode}`,
            imageUrl: ''
        };
    }
});


