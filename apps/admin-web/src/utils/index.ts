import dayjs from "dayjs";

export const get7days = () => {
  const date = dayjs();
  const dates = [];
  for (let i = 0; i < 7; i++) {
    const day = date.subtract(i, "day").format("MM/DD");
    dates.push(day);
  }
  return dates.reverse();
};

export const get6months = () => {
  const arr = [
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
  ];
  const currentMonth = dayjs().month();
  const months = [];
  for (let i = 0; i < 6; i++) {
    const month = arr[(currentMonth - i + 12) % 12];
    months.push(month);
  }
  return months.reverse();
};
