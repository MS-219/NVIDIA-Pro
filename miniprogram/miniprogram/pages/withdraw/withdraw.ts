import { API_BASE } from '../../config';
import { request, isTokenExpired, handleTokenExpired } from '../../utils/request';
export { }; // 使文件成为 ES 模块

Page({
    data: {
        navBarTop: 0,
        hashratePerYuan: 100, // 算力兑换比例
        minWithdraw: 0.01,     // 仅保留人民币最小计价单位，不设累计门槛
        withdrawFee: 1,        // 线上打款提现手续费
        walletInfo: {
            available: '0.00',
            total: '0.00',
            pending: '0.00',
            withdrawn: '0.00',
            hashrateBalance: 0,  // 可提现聚芯 Orin值
            totalHashrate: 0     // 累计聚芯 Orin值
        },
        form: {
            type: 3, // 1-微信(暂关) 2-支付宝 3-银行卡
            amount: '',
            account: '',
            realName: '',
            qrCode: ''
        },
        savedWxQrCode: '',
        savedAliQrCode: '',
        savedBankCardNo: '',
        savedAlipayAccount: '',
        savedRealName: '',
        realNameDisabled: false,
        previewAmount: '0.00',
        submitting: false,
        // 提现日期由后台配置
        canWithdraw: true,
        withdrawMessage: '每天均可申请提现',
        allowedDaysText: '每天',
        // 佣金保
        bosskgEnabled: false,
        contracted: false,
        // 修改卡号弹窗
        showEditCard: false,
        editCardType: 0, // 0-银行卡 1-支付宝
        editCardNo: '',
        editingCard: false,
        hasContract: false,
        cancelling: false,
        firstShowHandled: false,
        walletLoading: false,
        walletReady: false
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top
        });

        this.hydrateWalletSnapshot();

        this.checkWithdrawStatus();
        this.fetchSystemConfig();
        this.checkBossKgStatus();
        this.fetchWalletInfo();
    },

    onShow() {
        if (!this.data.firstShowHandled) {
            this.setData({ firstShowHandled: true });
            return;
        }

        this.checkBossKgStatus();
        this.fetchWalletInfo();
    },

    hydrateWalletSnapshot() {
        const snapshot = wx.getStorageSync('withdrawWalletSnapshot');
        if (!snapshot || !snapshot.ts || Date.now() - snapshot.ts > 5 * 60 * 1000) {
            return;
        }

        const rate = parseInt(snapshot.hashratePerYuan || this.data.hashratePerYuan, 10) || 100;
        const available = parseFloat(snapshot.available || '0') || 0;
        const total = parseFloat(snapshot.total || '0') || 0;

        this.setData({
            hashratePerYuan: rate,
            walletInfo: {
                ...this.data.walletInfo,
                available: available.toFixed(2),
                total: total.toFixed(2),
                hashrateBalance: snapshot.hashrateBalance || Math.round(available * rate),
                totalHashrate: snapshot.totalHashrate || Math.round(total * rate)
            }
        });
    },

    // 检查佣金保状态
    checkBossKgStatus() {
        request({
            url: '/api/bosskg/contract/status',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                this.setData({
                    bosskgEnabled: res.data.enabled,
                    contracted: res.data.contracted,
                    // 只要有签约记录（无论状态），就允许修改卡号
                    hasContract: res.data.enabled && res.data.status != null
                });
            }
        });
    },

    goContract() {
        wx.navigateTo({
            url: '/pages/bosskg-contract/bosskg-contract'
        });
    },

    // 获取后台配置的提现日期规则。
    checkWithdrawStatus() {
        request({
            url: '/api/settings/withdraw-status',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    canWithdraw: data.canWithdraw !== false,
                    withdrawMessage: data.message || '',
                    allowedDaysText: data.allowedDaysText || '每天'
                });
            }
        });
    },

    // 获取系统配置（算力兑换比例等）
    fetchSystemConfig(callback?: () => void) {
        return request({
            url: '/api/settings/earnings-config',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const config = res.data;
                const hashratePerYuan = parseInt(config.hashratePerYuan || 100, 10) || 100;
                const configuredMinWithdraw = Number(config.minWithdraw);
                this.setData({
                    hashratePerYuan,
                    minWithdraw: Number.isFinite(configuredMinWithdraw)
                        ? Math.max(0.01, configuredMinWithdraw)
                        : 0.01,
                    withdrawFee: config.withdrawFee || 1
                });
                this.refreshHashrateDisplay(hashratePerYuan);
            }
            return res;
        }).finally(() => {
            if (callback) callback();
        });
    },

    fetchWalletInfo() {
        if (this.data.walletLoading) {
            return Promise.resolve(null);
        }

        const userId = wx.getStorageSync('userId');
        if (!userId) {
            wx.showToast({ title: '请先登录', icon: 'none' });
            setTimeout(() => wx.navigateBack(), 1500);
            return Promise.resolve(null);
        }

        this.setData({ walletLoading: true, walletReady: false });

        return request({
            url: '/api/withdraw/wallet',
            method: 'GET',
            data: { userId }
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                const available = parseFloat(data.available) || 0;
                const total = parseFloat(data.total) || 0;
                const hashratePerYuan = this.data.hashratePerYuan;

                this.setData({
                    walletInfo: {
                        available: available.toFixed(2),
                        total: data.total || '0.00',
                        pending: data.pending || '0.00',
                        withdrawn: data.withdrawn || '0.00',
                        hashrateBalance: Math.round(available * hashratePerYuan),
                        totalHashrate: Math.round(total * hashratePerYuan)
                    },
                    savedWxQrCode: data.wxQrCode || '',
                    savedAliQrCode: data.aliQrCode || '',
                    savedBankCardNo: data.bankCardNo || '',
                    savedAlipayAccount: data.alipayAccount || '',
                    savedRealName: data.contractRealName || data.bankHolderName || data.savedRealName || ''
                });

                // 自动填充信息
                const form = this.data.form;
                form.realName = this.data.savedRealName;

                // 如果已签约（有contractRealName），则锁定姓名不可修改
                const realNameDisabled = !!data.contractRealName;
                this.setData({ realNameDisabled });

                if (form.type === 1) { // 微信
                    form.qrCode = data.wxQrCode || '';
                } else if (form.type === 2) { // 支付宝
                    form.qrCode = data.aliQrCode || '';
                    form.account = data.alipayAccount || '';
                } else if (form.type === 3) { // 银行卡
                    form.account = data.bankCardNo || '';
                }
                this.setData({ form });
                wx.setStorageSync('withdrawWalletSnapshot', {
                    available: available.toFixed(2),
                    total: (data.total || '0.00').toString(),
                    pending: (data.pending || '0.00').toString(),
                    hashrateBalance: Math.round(available * hashratePerYuan),
                    totalHashrate: Math.round(total * hashratePerYuan),
                    hashratePerYuan,
                    ts: Date.now()
                });
                this.setData({ walletReady: true });
            }
            return res;
        }).finally(() => {
            this.setData({ walletLoading: false });
        });
    },

    refreshHashrateDisplay(hashratePerYuan: number) {
        const available = parseFloat(this.data.walletInfo.available) || 0;
        const total = parseFloat(this.data.walletInfo.total) || 0;

        this.setData({
            'walletInfo.hashrateBalance': Math.round(available * hashratePerYuan),
            'walletInfo.totalHashrate': Math.round(total * hashratePerYuan)
        });
    },

    goBack() {
        wx.navigateBack();
    },

    goRecords() {
        wx.navigateTo({
            url: '/pages/withdraw-list/withdraw-list'
        });
    },

    selectType(e: any) {
        const type = parseInt(e.currentTarget.dataset.type);
        const { savedWxQrCode, savedAliQrCode } = this.data;
        this.setData({
            'form.type': type,
            'form.qrCode': type === 1 ? savedWxQrCode : (type === 2 ? savedAliQrCode : '')
        });
    },

    onAmountInput(e: any) {
        const amount = e.detail.value;
        this.setData({ 'form.amount': amount });
        this.calculatePreview(amount);
    },

    onAccountInput(e: any) {
        this.setData({ 'form.account': e.detail.value });
    },

    onRealNameInput(e: any) {
        this.setData({ 'form.realName': e.detail.value });
    },

    setAmount(e: any) {
        const amount = e.currentTarget.dataset.amount;
        this.setData({ 'form.amount': amount.toString() });
        this.calculatePreview(amount);
    },

    setAllAmount() {
        const available = parseFloat(this.data.walletInfo.available) || 0;
        this.setData({ 'form.amount': available.toFixed(2) });
        this.calculatePreview(available);
    },

    calculatePreview(amount: number | string) {
        const num = parseFloat(amount as string) || 0;
        const feePercent = this.data.withdrawFee || 1; // 线上打款使用配置的百分比
        const fee = num * (feePercent / 100);
        const actual = Math.max(0, num - fee).toFixed(2);
        this.setData({ previewAmount: actual });
    },

    uploadQRCode() {
        wx.chooseImage({
            count: 1,
            sizeType: ['compressed'],
            sourceType: ['album', 'camera'],
            success: (res) => {
                const tempFilePaths = res.tempFilePaths;
                wx.showLoading({ title: '上传中...' });

                const token = wx.getStorageSync('token');
                wx.uploadFile({
                    url: `${API_BASE}/api/upload/image`,
                    filePath: tempFilePaths[0],
                    name: 'file',
                    header: {
                        'Authorization': `Bearer ${token}`
                    },
                    success: (uploadRes) => {
                        try {
                            const result = JSON.parse(uploadRes.data);
                            if (result.code === 200) {
                                const url = result.data.url;
                                this.setData({ 'form.qrCode': url });

                                // 立即保存到后端
                                this.savePaymentInfoToServer(url);

                                wx.showToast({ title: '上传成功', icon: 'success' });
                            } else {
                                wx.showToast({ title: result.msg || '上传失败', icon: 'none' });
                            }
                        } catch (e) {
                            wx.showToast({ title: '解析失败', icon: 'none' });
                        }
                    },
                    fail: () => {
                        wx.showToast({ title: '上传失败', icon: 'none' });
                    },
                    complete: () => {
                        wx.hideLoading();
                    }
                });
            }
        });
    },

    // 保存收款信息到服务器
    savePaymentInfoToServer(qrCode: string) {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        const { form } = this.data;

        request({
            url: '/api/withdraw/save-payment-info',
            method: 'POST',
            data: {
                userId,
                type: form.type,
                qrCode: qrCode
            }
        }).then(res => {
            if (res.code === 200) {
                // 更新本地缓存
                if (form.type === 1) {
                    this.setData({ savedWxQrCode: qrCode });
                } else if (form.type === 2) {
                    this.setData({ savedAliQrCode: qrCode });
                }
                console.log('收款码保存成功');
            } else {
                console.error('收款码保存失败:', res.msg);
            }
        }).catch(err => {
            console.error('保存收款码请求失败:', err);
        });
    },

    submitWithdraw() {
        if (!this.data.canWithdraw) {
            wx.showToast({ title: this.data.withdrawMessage || '今日暂不可申请提现', icon: 'none' });
            return;
        }
        if (!this.data.walletReady) {
            wx.showToast({ title: '余额刷新中，请稍后', icon: 'none' });
            return;
        }

        const { form, walletInfo } = this.data;
        const amount = parseFloat(form.amount) || 0;
        const available = parseFloat(walletInfo.available) || 0;

        // 表单验证：不设置累计门槛，仅校验人民币最小计价单位。
        const minWithdraw = Math.max(0.01, Number(this.data.minWithdraw) || 0.01);
        if (amount < minWithdraw) {
            wx.showToast({ title: `提现金额最低为${minWithdraw}元`, icon: 'none' });
            return;
        }

        if (amount > available) {
            wx.showToast({ title: '提现金额超过可用余额', icon: 'none' });
            return;
        }

        // 银行卡号格式校验（type=3 为银行卡）
        if (form.type === 3) {
            const cardNo = (form.account || '').replace(/\s/g, '');
            if (!/^\d{15,19}$/.test(cardNo)) {
                wx.showToast({ title: '请输入正确的银行卡号（15-19位数字）', icon: 'none' });
                return;
            }
        }

        // 佣金保校验
        if (this.data.bosskgEnabled && !this.data.contracted) {
            wx.showModal({
                title: '需要实名签约',
                content: '根据合规要求，提现前请先完成实名签约。',
                confirmText: '去签约',
                success: (res) => {
                    if (res.confirm) {
                        this.goContract();
                    }
                }
            });
            return;
        }

        const userId = wx.getStorageSync('userId');
        if (!userId) {
            wx.showToast({ title: '请先登录', icon: 'none' });
            return;
        }

        this.setData({ submitting: true });

        request({
            url: '/api/withdraw/apply',
            method: 'POST',
            data: {
                userId,
                amount: form.amount,
                type: form.type,
                account: form.account,
                realName: form.realName,
                qrCode: form.qrCode
            }
        }).then(res => {
            if (res.code === 200) {
                wx.showToast({ title: '提现申请已提交', icon: 'success' });
                setTimeout(() => {
                    wx.navigateBack();
                }, 1500);
            } else {
                wx.showToast({ title: res.msg || '提交失败', icon: 'none' });
            }
        }).catch(() => {
            wx.showToast({ title: '网络错误', icon: 'none' });
        }).finally(() => {
            this.setData({ submitting: false });
        });
    },

    // ========== 修改卡号相关方法 ==========

    showEditCardModal() {
        // 跳转到独立的收款信息管理页面
        wx.navigateTo({
            url: '/pages/edit-payment/edit-payment'
        });
    },

    hideEditCardModal() {
        this.setData({ showEditCard: false });
    },

    selectEditCardType(e: any) {
        const type = parseInt(e.currentTarget.dataset.type);
        this.setData({ editCardType: type });
    },

    onEditCardInput(e: any) {
        this.setData({ editCardNo: e.detail.value });
    },

    submitEditCard() {
        const { editCardType, editCardNo } = this.data;

        if (!editCardNo || editCardNo.trim() === '') {
            wx.showToast({ title: '请输入收款账号', icon: 'none' });
            return;
        }

        // 简单校验
        if (editCardType === 0 && editCardNo.length < 10) {
            wx.showToast({ title: '银行卡号格式不正确', icon: 'none' });
            return;
        }

        this.setData({ editingCard: true });

        request({
            url: '/api/bosskg/contract/update-card',
            method: 'POST',
            data: {
                cardNo: editCardNo.trim(),
                paymentType: editCardType
            }
        }).then(res => {
            if (res.code === 200) {
                wx.showToast({ title: '修改成功', icon: 'success' });
                this.setData({
                    showEditCard: false,
                    'form.account': editCardNo.trim()
                });
                // 刷新钱包信息
                this.fetchWalletInfo();
            } else {
                wx.showToast({ title: res.msg || '修改失败', icon: 'none' });
            }
        }).catch(() => {
            wx.showToast({ title: '网络错误', icon: 'none' });
        }).finally(() => {
            this.setData({ editingCard: false });
        });
    },

    // 解约
    cancelContract() {
        if (this.data.cancelling) return;

        wx.showModal({
            title: '解约确认',
            content: '解约后将无法正常提现，确定要解除签约吗？',
            confirmText: '确定解约',
            confirmColor: '#C65A52',
            success: (res: any) => {
                if (res.confirm) {
                    this.doCancelContract();
                }
            }
        });
    },

    doCancelContract() {
        this.setData({ cancelling: true });

        request({
            url: '/api/bosskg/contract/cancel',
            method: 'POST'
        }).then((res: any) => {
            if (res.code === 200 && res.data && res.data.success) {
                wx.showToast({ title: '解约成功', icon: 'success' });
                this.setData({ contracted: false });
            } else {
                const msg = (res.data && res.data.message) || res.msg || '解约失败';
                wx.showToast({ title: msg, icon: 'none' });
            }
        }).catch(() => {
            wx.showToast({ title: '网络错误', icon: 'none' });
        }).finally(() => {
            this.setData({ cancelling: false });
        });
    }
})
