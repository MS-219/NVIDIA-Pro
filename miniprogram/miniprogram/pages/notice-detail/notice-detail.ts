import { API_BASE } from '../../config';
export { };

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        notice: null as any,
        loading: true
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });

        const id = options.id;
        if (id) {
            this.fetchNoticeDetail(id);
        } else {
            wx.showToast({ title: '公告不存在', icon: 'none' });
            setTimeout(() => wx.navigateBack(), 1500);
        }
    },

    fetchNoticeDetail(id: string) {
        wx.request({
            url: `${API_BASE}/api/notice/detail/${id}`,
            method: 'GET',
            success: (res: any) => {
                if (res.data.code === 200) {
                    const notice = res.data.data;
                    this.setData({
                        notice: {
                            id: notice.id,
                            title: notice.title,
                            content: notice.content,
                            imageUrl: notice.imageUrl || '',
                            publishTime: this.formatTime(notice.publishTime || notice.createTime)
                        },
                        loading: false
                    });
                } else {
                    wx.showToast({ title: res.data.msg || '获取公告失败', icon: 'none' });
                    setTimeout(() => wx.navigateBack(), 1500);
                }
            },
            fail: () => {
                wx.showToast({ title: '网络错误', icon: 'none' });
                setTimeout(() => wx.navigateBack(), 1500);
            }
        });
    },

    formatTime(timeStr: string) {
        if (!timeStr) return '';
        return timeStr.replace('T', ' ').substring(0, 19);
    },

    goBack() {
        wx.navigateBack();
    }
});
