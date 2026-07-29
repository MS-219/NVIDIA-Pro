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
        loaded: false,
        errorMessage: '',
        levelNames: ['普通', '会员', '社区', '县级', '市级', '联创']
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
    },

    onShow() {
        this.fetchProducts();
    },

    onPullDownRefresh() {
        this.fetchProducts().finally(() => wx.stopPullDownRefresh());
    },

    fetchProducts() {
        const userId = wx.getStorageSync('userId') || '';

        this.setData({ loading: true, errorMessage: '' });
        return request({
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
                    loaded: true
                });
            } else {
                console.error('获取商品失败:', res.msg);
                this.setData({
                    loaded: true,
                    errorMessage: res.msg || '商品列表加载失败'
                });
            }
        }).catch((err: any) => {
            console.error('请求异常:', err);
            this.setData({
                loaded: true,
                errorMessage: '网络连接异常，请稍后重试'
            });
        }).finally(() => {
            this.setData({ loading: false });
        });
    },

    retryFetch() {
        this.fetchProducts();
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
