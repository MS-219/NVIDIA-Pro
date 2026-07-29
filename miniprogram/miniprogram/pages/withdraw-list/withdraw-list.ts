import { API_BASE } from '../../config';

Page({
    data: {
        navBarTop: 0,
        list: [],
        page: 1,
        size: 10,
        loading: false,
        hasMore: true,
        initialLoading: true,
        loadError: ''
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top
        });
        this.loadData(true);
    },

    onPullDownRefresh() {
        this.loadData(true);
    },

    onReachBottom() {
        if (this.data.hasMore && !this.data.loading) {
            this.loadData(false);
        }
    },

    goBack() {
        wx.navigateBack();
    },

    loadData(reset = false) {
        if (this.data.loading) return Promise.resolve();

        if (reset) {
            this.setData({ page: 1, hasMore: true, loadError: '' });
        }

        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                initialLoading: false,
                loadError: '登录后可查看提现处理记录'
            });
            wx.stopPullDownRefresh();
            return Promise.resolve();
        }

        this.setData({ loading: true, loadError: '' });

        return new Promise<void>((resolve) => {
            wx.request({
                url: `${API_BASE}/api/withdraw/list`,
                method: 'GET',
                data: {
                    userId,
                    page: this.data.page,
                    size: this.data.size
                },
                success: (res: any) => {
                    if (res.data.code === 200) {
                        const records = res.data.data.records || [];
                        const newList = reset ? records : this.data.list.concat(records);

                        this.setData({
                            list: newList,
                            page: this.data.page + 1,
                            hasMore: records.length === this.data.size
                        });
                    } else {
                        this.setData({ loadError: res.data.msg || '记录同步异常' });
                    }
                },
                fail: () => {
                    this.setData({ loadError: '记录同步异常，请稍后重试' });
                },
                complete: () => {
                    wx.stopPullDownRefresh();
                    this.setData({ loading: false, initialLoading: false });
                    resolve();
                }
            });
        });
    },

    retryLoad() {
        this.loadData(true);
    }
})
