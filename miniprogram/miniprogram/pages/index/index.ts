import { request } from '../../utils/request';
export { }; // 使文件成为 ES 模块

Page({
  data: {
    navBarTop: 0,
    navBarHeight: 44,
    bannerList: [] as any[],
    earnings: {
      yesterday: '0.00',
      total: '0.00',
      yesterdayClass: '',
      totalClass: ''
    },
    nodeStats: {
      total: 0,
      online: 0,
      offline: 0,
      partnerDevices: 0,
      onlineRate: 0
    },
    notices: [] as any[],
    firstShowHandled: false,
    statisticsLoading: false,
    partnerStatsLoading: false,
    statisticsReady: false,
    statisticsError: false,
    syncLabel: '正在同步',
    syncState: 'is-syncing'
  },

  onLoad(options: any) {
    // 获取导航栏高度
    const menuButton = wx.getMenuButtonBoundingClientRect();
    this.setData({
      navBarTop: menuButton.top,
      navBarHeight: menuButton.height
    });

    // 处理邀请码参数（从分享链接进入时）
    if (options && options.inviteCode) {
      const inviteCode = options.inviteCode;
      console.log('收到邀请码:', inviteCode);
      // 保存邀请码到本地，等待用户登录后绑定
      wx.setStorageSync('pendingInviteCode', inviteCode);
      // 如果用户已登录且没有邀请人，尝试绑定
      const userId = wx.getStorageSync('userId');
      if (userId) {
        this.tryBindInviteCode(userId, inviteCode);
      }
    }

    this.hydrateHomeSnapshot();

    this.fetchBanners();
    this.fetchNotices();
    this.fetchStatistics({ includePartner: false }).finally(() => {
      setTimeout(() => this.fetchPartnerDevices(), 200);
    });
  },

  hydrateHomeSnapshot() {
    const snapshot = wx.getStorageSync(this.getHomeSnapshotKey());
    if (!snapshot || !snapshot.ts || Date.now() - snapshot.ts > 5 * 60 * 1000) {
      return;
    }

    const cachedNodeStats = snapshot.nodeStats || this.data.nodeStats;
    const total = Math.max(0, Number(cachedNodeStats.total) || 0);
    const online = Math.min(total, Math.max(0, Number(cachedNodeStats.online) || 0));
    const cachedEarnings = snapshot.earnings || this.data.earnings;
    const yesterday = this.formatEarningsValue(cachedEarnings.yesterday);
    const earningsTotal = this.formatEarningsValue(cachedEarnings.total);

    this.setData({
      bannerList: snapshot.bannerList || this.data.bannerList,
      notices: snapshot.notices || this.data.notices,
      earnings: {
        yesterday,
        total: earningsTotal,
        yesterdayClass: this.getAmountClass(yesterday),
        totalClass: this.getAmountClass(earningsTotal)
      },
      nodeStats: {
        ...this.data.nodeStats,
        ...cachedNodeStats,
        total,
        online,
        offline: total - online,
        onlineRate: total > 0 ? Math.round((online / total) * 100) : 0
      },
      statisticsReady: true,
      syncLabel: this.formatSyncLabel(snapshot.statisticsTs || snapshot.ts, true),
      syncState: 'is-idle'
    });
  },

  cacheHomeSnapshot(patch: any) {
    const current = wx.getStorageSync(this.getHomeSnapshotKey()) || {};
    wx.setStorageSync(this.getHomeSnapshotKey(), {
      ...current,
      ...patch,
      ts: Date.now()
    });
  },

  getHomeSnapshotKey() {
    const userId = wx.getStorageSync('userId');
    return userId ? `homePageSnapshot:${userId}` : 'homePageSnapshot:guest';
  },

  formatEarningsValue(value: unknown) {
    if (value === null || value === undefined || value === '') return '0.00';
    return String(value);
  },

  getAmountClass(value: string) {
    if (value.length > 9) return 'is-compact';
    if (value.length > 6) return 'is-medium';
    return '';
  },

  formatSyncLabel(timestamp: number, cached = false) {
    if (!timestamp) return cached ? '缓存数据' : '同步完成';
    const date = new Date(timestamp);
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${hours}:${minutes} ${cached ? '缓存' : '已同步'}`;
  },

  // 尝试绑定邀请码
  tryBindInviteCode(userId: number, inviteCode: string) {
    request({
      url: '/api/invite/bind',
      method: 'POST',
      data: { userId, inviteCode }
    }).then(res => {
      if (res.code === 200) {
        console.log('邀请码绑定成功');
        wx.removeStorageSync('pendingInviteCode');
        wx.showToast({ title: '邀请绑定成功', icon: 'success' });
      } else {
        console.log('邀请码绑定失败:', res.msg);
        // 如果是"已绑定邀请人"则清除待绑定码
        if (res.msg && res.msg.includes('已绑定')) {
          wx.removeStorageSync('pendingInviteCode');
        }
      }
    }).catch(err => {
      console.error('tryBindInviteCode error:', err);
    });
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 0
      })
    }

    if (!this.data.firstShowHandled) {
      this.setData({ firstShowHandled: true });
      return;
    }

    this.fetchStatistics({ includePartner: false });
    this.fetchPartnerDevices();
  },

  onPullDownRefresh() {
    Promise.all([
      this.fetchBanners(),
      this.fetchNotices(),
      this.fetchStatistics({ includePartner: false }),
      this.fetchPartnerDevices()
    ]).finally(() => {
      wx.stopPullDownRefresh();
    });
  },

  // 获取轮播图
  fetchBanners() {
    return request({
      url: '/api/settings/banners',
      method: 'GET'
    }).then(res => {
      if (res.code === 200 && res.data && res.data.banners) {
        const banners = res.data.banners.map((item: any, index: number) => ({
          id: index + 1,
          ...item
        }));
        if (banners.length > 0) {
          this.setData({ bannerList: banners });
          this.cacheHomeSnapshot({ bannerList: banners });
        } else {
          this.setDefaultBanners();
        }
      } else {
        this.setDefaultBanners();
      }
      return res;
    }).catch(() => {
      this.setDefaultBanners();
    });
  },

  setDefaultBanners() {
    if (this.data.bannerList.length > 0) return;

    const fallbackBanners = [
      {
        id: 1,
        imageUrl: '',
        title: 'Orin 边缘算力节点',
        subtitle: '设备在线监控 · 算力任务管理 · 运行收益结算'
      }
    ];
    this.setData({ bannerList: fallbackBanners });
  },

  // 获取公告列表
  fetchNotices() {
    return request({
      url: '/api/notice/list',
      method: 'GET',
      data: { limit: 5 }
    }).then(res => {
      if (res.code === 200) {
        const notices = (res.data || []).map((item: any) => ({
          id: item.id,
          title: item.title,
          content: item.content,
          time: item.publishTime || item.createTime,
          imageUrl: item.imageUrl || ''
        }));
        this.setData({ notices });
        this.cacheHomeSnapshot({ notices });
      }
      return res;
    }).catch(err => {
      console.error('获取公告失败', err);
      // 使用默认数据
      this.setData({
        notices: [
          {
            id: 1,
            title: 'Orin 节点平台已启用',
            content: '可在设备中心查看节点在线状态、运行数据与收益记录。',
            time: '2025-12-01 10:00',
            imageUrl: ''
          }
        ]
      });
      return err;
    });
  },

  // 获取统计数据
  fetchStatistics(options?: { includePartner?: boolean }) {
    if (this.data.statisticsLoading) {
      return Promise.resolve(null);
    }

    // 获取用户ID
    const userId = wx.getStorageSync('userId');
    if (!userId) {
      this.setData({
        statisticsReady: true,
        statisticsError: false,
        syncLabel: '登录后同步',
        syncState: 'is-idle'
      });
      return Promise.resolve(null);
    }

    this.setData({
      statisticsLoading: true,
      statisticsError: false,
      syncLabel: this.data.statisticsReady ? '正在更新' : '正在同步',
      syncState: 'is-syncing'
    });

    // 获取收益统计
    return request({
      url: '/api/statistics/earnings',
      method: 'GET',
      data: { userId }
    }).then(res => {
      if (res.code === 200) {
        const data = res.data;
        const statisticsTs = Date.now();
        const total = Math.max(0, Number(data.deviceCount) || 0);
        const online = Math.min(total, Math.max(0, Number(data.onlineCount) || 0));
        const yesterday = this.formatEarningsValue(data.yesterday);
        const earningsTotal = this.formatEarningsValue(data.total);
        this.setData({
          earnings: {
            yesterday,
            total: earningsTotal,
            yesterdayClass: this.getAmountClass(yesterday),
            totalClass: this.getAmountClass(earningsTotal)
          },
          nodeStats: {
            total,
            online,
            offline: total - online,
            partnerDevices: this.data.nodeStats.partnerDevices || 0,
            onlineRate: total > 0 ? Math.round((online / total) * 100) : 0
          },
          statisticsReady: true,
          statisticsError: false,
          syncLabel: this.formatSyncLabel(statisticsTs),
          syncState: 'is-online'
        });
        this.cacheHomeSnapshot({
          earnings: this.data.earnings,
          nodeStats: this.data.nodeStats,
          statisticsTs
        });
      } else {
        this.setData({
          statisticsError: true,
          syncLabel: this.data.statisticsReady ? '更新异常' : '同步异常',
          syncState: 'is-error'
        });
      }
      return res;
    }).catch(err => {
      this.setData({
        statisticsError: true,
        syncLabel: this.data.statisticsReady ? '更新异常' : '同步异常',
        syncState: 'is-error'
      });
      return err;
    }).finally(() => {
      this.setData({ statisticsLoading: false });
      if (options && options.includePartner === false) return;
      this.fetchPartnerDevices();
    });
  },

  fetchPartnerDevices() {
    if (this.data.partnerStatsLoading) {
      return Promise.resolve(null);
    }

    const userId = wx.getStorageSync('userId');
    if (!userId) return Promise.resolve(null);

    this.setData({ partnerStatsLoading: true });

    return request({
      url: '/api/invite/stats',
      method: 'GET',
      data: { userId }
    }).then(res => {
      if (res.code === 200) {
        const data = res.data;
        // 直接使用后端返回的团队设备数
        this.setData({
          'nodeStats.partnerDevices': data.teamDeviceCount || 0
        });
        this.cacheHomeSnapshot({ nodeStats: this.data.nodeStats });
      }
      return res;
    }).finally(() => {
      this.setData({ partnerStatsLoading: false });
    });
  },

  // 点击公告
  onNoticeTap(e: any) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/notice-detail/notice-detail?id=${id}`,
      fail: () => {
        // 如果页面不存在，显示提示
        wx.showToast({
          title: '查看公告详情',
          icon: 'none'
        });
      }
    });
  },

  onBannerTap(e: any) {
    const banner = this.data.bannerList[Number(e.currentTarget.dataset.index)];
    if (!banner) return;

    if (banner.noticeId) {
      wx.navigateTo({ url: `/pages/notice-detail/notice-detail?id=${banner.noticeId}` });
      return;
    }

    const target = banner.pagePath || banner.path || banner.linkUrl || banner.targetUrl || banner.url;
    if (!target) return;

    if (/^https?:\/\//.test(target)) {
      wx.navigateTo({ url: `/pages/webview/webview?url=${encodeURIComponent(target)}` });
      return;
    }

    const normalizedTarget = target.startsWith('/') ? target : `/${target}`;
    const tabPages = ['/pages/index/index', '/pages/device/device', '/pages/my/my'];
    if (tabPages.includes(normalizedTarget.split('?')[0])) {
      wx.switchTab({ url: normalizedTarget });
    } else if (normalizedTarget.startsWith('/pages/')) {
      wx.navigateTo({ url: normalizedTarget });
    }
  },

  // 跳转到伙伴设备页面
  goToPartnerDevices() {
    wx.navigateTo({
      url: '/pages/partner-devices/partner-devices'
    });
  }
})
