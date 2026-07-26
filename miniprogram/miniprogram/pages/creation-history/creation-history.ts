import { API_BASE } from '../../config';

interface TaskRecord {
    id: number;
    taskId: string;
    taskType: string;
    prompt: string;
    status: string;
    resultUrl: string;
    createTime: string;
    costQuota: number;
    options?: string;
    statusText?: string;
    typeText?: string;
    typeIcon?: string;
    errorMsg?: string;
    // 解析后的参数
    duration?: string;
    aspectRatio?: string;
    resolution?: string;
    size?: string;
    inputImageUrls?: string[];
    isVideo?: boolean;
}

Page({
    data: {
        records: [] as TaskRecord[],
        page: 1,
        size: 20,
        hasMore: true,
        loading: false,
        showDetailModal: false,
        currentTask: null as TaskRecord | null
    },

    onLoad() {
        this.fetchRecords();
    },

    onShow() {
        // 每次显示时刷新列表
        this.setData({ page: 1 }, () => {
            this.fetchRecords();
        });
    },

    onPullDownRefresh() {
        this.setData({ page: 1, hasMore: true }, () => {
            this.fetchRecords().finally(() => {
                wx.stopPullDownRefresh();
            });
        });
    },

    onReachBottom() {
        if (this.data.hasMore && !this.data.loading) {
            this.setData({ page: this.data.page + 1 }, () => {
                this.fetchRecords();
            });
        }
    },

    fetchRecords() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return Promise.resolve();

        this.setData({ loading: true });
        return new Promise((resolve) => {
            wx.request({
                url: `${API_BASE}/api/ai/my-tasks`,
                method: 'GET',
                data: {
                    userId,
                    page: this.data.page,
                    size: this.data.size
                },
                success: (res: any) => {
                    if (res.data.code === 200) {
                        const data = res.data.data;
                        const newRecords = (data.records || []).map((t: any) => this.formatTask(t));

                        this.setData({
                            records: this.data.page === 1 ? newRecords : [...this.data.records, ...newRecords],
                            hasMore: data.pages > data.current,
                            loading: false
                        });
                    } else {
                        this.setData({ loading: false });
                    }
                },
                fail: () => {
                    this.setData({ loading: false });
                    wx.showToast({ title: '加载失败', icon: 'none' });
                },
                complete: () => {
                    resolve(true);
                }
            });
        });
    },

    formatTask(task: any): TaskRecord {
        let typeText = '未知任务';
        let typeIcon = '❓';
        const type = task.taskType || '';
        const isVideo = type.toLowerCase().includes('video');

        if (type.includes('image')) {
            typeText = '图片生成';
            typeIcon = '🎨';
        } else if (isVideo) {
            typeText = '视频创作';
            typeIcon = '📹';
        } else if (type === 'chat') {
            typeText = 'AI 问答';
            typeIcon = '💬';
        }

        let statusText = '生成中';
        if (task.status === 'completed' || task.status === 'success') statusText = '已完成';
        else if (task.status === 'failed') statusText = '生成失败';
        else if (task.status === 'processing') statusText = '生成中';

        // 解析 options 参数
        const parsed = this.parseOptions(task.options);

        // 解析素材图 (多图支持)
        const inputImageUrls = task.inputImageUrl ? task.inputImageUrl.split(',') : [];

        // 处理本地文件路径
        let resultUrl = task.resultUrl;
        if (resultUrl && resultUrl.startsWith('/uploads/')) {
            resultUrl = `${API_BASE}${resultUrl}`;
        }

        return {
            ...task,
            typeText,
            typeIcon,
            statusText,
            resultUrl, // 使用处理后的 URL
            createTime: this.formatTime(task.createTime),
            inputImageUrls,
            isVideo, // 添加 isVideo 字段
            ...parsed
        };
    },

    parseOptions(options: string): any {
        if (!options) return {};
        const result: any = {};
        const parts = options.split(',');
        for (const part of parts) {
            const [key, value] = part.split('=');
            if (key && value && key !== 'apiTaskId') {
                result[key] = value;
            }
        }
        return result;
    },

    formatTime(timeStr: string) {
        if (!timeStr) return '';
        return timeStr.replace('T', ' ').substring(0, 19);
    },

    showDetail(e: any) {
        const item = e.currentTarget.dataset.item;
        this.setData({
            showDetailModal: true,
            currentTask: item
        });
    },

    hideDetail() {
        this.setData({ showDetailModal: false });
    },

    stopPropagation() {
        // 阻止事件冒泡
    },

    onVideoError(e: any) {
        console.error('视频播放错误:', e.detail);
        wx.showToast({
            title: '视频加载失败',
            icon: 'none'
        });
    },

    previewImage() {
        const task = this.data.currentTask;
        if (task && task.resultUrl) {
            wx.previewImage({ urls: [task.resultUrl] });
        }
    },

    previewMaterial(e: any) {
        const url = e.currentTarget.dataset.url;
        const task = this.data.currentTask;
        if (task && task.inputImageUrls) {
            wx.previewImage({
                current: url,
                urls: task.inputImageUrls
            });
        }
    },

    saveToAlbum() {
        const task = this.data.currentTask;
        if (!task || !task.resultUrl) {
            wx.showToast({ title: '未找到作品地址', icon: 'none' });
            return;
        }

        // 检查是否已经授权保存到相册
        wx.getSetting({
            success: (res) => {
                const auth = res.authSetting['scope.writePhotosAlbum'];
                if (auth === false) {
                    // 已拒绝过，引导开启
                    wx.showModal({
                        title: '需要相册权限',
                        content: '请在设置中开启相册权限，以便将作品保存到手机相册。',
                        confirmText: '去设置',
                        success: (modalRes) => {
                            if (modalRes.confirm) {
                                wx.openSetting();
                            }
                        }
                    });
                } else if (auth === true) {
                    // 已授权
                    this.doSaveAction(task);
                } else {
                    // 未请求过，直接发起下载和保存
                    this.doSaveAction(task);
                }
            },
            fail: () => {
                this.doSaveAction(task);
            }
        });
    },

    doSaveAction(task: TaskRecord) {
        // 验证 URL 是否有效
        if (!task.resultUrl || !task.resultUrl.startsWith('http')) {
            wx.showToast({ title: '文件地址无效', icon: 'none' });
            console.error('Invalid resultUrl:', task.resultUrl);
            return;
        }

        // 处理URL，确保正确编码
        let downloadUrl = task.resultUrl;
        // 如果URL包含中文或特殊字符，需要编码（但不要重复编码）
        if (!/^[a-zA-Z0-9:/.?=&_-]+$/.test(downloadUrl)) {
            // URL可能包含中文，尝试编码文件名部分
            const urlParts = downloadUrl.split('/');
            const fileName = urlParts[urlParts.length - 1];
            if (fileName && !/^[a-zA-Z0-9._-]+$/.test(fileName)) {
                urlParts[urlParts.length - 1] = encodeURIComponent(fileName);
                downloadUrl = urlParts.join('/');
            }
        }

        console.log('开始下载:', downloadUrl);
        wx.showLoading({ title: '下载中...', mask: true });

        // 构造本地路径，解决部分机型后缀丢失问题
        const isVideo = task.taskType.toLowerCase().includes('video');
        const ext = isVideo ? 'mp4' : 'jpg';
        const filePath = `${wx.env.USER_DATA_PATH}/file_${Date.now()}.${ext}`;

        wx.downloadFile({
            url: downloadUrl,
            filePath: filePath, // 指定固定路径
            success: (res) => {
                console.log('下载状态:', res.statusCode, '路径:', res.filePath);
                if (res.statusCode === 200 && res.filePath) {
                    if (isVideo) {
                        wx.saveVideoToPhotosAlbum({
                            filePath: res.filePath,
                            success: () => {
                                wx.hideLoading();
                                wx.showToast({ title: '视频已保存', icon: 'success' });
                            },
                            fail: (err: any) => {
                                wx.hideLoading();
                                console.error('保存视频失败:', err);
                                this.handleSaveError(err);
                            }
                        });
                    } else {
                        wx.saveImageToPhotosAlbum({
                            filePath: res.filePath,
                            success: () => {
                                wx.hideLoading();
                                wx.showToast({ title: '图片已保存', icon: 'success' });
                            },
                            fail: (err: any) => {
                                wx.hideLoading();
                                console.error('保存图片失败:', err);
                                this.handleSaveError(err);
                            }
                        });
                    }
                } else {
                    wx.hideLoading();
                    console.error('下载失败 HTTP状态:', res.statusCode);
                    wx.showModal({
                        title: '下载失败',
                        content: `HTTP状态码: ${res.statusCode}\n可能是文件不存在或服务器配置问题`,
                        showCancel: false
                    });
                }
            },
            fail: (err: any) => {
                wx.hideLoading();
                console.error('下载文件失败:', err);
                console.error('失败URL:', downloadUrl);

                // 提供更详细的错误信息
                if (err.errMsg && err.errMsg.includes('domain')) {
                    wx.showModal({
                        title: '域名未配置',
                        content: `文件服务器域名未在小程序后台配置。\n\n请在微信公众平台-开发管理-开发设置中，将 orin-support.cn 添加到 downloadFile 合法域名。`,
                        showCancel: false
                    });
                } else if (err.errMsg && err.errMsg.includes('url')) {
                    wx.showModal({
                        title: 'URL格式错误',
                        content: `文件URL格式不正确: ${task.resultUrl.substring(0, 50)}...`,
                        showCancel: false
                    });
                } else {
                    wx.showModal({
                        title: '下载失败',
                        content: err.errMsg || '网络错误，请检查网络连接',
                        showCancel: false
                    });
                }
            }
        });
    },

    handleSaveError(err: any) {
        if (err.errMsg && (err.errMsg.includes('auth') || err.errMsg.includes('deny'))) {
            wx.showModal({
                title: '需要相册权限',
                content: '请在设置中开启相册权限，以便将作品保存到手机相册。',
                confirmText: '去设置',
                success: (modalRes) => {
                    if (modalRes.confirm) {
                        wx.openSetting();
                    }
                }
            });
        } else {
            wx.showToast({ title: '保存失败', icon: 'none' });
        }
    },

    refreshTask() {
        const task = this.data.currentTask;
        if (!task) return;

        wx.showLoading({ title: '刷新中...' });
        wx.request({
            url: `${API_BASE}/api/ai/video-status/${task.taskId}`,
            method: 'GET',
            success: (res: any) => {
                wx.hideLoading();
                if (res.data.code === 200) {
                    const data = res.data.data;
                    if (data.status === 'completed') {
                        // 更新当前任务状态
                        const updatedTask = {
                            ...task,
                            status: 'completed',
                            statusText: '已完成',
                            resultUrl: data.videoUrl
                        };
                        this.setData({ currentTask: updatedTask });
                        // 刷新列表
                        this.setData({ page: 1 }, () => this.fetchRecords());
                        wx.showToast({ title: '生成完成', icon: 'success' });
                    } else if (data.status === 'failed') {
                        wx.showToast({ title: '生成失败', icon: 'none' });
                    } else {
                        wx.showToast({ title: '仍在生成中', icon: 'none' });
                    }
                }
            },
            fail: () => {
                wx.hideLoading();
                wx.showToast({ title: '刷新失败', icon: 'none' });
            }
        });
    }
});

