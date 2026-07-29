import { request } from '../../utils/request';
export {};

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        tabs: [
            { label: '全部', value: -1 },
            { label: '待发货', value: 0 },
            { label: '已发货', value: 1 },
            { label: '运输中', value: 2 },
            { label: '已到货', value: 3 }
        ],
        activeTab: -1,
        orders: [] as any[],
        loading: true,
        loaded: false,
        errorMessage: '',
        page: 1,
        hasMore: true,
        statusTexts: ['待发货', '已发货', '运输中', '已到货', '已取消'] as string[]
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
    },

    onShow() {
        this.fetchOrders(true);
    },

    onPullDownRefresh() {
        this.fetchOrders(true);
    },

    onReachBottom() {
        if (this.data.hasMore && !this.data.loading) {
            this.fetchOrders(false);
        }
    },

    switchTab(e: any) {
        const value = e.currentTarget.dataset.value;
        this.setData({ activeTab: value, orders: [], page: 1, hasMore: true });
        this.fetchOrders(true);
    },

    fetchOrders(refresh: boolean) {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                loading: false,
                loaded: true,
                errorMessage: '登录后可查看兑换订单'
            });
            wx.stopPullDownRefresh();
            return Promise.resolve();
        }

        const requestedPage = refresh ? 1 : this.data.page;
        if (refresh) {
            this.setData({ page: 1, orders: [], hasMore: true });
        }

        this.setData({ loading: true, errorMessage: '' });

        let url = `/api/exchange/orders?userId=${userId}&page=${requestedPage}&size=10`;
        if (this.data.activeTab >= 0) {
            url += `&status=${this.data.activeTab}`;
        }

        return request({ url, method: 'GET' }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                const records = data.records || [];
                const newOrders = refresh ? records : [...this.data.orders, ...records];
                this.setData({
                    orders: newOrders,
                    hasMore: requestedPage < (data.pages || 0),
                    page: requestedPage + 1,
                    loaded: true
                });
            } else {
                this.setData({
                    loaded: true,
                    errorMessage: res.msg || '订单列表加载失败'
                });
            }
        }).catch(() => {
            this.setData({
                loaded: true,
                errorMessage: '网络连接异常，请稍后重试'
            });
        }).finally(() => {
            this.setData({ loading: false });
            wx.stopPullDownRefresh();
        });
    },

    retryOrders() {
        this.fetchOrders(this.data.orders.length === 0);
    },

    goToDetail(e: any) {
        const orderNo = e.currentTarget.dataset.orderno;
        wx.navigateTo({ url: `/pages/exchange-order-detail/exchange-order-detail?orderNo=${orderNo}` });
    },

    onGoBack() {
        wx.navigateBack();
    }
});
