import { API_BASE } from '../../config';
export { }; // 使文件成为 ES 模块

Page({
    data: {
        navBarTop: 0,
        totalEarnings: '0.00',
        yesterdayEarnings: '0.00',
        monthEarnings: '0.00',
        deviceCount: 0,
        filter: 'all',
        subFilter: 'device', // 子筛选：device-设备收益, reward-分润收益
        records: [] as any[],
        rewards: [] as any[], // 分润记录
        page: 1,
        size: 20,
        hasMore: true,
        loading: false,
        monthlyData: [] as any[],
        dailyData: [] as any[]
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({ navBarTop: menuButtonInfo.top });

        this.fetchOverview();
        this.fetchRecords();
        this.fetchRewards(); // 获取分润记录
    },

    goBack() {
        wx.navigateBack();
    },

    fetchOverview() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        wx.request({
            url: `${API_BASE}/api/statistics/earnings`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    this.setData({
                        totalEarnings: data.total || '0.00',
                        yesterdayEarnings: data.yesterday || '0.00',
                        monthEarnings: data.month || '0.00',
                        deviceCount: data.onlineCount || 0
                    });
                }
            }
        });
    },

    fetchRecords() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                records: [],
                hasMore: false
            });
            return;
        }

        this.setData({ loading: true });

        wx.request({
            url: `${API_BASE}/api/earnings/user/list`,
            method: 'GET',
            data: {
                userId,
                page: this.data.page,
                size: this.data.size
            },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    // 格式化发放时间
                    const formattedRecords = (data.records || []).map((item: any) => ({
                        ...item,
                        createTimeFormatted: item.createTime
                            ? item.createTime.replace('T', ' ').substring(0, 19)
                            : ''
                    }));
                    this.setData({
                        records: this.data.page === 1 ? formattedRecords : [...this.data.records, ...formattedRecords],
                        hasMore: data.hasMore,
                        loading: false
                    });
                } else {
                    this.setData({ loading: false });
                    wx.showToast({ title: '加载失败', icon: 'none' });
                }
            },
            fail: () => {
                this.setData({ loading: false });
                wx.showToast({ title: '网络错误', icon: 'none' });
            }
        });
    },

    // 获取分润记录
    fetchRewards() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({ rewards: [] });
            return;
        }

        wx.request({
            url: `${API_BASE}/api/earnings/user/rewards`,
            method: 'GET',
            data: { userId, page: 1, size: 50 },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    // 格式化时间显示
                    const rewards = (data.records || []).map((item: any) => ({
                        ...item,
                        createTime: item.createTime ? item.createTime.split('T')[0] : ''
                    }));
                    this.setData({ rewards });
                }
            }
        });
    },

    setFilter(e: any) {
        const filter = e.currentTarget.dataset.filter;
        this.setData({
            filter,
            page: 1,
            records: [],
            monthlyData: [],
            dailyData: []
        });

        if (filter === 'month') {
            this.fetchMonthlyData();
        } else if (filter === 'day') {
            this.fetchDailyData();
        } else {
            this.fetchRecords();
        }
    },

    setSubFilter(e: any) {
        const subFilter = e.currentTarget.dataset.sub;
        this.setData({ subFilter });
    },

    fetchMonthlyData() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({ monthlyData: [] });
            return;
        }

        this.setData({ loading: true });

        wx.request({
            url: `${API_BASE}/api/earnings/user/monthly`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    this.setData({
                        monthlyData: res.data.data || [],
                        loading: false
                    });
                } else {
                    this.setData({ loading: false });
                    wx.showToast({ title: '加载失败', icon: 'none' });
                }
            },
            fail: () => {
                this.setData({ loading: false });
                wx.showToast({ title: '网络错误', icon: 'none' });
            }
        });
    },

    fetchDailyData() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({ dailyData: [] });
            return;
        }

        this.setData({ loading: true });

        wx.request({
            url: `${API_BASE}/api/earnings/user/daily`,
            method: 'GET',
            data: { userId, page: 1, size: 1000 },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    this.setData({
                        dailyData: data.records || [],
                        hasMore: data.hasMore || false,
                        loading: false
                    });
                } else {
                    this.setData({ loading: false });
                    wx.showToast({ title: '加载失败', icon: 'none' });
                }
            },
            fail: () => {
                this.setData({ loading: false });
                wx.showToast({ title: '网络错误', icon: 'none' });
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
        this.fetchOverview();
        this.fetchRecords();
        wx.stopPullDownRefresh();
    }
})
