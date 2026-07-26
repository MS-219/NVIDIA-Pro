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
        statusIcons: ['📦', '🚀', '🚚', '✅', '❌'] as string[]
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height,
            orderNo: options.orderNo || ''
        });
        this.fetchDetail();
    },

    onShow() {
        if (this.data.orderNo) {
            this.fetchDetail();
        }
    },

    fetchDetail() {
        const userId = wx.getStorageSync('userId');
        request({
            url: `/api/exchange/order/${this.data.orderNo}?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                this.setData({
                    order: res.data.order,
                    logistics: res.data.logistics || []
                });
                
                // 如果订单状态 >=1 且有快递单号，则查询实时物流网络
                if (res.data.order && res.data.order.expressNo && res.data.order.status >= 1) {
                    this.fetchExpressLogistics();
                }
            }
        });
    },

    fetchExpressLogistics() {
        const userId = wx.getStorageSync('userId');
        request({
            url: `/api/exchange/orders/${this.data.order.id}/logistics?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200 && res.data && res.data.result) {
                this.setData({
                    expressLogistics: res.data.result.list || []
                });
            }
        });
    },

    confirmReceive() {
        wx.showModal({
            title: '确认收货',
            content: '确认已收到设备？',
            confirmColor: '#16a34a',
            success: (res) => {
                if (res.confirm) {
                    const userId = wx.getStorageSync('userId');
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
