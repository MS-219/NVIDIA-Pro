import { request } from '../../utils/request';
export { }; // 使文件成为 ES 模块

Page({
  data: {
    navBarTop: 0,
    navBarHeight: 44,
    deviceList: [] as any[],
    allDeviceList: [] as any[], // 存储所有设备，用于筛选
    currentTab: 'all' as string, // 当前筛选tab
    searchKeyword: '',
    onlineCount: 0,
    offlineCount: 0,
    loading: false,
    loadError: '',
    loginRequired: false,
    firstShowHandled: false
  },

  onLoad() {
    const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
    this.setData({
      navBarTop: menuButtonInfo.top,
      navBarHeight: menuButtonInfo.height
    });
    this.fetchDeviceList();
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      })
    }
    if (!this.data.firstShowHandled) {
      this.setData({ firstShowHandled: true });
      return;
    }

    // 从详情页返回时刷新设备列表
    this.fetchDeviceList();
  },

  onPullDownRefresh() {
    this.fetchDeviceList().finally(() => {
      wx.stopPullDownRefresh();
    });
  },

  handleRefresh() {
    if (this.data.loading) return;
    wx.showLoading({ title: '刷新中...', mask: true });
    this.fetchDeviceList().then((res: any) => {
      if (res && res.code === 200) {
        wx.showToast({ title: '已刷新', icon: 'success' });
      }
    }).finally(() => {
      wx.hideLoading();
    });
  },

  // 获取设备列表
  fetchDeviceList() {
    if (this.data.loading) return Promise.resolve(null);

    this.setData({ loading: true, loadError: '', loginRequired: false });
    const userId = wx.getStorageSync('userId');
    if (!userId) {
      this.setData({ loading: false, loadError: '登录后可管理您的 Orin 节点', loginRequired: true });
      return Promise.resolve(null);
    }

    return request({
      url: '/api/device/list',
      method: 'GET',
      data: { userId }
    }).then(res => {
      if (res.code === 200) {
        const list = (res.data || []).map((item: any) => ({
          ...item,
          // 格式化显示字段
          statusText: item.status === 1 ? '服务中' : '已离线',
          status: item.status === 1 ? 'online' : 'offline',
          color: item.status === 1 ? '#10ac84' : '#bdc3c7',
          date: item.todayDate || this.formatDate(new Date().toISOString()),
          onlineTime: this.formatDate(item.bindTime),
          earnings: item.earnings || '0.00',
          remarks: item.name || '暂无备注'
        }));
        const onlineCount = list.filter((d: any) => d.status === 'online').length;
        const offlineCount = list.filter((d: any) => d.status === 'offline').length;
        // 保存所有设备，并根据当前 tab 和搜索词筛选
        this.setData({ allDeviceList: list, onlineCount, offlineCount });
        this.filterDeviceList();
      } else {
        this.setData({ loadError: res.msg || '设备数据同步异常' });
        wx.showToast({ title: res.msg || '获取列表失败', icon: 'none' });
      }
      return res;
    }).catch(err => {
      console.error('fetchDeviceList error:', err);
      this.setData({ loadError: '设备数据同步异常，请稍后重试' });
      return null;
    }).finally(() => {
      this.setData({ loading: false });
    });
  },

  // 格式化日期
  formatDate(dateStr: string) {
    if (!dateStr) return '--';
    if (dateStr.includes('T')) {
      return dateStr.split('T')[0];
    }
    if (dateStr.includes(' ')) {
      return dateStr.split(' ')[0];
    }
    return dateStr;
  },

  // 根据当前 tab 筛选设备列表
  filterDeviceList() {
    const { allDeviceList, currentTab, searchKeyword } = this.data;
    let filtered = allDeviceList;
    if (currentTab === 'online') {
      filtered = allDeviceList.filter((d: any) => d.status === 'online');
    } else if (currentTab === 'offline') {
      filtered = allDeviceList.filter((d: any) => d.status === 'offline');
    }
    const keyword = (searchKeyword || '').trim().toLowerCase();
    if (keyword) {
      filtered = filtered.filter((d: any) => {
        return [d.sn, d.bindCode, d.businessId, d.name, d.remarks]
          .filter(Boolean)
          .some((value: any) => String(value).toLowerCase().includes(keyword));
      });
    }
    this.setData({ deviceList: filtered });
  },

  // 切换筛选 tab
  switchTab(e: any) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ currentTab: tab });
    this.filterDeviceList();
  },

  onSearchInput(e: any) {
    this.setData({ searchKeyword: e.detail.value || '' }, () => {
      this.filterDeviceList();
    });
  },

  handleSearch() {
    const keyword = (this.data.searchKeyword || '').trim();
    this.filterDeviceList();
    if (keyword && this.data.deviceList.length === 0) {
      wx.showToast({ title: '没有匹配设备', icon: 'none' });
    }
  },

  clearSearch() {
    this.setData({ searchKeyword: '' }, () => {
      this.filterDeviceList();
    });
  },

  resetFilters() {
    this.setData({ searchKeyword: '', currentTab: 'all' }, () => {
      this.filterDeviceList();
    });
  },

  handleLoadErrorAction() {
    if (this.data.loginRequired) {
      wx.switchTab({ url: '/pages/my/my' });
      return;
    }
    this.fetchDeviceList();
  },

  onDeviceCardTap(e: any) {
    this.viewDeviceDetail(Number(e.currentTarget.dataset.id));
  },

  openFilterSheet() {
    wx.showActionSheet({
      itemList: ['全部设备', '在线设备', '离线设备'],
      success: (res) => {
        const tabs = ['all', 'online', 'offline'];
        this.setData({ currentTab: tabs[res.tapIndex] || 'all' });
        this.filterDeviceList();
      }
    });
  },

  onManageDevice(e: any) {
    const id = Number(e.currentTarget.dataset.id);
    wx.showActionSheet({
      itemList: ['编辑备注', '解绑设备'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.editDeviceName(id);
        } else if (res.tapIndex === 1) {
          this.unbindDevice(id);
        }
      }
    });
  },

  // 复制内容
  onCopyContent(e: any) {
    const content = e.currentTarget.dataset.content;
    if (!content || content === '暂未分配') {
      wx.showToast({ title: '无内容可复制', icon: 'none' });
      return;
    }
    wx.setClipboardData({
      data: content,
      success: () => {
        wx.showToast({ title: '绑定码已复制', icon: 'success' });
      },
      fail: () => {
        wx.showToast({ title: '复制失败，请重试', icon: 'none' });
      }
    });
  },

  // 查看设备详情
  viewDeviceDetail(id: number) {
    if (!id) return;
    wx.navigateTo({
      url: `/pages/device-detail/device-detail?id=${id}`,
      fail: (err) => {
        console.error('跳转失败', err);
        wx.showToast({ title: '页面跳转失败', icon: 'none' });
      }
    });
  },

  // 编辑设备备注
  editDeviceName(id: number) {
    const device = this.data.deviceList.find(d => d.id === id);
    wx.showModal({
      title: '编辑备注',
      editable: true,
      placeholderText: '请输入设备备注',
      content: device?.name || '',
      success: (res) => {
        if (res.confirm && res.content) {
          request({
            url: '/api/device/update',
            method: 'POST',
            data: { id, name: res.content }
          }).then(resp => {
            if (resp.code === 200) {
              wx.showToast({ title: '更新成功', icon: 'success' });
              this.fetchDeviceList();
            } else {
              wx.showToast({ title: resp.msg || '更新失败', icon: 'none' });
            }
          }).catch(err => {
            console.error('editDeviceName error:', err);
          });
        }
      }
    });
  },

  // 解绑设备
  unbindDevice(id: number) {
    wx.showModal({
      title: '确认解绑',
      content: '解绑后设备将从您的账户移除，确定要解绑吗？',
      success: (res) => {
        if (res.confirm) {
          request({
            url: '/api/device/unbind',
            method: 'POST',
            data: { id }
          }).then(resp => {
            if (resp.code === 200) {
              wx.showToast({ title: '解绑成功', icon: 'success' });
              this.fetchDeviceList();
            } else {
              wx.showToast({ title: resp.msg || '解绑失败', icon: 'none' });
            }
          }).catch(err => {
            console.error('unbindDevice error:', err);
          });
        }
      }
    });
  },

  // 扫码添加设备
  onAddDevice() {
    wx.scanCode({
      success: (res) => {
        let code = res.result;
        if (!code) {
          wx.showToast({ title: '无效的二维码', icon: 'none' });
          return;
        }

        // 解析 URL 格式的二维码 (如: https://nvidia.juxinsuanli.cn/bind?code=Orin-A1B2C3)
        if (code.includes('?code=')) {
          const match = code.match(/[?&]code=([^&]+)/);
          if (match) {
            code = decodeURIComponent(match[1]);
          }
        } else if (code.includes('?sn=')) {
          // 兼容旧的 SN 格式
          const match = code.match(/[?&]sn=([^&]+)/);
          if (match) {
            code = decodeURIComponent(match[1]);
          }
        }

        // 清理纯设备绑定码，后端兼容大小写输入
        code = code.trim().toUpperCase();

        // 先查询设备信息（使用绑定码查询）
        wx.showLoading({ title: '查询设备...' });
        request({
          url: '/api/device/query-by-code',
          method: 'GET',
          data: { code }
        }).then(queryRes => {
          wx.hideLoading();
          if (queryRes.code === 200) {
            const device = queryRes.data;

            if (device.bound) {
              wx.showModal({
                title: '设备已绑定',
                content: `该设备(${code})已被其他用户绑定，无法重复绑定。`,
                showCancel: false
              });
              return;
            }

            // 确认绑定
            wx.showModal({
              title: '确认绑定设备',
              content: `设备码: ${code}\n设备名称: ${device.name}\n状态: ${device.status === 1 ? '在线' : '离线'}\n\n确定要绑定此设备吗？`,
              success: (confirmRes) => {
                if (confirmRes.confirm) {
                  this.doBindDevice(code);
                }
              }
            });
          } else {
            wx.showToast({ title: queryRes.msg || '设备不存在', icon: 'none' });
          }
        }).catch(err => {
          wx.hideLoading();
          console.error('query-by-code error:', err);
        });
      },
      fail: () => {
        // 用户取消扫码
      }
    });
  },

  // 执行绑定
  doBindDevice(code: string) {
    wx.showLoading({ title: '绑定中...', mask: true });
    const userId = wx.getStorageSync('userId');
    if (!userId) {
      wx.hideLoading();
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }

    request({
      url: '/api/device/bind',
      method: 'POST',
      data: { code, userId }
    }).then(res => {
      wx.hideLoading();
      if (res.code === 200) {
        wx.showToast({ title: '绑定成功', icon: 'success' });
        // 成功后延迟刷新，确保数据一致性并给用户视觉反馈
        setTimeout(() => {
          this.fetchDeviceList();
        }, 800);
      } else {
        wx.showToast({ title: res.msg || '绑定失败', icon: 'none' });
      }
    }).catch(err => {
      wx.hideLoading();
      console.error('bind device error:', err);
    });
  }
})
