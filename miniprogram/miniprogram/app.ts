// app.ts

// 保存原始的 wx.request
const originalRequest = wx.request;

// 防止重复弹窗的标志
let isShowingLoginModal = false;

// 全局 token 过期处理
function handleTokenExpired() {
  if (isShowingLoginModal) return;
  isShowingLoginModal = true;

  // 清除所有登录相关的存储
  wx.removeStorageSync('token');
  wx.removeStorageSync('userId');
  wx.removeStorageSync('userInfo');
  wx.removeStorageSync('openid');

  wx.showModal({
    title: '登录已过期',
    content: '您的登录状态已过期，请重新登录',
    showCancel: false,
    confirmText: '去登录',
    success: () => {
      isShowingLoginModal = false;
      wx.switchTab({ url: '/pages/my/my' });
    }
  });
}

// 检查响应是否为 token 过期
function isTokenExpired(res: any): boolean {
  if (!res || !res.data) return false;

  const { code, msg } = res.data;

  // 仅 401 才判为 token 过期，403 是权限不足不应清除登录态
  if (code === 401) return true;

  // 消息匹配收紧：必须明确是登录过期相关
  if (msg && typeof msg === 'string') {
    if ((msg.includes('登录') && (msg.includes('过期') || msg.includes('失效'))) ||
      msg === '请重新登录' ||
      msg.includes('token已过期') ||
      msg.includes('Token已过期')) {
      return true;
    }
  }

  return false;
}

// 重写 wx.request，添加全局拦截
(wx as any).request = function (options: WechatMiniprogram.RequestOption) {
  const originalSuccess = options.success;

  options.success = function (res: any) {
    // 检查是否 token 过期
    if (isTokenExpired(res)) {
      handleTokenExpired();
      return;
    }

    // 调用原始的 success 回调
    if (originalSuccess) {
      originalSuccess(res);
    }
  };

  // 调用原始的 wx.request
  return originalRequest.call(wx, options);
};

App<IAppOption>({
  globalData: {},
  onLaunch() {
    // 检查小程序更新
    this.checkUpdate?.()

    // 展示本地存储能力
    const logs = wx.getStorageSync('logs') || []
    logs.unshift(Date.now())
    wx.setStorageSync('logs', logs)

    // 登录
    wx.login({
      success: res => {
        console.log(res.code)
        // 发送 res.code 到后台换取 openId, sessionKey, unionId
      },
    })
  },

  // 检查小程序更新
  checkUpdate() {
    if (!wx.canIUse('getUpdateManager')) {
      return
    }

    const updateManager = wx.getUpdateManager()

    updateManager.onCheckForUpdate((res) => {
      console.log('是否有新版本：', res.hasUpdate)
    })

    updateManager.onUpdateReady(() => {
      wx.showModal({
        title: '更新提示',
        content: '新版本已经准备好，是否重启应用？',
        showCancel: false,
        confirmText: '立即更新',
        success: () => {
          // 强制重启更新
          updateManager.applyUpdate()
        }
      })
    })

    updateManager.onUpdateFailed(() => {
      wx.showModal({
        title: '更新提示',
        content: '新版本下载失败，请检查网络后重试',
        showCancel: false
      })
    })
  }
})