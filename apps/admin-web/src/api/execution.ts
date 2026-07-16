import request from "@/utils/request";

// execution list search
export async function postExecutionSearch(params: any) {
  return request({
    url: "/api/v1/execution/search",
    method: "POST",
    data: params,
  });
}

// execution deatil
export async function getExecutionDetail(id: any) {
  return request({
    url: "/api/v1/execution/" + id,
    method: "GET",
  });
}

// execution create
export async function postExecutionCreate(params: any) {
  return request({
    url: "/api/v1/execution",
    method: "POST",
    data: params,
  });
}

// execution correct
export async function postExecutionCorrect(params: any) {
  return request({
    url: "/api/v1/execution/correct",
    method: "POST",
    data: params,
  });
}

// execution correct
export async function postExecutionCancel(params: any) {
  return request({
    url: "/api/v1/execution/cancel",
    method: "POST",
    data: params,
  });
}
