import request from "@/utils/request";

// order list search
export async function postOrderSearch(params: any) {
  return request({
    url: "/api/v1/order/search",
    method: "POST",
    data: params,
  });
}
