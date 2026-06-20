const DAY_MS = 24 * 60 * 60 * 1000;

function pad(value) {
  return String(value).padStart(2, "0");
}

function todayKey(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function displayDate(date = new Date()) {
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

function displayDayKey(dayKey) {
  const [year, month, day] = dayKey.split("-").map(Number);
  return `${year}年${month}月${day}日`;
}

function displayShortDayKey(dayKey) {
  const [year, month, day] = dayKey.split("-").map(Number);
  return `${month}月${day}日`;
}

function timeToMinutes(time) {
  const [hour, minute] = time.split(":").map(Number);
  return hour * 60 + minute;
}

function minutesToTime(totalMinutes) {
  const hour = Math.floor(totalMinutes / 60);
  const minute = totalMinutes % 60;
  return `${pad(hour)}:${pad(minute)}`;
}

function partOfDay(time) {
  const minutes = timeToMinutes(time);
  if (minutes < 11 * 60) return "早上";
  if (minutes < 14 * 60) return "中午";
  if (minutes < 18 * 60) return "下午";
  return "晚上";
}

function displayTime(time) {
  return `${partOfDay(time)} ${time}`;
}

function displayDateTime(dayKey, time) {
  return `${displayShortDayKey(dayKey)} ${displayTime(time)}`;
}

function weekdayIndex(dayKey) {
  return dateAtTime(dayKey, "00:00").getDay();
}

function displayWeekday(index) {
  return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][index] || "";
}

function dateAtTime(dayKey, time) {
  const [year, month, day] = dayKey.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  return new Date(year, month - 1, day, hour, minute, 0, 0);
}

function isToday(dayKey) {
  return dayKey === todayKey();
}

function addMinutes(timestamp, minutes) {
  return timestamp + minutes * 60 * 1000;
}

function startOfTomorrow() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime();
}

module.exports = {
  DAY_MS,
  addMinutes,
  dateAtTime,
  displayDateTime,
  displayDate,
  displayDayKey,
  displayShortDayKey,
  displayTime,
  displayWeekday,
  isToday,
  minutesToTime,
  partOfDay,
  startOfTomorrow,
  timeToMinutes,
  todayKey,
  weekdayIndex
};
