import { API_BASE } from '../../config';

export { }; // 使文件成为 ES 模块

interface HistoryItem {
    taskId: string;
    taskType: string;
    prompt: string;
    status: string;
    resultUrl: string;
    createTime: string;
}

interface PageData {
    navBarTop: number;
    navBarHeight: number;
    quota: number;
    balance: number; // Added for synchronization
    hashrateRate: number;
    consumedHashrate: number;
    imagesCount: number;
    i2vCount: number;
    vGenCount: number;
    chatCount: number;
    isLoggedIn: boolean;
    creationTools: Array<{
        id: string;
        title: string;
        desc: string;
        icon: string;
        cost: number;
        bgImage?: string;
    }>;
    historyList: HistoryItem[];
}



Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        quota: 0,  // 用户聚芯 Orin值
        balance: 0, // 用户余额
        hashrateRate: 100, // 算力兑换比例
        consumedHashrate: 0, // 已消耗聚芯 Orin值
        imagesCount: 0,
        i2vCount: 0,
        vGenCount: 0,
        chatCount: 0,
        isLoggedIn: false,
        creationTools: [
            {
                id: 'video',
                title: '视频创作',
                desc: '描述或上传图片，一键生成大片',
                icon: '📹',
                cost: 200,  // 默认值，实际从后台获取
                bgImage: 'https://images.unsplash.com/photo-1626814026160-2237a95fc5a0?ixlib=rb-4.0.3&auto=format&fit=crop&w=1000&q=80'
            },
            {
                id: 'image',
                title: '图片生成',
                desc: '创意绘画，风格转换',
                icon: '🎨',
                cost: 220  // 默认值，实际从后台获取
            },
            {
                id: 'chat',
                title: 'AI文案',
                desc: '智能对话，创意灵感',
                icon: '💬',
                cost: 30  // 默认值，实际从后台获取
            }
        ],
        historyList: [] as HistoryItem[]
    } as PageData,

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
    },

    onShow() {
        if (typeof this.getTabBar === 'function' && this.getTabBar()) {
            this.getTabBar().setData({
                selected: 2
            })
        }

        // 检查登录状态
        const userId = wx.getStorageSync('userId');
        this.setData({ isLoggedIn: !!userId });

        // 获取最新的收费标准
        this.fetchAiCosts();

        // 如果已登录，获取聚芯 Orin值和统计
        if (userId) {
            this.fetchQuota();
            this.fetchStats();
        }
    },

    // 获取 AI 计费标准（使用公开接口，无需管理员权限）
    fetchAiCosts() {
        wx.request({
            url: `${API_BASE}/api/settings/ai-config`,
            method: 'GET',
            success: (res: any) => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    const pricing = data.aiPricing;
                    const tools = this.data.creationTools;

                    if (pricing) {
                        tools.forEach(tool => {
                            if (tool.id === 'video') tool.cost = pricing.videoGenCost || 200;
                            if (tool.id === 'image') tool.cost = pricing.imageGenCost || 10;
                            if (tool.id === 'chat') tool.cost = pricing.chatCost || 1;
                        });
                        this.setData({ creationTools: tools });
                    }

                    // 保存算力兑换比例
                    if (data.hashratePerYuan) {
                        const rate = parseInt(data.hashratePerYuan);
                        this.setData({ hashrateRate: rate });
                        // 如果已经获取了余额，重新计算一下配额
                        if (this.data.balance > 0) {
                            this.setData({
                                quota: Math.round(this.data.balance * rate)
                            });
                        }
                    }
                }
            }
        });
    },

    // 获取用户聚芯 Orin值 (基于余额动态计算)
    fetchQuota() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        wx.request({
            url: `${API_BASE}/api/statistics/earnings`,
            method: 'GET',
            data: { userId },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const balance = parseFloat(res.data.data.currentBalance) || 0;
                    const rate = this.data.hashrateRate || 100;
                    this.setData({
                        balance: balance,
                        quota: Math.round(balance * rate) // 动态计算
                    });
                }
            }
        });
    },

    // 模拟或获取创作统计
    fetchStats() {
        const userId = wx.getStorageSync('userId');
        if (!userId) return;

        wx.request({
            url: `${API_BASE}/api/ai/my-tasks`,
            method: 'GET',
            data: { userId, page: 1, size: 100 },
            success: (res: any) => {
                if (res.data.code === 200) {
                    const tasks = res.data.data.records || [];
                    let img = 0, i2v = 0, vgen = 0, chat = 0;
                    let totalCost = 0;
                    tasks.forEach((t: any) => {
                        const type = t.taskType;
                        if (type === 'text-to-image' || type === 'image-to-image') img++;
                        else if (type === 'image-to-video') i2v++;
                        else if (type === 'text-to-video') vgen++;
                        else if (type === 'chat') chat++;

                        if (t.costQuota) {
                            totalCost += t.costQuota;
                        }
                    });
                    this.setData({
                        imagesCount: img,
                        i2vCount: i2v,
                        vGenCount: vgen,
                        chatCount: chat,
                        consumedHashrate: totalCost,
                        historyList: tasks.slice(0, 10)
                    });
                }
            }
        });
    },

    onToolTap(e: any) {
        const id = e.currentTarget.dataset.id;
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            // wx.showToast({ title: '请先登录', icon: 'none' });
            // return;
        }
        wx.navigateTo({
            url: `/pages/ai-create/ai-create?mode=${id}`
        });
    },

    goToHistory() {
        wx.navigateTo({ url: '/pages/creation-history/creation-history' });
    }
})
