import { API_BASE } from '../../config';
import { request } from '../../utils/request';

export { };

Page({
    data: {
        contractStatus: null as number | null, // 0-待签约 1-已签约 2-失败 3-中 5-解约
        failReason: '',
        form: {
            realName: '',
            idCard: '',
            mobile: '',
            cardNo: '',
            paymentType: 0, // 0-银行卡 1-支付宝
            idCardFront: '',
            idCardBack: '',
        },
        agreed: false,
        submitting: false,
        canSubmit: false,
        showForm: false, // 是否强制显示表单（用于失败重新申请）
        statusLoading: false,
        statusError: ''
    },

    onLoad() {
        this.checkContractStatus();
        this.loadSavedInfo();
    },

    onShow() {
        // 如果用户已经点了"重新申请"正在填表，就不要刷新状态覆盖表单
        if (!this.data.showForm) {
            this.checkContractStatus();
        }
    },

    // 检查签约状态
    checkContractStatus() {
        if (this.data.statusLoading) return Promise.resolve();

        this.setData({ statusLoading: true, statusError: '' });
        return request({
            url: '/api/bosskg/contract/status',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const status = (res.data.contracted ? 1 : res.data.status) as number;
                this.setData({
                    contractStatus: status,
                    failReason: res.data.failReason
                });
            } else {
                this.setData({ statusError: res.msg || '签约状态同步失败' });
            }
        }).catch(() => {
            this.setData({ statusError: '网络连接异常，请重新同步' });
        }).finally(() => {
            this.setData({ statusLoading: false });
        });
    },

    retryStatus() {
        this.checkContractStatus();
    },

    // 加载已填写的身份信息
    loadSavedInfo() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        // 获取用户信息
        request({
            url: '/api/user/info',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const user = res.data;
                this.setData({
                    'form.realName': user.bankHolderName || '',
                    'form.idCard': user.idCard || '',
                    'form.mobile': user.phone || '',
                    'form.cardNo': user.bankCardNo || user.alipayAccount || '',
                    'form.paymentType': user.bankCardNo ? 0 : (user.alipayAccount ? 1 : 0),
                    'form.idCardFront': user.idCardFront || '',
                    'form.idCardBack': user.idCardBack || '',
                });
                this.validateForm();
            }
        });
    },


    onInput(e: any) {
        const field = e.currentTarget.dataset.field;
        const value = e.detail.value;
        this.setData({ [`form.${field}`]: value });
        this.validateForm();
    },

    onPaymentTypeChange(e: any) {
        this.setData({ 'form.paymentType': parseInt(e.detail.value) });
        this.validateForm();
    },

    onAgreementChange(e: any) {
        this.setData({ agreed: e.detail.value.length > 0 });
        this.validateForm();
    },

    validateForm() {
        const { form, agreed } = this.data;
        const canSubmit = !!(
            form.realName &&
            form.idCard.length === 18 &&
            form.mobile.length === 11 &&
            form.cardNo &&
            form.idCardFront &&
            form.idCardBack &&
            agreed
        );
        this.setData({ canSubmit });
    },

    uploadPhoto(e: any) {
        const side = e.currentTarget.dataset.side;
        wx.chooseImage({
            count: 1,
            sizeType: ['compressed'],
            success: (res) => {
                const fileSize = res.tempFiles && res.tempFiles[0] ? res.tempFiles[0].size : 0;
                if (fileSize > 1024 * 1024) {
                    wx.showToast({ title: '图片需小于 1MB', icon: 'none' });
                    return;
                }
                wx.showLoading({ title: '上传中...' });
                const token = wx.getStorageSync('token');
                wx.uploadFile({
                    url: `${API_BASE}/api/upload/image`,
                    filePath: res.tempFilePaths[0],
                    name: 'file',
                    header: { 'Authorization': `Bearer ${token}` },
                    success: (uploadRes) => {
                        const result = JSON.parse(uploadRes.data);
                        if (result.code === 200) {
                            const field = side === 'front' ? 'idCardFront' : 'idCardBack';
                            this.setData({ [`form.${field}`]: result.data.url });
                            this.validateForm();
                        } else {
                            wx.showToast({ title: result.msg || '上传失败', icon: 'none' });
                        }
                    },
                    fail: () => {
                        wx.showToast({ title: '上传失败，请重试', icon: 'none' });
                    },
                    complete: () => wx.hideLoading()
                });
            }
        });
    },

    submitForm() {
        if (!this.data.canSubmit) return;

        this.setData({ submitting: true });

        request({
            url: '/api/bosskg/contract/signing-url',
            method: 'POST',
            data: this.data.form
        }).then(res => {
            if (res.code === 200) {
                const url = res.data;
                // 跳转到H5页面进行人脸识别
                wx.navigateTo({
                    url: `/pages/auth_web_view/index?url=${encodeURIComponent(url)}`
                });
            } else {
                const errMsg = res.msg || '获取签约链接失败';
                wx.showModal({
                    title: '签约提示',
                    content: errMsg,
                    showCancel: false,
                    confirmText: '我知道了'
                });
            }
        }).finally(() => {
            this.setData({ submitting: false });
        });
    },

    resetForm() {
        this.setData({ showForm: true });
    },

    showProtocol() {
        // 这里可以打开一个新页面显示协议内容，或者显示弹出层
        wx.showModal({
            title: '劳务服务协议',
            content: '本协议由您与平台签署，用于为您提供合法的劳务所得结算服务。实名信息仅用于税务核验及打款。',
            showCancel: false
        });
    }
});
