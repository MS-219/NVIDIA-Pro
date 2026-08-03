import { startEid } from './main.js';

Page({
  data: {
    url: null,
  },
  onLoad(options) {
    startEid({
      data: {
        token: options.token,
        needJumpPage: false,
      },
      verifyDoneCallback: ({ token, verifyDone }) => {
        if (verifyDone) {
          this.setData({
            url: decodeURIComponent(options.redirect) + '&token=' + token,
          });
        } else {
          wx.navigateBack();
        }
      },
    });
  },
});
