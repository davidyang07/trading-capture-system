import { createSlice } from "@reduxjs/toolkit";
import type { PayloadAction } from "@reduxjs/toolkit";

export interface UserSliceState {
  userId?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  scopes?: string[];
}

const initialState: UserSliceState = {
  userId: "",
  firstName: "",
  lastName: "",
  email: "",
  scopes: [],
};

export const userSlice = createSlice({
  name: "user",
  initialState,
  reducers: {
    updateUserInfo: (
      state: UserSliceState,
      action: PayloadAction<UserSliceState>
    ) => {
      const { userId, firstName, lastName, email, scopes } =
        action.payload ?? {};
      state.userId = userId;
      state.firstName = firstName;
      state.lastName = lastName;
      state.email = email;
      state.scopes = scopes;
    },
    clearUserInfo: (state: UserSliceState) => {
      state.userId = initialState.userId;
      state.firstName = initialState.firstName;
      state.lastName = initialState.lastName;
      state.email = initialState.email;
      state.scopes = initialState.scopes;
    },
  },
  selectors: {
    selectUserInfo: (user: UserSliceState) => user,
  },
});

export const { updateUserInfo, clearUserInfo } = userSlice.actions;
export const { selectUserInfo } = userSlice.selectors;
