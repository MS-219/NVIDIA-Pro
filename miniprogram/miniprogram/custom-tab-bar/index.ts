
import { API_BASE } from '../config';

Component({
    data: {
        selected: 0,
        color: "#999999",
        selectedColor: "#16a34a",
        list: [{
            "pagePath": "/pages/index/index",
            "text": "首页",
            "iconPath": "/images/tabbar/home.png",
            "selectedIconPath": "/images/tabbar/home-active.png"
        }, {
            "pagePath": "/pages/device/device",
            "text": "设备",
            "iconPath": "/images/tabbar/device.png",
            "selectedIconPath": "/images/tabbar/device-active.png"
        }, {
            "pagePath": "/pages/creation/creation",
            "text": "创作",
            "iconPath": "/images/tabbar/creation.png",
            "selectedIconPath": "/images/tabbar/creation-active.png"
        }, {
            "pagePath": "/pages/my/my",
            "text": "我的",
            "iconPath": "/images/tabbar/my.png",
            "selectedIconPath": "/images/tabbar/my-active.png"
        }]
    },
    methods: {
        switchTab(e: any) {
            const data = e.currentTarget.dataset
            const url = data.path
            wx.switchTab({ url })
            this.setData({
                selected: data.index
            })
        },

        // 点击中间扫码按钮 -> 弹出菜单
        onScanCode() {
            // 先检查是否登录
            const userId = wx.getStorageSync('userId');
            if (!userId) {
                wx.showModal({
                    title: '请先登录',
                    content: '绑定设备需要先登录账号',
                    confirmText: '去登录',
                    success: (res) => {
                        if (res.confirm) {
                            wx.switchTab({ url: '/pages/my/my' });
                        }
                    }
                });
                return;
            }

            wx.showActionSheet({
                itemList: ['扫码绑定', '手动输入设备号'],
                success: (res) => {
                    if (res.tapIndex === 0) {
                        this.startScan();
                    } else if (res.tapIndex === 1) {
                        this.inputDeviceSn();
                    }
                }
            });
        },

        // 手动输入设备号
        inputDeviceSn() {
            wx.showModal({
                title: '绑定设备',
                editable: true,
                placeholderText: '请输入设备SN或绑定码',
                success: (res) => {
                    if (res.confirm && res.content) {
                        const sn = res.content.trim();
                        if (sn) {
                            this.queryAndBind(sn);
                        }
                    }
                }
            });
        },

        // 实际执行扫码
        startScan() {
            wx.scanCode({
                success: (res) => {
                    let sn = res.result;
                    if (!sn) {
                        wx.showToast({ title: '无效的二维码', icon: 'none' });
                        return;
                    }

                    // 解析 URL 格式的二维码
                    if (sn.includes('?code=')) {
                        const match = sn.match(/[?&]code=([^&]+)/);
                        if (match) sn = decodeURIComponent(match[1]);
                    } else if (sn.includes('?sn=')) {
                        const match = sn.match(/[?&]sn=([^&]+)/);
                        if (match) sn = decodeURIComponent(match[1]);
                    }

                    this.queryAndBind(sn);
                }
            });
        },

        // 查询并绑定流程
        queryAndBind(snOrCode: string) {
            const code = snOrCode.trim();

            wx.showLoading({ title: '查询设备...' });

            wx.request({
                url: `${API_BASE}/api/device/query-by-code`,
                method: 'GET',
                data: { code },
                success: (queryRes: any) => {
                    wx.hideLoading();
                    if (queryRes.data.code === 200) {
                        const device = queryRes.data.data;
                        if (device.bound) {
                            wx.showModal({
                                title: '设备已绑定',
                                content: `该设备已被其他用户绑定，无法重复绑定。`,
                                showCancel: false
                            });
                            return;
                        }
                        // 确认绑定
                        wx.showModal({
                            title: '确认绑定设备',
                            content: `设备: ${device.name || device.sn}\n状态: ${device.status === 1 ? '在线' : '离线'}\n\n确定要绑定此设备吗？`,
                            success: (confirmRes) => {
                                if (confirmRes.confirm) {
                                    this.doBindDevice(code);
                                }
                            }
                        });
                    } else {
                        wx.showToast({ title: '设备不存在或码错误', icon: 'none' });
                    }
                },
                fail: () => {
                    wx.hideLoading();
                    wx.showToast({ title: '查询失败', icon: 'none' });
                }
            });
        },

        // 执行绑定
        doBindDevice(code: string) {
            const userId = wx.getStorageSync('userId');
            if (!userId) {
                wx.showToast({ title: '请先登录', icon: 'none' });
                return;
            }

            wx.showLoading({ title: '绑定中...' });

            wx.request({
                url: `${API_BASE}/api/device/bind`,
                method: 'POST',
                header: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${wx.getStorageSync('token') || ''}`
                },
                data: { code, userId },
                success: (res: any) => {
                    if (res.data.code === 200) {
                        wx.showToast({ title: '绑定成功', icon: 'success' });
                        // 跳转到设备页面
                        wx.switchTab({ url: '/pages/device/device' });
                    } else {
                        wx.showToast({ title: res.data.msg || '绑定失败', icon: 'none' });
                    }
                },
                fail: () => {
                    wx.showToast({ title: '请求失败', icon: 'none' });
                },
                complete: () => {
                    wx.hideLoading();
                }
            });
        }
    }
})
