import { API_BASE } from '../../config';
export { };

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        contactWechat: 'orin-support',
        contactWorkTime: '9:00-18:00',
        faqList: [
            {
                id: 1,
                question: '如何绑定设备？',
                answer: '进入"设备"页面，点击右上角"+"按钮，扫描设备上的二维码即可绑定。',
                expanded: false
            },
            {
                id: 2,
                question: '收益是如何计算的？',
                answer: '收益按自然日结算。设备在线并满足结算条件后，系统在次日结算上一自然日收益，金额根据设备算力和当天收益率计算。',
                expanded: false
            },
            {
                id: 3,
                question: '如何提现？',
                answer: '进入“我的”页面点击“申请提现”，页面会显示当前允许提现日。请在开放日完成实名签约、填写本人银行卡并提交，审核及打款将在1-3个工作日内完成。',
                expanded: false
            },
            {
                id: 4,
                question: '最低提现金额是多少？',
                answer: '平台不设置累计提现门槛，可提现余额满0.01元即可申请。线上打款按页面公示比例扣除手续费，线下打款不扣手续费。',
                expanded: false
            },
            {
                id: 5,
                question: '设备离线会影响收益吗？',
                answer: '是的，设备离线期间不产生收益。请确保设备保持在线状态以获得持续收益。',
                expanded: false
            },
        ]
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });

        this.fetchSystemSettings();
    },

    fetchSystemSettings() {
        wx.request({
            url: `${API_BASE}/api/settings/system-config`,
            method: 'GET',
            success: (res: any) => {
                if (res.data.code === 200 && res.data.data) {
                    const sys = res.data.data;
                    this.setData({
                        contactWechat: sys.contactWechat || 'orin-support',
                        contactWorkTime: sys.contactWorkTime || '9:00-18:00'
                    });
                }
            }
        });
    },

    goBack() {
        wx.navigateBack();
    },

    toggleFaq(e: any) {
        const id = e.currentTarget.dataset.id;
        const faqList = this.data.faqList.map(item => ({
            ...item,
            expanded: item.id === id ? !item.expanded : false
        }));
        this.setData({ faqList });
    },

    contactService() {
        const { contactWechat, contactWorkTime } = this.data;
        wx.showModal({
            title: '联系客服',
            content: `客服微信：${contactWechat}\n工作时间：${contactWorkTime}`,
            showCancel: false,
            confirmText: '我知道了'
        });
    }
});
