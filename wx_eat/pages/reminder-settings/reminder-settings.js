const storage = require("../../services/storage");
const adapters = require("../../services/reminderAdapters");

let testAudio = null;

Page({
  data: {
    settings: storage.DEFAULT_SETTINGS
  },

  onShow() {
    this.setData({ settings: storage.getSettings() });
  },

  onHide() {
    this.stopTestAudio();
  },

  onUnload() {
    this.stopTestAudio();
  },

  toggleSetting(event) {
    const key = event.currentTarget.dataset.key;
    const settings = Object.assign({}, this.data.settings, {
      [key]: event.detail.value
    });
    storage.saveSettings(settings);
    this.setData({ settings });
  },

  requestSubscribe() {
    adapters.requestSubscribeMessage();
  },

  async testCalendar() {
    wx.showLoading({ title: "正在测试" });
    const result = await adapters.testPhoneCalendar();
    wx.hideLoading();

    if (result.ok) {
      wx.showModal({
        title: "日历测试成功",
        content: `已尝试写入 5 分钟后的测试日程。使用接口：${result.api}`,
        showCancel: false,
        confirmText: "知道了"
      });
      return;
    }

    wx.showModal({
      title: "日历测试失败",
      content: `当前手机没有接通微信日历写入能力。原因：${result.errMsg || result.reason}`,
      showCancel: false,
      confirmText: "知道了"
    });
  },

  testAlert() {
    adapters.vibrate();
    this.stopTestAudio();
    testAudio = adapters.playReminderSound();
    wx.showModal({
      title: "测试提醒",
      content: "这是提醒弹窗。真实提醒时，需要点击“已吃这一顿”才会停止重复提醒。",
      confirmText: "知道了",
      complete: () => this.stopTestAudio()
    });
  },

  stopTestAudio() {
    if (testAudio) {
      if (testAudio.stop) {
        testAudio.stop();
      }
      if (testAudio.destroy) {
        testAudio.destroy();
      }
      testAudio = null;
    }
  }
});
