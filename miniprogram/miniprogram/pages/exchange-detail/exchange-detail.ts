import { request } from '../../utils/request';
export {};

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        productId: 0,
        product: null as any,
        allPrices: [] as any[],
        userLevel: 0,
        hashrateRate: 200,
        availableHashrate: 0,
        addresses: [] as any[],
        selectedAddress: null as any,
        showAddressPicker: false,
        quantity: 1,
        levelNames: ['普通', '会员', '社区', '县级', '市级', '联创']
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height,
            productId: options.id ? parseInt(options.id) : 0
        });
        this.fetchDetail();
        this.fetchAddresses();
    },

    onShow() {
        this.fetchAddresses();
    },

    fetchDetail() {
        const userId = wx.getStorageSync('userId');
        request({
            url: `/api/exchange/product/${this.data.productId}?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    product: data.product,
                    allPrices: data.allPrices || [],
                    userLevel: data.userLevel || 0,
                    hashrateRate: data.hashrateRate || 200,
                    availableHashrate: data.availableHashrate || 0
                });
            }
        });
    },

    fetchAddresses() {
        const userId = wx.getStorageSync('userId');
        request({
            url: `/api/address/list?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                const addresses = res.data || [];
                const defaultAddr = addresses.find((a: any) => a.isDefault === 1) || addresses[0] || null;
                this.setData({
                    addresses,
                    selectedAddress: this.data.selectedAddress || defaultAddr
                });
            }
        });
    },

    // 数量控制
    decreaseQty() {
        if (this.data.quantity > 1) {
            this.setData({ quantity: this.data.quantity - 1 });
        }
    },

    increaseQty() {
        const stock = this.data.product?.stock;
        if (stock && this.data.quantity >= stock) {
            wx.showToast({ title: '库存不足', icon: 'none' });
            return;
        }
        this.setData({ quantity: this.data.quantity + 1 });
    },

    // 地址选择
    toggleAddressPicker() {
        this.setData({ showAddressPicker: !this.data.showAddressPicker });
    },

    selectAddress(e: any) {
        const index = e.currentTarget.dataset.index;
        this.setData({
            selectedAddress: this.data.addresses[index],
            showAddressPicker: false
        });
    },

    goToAddressManage() {
        wx.navigateTo({ url: '/pages/address-manage/address-manage' });
    },

    // 确认兑换
    confirmExchange() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            wx.showModal({
                title: '请先登录',
                content: '需要登录后才能进行兑换，是否前往登录？',
                confirmText: '去登录',
                success: (res) => {
                    if (res.confirm) {
                        wx.switchTab({ url: '/pages/my/my' });
                    }
                }
            });
            return;
        }

        if (this.data.userLevel < 1) {
            wx.showToast({ title: '请先升级等级后再兑换', icon: 'none' });
            return;
        }

        if (!this.data.selectedAddress) {
            wx.showToast({ title: '请选择收货地址', icon: 'none' });
            return;
        }

        const product = this.data.product;
        const totalHashrate = product.userHashratePrice * this.data.quantity;

        wx.showModal({
            title: '确认兑换',
            content: `将消耗 ${totalHashrate} 算力值兑换 ${product.name} ×${this.data.quantity}`,
            confirmText: '确认兑换',
            confirmColor: '#16a34a',
            success: (res) => {
                if (res.confirm) {
                    this.doExchange();
                }
            }
        });
    },

    doExchange() {
        const userId = wx.getStorageSync('userId');
        wx.showLoading({ title: '兑换中...', mask: true });

        request({
            url: '/api/exchange/order',
            method: 'POST',
            data: {
                userId,
                productId: this.data.productId,
                addressId: this.data.selectedAddress.id,
                quantity: this.data.quantity
            }
        }).then((res: any) => {
            wx.hideLoading();
            if (res.code === 200) {
                wx.showToast({ title: '兑换成功！', icon: 'success' });
                setTimeout(() => {
                    wx.navigateTo({ url: `/pages/exchange-order-detail/exchange-order-detail?orderNo=${res.data.orderNo}` });
                }, 1500);
            } else {
                wx.showModal({ title: '兑换失败', content: res.msg || '请稍后重试', showCancel: false });
            }
        }).catch(() => {
            wx.hideLoading();
            wx.showToast({ title: '网络错误', icon: 'none' });
        });
    },

    onGoBack() {
        wx.navigateBack();
    }
});
