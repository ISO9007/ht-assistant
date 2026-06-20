const storage = require("../../services/storage");
const adapters = require("../../services/reminderAdapters");
const scheduler = require("../../services/reminderScheduler");
const { displayDayKey, displayWeekday, todayKey } = require("../../utils/date");

const mealOptions = ["饭后", "饭前", "随餐", "睡前", "不限"];
const repeatOptions = [
  { value: "once", label: "仅一次" },
  { value: "daily", label: "每天" },
  { value: "weekly", label: "每周" }
];
const weekdayOptions = [1, 2, 3, 4, 5, 6, 0].map((value) => ({
  value,
  label: displayWeekday(value)
}));
const weekdayRank = { 1: 1, 2: 2, 3: 3, 4: 4, 5: 5, 6: 6, 0: 7 };

function dateKeyAfterDays(date, days) {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
  const month = String(next.getMonth() + 1).padStart(2, "0");
  const day = String(next.getDate()).padStart(2, "0");
  return `${next.getFullYear()}-${month}-${day}`;
}

function nextDateForWeekday(weekday) {
  const now = new Date();
  const today = now.getDay();
  const diff = (weekday - today + 7) % 7;
  return dateKeyAfterDays(now, diff);
}

function expandCalendarReminderTimes(times) {
  return scheduler.normalizeReminderTimes(times).flatMap((item) => {
    if (item.repeat !== "weekly") return [item];

    return item.weekdays.map((weekday) => ({
      ...item,
      date: nextDateForWeekday(weekday),
      weekdays: [weekday]
    }));
  });
}

function blankForm() {
  return {
    id: "",
    name: "",
    dosage: "",
    meal: "饭后",
    note: "",
    enabled: true,
    times: []
  };
}

Page({
  data: {
    isEdit: false,
    mealOptions,
    repeatOptions,
    weekdayOptions,
    selectedDate: todayKey(),
    selectedTime: "08:00",
    selectedDateText: displayDayKey(todayKey()),
    selectedRepeat: "once",
    selectedWeekdays: [1],
    weekdayItems: weekdayOptions.map((item) => ({
      ...item,
      selected: item.value === 1
    })),
    form: blankForm(),
    timeItems: []
  },

  onLoad(options) {
    if (options.id) {
      const medicine = storage.getMedicines().find((item) => item.id === options.id);
      if (medicine) {
        this.setData({
          isEdit: true,
          form: medicine
        });
      }
    }
    this.refreshTimeLabels();
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  chooseMeal(event) {
    this.setData({
      "form.meal": event.currentTarget.dataset.meal
    });
  },

  onEnabledChange(event) {
    this.setData({ "form.enabled": event.detail.value });
  },

  onDateChange(event) {
    this.setData({
      selectedDate: event.detail.value,
      selectedDateText: displayDayKey(event.detail.value)
    });
  },

  onTimeChange(event) {
    this.setData({ selectedTime: event.detail.value });
  },

  chooseRepeat(event) {
    this.setData({
      selectedRepeat: event.currentTarget.dataset.repeat
    });
  },

  toggleWeekday(event) {
    const weekday = Number(event.currentTarget.dataset.weekday);
    const selected = this.data.selectedWeekdays.includes(weekday)
      ? this.data.selectedWeekdays.filter((item) => item !== weekday)
      : [...this.data.selectedWeekdays, weekday].sort((a, b) => weekdayRank[a] - weekdayRank[b]);
    this.setData({
      selectedWeekdays: selected
    });
    this.refreshWeekdayItems();
  },

  refreshWeekdayItems() {
    this.setData({
      weekdayItems: weekdayOptions.map((item) => ({
        ...item,
        selected: this.data.selectedWeekdays.includes(item.value)
      }))
    });
  },

  addTime() {
    if (this.data.selectedRepeat === "weekly" && !this.data.selectedWeekdays.length) {
      wx.showToast({ title: "请选择星期几", icon: "none" });
      return;
    }

    const selected = {
      repeat: this.data.selectedRepeat,
      date: this.data.selectedDate,
      time: this.data.selectedTime,
      weekdays: this.data.selectedRepeat === "weekly" ? this.data.selectedWeekdays : []
    };
    const times = scheduler.normalizeReminderTimes([...this.data.form.times, selected]);
    this.setData({
      "form.times": times
    });
    this.refreshTimeLabels();
  },

  removeTime(event) {
    const key = event.currentTarget.dataset.key;
    const times = scheduler.normalizeReminderTimes(this.data.form.times).filter((item) => scheduler.reminderKey(item) !== key);
    this.setData({ "form.times": times });
    this.refreshTimeLabels();
  },

  refreshTimeLabels() {
    const normalizedTimes = scheduler.normalizeReminderTimes(this.data.form.times);
    if (normalizedTimes !== this.data.form.times) {
      this.setData({ "form.times": normalizedTimes });
    }

    this.setData({
      timeItems: normalizedTimes.map((item) => ({
        key: scheduler.reminderKey(item),
        date: item.date,
        time: item.time,
        repeat: item.repeat,
        repeatLabel: scheduler.repeatLabel(item),
        label: scheduler.reminderDisplayLabel(item)
      }))
    });
  },

  async saveMedicine() {
    const form = scheduler.normalizeMedicine(this.data.form);
    if (!form.name) {
      wx.showToast({ title: "请填写药品名称", icon: "none" });
      return;
    }
    if (!form.times.length) {
      wx.showToast({ title: "请添加提醒时间", icon: "none" });
      return;
    }

    const medicines = storage.getMedicines();
    const index = medicines.findIndex((item) => item.id === form.id);
    if (index >= 0) {
      medicines[index] = form;
    } else {
      medicines.push(form);
    }
    storage.saveMedicines(medicines);

    const settings = storage.getSettings();
    if (settings.phoneCalendar) {
      const calendarTimes = expandCalendarReminderTimes(form.times);
      const calendarResults = await Promise.all(calendarTimes.map((time) => adapters.addPhoneCalendar(form, time, { silent: true })));
      const noSupportingApps = calendarResults.some((result) => result && result.reason === "noSupportingApps");
      const calendarAuthDenied = calendarResults.some((result) => result && result.reason === "calendarAuthDenied");
      if (noSupportingApps) {
        storage.saveSettings(Object.assign({}, settings, { phoneCalendar: false }));
        wx.showModal({
          title: "日历提醒不可用",
          content: "当前手机的日历应用无法被微信小程序写入。药品已保存，后续会使用小程序弹窗、铃声震动等方式提醒。",
          showCancel: false,
          confirmText: "知道了"
        });
      } else if (calendarAuthDenied) {
        wx.showModal({
          title: "需要日历授权",
          content: "药品已保存，但还没有获得日历权限。请在小程序设置里允许日历权限后，再重新保存一次药品。",
          showCancel: false,
          confirmText: "知道了"
        });
      }
    }

    wx.showToast({ title: "已保存", icon: "success" });
    setTimeout(() => wx.navigateBack(), 500);
  },

  deleteMedicine() {
    wx.showModal({
      title: "删除药品",
      content: "删除后，这个药的提醒不会再出现。",
      confirmText: "删除",
      confirmColor: "#b02f20",
      success: (res) => {
        if (!res.confirm) return;
        const medicines = storage.getMedicines().filter((item) => item.id !== this.data.form.id);
        storage.saveMedicines(medicines);
        wx.navigateBack();
      }
    });
  }
});
