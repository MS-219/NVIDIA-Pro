import { API_BASE } from '../../config';
export { };

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        noticeId: '',
        notice: null as any,
        loading: true,
        errorMessage: ''
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });

        const id = options.id;
        if (id) {
            this.setData({ noticeId: id });
            this.fetchNoticeDetail(id);
        } else {
            this.setData({ loading: false, errorMessage: '公告参数缺失' });
        }
    },

    fetchNoticeDetail(id: string) {
        this.setData({ loading: true, errorMessage: '' });
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
                    this.setData({
                        loading: false,
                        errorMessage: res.data.msg || '公告内容同步失败'
                    });
                }
            },
            fail: () => {
                this.setData({ loading: false, errorMessage: '网络连接异常，请重新同步' });
            }
        });
    },

    retryNotice() {
        if (this.data.noticeId) this.fetchNoticeDetail(this.data.noticeId);
    },

    formatTime(timeStr: string) {
        if (!timeStr) return '';
        return timeStr.replace('T', ' ').substring(0, 19);
    },

    goBack() {
        wx.navigateBack();
    }
});
