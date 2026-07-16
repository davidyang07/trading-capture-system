import request from "@/utils/request";

// account search
export async function postAccountSearch(params: { [T: string]: any }) {
  return request({
    url: "/api/v1/account/search",
    method: "POST",
    data: params,
  });
}

// add account
export async function postAccountAdd(params: { [T: string]: any }) {
  return request({
    url: "/api/v1/account",
    method: "POST",
    data: params,
  });
}

// security search
export async function postSecuritySearch(
  id: string,
  params: { [T: string]: any }
) {
  return request({
    url: "/api/v1/account/detail/" + id + "/security-balance",
    method: "POST",
    data: params,
  });
}

// add security transaction
export async function postAddSecurity(params: { [T: string]: any }) {
  return request({
    url: "/api/v1/account/security",
    method: "POST",
    data: params,
  });
}

// cash search
export async function postCashSearch(id: string, params: { [T: string]: any }) {
  return request({
    url: "/api/v1/account/detail/" + id + "/cash-balance",
    method: "POST",
    data: params,
  });
}

// add cash transaction
export async function postAddCash(params: { [T: string]: any }) {
  return request({
    url: "/api/v1/account/cash",
    method: "POST",
    data: params,
  });
}

// change account status
export async function patchAccountStatus(
  id: string,
  params: { [T: string]: any }
) {
  return request({
    url: "/api/v1/account/" + id + "/status",
    method: "patch",
    data: params,
  });
}
