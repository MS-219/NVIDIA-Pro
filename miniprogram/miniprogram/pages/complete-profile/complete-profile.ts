import { request } from '../../utils/request';
import { API_BASE } from '../../config';
export { };

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        avatarUrl: '',
        nickname: '',
        tempAvatarPath: '',
        saving: false,
        forceMode: false  // 强制完善模式，不可跳过
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height,
            forceMode: options.force === '1'  // URL参数 force=1 表示强制模式
        });

        // 加载已有信息
        const userInfo = wx.getStorageSync('userInfo');
        if (userInfo) {
            this.setData({
                avatarUrl: userInfo.avatarUrl || '',
                nickname: (userInfo.nickname && userInfo.nickname !== '微信用户') ? userInfo.nickname : ''
            });
        }
    },

    // 选择头像
    onChooseAvatar(e: any) {
        const avatarUrl = e.detail.avatarUrl;
        if (avatarUrl) {
            this.setData({
                tempAvatarPath: avatarUrl,
                avatarUrl: avatarUrl
            });
        }
    },

    syncNickname(e: any) {
        const value = e && e.detail && typeof e.detail.value === 'string'
            ? e.detail.value
            : this.data.nickname;

        if (value !== this.data.nickname) {
            this.setData({ nickname: value });
        }
        return value;
    },

    // 键盘输入时同步，并把最终值返回给原生输入框。
    onNicknameInput(e: any) {
        return this.syncNickname(e);
    },

    // 微信昵称候选在不同客户端可能以 change、blur 或 confirm 收口。
    onNicknameCommit(e: any) {
        this.syncNickname(e);
    },

    // 跳过
    onSkip() {
        if (this.data.forceMode) {
            wx.showToast({ title: '请先完善信息', icon: 'none' });
            return;
        }
        wx.navigateBack();
    },

    // 保存
    async onSave(e?: any) {
        const { tempAvatarPath, avatarUrl, forceMode } = this.data;
        const submittedNickname = e && e.detail && e.detail.value && typeof e.detail.value.nickname === 'string'
            ? e.detail.value.nickname
            : this.data.nickname;
        const nickname = submittedNickname.trim();

        if (!nickname) {
            wx.showToast({ title: '请输入昵称', icon: 'none' });
            return;
        }

        // 强制模式下必须设置头像
        if (forceMode && !avatarUrl) {
            wx.showToast({ title: '请设置头像', icon: 'none' });
            return;
        }

        this.setData({ saving: true });

        try {
            let finalAvatarUrl = avatarUrl;
            const token = wx.getStorageSync('token');

            // 如果有新选择的头像，先上传
            if (tempAvatarPath && (tempAvatarPath.startsWith('http://tmp') || tempAvatarPath.startsWith('wxfile://'))) {
                wx.showLoading({ title: '上传头像中...', mask: true });

                const uploadRes: any = await new Promise((resolve, reject) => {
                    wx.uploadFile({
                        url: `${API_BASE}/api/upload/image`,
                        filePath: tempAvatarPath,
                        name: 'file',
                        header: {
                            'Authorization': `Bearer ${token}`
                        },
                        success: resolve,
                        fail: reject
                    });
                });

                wx.hideLoading();
                const result = JSON.parse(uploadRes.data);
                if (result.code === 200 && result.data && result.data.url) {
                    finalAvatarUrl = this.formatUrl(result.data.url);
                } else {
                    throw new Error(result.msg || '上传头像失败');
                }
            }

            // 更新用户信息
            wx.showLoading({ title: '保存中...', mask: true });

            const userId = wx.getStorageSync('userId');
            const res = await request({
                url: '/api/user/updateProfile',
                method: 'POST',
                data: {
                    userId,
                    nickname,
                    avatarUrl: finalAvatarUrl
                }
            });

            wx.hideLoading();

            if (res.code === 200) {
                // 更新本地存储
                const userInfo = wx.getStorageSync('userInfo') || {};
                userInfo.nickname = nickname;
                userInfo.avatarUrl = finalAvatarUrl;
                wx.setStorageSync('userInfo', userInfo);

                // 记录资料更新时间，供“我的”页丢弃保存前发出的旧资料响应。
                const profileUpdatedAt = Date.now();
                wx.setStorageSync('last_complete_profile_time', profileUpdatedAt);
                wx.setStorageSync('last_profile_update_time', profileUpdatedAt);

                // 关键点：设置永久完成标记，后续不再强制弹窗
                wx.setStorageSync('profile_completed_once', true);

                wx.showToast({ title: '保存成功', icon: 'success' });

                setTimeout(() => {
                    // 如果是强制模式且当前只有这一层页面，尝试返回上一页或回到首页
                    const pages = getCurrentPages();
                    if (pages.length > 1) {
                        wx.navigateBack();
                    } else {
                        wx.switchTab({ url: '/pages/index/index' });
                    }
                }, 1500);
            } else {
                wx.showToast({ title: res.msg || '保存失败', icon: 'none' });
            }
        } catch (error: any) {
            wx.hideLoading();
            wx.showToast({ title: error.message || '操作失败，请重试', icon: 'none' });
        } finally {
            this.setData({ saving: false });
        }
    },

    // URL 格式化
    formatUrl(url: string) {
        if (!url) return '';
        let fullUrl = url;
        if (url.includes('/uploads/')) {
            const idx = url.indexOf('/uploads/');
            fullUrl = API_BASE + url.substring(idx);
        }
        if (API_BASE.startsWith('https') && fullUrl.startsWith('http://')) {
            fullUrl = fullUrl.replace('http://', 'https://');
        }
        return fullUrl;
    }
});
