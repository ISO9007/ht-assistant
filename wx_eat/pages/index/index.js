const storage = require("../../services/storage");
const adapters = require("../../services/reminderAdapters");
const scheduler = require("../../services/reminderScheduler");
const { displayDate } = require("../../utils/date");

let pageTimer = null;
let activeAudio = null;

function statusText(status) {
  const map = {
    upcoming: "未到",
    due: "待确认",
    missed: "已错过",
    taken: "已吃"
  };
  return map[status] || "未到";
}

Page({
  data: {
    todayText: "",
    todaySummary: "",
    nextLabel: "下一顿要吃",
    nextOccurrence: null,
    medicineGroups: [],
    reminderVisible: false,
    activeReminder: null
  },

  onLoad() {
    scheduler.ensureSeedData();
  },

  onShow() {
    this.refresh();
    this.startTimer();
  },

  onHide() {
    this.stopTimer();
    this.stopAudio();
  },

  onUnload() {
    this.stopTimer();
    this.stopAudio();
  },

  startTimer() {
    this.stopTimer();
    pageTimer = setInterval(() => {
      this.refresh();
    }, 30000);
  },

  stopTimer() {
    if (pageTimer) {
      clearInterval(pageTimer);
      pageTimer = null;
    }
  },

  refresh() {
    const due = scheduler.checkDueReminders();
    const occurrences = scheduler.getTodayOccurrences().map((item) => ({
      ...item,
      statusText: statusText(item.status)
    }));
    const medicines = storage.getMedicines();
    const groups = medicines
      .map((medicine) => ({
        medicineId: medicine.id,
        name: medicine.name,
        dosage: medicine.dosage,
        meal: medicine.meal,
        occurrences: occurrences.filter((item) => item.medicineId === medicine.id)
      }))
      .filter((group) => group.occurrences.length);
    const takenCount = occurrences.filter((item) => item.status === "taken").length;
    const nextOccurrence = scheduler.getNextOccurrence();
    const nextLabel = nextOccurrence && (nextOccurrence.status === "due" || nextOccurrence.status === "missed")
      ? "现在需要确认"
      : "下一顿要吃";

    this.setData({
      todayText: displayDate(),
      todaySummary: `${takenCount}/${occurrences.length} 已吃`,
      nextLabel,
      nextOccurrence: nextOccurrence ? { ...nextOccurrence, statusText: statusText(nextOccurrence.status) } : null,
      medicineGroups: groups
    });

    const settings = storage.getSettings();
    if (due && settings.inAppAlert && !this.data.reminderVisible) {
      this.showReminder(due);
    }
  },

  showReminder(reminder) {
    const settings = storage.getSettings();
    if (settings.soundVibrate) {
      adapters.vibrate();
      activeAudio = adapters.playReminderSound();
    }
    this.setData({
      activeReminder: reminder,
      reminderVisible: true
    });
  },

  stopAudio() {
    if (activeAudio) {
      if (activeAudio.stop) {
        activeAudio.stop();
      }
      if (activeAudio.destroy) {
        activeAudio.destroy();
      }
      activeAudio = null;
    }
  },

  onReminderTaken(event) {
    scheduler.markTaken(event.detail.reminder.id);
    this.stopAudio();
    this.setData({ reminderVisible: false, activeReminder: null });
    wx.showToast({ title: "已记录", icon: "success" });
    this.refresh();
  },

  onReminderLater(event) {
    scheduler.snooze(event.detail.reminder.id);
    this.stopAudio();
    this.setData({ reminderVisible: false, activeReminder: null });
    wx.showToast({ title: "10分钟后再提醒", icon: "none" });
    this.refresh();
  },

  markNextTaken() {
    if (!this.data.nextOccurrence) return;
    scheduler.markTaken(this.data.nextOccurrence.id);
    wx.showToast({ title: "已记录", icon: "success" });
    this.refresh();
  },

  markTakenById(event) {
    scheduler.markTaken(event.currentTarget.dataset.id);
    wx.showToast({ title: "已记录", icon: "success" });
    this.refresh();
  },

  addMedicine() {
    wx.navigateTo({ url: "/pages/medicine-form/medicine-form" });
  },

  editMedicine(event) {
    wx.navigateTo({ url: `/pages/medicine-form/medicine-form?id=${event.currentTarget.dataset.id}` });
  },

  goSettings() {
    wx.navigateTo({ url: "/pages/reminder-settings/reminder-settings" });
  }
});
