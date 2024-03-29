import React, { createContext, useState } from "react";

export interface loggedIn {
  uid: string | null;
  userName: string | null;
  status: string | null;
  cash: number;
  notifications: Object[];
  "gated?": boolean;
}

export const defaultLoggedIn: loggedIn = {
  uid: null,
  userName: null,
  status: null,
  cash: -1,
  notifications: [],
  "gated?": false,
};

interface loggedInState {
  loggedInState: loggedIn;
  setLoggedInState: React.Dispatch<React.SetStateAction<loggedIn>>;
}

const defaultShitState: loggedInState = {
  loggedInState: defaultLoggedIn,
  setLoggedInState: (): void => {},
};

const LoginContext = createContext<loggedInState>(defaultShitState);
export { LoginContext};
