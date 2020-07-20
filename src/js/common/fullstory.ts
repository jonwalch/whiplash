import * as FullStory from "@fullstory/browser";
import {loggedIn} from "../contexts/LoginContext";

export const identify = (loggedInState: loggedIn) => {
    // @ts-ignore
    FullStory.identify(loggedInState.uid,
        {displayName: loggedInState.userName,}
    )
}
