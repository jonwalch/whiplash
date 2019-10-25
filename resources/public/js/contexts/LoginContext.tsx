import React, { createContext, useState } from "react";

const defaultLoggedIn: shit = {userLoggedIn: null}
interface shitState {
  state: shit;
  setState: React.Dispatch<React.SetStateAction<shit>>;
}

const defaultShitState: shitState = {
  state: defaultLoggedIn,
  setState: (): void => {},
};

export interface shit {
  userLoggedIn: boolean | null;
}
const LoginContext = createContext<shitState>(defaultShitState);

const LoginProvider = (props: any) => {
  const [state, setState] = useState(defaultLoggedIn);
  return (
    <LoginContext.Provider value={{state, setState}}>
      {props.children}
    </LoginContext.Provider>
  );
};

export { LoginContext, LoginProvider };
