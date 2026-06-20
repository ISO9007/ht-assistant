function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      timeout: 12000,
      ...options,
      success: resolve,
      fail: reject
    });
  });
}

module.exports = {
  request
};
