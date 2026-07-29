import { request } from '../../utils/request';
export { };

Page({
    data: {
        navBarTop: 0,
        currentInfo: {
            realName: '',
            bankCardNo: '',
            bankCardNoMask: ''
        },
        form: {
            cardNo: ''
        },
        submitting: false,
        canSubmit: false,
        contractStatus: -1, // -1=未知 0=待签约 1=已签约 5=已解约
        cancelling: false,
        hasPendingApply: false
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top
        });
        this.loadCurrentInfo();
        this.loadContractStatus();
        this.checkPendingApply();
    },

    // 检查是否有待审核的变更申请
    checkPendingApply() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        request({
            url: '/api/bosskg/contract/pending-apply',
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200 && res.data) {
                this.setData({ hasPendingApply: true });
                this.validateForm();
            } else {
                this.setData({ hasPendingApply: false });
                this.validateForm();
            }
        });
    },

    // 加载当前收款信息
    loadCurrentInfo() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        // 获取钱包信息（包含银行卡号等）
        request({
            url: '/api/withdraw/wallet',
            method: 'GET',
            data: { userId }
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                const bankCardNo = data.bankCardNo || '';
                const realName = data.contractRealName || data.bankHolderName || '';

                this.setData({
                    currentInfo: {
                        realName,
                        bankCardNo,
                        bankCardNoMask: this.maskCardNo(bankCardNo)
                    }
                });
            }
        });
    },

    // 银行卡号脱敏
    maskCardNo(cardNo: string) {
        if (!cardNo || cardNo.length < 8) return cardNo;
        return cardNo.substring(0, 4) + ' **** **** ' + cardNo.substring(cardNo.length - 4);
    },

    selectType(e: any) {
        this.setData({
            'form.cardNo': ''
        });
        this.validateForm();
    },

    onCardNoInput(e: any) {
        this.setData({ 'form.cardNo': e.detail.value });
        this.validateForm();
    },

    validateForm() {
        const { form, hasPendingApply } = this.data;
        // 银行卡：15-19位数字
        const cardNo = form.cardNo.replace(/\s/g, '');
        const canSubmit = /^\d{15,19}$/.test(cardNo) && !hasPendingApply;

        this.setData({ canSubmit });
    },

    submitChange() {
        if (!this.data.canSubmit || this.data.submitting) return;

        const { form } = this.data;

        // 二次确认
        wx.showModal({
            title: '确认修改',
            content: `确定将银行卡号修改为：${form.cardNo}？`,
            success: (res) => {
                if (res.confirm) {
                    this.doSubmit();
                }
            }
        });
    },

    doSubmit() {
        this.setData({ submitting: true });

        request({
            url: '/api/bosskg/contract/update-card',
            method: 'POST',
            data: {
                cardNo: this.data.form.cardNo.trim(),
                paymentType: 0
            }
        }).then(res => {
            if (res.code === 200) {
                wx.showToast({ title: '修改申请已提交', icon: 'success' });
                this.checkPendingApply();
                // 延迟返回上一页
                setTimeout(() => {
                    wx.navigateBack();
                }, 1500);
            } else {
                wx.showToast({ title: res.msg || '修改失败', icon: 'none' });
            }
        }).catch(() => {
            wx.showToast({ title: '网络错误', icon: 'none' });
        }).finally(() => {
            this.setData({ submitting: false });
        });
    },

    goBack() {
        wx.navigateBack();
    },

    // 加载签约状态
    loadContractStatus() {
        request({
            url: '/api/bosskg/contract/status',
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    contractStatus: data.status != null ? data.status : -1
                });
            }
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
            success: (res) => {
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
                this.setData({ contractStatus: 5 });
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
});
