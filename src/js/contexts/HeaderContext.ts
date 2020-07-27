import React, { createContext } from "react";

export interface header {
    showSignup: boolean;
    showLogin: boolean;
}

export const defaultHeader: header = {
    showSignup: false,
    showLogin: false,
};

interface headerState {
    headerState: header;
    setHeaderState: React.Dispatch<React.SetStateAction<header>>;
}

const defaultHeaderState: headerState = {
    headerState: defaultHeader,
    setHeaderState: (): void => {},
};

const HeaderContext = createContext<headerState>(defaultHeaderState);
export { HeaderContext};
