const reminderScheduler = require("./services/reminderScheduler");

App({
  globalData: {
    pendingReminder: null
  },

  onLaunch() {
    reminderScheduler.ensureSeedData();
    reminderScheduler.checkDueReminders();
  },

  onShow() {
    reminderScheduler.checkDueReminders();
  }
});
