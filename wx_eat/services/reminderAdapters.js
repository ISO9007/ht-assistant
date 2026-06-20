const { dateAtTime, displayTime, todayKey } = require("../utils/date");

const SUBSCRIBE_TEMPLATE_IDS = [];

function getRuntimeInfo() {
  try {
    return wx.getSystemInfoSync();
  } catch (error) {
    return {};
  }
}

function isMobileRuntime() {
  const info = getRuntimeInfo();
  const platform = String(info.platform || "").toLowerCase();
  const system = String(info.system || "").toLowerCase();
  return platform === "ios" || platform === "android" || system.includes("ios") || system.includes("android");
}

function showDevtoolsSkipToast(featureName) {
  wx.showToast({
    title: `${featureName}请用真机测试`,
    icon: "none"
  });
}

function vibrate() {
  if (isMobileRuntime() && wx.vibrateLong) {
    wx.vibrateLong({ fail: () => {} });
  }
}

function playReminderSound() {
  if (!isMobileRuntime() || !wx.createInnerAudioContext) {
    return null;
  }

  const audio = wx.createInnerAudioContext();
  audio.src = "/assets/reminder.wav";
  audio.loop = true;
  audio.obeyMuteSwitch = false;
  audio.onError(() => {});
  audio.play();
  return audio;
}

function requestSubscribeMessage() {
  if (!wx.requestSubscribeMessage) {
    wx.showToast({ title: "当前微信版本不支持", icon: "none" });
    return Promise.resolve(false);
  }

  if (!SUBSCRIBE_TEMPLATE_IDS.length) {
    wx.showModal({
      title: "订阅消息待配置",
      content: "本期已预留授权入口。配置模板 ID 和后端后，可发送微信订阅提醒。",
      showCancel: false,
      confirmText: "知道了"
    });
    return Promise.resolve(false);
  }

  return new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: SUBSCRIBE_TEMPLATE_IDS,
      success: () => resolve(true),
      fail: () => resolve(false)
    });
  });
}

function normalizeCalendarEntry(reminderTime) {
  return typeof reminderTime === "string"
    ? { date: todayKey(), time: reminderTime }
    : {
        repeat: reminderTime.repeat || "once",
        date: reminderTime.date || todayKey(),
        time: reminderTime.time,
        weekdays: reminderTime.weekdays || []
      };
}

function buildCalendarPayload(medicine, reminderTime) {
  const entry = normalizeCalendarEntry(reminderTime);
  const start = dateAtTime(entry.date || todayKey(), entry.time).getTime();
  const startTime = Math.floor(start / 1000);
  const endTime = startTime + 10 * 60;

  return {
    title: `吃药提醒：${medicine.name}`,
    startTime,
    endTime,
    description: `${displayTime(entry.time)} ${medicine.dosage || ""} ${medicine.meal || ""}`,
    location: "华佗时间",
    alarmOffset: 0,
    repeat: entry.repeat || "once"
  };
}

function ensureCalendarRuntime(silent) {
  if (!isMobileRuntime()) {
    if (!silent) {
      showDevtoolsSkipToast("日历提醒");
    }
    return { ok: false, reason: "notMobileRuntime" };
  }

  if (!wx.addPhoneCalendar) {
    if (!silent) {
      wx.showToast({ title: "当前微信版本不支持日历", icon: "none" });
    }
    return { ok: false, reason: "apiUnavailable" };
  }

  return { ok: true };
}

function ensureCalendarPermission(silent) {
  if (!wx.getSetting || !wx.authorize) {
    return Promise.resolve({ ok: true, reason: "authApiUnavailable" });
  }

  return new Promise((resolve) => {
    wx.getSetting({
      success: (res) => {
        const setting = res.authSetting || {};
        if (setting["scope.addPhoneCalendar"]) {
          resolve({ ok: true });
          return;
        }

        wx.authorize({
          scope: "scope.addPhoneCalendar",
          success: () => resolve({ ok: true }),
          fail: (error) => {
            const errMsg = error && error.errMsg ? error.errMsg : "";
            if (!silent) {
              wx.showToast({ title: "请先授权日历", icon: "none" });
            }
            resolve({ ok: false, reason: "calendarAuthDenied", errMsg });
          }
        });
      },
      fail: (error) => {
        const errMsg = error && error.errMsg ? error.errMsg : "";
        resolve({ ok: true, reason: "getSettingFailed", errMsg });
      }
    });
  });
}

function invokeAddPhoneCalendar(payload, silent) {
  const { repeat, ...calendarPayload } = payload;
  return new Promise((resolve) => {
    wx.addPhoneCalendar({
      ...calendarPayload,
      success: () => resolve({ ok: true, api: "addPhoneCalendar" }),
      fail: (error) => {
        console.warn("addPhoneCalendar failed", error);
        if (!silent) {
          wx.showToast({ title: "日历写入失败，请检查授权", icon: "none" });
        }
        const errMsg = error && error.errMsg ? error.errMsg : "";
        resolve({
          ok: false,
          reason: errMsg.includes("no supporting apps") ? "noSupportingApps" : "failed",
          errMsg
        });
      }
    });
  });
}

function invokeAddPhoneRepeatCalendar(payload, silent) {
  if (!wx.addPhoneRepeatCalendar) {
    return Promise.resolve({ ok: false, reason: "repeatApiUnavailable" });
  }

  const repeatInterval = payload.repeat === "weekly" ? "week" : "day";
  const { repeat, ...calendarPayload } = payload;
  return new Promise((resolve) => {
    wx.addPhoneRepeatCalendar({
      ...calendarPayload,
      repeatInterval,
      success: () => resolve({ ok: true, api: "addPhoneRepeatCalendar" }),
      fail: (error) => {
        console.warn("addPhoneRepeatCalendar failed", error);
        const errMsg = error && error.errMsg ? error.errMsg : "";
        resolve({
          ok: false,
          reason: errMsg.includes("no supporting apps") ? "noSupportingApps" : "repeatFailed",
          errMsg
        });
      }
    });
  });
}

async function addPhoneCalendar(medicine, reminderTime, options = {}) {
  const silent = Boolean(options.silent);
  const runtimeCheck = ensureCalendarRuntime(silent);
  if (!runtimeCheck.ok) {
    return runtimeCheck;
  }

  const permissionCheck = await ensureCalendarPermission(silent);
  if (!permissionCheck.ok) {
    return permissionCheck;
  }

  const payload = buildCalendarPayload(medicine, reminderTime);
  if (payload.repeat === "daily" || payload.repeat === "weekly") {
    const repeatResult = await invokeAddPhoneRepeatCalendar(payload, silent);
    if (repeatResult.ok || repeatResult.reason !== "repeatApiUnavailable") {
      return repeatResult;
    }
  }

  const result = await invokeAddPhoneCalendar(payload, silent);
  if (result.ok || result.reason !== "noSupportingApps") {
    return result;
  }

  const repeatResult = await invokeAddPhoneRepeatCalendar(payload, silent);
  return repeatResult.ok ? repeatResult : result;
}

function testPhoneCalendar() {
  const now = new Date();
  now.setMinutes(now.getMinutes() + 5);
  const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  const time = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;

  return addPhoneCalendar(
    {
      name: "日历测试",
      dosage: "测试提醒",
      meal: "",
      note: ""
    },
    { date, time },
    { silent: true }
  );
}

module.exports = {
  addPhoneCalendar,
  isMobileRuntime,
  playReminderSound,
  requestSubscribeMessage,
  testPhoneCalendar,
  vibrate
};
