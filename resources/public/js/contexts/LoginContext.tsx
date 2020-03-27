import React, { createContext, useState } from "react";

export interface loggedIn {
  userName: string | null;
  status: string;
  cash: number;
  notifications: Object[];
}
export const defaultLoggedIn: loggedIn = { userName: null, status: 'user.status/pending', cash: 0, notifications: []};

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
