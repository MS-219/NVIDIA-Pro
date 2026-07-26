import { request } from '../../utils/request';
export { }; // 使文件成为 ES 模块

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        isLoggedIn: false,
        needCompleteInfo: false,
        userInfo: {
            id: '000000',
            nickname: '',
            avatarUrl: '',
            phone: '',
            quota: 0,
            level: 0
        },
        hashrateRate: 100, // 算力兑换比例
        walletInfo: {
            balance: '0.00',
            totalEarnings: '0.00',
            deviceCount: 0,
            displayHashrate: 0
        },
        deviceStats: {
            total: 0,
            online: 0,
            offline: 0
        },
        menuList: [
            [
                { id: 'earnings', icon: '📊', text: '收益明细', iconClass: 'icon-earnings' },
                { id: 'devices', icon: '📱', text: '我的设备', iconClass: 'icon-device' },
                { id: 'payment', icon: '💳', text: '收款信息设置', iconClass: 'icon-payment' },
                { id: 'invite', icon: '🎁', text: '邀请好友', iconClass: 'icon-invite' },
                { id: 'exchange', icon: '📦', text: '兑换设备', iconClass: 'icon-exchange' },
                { id: 'exchange-orders', icon: '📋', text: '兑换订单', iconClass: 'icon-orders' }
            ],
            [
                { id: 'help', icon: '❓', text: '帮助中心', iconClass: 'icon-help' },
                { id: 'feedback', icon: '💬', text: '意见反馈', iconClass: 'icon-feedback' }
            ]
        ]
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });

        // 检查登录状态
        this.checkLoginStatus();
    },

    onShow() {
        if (typeof this.getTabBar === 'function' && this.getTabBar()) {
            this.getTabBar().setData({
                selected: 2
            })
        }

        // 每次显示页面时重新检查登录状态（含 token 有效性验证）
        this.verifyLoginStatus();
    },

    // 验证 token 有效性（发请求验证，失败则自动清除登录态）
    verifyLoginStatus() {
        const token = wx.getStorageSync('token');
        const userId = wx.getStorageSync('userId');

        if (!token || !userId) {
            // 没有登录信息，显示登出状态
            this.resetToLoggedOut();
            return;
        }

        // 先用本地缓存显示（避免白屏）
        const userInfo = wx.getStorageSync('userInfo');
        if (!this.data.isLoggedIn) {
            const displayId = String(userId).padStart(6, '0');
            this.setData({
                isLoggedIn: true,
                userInfo: userInfo ? { ...userInfo, id: displayId } : { id: displayId, nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0 }
            });
        }

        // 发请求验证 token 是否真的有效
        request({
            url: `/api/user/info/${userId}`,
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                // token 有效，正常刷新数据
                const data = res.data;
                const lastCompleteTime = wx.getStorageSync('last_complete_profile_time');
                const shouldSkipStorageUpdate = lastCompleteTime && (Date.now() - lastCompleteTime < 5000);

                const newUserInfo = {
                    id: String(data.id).padStart(6, '0'),
                    nickname: data.nickname || '',
                    avatarUrl: this.formatUrl(data.avatarUrl || ''),
                    phone: data.phone || '',
                    quota: data.quota || 0,
                    level: data.level || 0
                };

                this.setData({ isLoggedIn: true, userInfo: newUserInfo });
                if (!shouldSkipStorageUpdate) {
                    wx.setStorageSync('userInfo', newUserInfo);
                }
                this.checkNeedCompleteInfo();
                this.fetchSettings();
                // 获取钱包和设备统计数据
                this.fetchWalletAndDeviceStats();
                this.checkAndRedirectToCompleteProfile();
            } else {
                // 非正常响应，可能 token 无效
                this.resetToLoggedOut();
            }
        }).catch(err => {
            // 如果是 401 token 过期，request 工具会自动清除存储
            // 这里同步页面状态为登出
            if (err && err.code === 401) {
                this.resetToLoggedOut();
            }
        });
    },

    // 重置为登出状态
    resetToLoggedOut() {
        this.setData({
            isLoggedIn: false,
            needCompleteInfo: false,
            userInfo: { id: '000000', nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0 },
            walletInfo: { balance: '0.00', totalEarnings: '0.00', deviceCount: 0, displayHashrate: 0 },
            deviceStats: { total: 0, online: 0, offline: 0 }
        });
    },

    // 检查并跳转到完善资料页面 (集中管理跳转点)
    checkAndRedirectToCompleteProfile() {
        // 1. 如果用户曾经成功完善过资料，永久不再强制弹窗
        const hasCompletedOnce = wx.getStorageSync('profile_completed_once');
        if (hasCompletedOnce) {
            return;
        }

        // 2. 如果之前刚完善过一段时间内，不再弹窗（延长到1小时，应对各种延迟场景）
        const lastCompleteTime = wx.getStorageSync('last_complete_profile_time');
        if (lastCompleteTime && Date.now() - lastCompleteTime < 3600000) { // 1小时
            return;
        }

        // 3. 每天最多提示一次
        const today = new Date().toDateString();
        const lastPromptDate = wx.getStorageSync('last_profile_prompt_date');
        if (lastPromptDate === today) {
            return;
        }

        // 使用页面实际数据（已从服务端获取）来判断，而不是可能过时的本地缓存
        const userInfo = this.data.userInfo;
        const hasAvatar = userInfo && userInfo.avatarUrl && userInfo.avatarUrl.length > 10;
        const hasValidNickname = userInfo && userInfo.nickname && userInfo.nickname !== '微信用户' && userInfo.nickname.length > 0;

        // 只要有头像或有效昵称任一个，就不弹窗
        if (hasAvatar || hasValidNickname) {
            // 自动标记为已完善，后续不再弹
            wx.setStorageSync('profile_completed_once', true);
            return;
        }

        const pages = getCurrentPages();
        const currentPage = pages[pages.length - 1];
        // 已经在该页面，或者正在跳转中
        if (currentPage.route.includes('complete-profile')) return;

        // 记录今天已经提示过
        wx.setStorageSync('last_profile_prompt_date', today);

        wx.navigateTo({
            url: '/pages/complete-profile/complete-profile?force=1'
        });
    },

    // 检查登录状态
    checkLoginStatus() {
        const token = wx.getStorageSync('token');
        const userId = wx.getStorageSync('userId');
        const userInfo = wx.getStorageSync('userInfo');

        if (token && userId) {
            const displayId = String(userId).padStart(6, '0');
            this.setData({
                isLoggedIn: true,
                userInfo: userInfo ? { ...userInfo, id: displayId } : { id: displayId, nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0 }
            });
            this.fetchSettings();
            this.fetchUserData();
            this.checkNeedCompleteInfo();
            // 注意：这里不再立即调用跳转，统一由 onShow 处理
        }
    },

    // 检查是否需要完善信息（仅用于主页视觉标识）
    checkNeedCompleteInfo() {
        const { userInfo } = this.data;
        const needComplete = !userInfo.avatarUrl ||
            !userInfo.nickname ||
            userInfo.nickname === '微信用户';
        this.setData({ needCompleteInfo: needComplete });
    },

    // 获取系统配置
    fetchSettings(callback?: () => void) {
        request({
            url: '/api/settings/hashrate-rate',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const rate = parseInt(res.data);
                if (!isNaN(rate)) {
                    this.setData({ hashrateRate: rate });
                }
            }
        }).finally(() => {
            if (callback) callback();
        });
    },

    // 获取用户数据
    fetchUserData() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        // 获取用户完整信息
        request({
            url: `/api/user/info/${userId}`,
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                // 关键防御：如果本地刚刚更新过（带了标志），则不让服务器老数据覆盖本地 storage
                const lastCompleteTime = wx.getStorageSync('last_complete_profile_time');
                const shouldSkipStorageUpdate = lastCompleteTime && (Date.now() - lastCompleteTime < 5000);

                const newUserInfo = {
                    id: String(data.id).padStart(6, '0'),
                    nickname: data.nickname || '',
                    avatarUrl: this.formatUrl(data.avatarUrl || ''),
                    phone: data.phone || '',
                    quota: data.quota || 0,
                    level: data.level || 0
                };

                this.setData({ userInfo: newUserInfo });
                if (!shouldSkipStorageUpdate) {
                    wx.setStorageSync('userInfo', newUserInfo);
                }
                this.checkNeedCompleteInfo();
            }
        });

        // 获取钱包和设备统计
        request({
            url: '/api/statistics/earnings',
            method: 'GET',
            data: { userId }
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    walletInfo: {
                        balance: data.currentBalance || '0.00',
                        totalEarnings: data.totalEarnings || data.total || '0.00', // 累计收益
                        deviceCount: data.deviceCount || 0,
                        displayHashrate: Math.round((parseFloat(data.currentBalance) || 0) * (this.data.hashrateRate || 100)) // 动态计算聚芯 Orin值
                    },
                    deviceStats: {
                        total: data.deviceCount || 0,
                        online: data.onlineCount || 0,
                        offline: (data.deviceCount || 0) - (data.onlineCount || 0)
                    }
                });
            }
        });
    },

    // 获取钱包和设备统计（独立方法，供 verifyLoginStatus 调用）
    fetchWalletAndDeviceStats() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        request({
            url: '/api/statistics/earnings',
            method: 'GET',
            data: { userId }
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    walletInfo: {
                        balance: data.currentBalance || '0.00',
                        totalEarnings: data.totalEarnings || data.total || '0.00',
                        deviceCount: data.deviceCount || 0,
                        displayHashrate: Math.round((parseFloat(data.currentBalance) || 0) * (this.data.hashrateRate || 100))
                    },
                    deviceStats: {
                        total: data.deviceCount || 0,
                        online: data.onlineCount || 0,
                        offline: (data.deviceCount || 0) - (data.onlineCount || 0)
                    }
                });
            }
        });
    },

    // 登录 - 微信一键登录
    onLogin() {
        this.wxLogin();
    },

    // 微信登录
    wxLogin() {
        wx.showLoading({ title: '登录中...' });

        wx.login({
            success: (loginRes) => {
                if (loginRes.code) {
                    this.doLogin(loginRes.code, null);
                } else {
                    wx.hideLoading();
                    wx.showToast({ title: '登录失败', icon: 'none' });
                }
            },
            fail: () => {
                wx.hideLoading();
                wx.showToast({ title: '登录失败', icon: 'none' });
            }
        });
    },

    // 执行登录请求
    doLogin(code: string, userInfo: any) {
        const pendingInviteCode = wx.getStorageSync('pendingInviteCode') || '';

        request({
            url: '/api/user/wxLogin',
            method: 'POST',
            data: {
                code: code,
                nickname: userInfo?.nickName || '',
                avatarUrl: userInfo?.avatarUrl || '',
                inviteCode: pendingInviteCode
            }
        }).then(res => {
            wx.hideLoading();
            if (res.code === 200) {
                const data = res.data;
                wx.removeStorageSync('pendingInviteCode');

                // 保存登录状态
                wx.setStorageSync('token', data.token);
                wx.setStorageSync('userId', data.userId);
                const savedUserInfo = {
                    id: data.userId,
                    nickname: data.nickname || '',
                    avatarUrl: data.avatarUrl || '',
                    phone: data.phone || '',
                    quota: data.quota || 0,
                    level: data.level || 0
                };
                wx.setStorageSync('userInfo', savedUserInfo);

                this.setData({
                    isLoggedIn: true,
                    userInfo: { ...savedUserInfo, id: String(data.userId).padStart(6, '0') }
                });

                wx.showToast({ title: '登录成功', icon: 'success' });

                // 登录成功后直接触发数据和跳转检测
                this.fetchUserData();
                setTimeout(() => {
                    this.checkAndRedirectToCompleteProfile();
                }, 500);
            } else {
                wx.showToast({ title: res.msg || '登录失败', icon: 'none' });
            }
        }).catch(err => {
            wx.hideLoading();
            console.error('doLogin error:', err);
        });
    },

    // ========== 用户信息完善功能 ==========

    // URL 补全与 HTTPS 强制升级
    formatUrl(url: string) {
        if (!url) return '';
        let fullUrl = url;

        // 1. 统一把历史上传地址收口到主域名，修复 IP/端口头像地址
        if (url.includes('/uploads/')) {
            const { API_BASE } = require('../../config');
            const idx = url.indexOf('/uploads/');
            fullUrl = API_BASE + url.substring(idx);
        }

        // 2. 针对生产环境的简单处理
        if (fullUrl.startsWith('https://orin-api.example.invalid')) {
            fullUrl = fullUrl.replace('http://', 'https://');
        }

        return fullUrl;
    },

    // 选择头像（微信组件方式）
    onChooseAvatar(e: any) {
        const avatarUrl = e.detail.avatarUrl;
        if (!avatarUrl) return;

        wx.showLoading({ title: '上传中...', mask: true });

        const { API_BASE } = require('../../config');
        const token = wx.getStorageSync('token');
        wx.uploadFile({
            url: `${API_BASE}/api/upload/image`,
            filePath: avatarUrl,
            name: 'file',
            header: {
                'Authorization': `Bearer ${token}`
            },
            success: (uploadRes: any) => {
                wx.hideLoading(); // 必须先关闭 Loading
                try {
                    const result = JSON.parse(uploadRes.data);
                    if (result.code === 200 && result.data && result.data.url) {
                        const fullUrl = this.formatUrl(result.data.url);
                        this.updateUserProfile({ avatarUrl: fullUrl });
                    } else {
                        wx.showToast({ title: '上传失败', icon: 'none' });
                    }
                } catch (e) {
                    wx.showToast({ title: '数据解析失败', icon: 'none' });
                }
            },
            fail: () => {
                wx.hideLoading();
                wx.showToast({ title: '上传网络错误', icon: 'none' });
            }
        });
    },

    // 昵称输入
    onNicknameInput(e: any) {
        const nickname = e.detail.value;
        if (nickname && nickname.trim() && nickname.trim() !== this.data.userInfo.nickname) {
            this.updateUserProfile({ nickname: nickname.trim() });
        }
    },

    // 编辑昵称（已有昵称时点击）
    onEditNickname() {
        // 可以弹出输入框让用户修改
        wx.showModal({
            title: '修改昵称',
            editable: true,
            placeholderText: '请输入新昵称',
            success: (res) => {
                if (res.confirm && res.content && res.content.trim()) {
                    this.updateUserProfile({ nickname: res.content.trim() });
                }
            }
        });
    },

    // 获取手机号
    onGetPhoneNumber(e: any) {
        if (e.detail.errMsg !== 'getPhoneNumber:ok') {
            wx.showToast({ title: '取消获取手机号', icon: 'none' });
            return;
        }

        const code = e.detail.code;
        if (!code) {
            wx.showToast({ title: '获取手机号失败', icon: 'none' });
            return;
        }

        wx.showLoading({ title: '绑定中...' });

        // 发送 code 到后端解密获取手机号
        request({
            url: '/api/user/bindPhone',
            method: 'POST',
            data: {
                userId: wx.getStorageSync('userId'),
                code: code
            }
        }).then(res => {
            wx.hideLoading();
            if (res.code === 200) {
                const phone = res.data.phone;
                const newUserInfo = { ...this.data.userInfo, phone };
                this.setData({ userInfo: newUserInfo });
                wx.setStorageSync('userInfo', newUserInfo);
                wx.showToast({ title: '绑定成功', icon: 'success' });
                this.checkNeedCompleteInfo();
            } else {
                wx.showToast({ title: res.msg || '绑定失败', icon: 'none' });
            }
        }).catch(err => {
            wx.hideLoading();
            console.error('bindPhone error:', err);
        });
    },

    // 统一更新个人主页数据
    refreshAllData() {
        this.fetchUserData();
    },

    // 统一个人信息更新方法
    updateUserProfile(data: { nickname?: string; avatarUrl?: string }) {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        // 设置一个标志位，防止在上传头像过程中触发重复的 Loading
        const isUploading = data.avatarUrl && (data.avatarUrl.startsWith('http') || data.avatarUrl.startsWith('https'));

        if (!isUploading) {
            wx.showLoading({ title: '保存中...', mask: true });
        }

        request({
            url: '/api/user/updateProfile',
            method: 'POST',
            data: {
                userId,
                ...data
            }
        }).then(res => {
            if (!isUploading) {
                wx.hideLoading();
            }

            if (res.code === 200) {
                // 更新本地内存
                const currentInfo = this.data.userInfo;
                let updatedData = { ...data };
                if (data.avatarUrl) {
                    updatedData.avatarUrl = this.formatUrl(data.avatarUrl);
                }

                const newUserInfo = { ...currentInfo, ...updatedData };
                this.setData({ userInfo: newUserInfo });
                wx.setStorageSync('userInfo', newUserInfo);

                // 设置永久完成标记，防止再次弹出完善资料弹窗
                if (data.avatarUrl || (data.nickname && data.nickname !== '微信用户')) {
                    wx.setStorageSync('profile_completed_once', true);
                }

                // 提示并刷新
                setTimeout(() => {
                    wx.showToast({ title: '修改成功', icon: 'success' });
                    this.refreshAllData();
                }, 50);
            } else {
                wx.showToast({ title: res.msg || '保存失败', icon: 'none' });
            }
        }).catch(err => {
            if (!isUploading) {
                wx.hideLoading();
            }
            console.error('updateProfile error:', err);
        });
    },

    // 退出登录
    onLogout() {
        wx.showModal({
            title: '确认退出',
            content: '确定要退出登录吗？',
            success: (res) => {
                if (res.confirm) {
                    // 清除存储
                    const { logout } = require('../../utils/request');
                    logout();

                    this.setData({
                        isLoggedIn: false,
                        needCompleteInfo: false,
                        userInfo: { id: '000000', nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0 },
                        walletInfo: { balance: '0.00', totalEarnings: '0.00', deviceCount: 0, displayHashrate: 0 },
                        deviceStats: { total: 0, online: 0, offline: 0 }
                    });
                }
            }
        });
    },

    // 提现与记录操作
    onWithdraw() {
        if (parseFloat(this.data.walletInfo.balance) <= 0) {
            wx.showToast({ title: '暂无可提现余额', icon: 'none' });
            return;
        }

        wx.setStorageSync('withdrawWalletSnapshot', {
            available: this.data.walletInfo.balance,
            total: this.data.walletInfo.totalEarnings,
            hashrateBalance: this.data.walletInfo.displayHashrate,
            hashratePerYuan: this.data.hashrateRate || 100,
            ts: Date.now()
        });
        wx.navigateTo({ url: '/pages/withdraw/withdraw' });
    },

    onWithdrawRecord() {
        wx.navigateTo({ url: '/pages/withdraw-record/withdraw-record' });
    },

    onWalletDetail() {
        wx.navigateTo({ url: '/pages/earnings-detail/earnings-detail' });
    },

    goToDevicePage() {
        wx.switchTab({ url: '/pages/device/device' });
    },

    onMenuTap(e: any) {
        const id = e.currentTarget.dataset.id;
        switch (id) {
            case 'devices':
                wx.switchTab({ url: '/pages/device/device' });
                break;
            case 'earnings':
                wx.navigateTo({ url: '/pages/earnings-detail/earnings-detail' });
                break;
            case 'payment':
                wx.navigateTo({ url: '/pages/edit-payment/edit-payment' });
                break;
            case 'invite':
                wx.navigateTo({ url: '/pages/invite/invite' });
                break;
            case 'exchange':
                wx.navigateTo({ url: '/pages/exchange/exchange' });
                break;
            case 'exchange-orders':
                wx.navigateTo({ url: '/pages/exchange-orders/exchange-orders' });
                break;
            case 'help':
                wx.navigateTo({ url: '/pages/help/help' });
                break;
            case 'feedback':
                wx.navigateTo({ url: '/pages/feedback/feedback' });
                break;
            default:
                wx.showToast({ title: '功能开发中', icon: 'none' });
        }
    }
})
