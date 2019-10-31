import React, { createContext, useState } from "react";

export const defaultLoggedIn: loggedIn = { userName: null };
interface loggedInState {
  loggedInState: loggedIn;
  setLoggedInState: React.Dispatch<React.SetStateAction<loggedIn>>;
}

const defaultShitState: loggedInState = {
  loggedInState: defaultLoggedIn,
  setLoggedInState: (): void => {},
};

export interface loggedIn {
  userName: string | null;
}
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
