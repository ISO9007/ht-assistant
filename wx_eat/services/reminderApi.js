const storage = require("./storage");

function listMedicines() {
  return Promise.resolve(storage.getMedicines());
}

function saveMedicines(medicines) {
  storage.saveMedicines(medicines);
  return Promise.resolve(medicines);
}

function getReminderSettings() {
  return Promise.resolve(storage.getSettings());
}

function saveReminderSettings(settings) {
  storage.saveSettings(settings);
  return Promise.resolve(storage.getSettings());
}

function getRecords() {
  return Promise.resolve(storage.getRecords());
}

function saveRecords(records) {
  storage.saveRecords(records);
  return Promise.resolve(records);
}

module.exports = {
  getRecords,
  getReminderSettings,
  listMedicines,
  saveMedicines,
  saveRecords,
  saveReminderSettings
};
