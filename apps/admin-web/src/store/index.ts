import type { Action, ThunkAction } from "@reduxjs/toolkit";
import { combineSlices, configureStore } from "@reduxjs/toolkit";
import { userSlice } from "./user/userSlice";

// combines the reducers
const rootReducer = combineSlices(userSlice);

// creating unique store instances
export const makeStore = () => {
  return configureStore({
    reducer: rootReducer,
  });
};


// Infer the `RootState` type from the root reducer
export type RootState = ReturnType<typeof rootReducer>;
// Infer the return type of `makeStore`
export type AppStore = ReturnType<typeof makeStore>;
// Infer the `AppDispatch` type from the store itself
export type AppDispatch = AppStore["dispatch"];

export type AppThunk<ThunkReturnType = void> = ThunkAction<
  ThunkReturnType,
  RootState,
  unknown,
  Action
>;
