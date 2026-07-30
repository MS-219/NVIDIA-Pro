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
        loading: true,
        loaded: false,
        errorMessage: '',
        addressLoading: true,
        addressError: '',
        submitting: false,
        levelNames: ['普通', '会员', '社区', '县级', '市级', '联创']
    },

    onLoad(options: any) {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height,
            productId: options.id ? parseInt(options.id) : 0
        }, () => {
            this.fetchDetail();
        });
    },

    onShow() {
        if (this.data.productId) {
            this.fetchAddresses();
        }
    },

    fetchDetail() {
        if (!this.data.productId) {
            this.setData({
                loading: false,
                loaded: true,
                errorMessage: '商品参数缺失'
            });
            return Promise.resolve();
        }
        const userId = wx.getStorageSync('userId');
        this.setData({ loading: true, errorMessage: '' });
        return request({
            url: `/api/exchange/product/${this.data.productId}?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                const data = res.data;
                this.setData({
                    product: data.product || null,
                    allPrices: data.allPrices || [],
                    userLevel: data.userLevel || 0,
                    levelNames: data.levelNames || this.data.levelNames,
                    hashrateRate: data.hashrateRate || 200,
                    availableHashrate: data.availableHashrate || 0,
                    loaded: true
                });
            } else {
                this.setData({
                    loaded: true,
                    errorMessage: res.msg || '商品详情加载失败'
                });
            }
        }).catch(() => {
            this.setData({
                loaded: true,
                errorMessage: '网络连接异常，请稍后重试'
            });
        }).finally(() => {
            this.setData({ loading: false });
        });
    },

    retryDetail() {
        this.fetchDetail();
    },

    fetchAddresses() {
        const userId = wx.getStorageSync('userId');
        if (!userId) {
            this.setData({
                addresses: [],
                selectedAddress: null,
                addressLoading: false,
                addressError: '登录后可读取收货地址'
            });
            return Promise.resolve();
        }
        this.setData({ addressLoading: true, addressError: '' });
        return request({
            url: `/api/address/list?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                const addresses = res.data || [];
                const defaultAddr = addresses.find((a: any) => a.isDefault === 1) || addresses[0] || null;
                const selectedAddress = addresses.find((a: any) => a.id === this.data.selectedAddress?.id) || defaultAddr;
                this.setData({
                    addresses,
                    selectedAddress
                });
            } else {
                this.setData({ addressError: res.msg || '收货地址加载失败' });
            }
        }).catch(() => {
            this.setData({ addressError: '收货地址加载失败' });
        }).finally(() => {
            this.setData({ addressLoading: false });
        });
    },

    retryAddresses() {
        this.fetchAddresses();
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
            confirmColor: '#76B900',
            success: (res) => {
                if (res.confirm) {
                    this.doExchange();
                }
            }
        });
    },

    doExchange() {
        if (this.data.submitting) return;
        const userId = wx.getStorageSync('userId');
        this.setData({ submitting: true });
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
            if (res.code === 200) {
                wx.showToast({ title: '兑换成功', icon: 'success' });
                setTimeout(() => {
                    wx.navigateTo({ url: `/pages/exchange-order-detail/exchange-order-detail?orderNo=${res.data.orderNo}` });
                }, 1500);
            } else {
                wx.showModal({ title: '兑换失败', content: res.msg || '请稍后重试', showCancel: false });
            }
        }).catch(() => {
            wx.showToast({ title: '网络错误', icon: 'none' });
        }).finally(() => {
            wx.hideLoading();
            this.setData({ submitting: false });
        });
    },

    onGoBack() {
        wx.navigateBack();
    }
});
