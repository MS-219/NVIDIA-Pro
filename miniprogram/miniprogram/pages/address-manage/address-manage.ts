import { request } from '../../utils/request';
export {};

Page({
    data: {
        navBarTop: 0,
        navBarHeight: 44,
        addresses: [] as any[],
        showForm: false,
        editingAddress: null as any,
        form: {
            receiverName: '',
            phone: '',
            province: '',
            city: '',
            district: '',
            detailAddress: '',
            isDefault: 0
        },
        regionArray: [] as string[]
    },

    onLoad() {
        const menuButtonInfo = wx.getMenuButtonBoundingClientRect();
        this.setData({
            navBarTop: menuButtonInfo.top,
            navBarHeight: menuButtonInfo.height
        });
        this.fetchAddresses();
    },

    onShow() {
        this.fetchAddresses();
    },

    fetchAddresses() {
        const userId = wx.getStorageSync('userId');
        request({
            url: `/api/address/list?userId=${userId}`,
            method: 'GET'
        }).then((res: any) => {
            if (res.code === 200) {
                this.setData({ addresses: res.data || [] });
            }
        });
    },

    showAddForm() {
        this.setData({
            showForm: true,
            editingAddress: null,
            form: { receiverName: '', phone: '', province: '', city: '', district: '', detailAddress: '', isDefault: 0 },
            regionArray: []
        });
    },

    showEditForm(e: any) {
        const index = e.currentTarget.dataset.index;
        const addr = this.data.addresses[index];
        this.setData({
            showForm: true,
            editingAddress: addr,
            form: {
                receiverName: addr.receiverName || '',
                phone: addr.phone || '',
                province: addr.province || '',
                city: addr.city || '',
                district: addr.district || '',
                detailAddress: addr.detailAddress || '',
                isDefault: addr.isDefault || 0
            },
            regionArray: [addr.province || '', addr.city || '', addr.district || '']
        });
    },

    hideForm() {
        this.setData({ showForm: false });
    },

    onInputChange(e: any) {
        const field = e.currentTarget.dataset.field;
        this.setData({ [`form.${field}`]: e.detail.value });
    },

    onRegionChange(e: any) {
        const region = e.detail.value;
        this.setData({
            regionArray: region,
            'form.province': region[0],
            'form.city': region[1],
            'form.district': region[2]
        });
    },

    onDefaultChange(e: any) {
        this.setData({ 'form.isDefault': e.detail.value ? 1 : 0 });
    },

    saveAddress() {
        const { form, editingAddress } = this.data;
        if (!form.receiverName.trim()) {
            wx.showToast({ title: '请输入收货人', icon: 'none' }); return;
        }
        if (!form.phone.trim()) {
            wx.showToast({ title: '请输入电话', icon: 'none' }); return;
        }
        if (!form.province) {
            wx.showToast({ title: '请选择地区', icon: 'none' }); return;
        }
        if (!form.detailAddress.trim()) {
            wx.showToast({ title: '请输入详细地址', icon: 'none' }); return;
        }

        const userId = wx.getStorageSync('userId');
        const data: any = { userId, ...form };
        if (editingAddress) {
            data.id = editingAddress.id;
        }

        wx.showLoading({ title: '保存中...' });
        request({
            url: '/api/address/save',
            method: 'POST',
            data
        }).then((res: any) => {
            wx.hideLoading();
            if (res.code === 200) {
                wx.showToast({ title: '保存成功', icon: 'success' });
                this.setData({ showForm: false });
                this.fetchAddresses();
            } else {
                wx.showToast({ title: res.msg || '保存失败', icon: 'none' });
            }
        });
    },

    deleteAddress(e: any) {
        const id = e.currentTarget.dataset.id;
        wx.showModal({
            title: '删除地址',
            content: '确定删除该收货地址？',
            confirmColor: '#ef4444',
            success: (res) => {
                if (res.confirm) {
                    const userId = wx.getStorageSync('userId');
                    request({
                        url: `/api/address/${id}?userId=${userId}`,
                        method: 'DELETE'
                    }).then((res: any) => {
                        if (res.code === 200) {
                            wx.showToast({ title: '已删除', icon: 'success' });
                            this.fetchAddresses();
                        }
                    });
                }
            }
        });
    },

    setDefault(e: any) {
        const id = e.currentTarget.dataset.id;
        const userId = wx.getStorageSync('userId');
        request({
            url: '/api/address/setDefault',
            method: 'POST',
            data: { userId, addressId: id }
        }).then((res: any) => {
            if (res.code === 200) {
                wx.showToast({ title: '设置成功', icon: 'success' });
                this.fetchAddresses();
            }
        });
    },

    onGoBack() {
        wx.navigateBack();
    }
});
