import request from "@/utils/request";

// get token
export async function postAuthToken(params: any) {
  return request({
    url: "/api/v1/auth/token",
    method: "POST",
    data: params,
  });
}

// logout
export async function postLogout() {
  return request({
    url: "/api/v1/auth/logout",
    method: "POST",
  });
}
