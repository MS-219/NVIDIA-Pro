import { API_BASE } from '../../config';
import { request } from '../../utils/request';
export { };

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        feedbackType: 'suggestion',
        typeOptions: [
            { value: 'suggestion', label: '功能建议' },
            { value: 'bug', label: '问题反馈' },
            { value: 'complaint', label: '投诉建议' },
            { value: 'other', label: '其他' }
        ],
        content: '',
        contact: '',
        images: [] as string[],
        submitting: false
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
    },

    goBack() {
        wx.navigateBack();
    },

    selectType(e: any) {
        this.setData({ feedbackType: e.currentTarget.dataset.type });
    },

    onContentInput(e: any) {
        this.setData({ content: e.detail.value });
    },

    onContactInput(e: any) {
        this.setData({ contact: e.detail.value });
    },

    chooseImage() {
        const maxCount = 3 - this.data.images.length;
        if (maxCount <= 0) {
            wx.showToast({ title: '最多上传3张图片', icon: 'none' });
            return;
        }

        wx.chooseMedia({
            count: maxCount,
            mediaType: ['image'],
            sourceType: ['album', 'camera'],
            success: (res) => {
                const newImages = res.tempFiles.map(f => f.tempFilePath);
                this.setData({
                    images: [...this.data.images, ...newImages]
                });
            }
        });
    },

    removeImage(e: any) {
        const index = e.currentTarget.dataset.index;
        const images = [...this.data.images];
        images.splice(index, 1);
        this.setData({ images });
    },

    async submit() {
        const { feedbackType, content, contact, images } = this.data;

        if (!content || content.trim().length < 10) {
            wx.showToast({ title: '请详细描述您的问题（至少10个字）', icon: 'none' });
            return;
        }

        this.setData({ submitting: true });

        try {
            const token = wx.getStorageSync('token');
            // 上传图片
            let imageUrls: string[] = [];
            if (images.length > 0) {
                wx.showLoading({ title: '上传图片中...', mask: true });
                for (const img of images) {
                    const uploadRes: any = await new Promise((resolve, reject) => {
                        wx.uploadFile({
                            url: `${API_BASE}/api/upload/image`,
                            filePath: img,
                            name: 'file',
                            header: {
                                'Authorization': `Bearer ${token}`
                            },
                            success: resolve,
                            fail: reject
                        });
                    });
                    const result = JSON.parse(uploadRes.data);
                    if (result.code === 200 && result.data?.url) {
                        imageUrls.push(result.data.url);
                    }
                }
                wx.hideLoading();
            }

            // 提交反馈
            wx.showLoading({ title: '提交中...', mask: true });
            const userId = wx.getStorageSync('userId');

            const res = await request({
                url: '/api/feedback/submit',
                method: 'POST',
                data: {
                    userId,
                    type: feedbackType,
                    content: content.trim(),
                    contact: contact.trim(),
                    images: imageUrls.join(',')
                }
            });

            wx.hideLoading();

            if (res.code === 200) {
                wx.showModal({
                    title: '提交成功',
                    content: '感谢您的反馈，我们会尽快处理！',
                    showCancel: false,
                    success: () => {
                        wx.navigateBack();
                    }
                });
            } else {
                wx.showToast({ title: res.msg || '提交失败，请重试', icon: 'none' });
            }
        } catch (error) {
            wx.hideLoading();
            wx.showToast({ title: '提交失败，请重试', icon: 'none' });
        } finally {
            this.setData({ submitting: false });
        }
    }
});

