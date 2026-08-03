import {
  initEid
} from "./main.js";

Page({
  data: {
    url: null
  },
  onLoad({
    url
  }) {
    if (!url) {
      wx.navigateBack();
      return;
    }
    if (!wx.eidBaseUrl) {
      initEid();
    }
    this.setData({
      url:decodeURIComponent(url)
    })
  }
});