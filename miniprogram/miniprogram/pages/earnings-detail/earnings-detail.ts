import { API_BASE } from '../../config';

export { };

Page({
    data: {
        navBarTop: 0,
        totalEarnings: '0.00',
        yesterdayEarnings: '0.00',
        monthEarnings: '0.00',
        deviceCount: 0,
        overviewLoading: true,
        overviewError: false,
        filter: 'all',
        subFilter: 'device',
        records: [] as any[],
        rewards: [] as any[],
        page: 1,
        size: 20,
        hasMore: true,
        loading: false,
        rewardsLoading: false,
        dailyLoading: false,
        monthlyLoading: false,
        errorMessage: '',
        rewardsError: '',
        dailyError: '',
        monthlyError: '',
        monthlyData: [] as any[],
        dailyData: [] as any[],
        visibleMonthlyData: [] as any[],
        visibleDailyData: [] as any[],
        contentState: 'loading',
        contentError: '',
        currentCount: 0,
        emptyTitle: '暂无设备收益',
        emptyDescription: '设备在线结算后，收益记录将在这里生成'
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({ navBarTop: menuButtonInfo.top });

        this.fetchOverview();
        this.fetchRecords();
        this.fetchRewards();
    },

    goBack() {
        wx.navigateBack();
    },

    fetchOverview() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({ overviewLoading: false });
            return;
        }

        this.setData({ overviewLoading: true, overviewError: false });

        wx.request({
            url: `${API_BASE}/api/statistics/earnings`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data || {};
                    this.setData({
                        totalEarnings: data.total || '0.00',
                        yesterdayEarnings: data.yesterday || '0.00',
                        monthEarnings: data.month || '0.00',
                        deviceCount: data.onlineCount || 0,
                        overviewLoading: false,
                        overviewError: false
                    });
                } else {
                    this.setData({ overviewLoading: false, overviewError: true });
                }
            },
            fail: () => {
                this.setData({ overviewLoading: false, overviewError: true });
            }
        });
    },

    fetchRecords() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                records: [],
                hasMore: false,
                loading: false,
                errorMessage: ''
            }, () => this.syncContentState());
            return;
        }

        this.setData({ loading: true, errorMessage: '' }, () => this.syncContentState());

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
                    const data = res.data.data || {};
                    const formattedRecords = (data.records || []).map((item: any) => ({
                        ...item,
                        createTimeFormatted: item.createTime
                            ? item.createTime.replace('T', ' ').substring(0, 19)
                            : ''
                    }));
                    this.setData({
                        records: this.data.page === 1
                            ? formattedRecords
                            : [...this.data.records, ...formattedRecords],
                        hasMore: Boolean(data.hasMore),
                        loading: false,
                        errorMessage: ''
                    }, () => this.syncContentState());
                } else {
                    this.handleRecordsError('收益数据暂时未同步，请稍后重试');
                }
            },
            fail: () => {
                this.handleRecordsError('网络连接异常，请检查后重试');
            }
        });
    },

    fetchRewards() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                rewards: [],
                rewardsLoading: false,
                rewardsError: ''
            }, () => this.syncContentState());
            return;
        }

        this.setData({ rewardsLoading: true, rewardsError: '' }, () => this.syncContentState());

        wx.request({
            url: `${API_BASE}/api/earnings/user/rewards`,
            method: 'GET',
            data: { userId, page: 1, size: 50 },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data || {};
                    const rewards = (data.records || []).map((item: any) => ({
                        ...item,
                        createTime: item.createTime ? item.createTime.split('T')[0] : ''
                    }));
                    this.setData({
                        rewards,
                        rewardsLoading: false,
                        rewardsError: ''
                    }, () => this.syncContentState());
                } else {
                    this.setData({
                        rewardsLoading: false,
                        rewardsError: '分润数据暂时未同步，请稍后重试'
                    }, () => this.syncContentState());
                }
            },
            fail: () => {
                this.setData({
                    rewardsLoading: false,
                    rewardsError: '网络连接异常，请检查后重试'
                }, () => this.syncContentState());
            }
        });
    },

    setFilter(e: WechatMiniprogram.TouchEvent) {
        const filter = e.currentTarget.dataset.filter as string;
        this.setData({
            filter,
            page: 1,
            records: [],
            hasMore: true,
            errorMessage: '',
            dailyError: '',
            monthlyError: '',
            monthlyData: [],
            dailyData: [],
            visibleMonthlyData: [],
            visibleDailyData: [],
            contentState: 'loading'
        }, () => {
            if (filter === 'month') {
                this.fetchMonthlyData();
            } else if (filter === 'day') {
                this.fetchDailyData();
            } else if (this.data.subFilter === 'reward') {
                this.fetchRewards();
            } else {
                this.fetchRecords();
            }
        });
    },

    setSubFilter(e: WechatMiniprogram.TouchEvent) {
        const subFilter = e.currentTarget.dataset.sub as string;
        this.setData({
            subFilter,
            visibleMonthlyData: this.filterMonthlyData(this.data.monthlyData, subFilter),
            visibleDailyData: this.filterDailyData(this.data.dailyData, subFilter)
        }, () => {
            this.syncContentState();

            if (this.data.filter === 'all' && subFilter === 'device' && this.data.records.length === 0 && !this.data.loading) {
                this.fetchRecords();
            } else if (this.data.filter === 'all' && subFilter === 'reward' && this.data.rewards.length === 0 && !this.data.rewardsLoading) {
                this.fetchRewards();
            }
        });
    },

    fetchMonthlyData() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                monthlyData: [],
                visibleMonthlyData: [],
                monthlyLoading: false,
                monthlyError: ''
            }, () => this.syncContentState());
            return;
        }

        this.setData({ monthlyLoading: true, monthlyError: '' }, () => this.syncContentState());

        wx.request({
            url: `${API_BASE}/api/earnings/user/monthly`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const monthlyData = res.data.data || [];
                    this.setData({
                        monthlyData,
                        visibleMonthlyData: this.filterMonthlyData(monthlyData, this.data.subFilter),
                        monthlyLoading: false,
                        monthlyError: ''
                    }, () => this.syncContentState());
                } else {
                    this.handlePeriodError('month', '月度收益暂时未同步，请稍后重试');
                }
            },
            fail: () => {
                this.handlePeriodError('month', '网络连接异常，请检查后重试');
            }
        });
    },

    fetchDailyData() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                dailyData: [],
                visibleDailyData: [],
                dailyLoading: false,
                dailyError: ''
            }, () => this.syncContentState());
            return;
        }

        this.setData({ dailyLoading: true, dailyError: '' }, () => this.syncContentState());

        wx.request({
            url: `${API_BASE}/api/earnings/user/daily`,
            method: 'GET',
            data: { userId, page: 1, size: 1000 },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data || {};
                    const dailyData = data.records || [];
                    this.setData({
                        dailyData,
                        visibleDailyData: this.filterDailyData(dailyData, this.data.subFilter),
                        dailyLoading: false,
                        dailyError: ''
                    }, () => this.syncContentState());
                } else {
                    this.handlePeriodError('day', '日收益暂时未同步，请稍后重试');
                }
            },
            fail: () => {
                this.handlePeriodError('day', '网络连接异常，请检查后重试');
            }
        });
    },

    filterMonthlyData(data: any[], subFilter: string) {
        return data.filter((item: any) => subFilter === 'device'
            ? Number(item.deviceAmount || 0) > 0 || !item.deviceAmount
            : Number(item.rewardAmount || 0) > 0);
    },

    filterDailyData(data: any[], subFilter: string) {
        return data.filter((item: any) => subFilter === 'device'
            ? Number(item.deviceAmount || 0) > 0 || !item.deviceAmount
            : Number(item.rewardAmount || 0) > 0);
    },

    handleRecordsError(message: string) {
        this.setData({ loading: false, errorMessage: message }, () => this.syncContentState());
        wx.showToast({ title: message, icon: 'none' });
    },

    handlePeriodError(period: string, message: string) {
        if (period === 'month') {
            this.setData({ monthlyLoading: false, monthlyError: message }, () => this.syncContentState());
        } else {
            this.setData({ dailyLoading: false, dailyError: message }, () => this.syncContentState());
        }
        wx.showToast({ title: message, icon: 'none' });
    },

    syncContentState() {
        const { filter, subFilter } = this.data;
        let loading = this.data.loading;
        let error = this.data.errorMessage;
        let count = 0;
        let emptyTitle = '暂无设备收益';
        let emptyDescription = '设备在线结算后，收益记录将在这里生成';

        if (filter === 'all' && subFilter === 'device') {
            count = this.data.records.length;
        } else if (filter === 'all') {
            loading = this.data.rewardsLoading;
            error = this.data.rewardsError;
            count = this.data.rewards.length;
            emptyTitle = '暂无分润收益';
            emptyDescription = '团队设备产生分润后，记录将在这里生成';
        } else if (filter === 'day') {
            loading = this.data.dailyLoading;
            error = this.data.dailyError;
            count = this.data.visibleDailyData.length;
            emptyTitle = '暂无日收益数据';
            emptyDescription = subFilter === 'device'
                ? '当前周期内没有设备收益结算'
                : '当前周期内没有团队分润结算';
        } else {
            loading = this.data.monthlyLoading;
            error = this.data.monthlyError;
            count = this.data.visibleMonthlyData.length;
            emptyTitle = '暂无月收益数据';
            emptyDescription = subFilter === 'device'
                ? '当前月份内没有设备收益结算'
                : '当前月份内没有团队分润结算';
        }

        const contentState = loading && count === 0
            ? 'loading'
            : error && count === 0
                ? 'error'
                : count > 0
                    ? 'ready'
                    : 'empty';

        this.setData({
            contentState,
            contentError: error,
            currentCount: count,
            emptyTitle,
            emptyDescription
        });
    },

    retryContent() {
        if (this.data.filter === 'month') {
            this.fetchMonthlyData();
        } else if (this.data.filter === 'day') {
            this.fetchDailyData();
        } else if (this.data.subFilter === 'reward') {
            this.fetchRewards();
        } else {
            this.setData({ page: 1, hasMore: true }, () => this.fetchRecords());
        }
    },

    loadMore() {
        if (this.data.loading) return;

        if (this.data.errorMessage) {
            this.fetchRecords();
            return;
        }

        if (!this.data.hasMore) return;

        this.setData({ page: this.data.page + 1, errorMessage: '' });
        this.fetchRecords();
    },

    onPullDownRefresh() {
        this.setData({
            page: 1,
            hasMore: true,
            errorMessage: '',
            rewardsError: '',
            dailyError: '',
            monthlyError: ''
        });
        this.fetchOverview();

        if (this.data.filter === 'month') {
            this.fetchMonthlyData();
        } else if (this.data.filter === 'day') {
            this.fetchDailyData();
        } else if (this.data.subFilter === 'reward') {
            this.fetchRewards();
        } else {
            this.fetchRecords();
        }

        wx.stopPullDownRefresh();
    }
});
