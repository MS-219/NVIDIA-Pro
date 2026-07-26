import { request } from '../../utils/request';
export {};

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        products: [] as any[],
        userLevel: 0,
        hashrateRate: 200,
        availableHashrate: 0,
        loading: true,
        levelNames: ['普通', '会员', '社区', '县级', '市级', '联创']
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
        this.fetchProducts();
    },

    onShow() {
        this.fetchProducts();
    },

    onPullDownRefresh() {
        this.fetchProducts();
        wx.stopPullDownRefresh();
    },

    fetchProducts() {
        const userId = wx.getStorageSync('userId') || '';

        this.setData({ loading: true });
        request({
            url: `/api/exchange/products?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            console.log('exchange products response:', JSON.stringify(res));
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    products: data.products || [],
                    userLevel: data.userLevel || 0,
                    hashrateRate: data.hashrateRate || 200,
                    availableHashrate: data.availableHashrate || 0,
                    loading: false
                });
            } else {
                console.error('获取商品失败:', res.msg);
                wx.showToast({ title: res.msg || '获取商品失败', icon: 'none' });
                this.setData({ loading: false });
            }
        }).catch((err: any) => {
            console.error('请求异常:', err);
            this.setData({ loading: false });
        });
    },

    goToDetail(e: any) {
        const id = e.currentTarget.dataset.id;
        wx.navigateTo({ url: `/pages/exchange-detail/exchange-detail?id=${id}` });
    },

    goToOrders() {
        wx.navigateTo({ url: '/pages/exchange-orders/exchange-orders' });
    },

    formatHashrate(value: number): string {
        if (value >= 10000) {
            return (value / 10000).toFixed(1) + '万';
        }
        return value.toString();
    },

    onGoBack() {
        wx.navigateBack();
    }
});
