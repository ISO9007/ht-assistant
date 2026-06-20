const KEYS = {
  medicines: "eat_helper_medicines",
  settings: "eat_helper_reminder_settings",
  records: "eat_helper_records"
};

const DEFAULT_SETTINGS = {
  inAppAlert: true,
  soundVibrate: true,
  subscribeMessage: true,
  phoneCalendar: true
};

function get(key, fallback) {
  try {
    const value = wx.getStorageSync(key);
    return value || fallback;
  } catch (error) {
    return fallback;
  }
}

function set(key, value) {
  wx.setStorageSync(key, value);
}

function getMedicines() {
  return get(KEYS.medicines, []);
}

function saveMedicines(medicines) {
  set(KEYS.medicines, medicines);
}

function getSettings() {
  return Object.assign({}, DEFAULT_SETTINGS, get(KEYS.settings, {}));
}

function saveSettings(settings) {
  set(KEYS.settings, Object.assign({}, DEFAULT_SETTINGS, settings));
}

function getRecords() {
  return get(KEYS.records, {});
}

function saveRecords(records) {
  set(KEYS.records, records);
}

module.exports = {
  DEFAULT_SETTINGS,
  getMedicines,
  getRecords,
  getSettings,
  saveMedicines,
  saveRecords,
  saveSettings
};
