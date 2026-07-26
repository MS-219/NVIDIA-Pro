import { request } from '../../utils/request';
export { };

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        loading: true,
        deviceId: 0,
        device: {} as any
    },

    onLoad(options: any) {
        const menuButton = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButton.top,
            navBarHeight: menuButton.height + 8
        });

        if (options.deviceId) {
            this.setData({ deviceId: parseInt(options.deviceId) });
            this.fetchDeviceDetail(options.deviceId);
        }
    },

    goBack() {
        wx.navigateBack();
    },

    fetchDeviceDetail(deviceId: number) {
        const userId = wx.getStorageSync('userId');
        this.setData({ loading: true });

        request({
            url: '/api/invite/partner-device-detail',
            method: 'GET',
            data: { userId, deviceId }
        }).then((res: any) => {
            if (res.code === 200) {
                const { device } = res.data;
                // 格式化时间
                if (device.lastHeartbeat) {
                    device.lastHeartbeat = device.lastHeartbeat.replace('T', ' ').substring(0, 19);
                }
                if (device.bindTime) {
                    device.bindTime = device.bindTime.replace('T', ' ').substring(0, 10);
                }
                this.setData({
                    device,
                    loading: false
                });
            } else {
                wx.showToast({ title: res.msg || '加载失败', icon: 'none' });
                this.setData({ loading: false });
            }
        }).catch(err => {
            console.error('fetchDeviceDetail error:', err);
            wx.showToast({ title: '网络错误', icon: 'none' });
            this.setData({ loading: false });
        });
    },

    onPullDownRefresh() {
        this.fetchDeviceDetail(this.data.deviceId);
        wx.stopPullDownRefresh();
    }
});
