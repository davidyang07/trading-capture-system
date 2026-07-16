import request from "@/utils/request";

// get user list (for admin)
export async function postUserSearch(params: { [T: string]: any }) {
  return request({
    url: "/api/v1/user/search",
    method: "POST",
    data: params,
  });
}

// get current user info
export async function getCurrent() {
  return request({
    url: "/api/v1/user/current",
    method: "GET",
  });
}
