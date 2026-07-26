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
        this.fetchOrders(true);
    },

    onShow() {
        this.fetchOrders(true);
    },

    onPullDownRefresh() {
        this.fetchOrders(true);
        wx.stopPullDownRefresh();
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
        if (!userId) return;

        if (refresh) {
            this.setData({ page: 1, orders: [], hasMore: true });
        }

        this.setData({ loading: true });

        let url = `/api/exchange/orders?userId=${userId}&page=${this.data.page}&size=10`;
        if (this.data.activeTab >= 0) {
            url += `&status=${this.data.activeTab}`;
        }

        request({ url, method: 'GET' }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                const newOrders = refresh ? data.records : [...this.data.orders, ...data.records];
                this.setData({
                    orders: newOrders,
                    hasMore: this.data.page < data.pages,
                    page: this.data.page + 1,
                    loading: false
                });
            }
        }).catch(() => {
            this.setData({ loading: false });
        });
    },

    goToDetail(e: any) {
        const orderNo = e.currentTarget.dataset.orderno;
        wx.navigateTo({ url: `/pages/exchange-order-detail/exchange-order-detail?orderNo=${orderNo}` });
    },

    onGoBack() {
        wx.navigateBack();
    }
});
