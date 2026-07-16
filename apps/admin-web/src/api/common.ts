import request from "@/utils/request";

export async function getCurrencyOptions() {
  return request({
    url: "/api/v1/currency",
    method: "GET",
  });
}

export async function getInstrumentOptions() {
  return request({
    url: "/api/v1/instrument",
    method: "GET",
  });
}
