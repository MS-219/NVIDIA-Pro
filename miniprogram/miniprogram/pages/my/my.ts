import { request } from '../../utils/request';
export { }; // 使文件成为 ES 模块

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        isLoggedIn: false,
        isLoggingIn: false,
        needCompleteInfo: false,
        userInfo: {
            id: '000000',
            nickname: '',
            avatarUrl: '',
            phone: '',
            quota: 0,
            level: 0,
            levelName: '普通',
            levelNameLevel: 0
        },
        hashrateRate: 100, // 算力兑换比例
        walletInfo: {
            balance: '0.00',
            totalEarnings: '0.00',
            displayHashrate: 0
        },
        menuList: [
            [
                { id: 'earnings', iconPath: '/images/menu/earnings.png', text: '收益明细', desc: '每日任务与结算记录', iconClass: 'icon-earnings' },
                { id: 'devices', iconPath: '/images/menu/devices.png', text: '节点设备', desc: '查看设备运行状态', iconClass: 'icon-device' },
                { id: 'payment', iconPath: '/images/menu/payment.png', text: '收款账户', desc: '维护结算收款信息', iconClass: 'icon-payment' },
                { id: 'invite', iconPath: '/images/menu/invite.png', text: '邀请伙伴', desc: '团队关系与节点统计', iconClass: 'icon-invite' },
                { id: 'exchange', iconPath: '/images/menu/exchange.png', text: '设备兑换', desc: '提交节点设备兑换', iconClass: 'icon-exchange' },
                { id: 'exchange-orders', iconPath: '/images/menu/orders.png', text: '兑换订单', desc: '查询兑换处理进度', iconClass: 'icon-orders' }
            ],
            [
                { id: 'help', iconPath: '/images/menu/help.png', text: '帮助中心', desc: '设备接入与使用说明', iconClass: 'icon-help' },
                { id: 'feedback', iconPath: '/images/menu/feedback.png', text: '问题反馈', desc: '提交设备与账户问题', iconClass: 'icon-feedback' }
            ]
        ]
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });

        // 首屏只读取本地登录态，网络刷新统一由 onShow 发起。
        this.checkLoginStatus();
    },

    onShow() {
        if (typeof this.getTabBar === 'function' && this.getTabBar()) {
            this.getTabBar().setData({
                selected: 3
            })
        }

        // 每次显示页面时重新检查登录状态（含 token 有效性验证）
        this.verifyLoginStatus();
    },

    // 页面返回时先展示完善资料页刚写入的缓存，再进行网络校验。
    hydrateUserInfoFromStorage(userId: any) {
        const cachedUserInfo = wx.getStorageSync('userInfo');
        const displayId = String(userId).padStart(6, '0');

        if (!cachedUserInfo) {
            if (!this.data.isLoggedIn) {
                this.setData({
                    isLoggedIn: true,
                    userInfo: { id: displayId, nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0, levelName: '普通', levelNameLevel: 0 }
                });
            }
            return;
        }

        const currentUserInfo = this.data.userInfo;
        const cachedLevel = Number(cachedUserInfo.level ?? currentUserInfo.level) || 0;
        const cachedLevelName = typeof cachedUserInfo.levelName === 'string' ? cachedUserInfo.levelName.trim() : '';
        const cachedNameMatchesLevel = Number(cachedUserInfo.levelNameLevel) === cachedLevel;
        this.setData({
            isLoggedIn: true,
            userInfo: {
                ...currentUserInfo,
                ...cachedUserInfo,
                id: displayId,
                nickname: cachedUserInfo.nickname || currentUserInfo.nickname || '',
                avatarUrl: this.formatUrl(cachedUserInfo.avatarUrl || currentUserInfo.avatarUrl || ''),
                levelName: cachedLevel === 0 ? '普通' : (cachedNameMatchesLevel ? cachedLevelName : ''),
                levelNameLevel: cachedLevel === 0 ? 0 : (cachedNameMatchesLevel ? cachedLevel : -1)
            }
        });
        this.checkNeedCompleteInfo();
    },

    getLastProfileUpdateTime() {
        return Math.max(
            Number(wx.getStorageSync('last_profile_update_time')) || 0,
            Number(wx.getStorageSync('last_complete_profile_time')) || 0
        );
    },

    mergeServerUserInfo(data: any, preserveLocalProfile: boolean) {
        const cachedUserInfo = wx.getStorageSync('userInfo') || {};
        const serverNickname = data.nickname || '';
        const serverAvatarUrl = this.formatUrl(data.avatarUrl || '');
        const cachedNickname = cachedUserInfo.nickname || '';
        const cachedAvatarUrl = this.formatUrl(cachedUserInfo.avatarUrl || '');
        const hasRecentNickname = cachedNickname && cachedNickname !== '微信用户';
        const serverLevel = Number(data.level) || 0;
        const serverLevelName = typeof data.levelName === 'string' ? data.levelName.trim() : '';
        const cachedLevelName = typeof cachedUserInfo.levelName === 'string' ? cachedUserInfo.levelName.trim() : '';
        const cachedNameMatchesLevel = Number(cachedUserInfo.levelNameLevel) === serverLevel;
        const resolvedLevelName = serverLevelName ||
            (cachedNameMatchesLevel ? cachedLevelName : '') ||
            (serverLevel === 0 ? '普通' : '');

        return {
            id: String(data.id).padStart(6, '0'),
            nickname: preserveLocalProfile && hasRecentNickname
                ? cachedNickname
                : serverNickname,
            avatarUrl: preserveLocalProfile && cachedAvatarUrl
                ? cachedAvatarUrl
                : serverAvatarUrl,
            phone: data.phone || '',
            quota: data.quota || 0,
            level: serverLevel,
            levelName: resolvedLevelName,
            levelNameLevel: resolvedLevelName ? serverLevel : -1
        };
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

        // 始终先水合缓存，确保从完善资料页返回时立刻显示新头像和昵称。
        this.hydrateUserInfoFromStorage(userId);

        // 发请求验证 token 是否真的有效
        const requestStartedAt = Date.now();
        request({
            url: `/api/user/info/${userId}`,
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                // token 有效，正常刷新数据
                const data = res.data;
                const profileUpdatedAt = this.getLastProfileUpdateTime();
                const preserveLocalProfile = profileUpdatedAt > 0 && profileUpdatedAt >= requestStartedAt;
                const newUserInfo = this.mergeServerUserInfo(data, preserveLocalProfile);

                this.setData({ isLoggedIn: true, userInfo: newUserInfo });
                wx.setStorageSync('userInfo', newUserInfo);
                if (typeof data.levelName !== 'string' || !data.levelName.trim()) {
                    this.fetchConfiguredLevelName(userId);
                }
                this.checkNeedCompleteInfo();
                this.fetchSettings();
                // 获取钱包收益数据
                this.fetchWalletStats();
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
            userInfo: { id: '000000', nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0, levelName: '普通', levelNameLevel: 0 },
            walletInfo: { balance: '0.00', totalEarnings: '0.00', displayHashrate: 0 }
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

        if (token && userId) {
            this.hydrateUserInfoFromStorage(userId);
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

    // 兼容尚未返回 levelName 的旧资料接口，按动态等级配置精确匹配名称。
    fetchConfiguredLevelName(userId: any) {
        const page = this as any;
        if (page.levelNameSyncPromise) {
            return page.levelNameSyncPromise;
        }

        page.levelNameSyncPromise = request({
            url: '/api/invite/stats',
            method: 'GET',
            data: { userId },
            showErrorToast: false
        }).then(res => {
            const data = res.code === 200 ? res.data : null;
            const level = Number(data && data.level) || 0;
            const levelConfigs = data && Array.isArray(data.levelConfigs) ? data.levelConfigs : [];
            const currentLevel = levelConfigs.find((item: any) => Number(item.index) === level);
            const levelName = currentLevel && typeof currentLevel.name === 'string'
                ? currentLevel.name.trim()
                : '';
            if (!levelName) return;

            const userInfo = {
                ...this.data.userInfo,
                level,
                levelName,
                levelNameLevel: level
            };
            this.setData({ userInfo });
            wx.setStorageSync('userInfo', userInfo);
        }).catch(() => {
            // 名称同步失败时保留数字等级，不显示错误名称。
        }).finally(() => {
            page.levelNameSyncPromise = null;
        });
        return page.levelNameSyncPromise;
    },

    // 获取用户数据
    fetchUserData() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        // 获取用户完整信息
        const requestStartedAt = Date.now();
        request({
            url: `/api/user/info/${userId}`,
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                const profileUpdatedAt = this.getLastProfileUpdateTime();
                const preserveLocalProfile = profileUpdatedAt > 0 && profileUpdatedAt >= requestStartedAt;
                const newUserInfo = this.mergeServerUserInfo(data, preserveLocalProfile);

                this.setData({ userInfo: newUserInfo });
                wx.setStorageSync('userInfo', newUserInfo);
                if (typeof data.levelName !== 'string' || !data.levelName.trim()) {
                    this.fetchConfiguredLevelName(userId);
                }
                this.checkNeedCompleteInfo();
            }
        });

        // 获取钱包收益数据
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
                        displayHashrate: Math.round((parseFloat(data.currentBalance) || 0) * (this.data.hashrateRate || 100)) // 动态计算聚芯 Orin值
                    }
                });
            }
        });
    },

    // 获取钱包收益数据（独立方法，供 verifyLoginStatus 调用）
    fetchWalletStats() {
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
                        displayHashrate: Math.round((parseFloat(data.currentBalance) || 0) * (this.data.hashrateRate || 100))
                    }
                });
            }
        });
    },

    // 登录 - 微信一键登录
    onLogin() {
        this.wxLogin();
    },

    onProfileCardTap() {
        if (!this.data.isLoggedIn) {
            this.onLogin();
            return;
        }
        wx.navigateTo({ url: '/pages/complete-profile/complete-profile' });
    },

    stopPropagation() {
        // Used by nested profile controls to keep the account-card navigation explicit.
    },

    // 微信登录
    async wxLogin() {
        if (this.data.isLoggingIn) return;

        this.setData({ isLoggingIn: true });
        wx.showLoading({ title: '登录中...', mask: true });

        let loginRequestStarted = false;
        let loginToast: { title: string; icon: 'success' | 'none' } | null = null;
        try {
            const loginRes: any = await new Promise((resolve, reject) => {
                wx.login({ success: resolve, fail: reject });
            });

            if (!loginRes.code) {
                throw new Error('微信未返回登录凭证');
            }

            loginRequestStarted = true;
            loginToast = await this.doLogin(loginRes.code, null);
        } catch (err) {
            console.error('wxLogin error:', err);
            loginToast = {
                title: loginRequestStarted ? '网络错误，请稍后重试' : '登录失败',
                icon: 'none'
            };
        } finally {
            wx.hideLoading();
            this.setData({ isLoggingIn: false });
        }

        if (loginToast) {
            wx.showToast(loginToast);
        }
    },

    // 执行登录请求
    async doLogin(code: string, userInfo: any): Promise<{ title: string; icon: 'success' | 'none' }> {
        const pendingInviteCode = wx.getStorageSync('pendingInviteCode') || '';

        const res = await request({
            url: '/api/user/wxLogin',
            method: 'POST',
            data: {
                code: code,
                nickname: userInfo?.nickName || '',
                avatarUrl: userInfo?.avatarUrl || '',
                inviteCode: pendingInviteCode
            },
            showErrorToast: false
        });

        if (res.code === 200) {
            const data = res.data;
            const loginLevel = Number(data.level) || 0;
            const loginLevelName = typeof data.levelName === 'string' ? data.levelName.trim() : '';
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
                level: loginLevel,
                levelName: loginLevelName || (loginLevel === 0 ? '普通' : ''),
                levelNameLevel: (loginLevelName || loginLevel === 0) ? loginLevel : -1
            };
            wx.setStorageSync('userInfo', savedUserInfo);

            this.setData({
                isLoggedIn: true,
                userInfo: { ...savedUserInfo, id: String(data.userId).padStart(6, '0') }
            });

            // 登录成功后直接触发数据和跳转检测
            this.fetchUserData();
            setTimeout(() => {
                this.checkAndRedirectToCompleteProfile();
            }, 500);

            return { title: '登录成功', icon: 'success' };
        }

        return { title: res.msg || '登录失败', icon: 'none' };
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

        // 2. 正式域名强制使用 HTTPS
        if (fullUrl.startsWith('http://nvidia.juxinsuanli.cn')) {
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
                wx.setStorageSync('last_profile_update_time', Date.now());

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
                        userInfo: { id: '000000', nickname: '', avatarUrl: '', phone: '', quota: 0, level: 0, levelName: '普通', levelNameLevel: 0 },
                        walletInfo: { balance: '0.00', totalEarnings: '0.00', displayHashrate: 0 }
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
        wx.navigateTo({ url: '/pages/withdraw-list/withdraw-list' });
    },

    onWalletDetail() {
        wx.navigateTo({ url: '/pages/earnings-detail/earnings-detail' });
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
                wx.switchTab({ url: '/pages/exchange/exchange' });
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
