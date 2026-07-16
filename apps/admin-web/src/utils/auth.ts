/**
 * token
 */
export const TOKEN_KEY = "tcs_token";

export const setToken = (token: string) => {
  localStorage.setItem(TOKEN_KEY, token);
};

export const getToken = () => {
  return localStorage.getItem(TOKEN_KEY);
};

export const removeToken = () => {
  localStorage.removeItem(TOKEN_KEY);
};

/**
 * redirect_uri
 */
export const REDIRECT_URI_KEY = "tcs_redirect_uri";

export const setRedirectUri = (uri: string) => {
  localStorage.setItem(REDIRECT_URI_KEY, uri);
};

export const getRedirectUri = () => {
  return localStorage.getItem(REDIRECT_URI_KEY);
};

export const removeRedirectUri = () => {
  localStorage.removeItem(REDIRECT_URI_KEY);
};
