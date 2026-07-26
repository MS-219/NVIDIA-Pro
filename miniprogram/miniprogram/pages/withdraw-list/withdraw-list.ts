import { API_BASE } from '../../config';

Page({
    data: {
        navBarTop: 0,
        list: [],
        page: 1,
        size: 10,
        loading: false,
        hasMore: true
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
        if (reset) {
            this.setData({ page: 1, hasMore: true, list: [] });
        }

        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        this.setData({ loading: true });

        wx.request({
            url: `${API_BASE}/api/withdraw/list`,
            method: 'GET',
            data: {
                userId,
                page: this.data.page,
                size: this.data.size
            },
            success: (res: any) => {
                wx.stopPullDownRefresh();
                if (res.data.code === 200) {
                    const records = res.data.data.records || [];
                    const newList = reset ? records : this.data.list.concat(records);

                    this.setData({
                        list: newList,
                        page: this.data.page + 1,
                        hasMore: records.length === this.data.size,
                        loading: false
                    });
                }
            },
            fail: () => {
                wx.stopPullDownRefresh();
                this.setData({ loading: false });
            }
        });
    }
})
