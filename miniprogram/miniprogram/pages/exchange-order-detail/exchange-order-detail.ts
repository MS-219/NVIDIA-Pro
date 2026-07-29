import { request } from '../../utils/request';
export {};

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        orderNo: '',
        order: null as any,
        logistics: [] as any[],
        expressLogistics: [] as any[],
        statusTexts: ['待发货', '已发货', '运输中', '已到货', '已取消'] as string[],
        loading: true,
        loaded: false,
        errorMessage: '',
        logisticsLoading: false,
        logisticsError: '',
        confirming: false
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height,
            orderNo: options.orderNo || ''
        });
    },

    onShow() {
        if (this.data.orderNo) {
            this.fetchDetail();
        }
    },

    fetchDetail() {
        if (!this.data.orderNo) {
            this.setData({
                loading: false,
                loaded: true,
                errorMessage: '订单参数缺失'
            });
            return Promise.resolve();
        }
        const userId = wx.getStorageSync('userId');
        this.setData({ loading: true, errorMessage: '' });
        return request({
            url: `/api/exchange/order/${this.data.orderNo}?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                this.setData({
                    order: res.data.order || null,
                    logistics: res.data.logistics || [],
                    loaded: true
                });
                
                // 如果订单状态 >=1 且有快递单号，则查询实时物流网络
                if (res.data.order && res.data.order.expressNo && res.data.order.status >= 1) {
                    this.fetchExpressLogistics();
                } else {
                    this.setData({
                        expressLogistics: [],
                        logisticsLoading: false,
                        logisticsError: ''
                    });
                }
            } else {
                this.setData({
                    loaded: true,
                    errorMessage: res.msg || '订单详情加载失败'
                });
            }
        }).catch(() => {
            this.setData({
                loaded: true,
                errorMessage: '网络连接异常，请稍后重试'
            });
        }).finally(() => {
            this.setData({ loading: false });
        });
    },

    retryDetail() {
        this.fetchDetail();
    },

    fetchExpressLogistics() {
        if (!this.data.order?.id) return Promise.resolve();
        const userId = wx.getStorageSync('userId');
        this.setData({ logisticsLoading: true, logisticsError: '' });
        return request({
            url: `/api/exchange/orders/${this.data.order.id}/logistics?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200 && res.data && res.data.result) {
                this.setData({
                    expressLogistics: res.data.result.list || []
                });
            } else {
                this.setData({ logisticsError: res.msg || '物流轨迹同步失败' });
            }
        }).catch(() => {
            this.setData({ logisticsError: '物流轨迹同步失败' });
        }).finally(() => {
            this.setData({ logisticsLoading: false });
        });
    },

    retryLogistics() {
        this.fetchExpressLogistics();
    },

    confirmReceive() {
        wx.showModal({
            title: '确认收货',
            content: '确认已收到设备？',
            confirmColor: '#76B900',
            success: (res) => {
                if (res.confirm) {
                    const userId = wx.getStorageSync('userId');
                    this.setData({ confirming: true });
                    request({
                        url: `/api/exchange/order/${this.data.orderNo}/confirm?userId=${userId}`,
                        method: 'POST'
                    }).then((res: any) => {
                        if (res.code === 200) {
                            wx.showToast({ title: '已确认收货', icon: 'success' });
                            this.fetchDetail();
                        } else {
                            wx.showToast({ title: res.msg || '操作失败', icon: 'none' });
                        }
                    }).catch(() => {
                        wx.showToast({ title: '网络错误', icon: 'none' });
                    }).finally(() => {
                        this.setData({ confirming: false });
                    });
                }
            }
        });
    },

    copyExpressNo() {
        if (this.data.order?.expressNo) {
            wx.setClipboardData({
                data: this.data.order.expressNo,
                success: () => wx.showToast({ title: '已复制', icon: 'success' })
            });
        }
    },

    onGoBack() {
        wx.navigateBack();
    }
});
