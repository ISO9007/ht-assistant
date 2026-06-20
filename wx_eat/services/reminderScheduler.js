const storage = require("./storage");
const { addMinutes, dateAtTime, displayDateTime, displayTime, displayWeekday, todayKey, timeToMinutes, weekdayIndex } = require("../utils/date");

const SNOOZE_MINUTES = 10;

function makeId(prefix = "id") {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
}

function ensureSeedData() {
  storage.saveSettings(storage.getSettings());
}

function occurrenceId(medicineId, dayKey, time) {
  return `${medicineId}_${dayKey}_${time}`;
}

function normalizeReminderTime(item) {
  if (typeof item === "string") {
    return {
      repeat: "once",
      date: todayKey(),
      time: item
    };
  }

  const repeat = item.repeat || "once";
  const weekdays = Array.isArray(item.weekdays) ? item.weekdays.map(Number).filter((day) => day >= 0 && day <= 6) : [];

  return {
    repeat,
    date: repeat === "once" ? item.date || todayKey() : "",
    time: item.time,
    weekdays: repeat === "weekly" ? sortWeekdays(Array.from(new Set(weekdays))) : []
  };
}

function sortWeekdays(weekdays) {
  const rank = { 1: 1, 2: 2, 3: 3, 4: 4, 5: 5, 6: 6, 0: 7 };
  return weekdays.sort((a, b) => rank[a] - rank[b]);
}

function normalizeReminderTimes(times = []) {
  const unique = {};
  times.forEach((item) => {
    const entry = normalizeReminderTime(item);
    if (entry.time) {
      const key = `${entry.repeat}_${entry.date}_${entry.time}_${(entry.weekdays || []).join("-")}`;
      unique[key] = entry;
    }
  });

  return Object.values(unique).sort((a, b) => {
    const repeatCompare = repeatRank(a.repeat) - repeatRank(b.repeat);
    if (repeatCompare) return repeatCompare;
    const dateCompare = a.date.localeCompare(b.date);
    return dateCompare || timeToMinutes(a.time) - timeToMinutes(b.time);
  });
}

function repeatRank(repeat) {
  return { once: 0, daily: 1, weekly: 2 }[repeat] || 0;
}

function reminderKey(entry) {
  return `${entry.repeat}_${entry.date}_${entry.time}_${(entry.weekdays || []).join("-")}`;
}

function repeatLabel(entry) {
  if (entry.repeat === "daily") return "每天";
  if (entry.repeat === "weekly") return entry.weekdays.map(displayWeekday).join("、");
  return "仅一次";
}

function reminderDisplayLabel(entry) {
  if (entry.repeat === "daily") return `每天 ${displayTime(entry.time)}`;
  if (entry.repeat === "weekly") return `${repeatLabel(entry)} ${displayTime(entry.time)}`;
  return displayDateTime(entry.date, entry.time);
}

function expandReminderTimesForDay(times, dayKey) {
  const todayWeekday = weekdayIndex(dayKey);
  return normalizeReminderTimes(times)
    .filter((entry) => {
      if (entry.repeat === "daily") return true;
      if (entry.repeat === "weekly") return entry.weekdays.includes(todayWeekday);
      return entry.date === dayKey;
    })
    .map((entry) => ({
      ...entry,
      date: dayKey,
      sourceKey: reminderKey(entry),
      repeatLabel: repeatLabel(entry),
      displayLabel: reminderDisplayLabel(entry)
    }))
    .sort((a, b) => timeToMinutes(a.time) - timeToMinutes(b.time));
}

function getRecord(records, medicineId, dayKey, time) {
  return records[occurrenceId(medicineId, dayKey, time)];
}

function upsertRecord(records, medicine, dayKey, time, patch) {
  const id = occurrenceId(medicine.id, dayKey, time);
  records[id] = Object.assign(
    {
      id,
      medicineId: medicine.id,
      medicineName: medicine.name,
      dosage: medicine.dosage,
      meal: medicine.meal,
      note: medicine.note,
      dayKey,
      time,
      status: "pending",
      acknowledgedAt: null,
      nextReminderAt: dateAtTime(dayKey, time).getTime()
    },
    records[id] || {},
    patch
  );
  return records[id];
}

