import { API_BASE } from '../../config';
import { request } from '../../utils/request';

export { };

interface PageData {
    navBarTop: number;
    navBarHeight: number;
    mode: string;
    modeTitle: string;
    modeCost: number;
    totalCost: number;
    isVideoMode: boolean;
    isCopywritingMode: boolean;
    needImage: boolean;
    userQuota: number;
    balance: number;
    hashrateRate: number;
    placeholder: string;
    prompt: string;
    uploadedImages: string[];
    duration: number;
    size: string;
    style: string;
    copyType: string;
    aspectRatio: string;
    resolution: string;
    durationOptions: { label: string; value: number; extraCost: number }[];
    aspectRatioOptions: { label: string; value: string; desc: string }[];
    resolutionOptions: { label: string; value: string; desc: string }[];
    sizeOptions: { label: string; value: string; desc: string }[];
    styleOptions: { label: string; value: string; icon: string }[];
    copyTypeOptions: { label: string; value: string; icon: string }[];
    quickPrompts: string[];
    generating: boolean;
    statusText: string;
    resultUrl: string;
    resultContent: string;
    taskId: string;
}

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        mode: 'video',
        modeTitle: '视频创作',
        modeCost: 200,
        totalCost: 200,
        isVideoMode: true,
        isCopywritingMode: false,
        needImage: true,
        userQuota: 0,
        balance: 0,
        hashrateRate: 100,
        placeholder: '描述你想要生成的视频内容，上传图片可让图片动起来...',
        prompt: '',
        uploadedImages: [],
        duration: 4,
        size: '1024x1024',
        style: 'default',
        aspectRatio: '16:9',
        resolution: '720p',
        durationOptions: [
            { label: '4秒', value: 4, extraCost: 0 },
            { label: '10秒', value: 10, extraCost: 50 },
            { label: '15秒', value: 15, extraCost: 100 }
        ],
        aspectRatioOptions: [
            { label: '16:9', value: '16:9', desc: '横屏' },
            { label: '9:16', value: '9:16', desc: '竖屏' },
            { label: '1:1', value: '1:1', desc: '正方形' }
        ],
        resolutionOptions: [
            { label: '720p', value: '720p', desc: '标清' },
            { label: '1080p', value: '1080p', desc: '高清' }
        ],
        sizeOptions: [
            { label: '1:1', value: '1024x1024', desc: '正方形' },
            { label: '16:9', value: '1792x1024', desc: '横屏' },
            { label: '9:16', value: '1024x1792', desc: '竖屏' }
        ],
        styleOptions: [] as { label: string; value: string; icon: string }[],
        copyTypeOptions: [] as { label: string; value: string; icon: string }[],
        copyType: 'marketing',
        quickPrompts: [] as string[],
        generating: false,
        statusText: '正在生成中...',
        resultUrl: '',
        resultContent: '',
        taskId: ''
    } as PageData,

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });

        const mode = options.mode || 'video';
        // 渲染前拉取最新配置
        this.fetchSettings(mode);
        this.loadUserQuota();
    },

    fetchSettings(mode: string) {
        request({
            url: '/api/settings/ai-config',
            method: 'GET'
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                const pricing = data.aiPricing;
                this.initMode(mode, pricing);

                // Handle hashratePerYuan from ai-config (root level) or all (earnings object)
                let rateVal = data.hashratePerYuan;
                if (!rateVal && data.earnings) {
                    rateVal = data.earnings.hashratePerYuan;
                }

                if (rateVal) {
                    const rate = parseInt(rateVal);
                    this.setData({ hashrateRate: rate });
                    // 如果已有余额，重新计算配额
                    if (this.data.balance > 0) {
                        this.setData({ userQuota: Math.round(this.data.balance * rate) });
                    }
                }
            } else {
                this.initMode(mode);
            }
        }).catch(() => {
            this.initMode(mode);
        });
    },

    loadUserQuota() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;
        request({
            url: '/api/statistics/earnings',
            method: 'GET',
            data: { userId }
        }).then(res => {
            if (res.code === 200) {
                const data = res.data;
                const balance = parseFloat(data.currentBalance !== undefined ? data.currentBalance : data.total) || 0;
                const serverQuota = data.quota;

                let finalQuota = 0;
                if (serverQuota !== undefined && serverQuota !== null) {
                    finalQuota = serverQuota;
                } else {
                    const rate = this.data.hashrateRate || 100;
                    finalQuota = Math.round(balance * rate);
                }

                this.setData({
                    balance: balance,
                    userQuota: finalQuota
                });
            }
        });
    },

    initMode(mode: string, pricing?: any) {
        const durationOptionsWithPricing = [
            { label: '4秒', value: 4, extraCost: pricing?.videoExtra4s ?? 0 },
            { label: '10秒', value: 10, extraCost: pricing?.videoExtra10s ?? 100 },
            { label: '15秒', value: 15, extraCost: pricing?.videoExtra15s ?? 300 }
        ];

        const modeConfig: Record<string, any> = {
            'video': {
                modeTitle: '视频创作',
                modeCost: pricing ? (pricing.videoGenCost || 300) : 300,
                totalCost: pricing ? (pricing.videoGenCost || 300) : 300,
                isVideoMode: true,
                needImage: true,
                placeholder: '描述视频内容，或上传图片让它动起来。例如：海浪拍打沙滩...',
                quickPrompts: ['阳光明媚的海滩', '城市夜景霓虹灯', '森林中的小溪', '雪山日出', '漫天繁星'],
                durationOptions: durationOptionsWithPricing,
                styleOptions: [
                    { label: '默认', value: 'default', icon: '🎬' },
                    { label: '电影感', value: 'cinematic', icon: '🎥' },
                    { label: '动漫风', value: 'anime', icon: '🎨' },
                    { label: '写实风', value: 'realistic', icon: '📷' },
                    { label: '科幻风', value: 'scifi', icon: '🚀' },
                    { label: '复古风', value: 'vintage', icon: '📼' },
                    { label: '梦幻风', value: 'dreamy', icon: '🌙' },
                    { label: '赛博朋克', value: 'cyberpunk', icon: '🌆' },
                    { label: '水彩画', value: 'watercolor', icon: '💧' },
                    { label: '3D渲染', value: '3d', icon: '🎲' }
                ]
            },
            'image': {
                modeTitle: '图片生成',
                modeCost: pricing ? (pricing.imageGenCost || 10) : 10,
                totalCost: pricing ? (pricing.imageGenCost || 10) : 10,
                isVideoMode: false,
                needImage: true,
                placeholder: '描述图片内容，或上传参考图进行创作。例如：赛博朋克森林...',
                quickPrompts: ['油画风格', '赛博朋克', '中国水墨', '治愈系动漫', '3D渲染'],
                styleOptions: [
                    { label: '默认', value: 'default', icon: '🖼️' },
                    { label: '油画', value: 'oil', icon: '🎨' },
                    { label: '动漫', value: 'anime', icon: '✨' },
                    { label: '写实', value: 'photo', icon: '📷' },
                    { label: '水墨画', value: 'ink', icon: '🖌️' },
                    { label: '素描', value: 'sketch', icon: '✏️' },
                    { label: '像素风', value: 'pixel', icon: '👾' },
                    { label: '赛博朋克', value: 'cyberpunk', icon: '🌆' },
                    { label: '极简风', value: 'minimal', icon: '⬜' },
                    { label: '插画风', value: 'illustration', icon: '🎭' }
                ]
            },
            'chat': {
                modeTitle: 'AI 文案',
                modeCost: pricing ? (pricing.chatCost || 1) : 1,
                totalCost: pricing ? (pricing.chatCost || 1) : 1,
                isVideoMode: false,
                isCopywritingMode: true,
                needImage: false,
                placeholder: '描述你的文案需求，例如：帮我写一段探店文案，要求有趣味性...',
                quickPrompts: ['探店美食推荐', '产品营销文案', '朗朗上口的广告语', '公众号推文', '电商产品介绍'],
                copyTypeOptions: [
                    { label: '营销推广', value: 'marketing', icon: '💰' },
                    { label: '社交媒体', value: 'social', icon: '📱' },
                    { label: '文章博客', value: 'article', icon: '📝' },
                    { label: '广告语', value: 'slogan', icon: '💡' },
                    { label: '邮件文案', value: 'email', icon: '✉️' }
                ],
                styleOptions: [
                    { label: '专业正式', value: 'professional', icon: '👔' },
                    { label: '轻松随意', value: 'casual', icon: '😊' },
                    { label: '创意新颖', value: 'creative', icon: '✨' },
                    { label: '正式严肃', value: 'formal', icon: '🎓' },
                    { label: '幽默风趣', value: 'humorous', icon: '😄' }
                ]
            }
        };

        const config = modeConfig[mode] || modeConfig['video'];
        this.setData({
            mode,
            style: config.styleOptions?.length > 0 ? config.styleOptions[0].value : 'default',
            copyType: config.copyTypeOptions?.length > 0 ? config.copyTypeOptions[0].value : 'marketing',
            isCopywritingMode: config.isCopywritingMode || false,
            ...config
        });
    },

    goBack() {
        wx.navigateBack();
    },

    onPromptInput(e: any) {
        this.setData({ prompt: e.detail.value });
    },

    selectDuration(e: any) {
        const value = e.currentTarget.dataset.value;
        const option = this.data.durationOptions.find((o: any) => o.value === value);
        const extraCost = option ? option.extraCost : 0;
        this.setData({
            duration: value,
            totalCost: this.data.modeCost + extraCost
        });
    },

    selectSize(e: any) {
        this.setData({ size: e.currentTarget.dataset.value });
    },

    selectStyle(e: any) {
        this.setData({ style: e.currentTarget.dataset.value });
    },

    selectAspectRatio(e: any) {
        this.setData({ aspectRatio: e.currentTarget.dataset.value });
    },

    selectResolution(e: any) {
        this.setData({ resolution: e.currentTarget.dataset.value });
    },

    selectCopyType(e: any) {
        this.setData({ copyType: e.currentTarget.dataset.value });
    },

    showExamples() {
        const examples: Record<string, string[]> = {
            'video': ['一只猫在阳光下打哈欠', '雨后的城市街道倒影', '科幻风格的飞船起飞'],
            'image': ['一个在云端读书的小女孩', '未来主义风格的茶馆', '极简主义的夏日午后']
        };
        const list = examples[this.data.mode] || examples['video'];
        wx.showActionSheet({
            itemList: list,
            success: (res) => {
                this.setData({ prompt: list[res.tapIndex] });
            }
        });
    },

    useQuickPrompt(e: any) {
        const promptText = e.currentTarget.dataset.prompt;
        const currentPrompt = this.data.prompt;
        const newPrompt = currentPrompt ? `${currentPrompt}，${promptText}` : promptText;
        this.setData({ prompt: newPrompt });
    },

    chooseImage() {
        const maxCount = this.data.isVideoMode ? 1 : 9;
        const remainingCount = maxCount - this.data.uploadedImages.length;
        if (remainingCount <= 0) {
            wx.showToast({ title: `最多上传${maxCount}张图片`, icon: 'none' });
            return;
        }
        wx.chooseMedia({
            count: remainingCount,
            mediaType: ['image'],
            sourceType: ['album', 'camera'],
            success: (res) => {
                const newPaths = res.tempFiles.map(f => f.tempFilePath);
                this.setData({
                    uploadedImages: [...this.data.uploadedImages, ...newPaths]
                });
            }
        });
    },

    removeImage(e: any) {
        const index = e.currentTarget.dataset.index;
        const images = [...this.data.uploadedImages];
        images.splice(index, 1);
        this.setData({ uploadedImages: images });
    },

    previewUploaded(e: any) {
        const index = e.currentTarget.dataset.index;
        wx.previewImage({
            current: this.data.uploadedImages[index],
            urls: this.data.uploadedImages
        });
    },

    async startGenerate() {
        const { mode, prompt, uploadedImages, duration, size, aspectRatio, resolution, style, userQuota, totalCost } = this.data;

        const userId = wx.getStorageSync('userId');
        if (!userId) {
            wx.showModal({
                title: '请先登录',
                content: '需要登录后才能进行创作，是否前往登录？',
                confirmText: '去登录',
                success: (res) => {
                    if (res.confirm) {
                        wx.switchTab({ url: '/pages/my/my' });
                    }
                }
            });
            return;
        }

        if (userQuota < totalCost) {
            wx.showModal({
                title: '算力不足',
                content: `本次创作需要 ${totalCost} 算力，您当前仅有 ${userQuota} 算力。`,
                showCancel: false,
                confirmText: '去获取'
            });
            return;
        }

        if (!prompt) {
            wx.showToast({ title: '请输入描述', icon: 'none' });
            return;
        }

        this.setData({
            generating: true,
            statusText: '正在提交任务...',
            resultUrl: ''
        });

        const stylePromptMap: Record<string, string> = {
            'default': '',
            'cinematic': 'cinematic style, movie-like, dramatic lighting',
            'anime': 'anime style, Japanese animation, vibrant colors',
            'realistic': 'photorealistic, ultra realistic, 8k, high detail',
            'scifi': 'science fiction style, futuristic, high-tech',
            'vintage': 'vintage style, retro, old film effect, nostalgic',
            'dreamy': 'dreamy style, soft focus, ethereal, fantasy',
            'cyberpunk': 'cyberpunk style, neon lights, dark city, futuristic',
            'watercolor': 'watercolor painting style, soft colors, artistic',
            '3d': '3D rendered, CGI, digital art, Unreal Engine',
            'oil': 'oil painting style, classical art, brush strokes',
            'photo': 'professional photography, DSLR, high resolution',
            'ink': 'Chinese ink painting style, traditional, minimalist',
            'sketch': 'pencil sketch style, hand-drawn, black and white',
            'pixel': 'pixel art style, 8-bit, retro game',
            'minimal': 'minimalist style, clean, simple, modern',
            'illustration': 'digital illustration, vector art, colorful'
        };

        const styleDesc = stylePromptMap[style] || '';
        const enhancedPrompt = styleDesc ? `${prompt}, ${styleDesc}` : prompt;

        try {
            let apiUrl = '';
            const userId = wx.getStorageSync('userId');
            let requestData: any = { prompt: enhancedPrompt, userId };

            let finalImageUrls = '';
            if (this.data.uploadedImages.length > 0) {
                wx.showLoading({ title: '上传图片中...', mask: true });
                const uploadPromises = this.data.uploadedImages.map(path => this.uploadImage(path));
                const urls = await Promise.all(uploadPromises);
                finalImageUrls = urls.join(',');
                wx.hideLoading();
            }

            if (mode === 'video') {
                requestData.duration = duration;
                requestData.aspectRatio = aspectRatio;
                requestData.resolution = resolution;
                if (finalImageUrls) {
                    apiUrl = '/api/ai/image-to-video';
                    requestData.imageUrl = finalImageUrls;
                } else {
                    apiUrl = '/api/ai/text-to-video';
                }
            } else if (mode === 'image') {
                requestData.size = size;
                if (finalImageUrls) {
                    apiUrl = '/api/ai/image-to-image';
                    requestData.imageUrl = finalImageUrls;
                } else {
                    apiUrl = '/api/ai/text-to-image';
                }
            } else if (mode === 'chat') {
                apiUrl = '/api/ai/copywriting/sync';
                requestData.copyType = this.data.copyType;
                requestData.style = style;
            }

            request({
                url: apiUrl,
                method: 'POST',
                data: requestData
            }).then(res => {
                this.setData({ generating: false });
                if (res.code === 200) {
                    const data = res.data;
                    if (this.data.isCopywritingMode) {
                        const content = data.content || '';
                        this.setData({ resultContent: content });
                        wx.showToast({ title: '文案生成成功', icon: 'success' });
                    } else if (this.data.isVideoMode) {
                        wx.showModal({
                            title: '任务已提交',
                            content: '视频正在努力生成中，预计需要1-3分钟。您可以留在本页等待，或前往"历史创作"查看进度。',
                            confirmText: '去历史',
                            cancelText: '留在本页',
                            success: (modalRes) => {
                                if (modalRes.confirm) {
                                    wx.navigateTo({ url: '/pages/creation-history/creation-history' });
                                } else {
                                    this.setData({ generating: true, taskId: data.taskId, statusText: '正在生成视频...' });
                                    this.pollVideoStatus(data.taskId);
                                }
                            }
                        });
                    } else {
                        wx.showModal({
                            title: '任务已提交',
                            content: '图片生成请求已发送，预计10-30秒完成。您可以前往"历史创作"查看并预览结果。',
                            confirmText: '去历史',
                            cancelText: '留在此页',
                            success: (modalRes) => {
                                if (modalRes.confirm) {
                                    wx.navigateTo({ url: '/pages/creation-history/creation-history' });
                                }
                            }
                        });
                    }
                } else if (res.code === 500 && res.msg && res.msg.includes('算力不足')) {
                    wx.showModal({
                        title: '算力不足',
                        content: '您的算力余额不足，无法完成本次创作。',
                        showCancel: false,
                        confirmText: '我知道了'
                    });
                } else {
                    wx.showToast({ title: res.msg || '任务提交失败', icon: 'none' });
                }
            }).catch(() => {
                this.setData({ generating: false });
                wx.showToast({ title: '网络连接或者认证失败', icon: 'none' });
            });
        } catch (error) {
            this.setData({ generating: false });
            wx.showToast({ title: '操作失败', icon: 'none' });
        }
    },

    uploadImage(filePath: string): Promise<string> {
        return new Promise((resolve, reject) => {
            const token = wx.getStorageSync('token');
            wx.uploadFile({
                url: `${API_BASE}/api/upload/image`,
                filePath: filePath,
                name: 'file',
                header: {
                    'Authorization': `Bearer ${token}`
                },
                success: (res) => {
                    try {
                        const data = JSON.parse(res.data);
                        if (data.code === 200) resolve(data.data.url);
                        else reject(new Error('上传失败'));
                    } catch { reject(new Error('上传失败')); }
                },
                fail: () => reject(new Error('上传失败'))
            });
        });
    },

    pollVideoStatus(taskId: string) {
        let attempts = 0;
        const maxAttempts = 180;
        const poll = () => {
            if (attempts >= maxAttempts) {
                this.setData({ generating: false });
                wx.showModal({
                    title: '生成中',
                    content: '视频生成时间较长，您可以先退出页面，稍后在"历史创作"中查看结果',
                    showCancel: false,
                    confirmText: '我知道了'
                });
                return;
            }
            attempts++;
            const minutes = Math.floor((attempts * 5) / 60);
            const seconds = (attempts * 5) % 60;
            this.setData({ statusText: `正在生成视频 (${minutes}分${seconds}秒)...` });

            request({
                url: `/api/ai/video-status/${taskId}`,
                method: 'GET'
            }).then(res => {
                if (res.code === 200) {
                    const data = res.data;
                    if (data.status === 'completed') {
                        this.setData({ generating: false, resultUrl: data.videoUrl });
                        wx.showToast({ title: '完成', icon: 'success' });
                    } else if (data.status === 'failed') {
                        this.setData({ generating: false });
                        wx.showToast({ title: '生成失败，请重试', icon: 'none' });
                    } else {
                        setTimeout(poll, 5000);
                    }
                } else {
                    setTimeout(poll, 5000);
                }
            }).catch(() => setTimeout(poll, 5000));
        };
        setTimeout(poll, 5000);
    },

    previewImage() {
        if (this.data.resultUrl) wx.previewImage({ urls: [this.data.resultUrl] });
    },

    saveResult() {
        if (!this.data.resultUrl) return;
        wx.getSetting({
            success: (res) => {
                const auth = res.authSetting['scope.writePhotosAlbum'];
                if (auth === false) {
                    wx.showModal({
                        title: '需要相册权限',
                        content: '请在设置中开启相册权限，以便将作品保存到手机相册。',
                        confirmText: '去设置',
                        success: (modalRes) => { if (modalRes.confirm) wx.openSetting(); }
                    });
                } else {
                    this.doSaveResultAction();
                }
            }
        });
    },

    doSaveResultAction() {
        const { resultUrl, isVideoMode } = this.data;
        if (!resultUrl) return;
        wx.showLoading({ title: '下载中...', mask: true });
        const ext = isVideoMode ? 'mp4' : 'jpg';
        const filePath = `${wx.env.USER_DATA_PATH}/file_${Date.now()}.${ext}`;

        wx.downloadFile({
            url: resultUrl,
            filePath: filePath,
            success: (res) => {
                if (res.statusCode === 200 && res.filePath) {
                    const save = isVideoMode ? wx.saveVideoToPhotosAlbum : wx.saveImageToPhotosAlbum;
                    save({
                        filePath: res.filePath,
                        success: () => {
                            wx.hideLoading();
                            wx.showToast({ title: '已保存到相册', icon: 'success' });
                        },
                        fail: () => {
                            wx.hideLoading();
                            wx.showToast({ title: '保存失败', icon: 'none' });
                        }
                    });
                } else {
                    wx.hideLoading();
                    wx.showToast({ title: '下载失败', icon: 'none' });
                }
            },
            fail: () => {
                wx.hideLoading();
                wx.showToast({ title: '网络下载失败', icon: 'none' });
            }
        });
    },

    copyCopywriting() {
        const content = this.data.resultContent;
        if (!content) return;
        wx.setClipboardData({
            data: content,
            success: () => wx.showToast({ title: '已复制', icon: 'success' })
        });
    },

    onShareAppMessage() {
        return { title: '看我用AI做的作品', path: '/pages/creation/creation' };
    }
});
