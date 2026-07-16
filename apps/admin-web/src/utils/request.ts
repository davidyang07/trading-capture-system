import axios from "axios";
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from "axios";
import { getToken, setRedirectUri } from "./auth";

export interface IRes {
  data: any;
  serviceStatus: {
    success: boolean;
    statusCode: number;
    errorMessage?: string;
  };
}

class Request {
  private static instance: AxiosInstance;

  constructor(config: AxiosRequestConfig) {
    if (!Request.instance) {
      Request.instance = axios.create(config);
      this.setupInterceptors(Request.instance);
    }
  }
  private setupInterceptors(instance: AxiosInstance) {
    instance.interceptors.request.use(
      (req) => {
        req.headers["Content-Type"] = "application/json";
        const token = getToken();
        if (token) {
          req.headers.Authorization = `Bearer ${token}`;
        }
        return req;
      },
      (error) => Promise.reject(error)
    );

    instance.interceptors.response.use(
      (res: AxiosResponse) => this.handleResponse(res),
      (err: any) => this.handleError(err)
    );
  }

  private handleResponse(response: AxiosResponse) {
    const { status, data } = response;
    const _msg = (window as any).$message ?? {};

    if (status === 200) {
      const { serviceStatus } = data ?? {};
      if (!serviceStatus?.success) {
        serviceStatus?.errorMessage &&
          _msg?.error?.({ detail: serviceStatus.errorMessage });
      }
      return Promise.resolve(data);
    }

    data?.errorMessage && _msg?.error?.({ detail: data.errorMessage });
    return Promise.reject(data);
  }

  private handleError(error: any) {
    const { response } = error;
    const _msg = (window as any).$message ?? {};
    //TODO: temporarily disabled 401 redirection since currently not using OAuth
    // if (response?.status === 401) {
    //   setRedirectUri(window.location.href);
    //   window.location.href = response?.data?.serviceStatus?.errorMessage || "/";
    // } else {
    response?.data?.serviceStatus?.errorMessage &&
      _msg?.error?.({ detail: response.data.serviceStatus.errorMessage });
    // }

    return Promise.reject(error);
  }

  public async request(config: AxiosRequestConfig): Promise<IRes> {
    const res = (await Request.instance.request(config)) as IRes;
    return res;
  }
}

const getBaseURL = (): string => {
  return process.env.APP_ROLE === "admin"
    ? process.env.API_BASE_URL_ADMIN!
    : process.env.API_BASE_URL_INVESTOR!;
};

const request = (config: AxiosRequestConfig): Promise<IRes> => {
  const instance = new Request({
    timeout: 1000 * 60 * 5,
    baseURL: getBaseURL(),
    withCredentials: true,
  });

  return instance.request(config);
};

export default request;
