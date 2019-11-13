import React, { createContext, useState } from "react";

export interface loggedIn {
  userName: string | null;
  cash: number | null;
}
export const defaultLoggedIn: loggedIn = { userName: null, cash: null};

interface loggedInState {
  loggedInState: loggedIn;
  setLoggedInState: React.Dispatch<React.SetStateAction<loggedIn>>;
}

const defaultShitState: loggedInState = {
  loggedInState: defaultLoggedIn,
  setLoggedInState: (): void => {},
};

const LoginContext = createContext<loggedInState>(defaultShitState);

const LoginProvider = (props: any) => {
  const [loggedInState, setLoggedInState] = useState(defaultLoggedIn);
  return (
    <LoginContext.Provider value={{loggedInState: loggedInState, setLoggedInState: setLoggedInState}}>
      {props.children}
    </LoginContext.Provider>
  );
};

export { LoginContext, LoginProvider };