function markTaken(recordId) {
  const records = storage.getRecords();
  if (records[recordId]) {
    records[recordId].status = "taken";
    records[recordId].acknowledgedAt = Date.now();
    records[recordId].nextReminderAt = null;
    storage.saveRecords(records);
  }
}

function snooze(recordId) {
  const records = storage.getRecords();
  if (records[recordId]) {
    records[recordId].status = "due";
    records[recordId].nextReminderAt = addMinutes(Date.now(), SNOOZE_MINUTES);
    storage.saveRecords(records);
  }
}

function getTodayOccurrences() {
  const medicines = storage.getMedicines().filter((medicine) => medicine.enabled);
  const records = storage.getRecords();
  const dayKey = todayKey();
  const now = Date.now();

  return medicines
    .flatMap((medicine) =>
      expandReminderTimesForDay(medicine.times, dayKey)
        .map((entry, index, entries) => {
          const scheduledAt = dateAtTime(entry.date, entry.time).getTime();
          const record = getRecord(records, medicine.id, entry.date, entry.time);
          const baseStatus = scheduledAt <= now ? "missed" : "upcoming";
          const status = record ? record.status : baseStatus;

          return {
            id: occurrenceId(medicine.id, entry.date, entry.time),
            medicineId: medicine.id,
            medicineName: medicine.name,
            dosage: medicine.dosage,
            meal: medicine.meal,
            note: medicine.note,
            dayKey: entry.date,
            time: entry.time,
            repeat: entry.repeat,
            repeatLabel: entry.repeatLabel,
            timeLabel: entry.displayLabel,
            doseIndex: index + 1,
            totalDoses: entries.length,
            scheduledAt,
            status,
            nextReminderAt: record ? record.nextReminderAt : scheduledAt
          };
        })
    )
    .sort((a, b) => a.scheduledAt - b.scheduledAt);
}

function checkDueReminders() {
  const medicines = storage.getMedicines().filter((medicine) => medicine.enabled);
  const records = storage.getRecords();
  const dayKey = todayKey();
  const now = Date.now();
  let dueReminder = null;

  medicines.forEach((medicine) => {
    const reminderTimes = expandReminderTimesForDay(medicine.times, dayKey);
    reminderTimes.forEach((entry, index) => {
      const scheduledAt = dateAtTime(entry.date, entry.time).getTime();
      const record = getRecord(records, medicine.id, entry.date, entry.time);
      const nextReminderAt = record ? record.nextReminderAt : scheduledAt;
      const alreadyTaken = record && record.status === "taken";

      if (!alreadyTaken && scheduledAt <= now && nextReminderAt <= now) {
        const sameDateEntries = reminderTimes.filter((item) => item.date === entry.date);
        const sameDateIndex = sameDateEntries.findIndex((item) => item.time === entry.time);
        const due = upsertRecord(records, medicine, entry.date, entry.time, {
          status: "due",
          nextReminderAt: now,
          scheduledAt,
          timeLabel: entry.displayLabel,
          doseIndex: sameDateIndex >= 0 ? sameDateIndex + 1 : index + 1,
          totalDoses: sameDateEntries.length || reminderTimes.length
        });

        if (!dueReminder || scheduledAt < dueReminder.scheduledAt) {
          dueReminder = due;
        }
      }
    });
  });

  storage.saveRecords(records);
  return dueReminder;
}

function getNextOccurrence() {
  const occurrences = getTodayOccurrences().filter((item) => item.status !== "taken");
  const due = occurrences.find((item) => item.status === "due" || item.status === "missed");
  if (due) return due;

  const upcoming = occurrences.find((item) => item.status === "upcoming");
  if (upcoming) return upcoming;

  return null;
}

function normalizeMedicine(formData) {
  const times = normalizeReminderTimes(formData.times);

  return {
    id: formData.id || makeId("medicine"),
    name: formData.name.trim(),
    dosage: formData.dosage.trim(),
    meal: formData.meal,
    note: formData.note.trim(),
    enabled: formData.enabled !== false,
    times
  };
}

module.exports = {
  SNOOZE_MINUTES,
  checkDueReminders,
  ensureSeedData,
  getNextOccurrence,
  getTodayOccurrences,
  makeId,
  markTaken,
  normalizeMedicine,
  normalizeReminderTimes,
  reminderDisplayLabel,
  reminderKey,
  repeatLabel,
  occurrenceId,
  snooze
};
