import { API_BASE } from '../../config';
export { }; // 使文件成为 ES 模块

Page({
    data: {
        navBarTop: 0,
        records: [] as any[],
        page: 1,
        size: 10,
        hasMore: true,
        loading: false
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({ navBarTop: menuButtonInfo.top });
        this.fetchRecords();
    },

    goBack() {
        wx.navigateBack();
    },

    fetchRecords() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            wx.showToast({ title: '请先登录', icon: 'none' });
            return;
        }

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
                if (res.data.code === 200) {
                    const data = res.data.data;
                    const newRecords = data.records || [];

                    this.setData({
                        records: this.data.page === 1 ? newRecords : [...this.data.records, ...newRecords],
                        hasMore: newRecords.length >= this.data.size
                    });
                }
            },
            complete: () => {
                this.setData({ loading: false });
            }
        });
    },

    loadMore() {
        if (this.data.loading || !this.data.hasMore) return;

        this.setData({ page: this.data.page + 1 });
        this.fetchRecords();
    },

    onPullDownRefresh() {
        this.setData({ page: 1, hasMore: true });
        this.fetchRecords();
        wx.stopPullDownRefresh();
    }
})
